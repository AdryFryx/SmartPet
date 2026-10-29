package com.example.smartpet.controller

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.smartpet.databinding.ActivityMonitoringBinding
import com.example.smartpet.model.SmartPetData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.io.File
import kotlin.math.min

class MonitoringActivity : AppCompatActivity() {

    private val CARTO_API_KEY = "cb1_4x9i_1_046a8cb08f91bcfa56d6d119"

    // Vistas y Firebase
    private lateinit var binding: ActivityMonitoringBinding
    private lateinit var databaseRef: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private var petDataListener: ValueEventListener? = null
    private lateinit var notificationHelper: NotificationHelper

    // Mini-Mapa Carto
    private lateinit var miniMapView: MapView
    private var miniPetMarker: Marker? = null
    private var miniHomeMarker: Marker? = null
    private var miniSafeZoneCircle: Polygon? = null

    private var baseLat = -33.4569
    private var baseLon = -70.6483

    // Estados de alerta
    private var wasInsideSafeZoneLastTime = true
    private var hasNotifiedLowFood = false

    private val PREFS_NAME = "SmartPetProfilePrefs"
    private val KEY_PET_PHOTO = "uri_pet_photo"
    private val KEY_USER_PHOTO = "uri_user_photo"

    // Lanzador múltiple para permisos del sistema
    private val requestMultiplePermissionsLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (!locationGranted) {
            Toast.makeText(this, "La ubicación es necesaria para el collar satelital", Toast.LENGTH_SHORT).show()
        }
    }

    // Selectores para fotos de perfil
    private val selectPetImageLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            saveImageUriLocally(it, KEY_PET_PHOTO)
            binding.ivPetAvatar.setImageURI(it)
            Toast.makeText(this, "Foto de mascota actualizada", Toast.LENGTH_SHORT).show()
        }
    }

    private val selectUserImageLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            saveImageUriLocally(it, KEY_USER_PHOTO)
            binding.ivUserAvatar.setImageURI(it)
            Toast.makeText(this, "Foto de perfil actualizada", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configuración de OpenStreetMap y Carto
        val osmConfig = Configuration.getInstance()
        osmConfig.userAgentValue = packageName
        val cachePath = File(cacheDir, "carto_mini_tiles")
        osmConfig.osmdroidBasePath = cachePath
        osmConfig.osmdroidTileCache = File(cachePath, "tiles")
        osmConfig.load(this, PreferenceManager.getDefaultSharedPreferences(this))

        binding = ActivityMonitoringBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializar Mini-Mapa
        miniMapView = binding.miniMapView
        val cartoSource = XYTileSource(
            "CartoMiniMap",
            0,
            20,
            256,
            ".png?key=$CARTO_API_KEY",
            arrayOf(
                "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
                "https://b.basemaps.cartocdn.com/rastertiles/voyager/",
                "https://c.basemaps.cartocdn.com/rastertiles/voyager/"
            )
        )
        miniMapView.setTileSource(cartoSource)
        miniMapView.setMultiTouchControls(true)
        miniMapView.controller.setZoom(16.8)
        miniMapView.controller.setCenter(GeoPoint(baseLat, baseLon))

        auth = FirebaseAuth.getInstance()
        databaseRef = FirebaseDatabase.getInstance().getReference("smartpet_simulation")
        notificationHelper = NotificationHelper(this)

        checkAndRequestAppPermissions()
        setupUserHeader()
        setupListeners()
        setupNavigation()
        loadSavedAvatars()

        binding.ivPetAvatar.setOnClickListener {
            selectPetImageLauncher.launch("image/*")
        }

        binding.ivUserAvatar.setOnClickListener {
            selectUserImageLauncher.launch("image/*")
        }
    }

    override fun onStart() {
        super.onStart()
        listenToRealtimeData()
    }

    override fun onStop() {
        super.onStop()
        petDataListener?.let { databaseRef.removeEventListener(it) }
    }

    override fun onResume() {
        super.onResume()
        miniMapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        miniMapView.onPause()
    }

    private fun checkAndRequestAppPermissions() {
        val permissionsNeeded = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsNeeded.add(android.Manifest.permission.POST_NOTIFICATIONS)
            permissionsNeeded.add(android.Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissionsNeeded.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsNeeded.add(android.Manifest.permission.BLUETOOTH_SCAN)
            permissionsNeeded.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        }

        val listToRequest = permissionsNeeded.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (listToRequest.isNotEmpty()) {
            requestMultiplePermissionsLauncher.launch(listToRequest.toTypedArray())
        }
    }

    private fun saveImageUriLocally(uri: android.net.Uri, key: String) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putString(key, uri.toString()).apply()
    }

    private fun loadSavedAvatars() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val petUriStr = prefs.getString(KEY_PET_PHOTO, null)
        if (!petUriStr.isNullOrEmpty()) {
            try {
                binding.ivPetAvatar.setImageURI(android.net.Uri.parse(petUriStr))
            } catch (e: Exception) {
                prefs.edit().remove(KEY_PET_PHOTO).apply()
            }
        }

        val userUriStr = prefs.getString(KEY_USER_PHOTO, null)
        if (!userUriStr.isNullOrEmpty()) {
            try {
                binding.ivUserAvatar.setImageURI(android.net.Uri.parse(userUriStr))
            } catch (e: Exception) {
                prefs.edit().remove(KEY_USER_PHOTO).apply()
            }
        }
    }

    private fun setupUserHeader() {
        val userDisplayName = auth.currentUser?.displayName
        if (!userDisplayName.isNullOrEmpty()) {
            binding.tvWelcomeUser.text = "Hola, $userDisplayName"
        } else {
            val email = auth.currentUser?.email
            val fallback = email?.substringBefore("@")?.replaceFirstChar { it.uppercase() } ?: "Propietario"
            binding.tvWelcomeUser.text = "Hola, $fallback"
        }
    }

    private fun listenToRealtimeData() {
        petDataListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val data = snapshot.getValue(SmartPetData::class.java)
                if (data != null) {
                    updateUI(data)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MonitoringActivity, "Error de sincronización: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }
        databaseRef.addValueEventListener(petDataListener!!)
    }

    private fun updateUI(data: SmartPetData) {
        // 1. Mascota
        binding.tvPetName.text = "${data.petName} ✏️"
        binding.tvPetBreed.text = data.petBreed

        // 2. Comida
        binding.tvFoodPercent.text = "${data.foodPercent}%"
        binding.pbFood.progress = data.foodPercent
        binding.tvFoodWeight.text = "%.1f g disponibles".format(data.foodWeightGrams)

        // 3. Agua
        binding.tvWaterPercent.text = "${data.waterPercent}%"
        binding.pbWater.progress = data.waterPercent
        binding.tvWaterWeight.text = "%.0f ml restantes".format(data.waterWeightGrams)

        // 4. Ubicación y Mini-Mapa Carto
        binding.tvGpsCoords.text = "GPS: %.4f, %.4f".format(data.latitude, data.longitude)
        binding.tvGpsDistance.text = "Distancia: %.1f m".format(data.distanceFromBaseMeters)
        updateMiniMap(data)

        // 5. Estado de la Geocerca
        if (data.isInsideSafeZone) {
            binding.layoutSafeZoneStatus.setBackgroundColor(Color.parseColor("#F0FDF4"))
            binding.tvSafeZoneStatus.text = "✔ Dentro de Zona Segura"
            binding.tvSafeZoneStatus.setTextColor(Color.parseColor("#166534"))
            binding.tvSafeZoneSubtitle.text = "Radio activo: %.0f m | Tu mascota está segura".format(data.safeZoneRadiusMeters)
        } else {
            binding.layoutSafeZoneStatus.setBackgroundColor(Color.parseColor("#FEF2F2"))
            binding.tvSafeZoneStatus.text = "⚠️ ¡FUERA DE ZONA SEGURA!"
            binding.tvSafeZoneStatus.setTextColor(Color.parseColor("#B91C1C"))
            binding.tvSafeZoneSubtitle.text = "¡Alerta: Superó los %.0f m permitidos!".format(data.safeZoneRadiusMeters)
        }

        // 6. Conexión del collar
        if (data.isDeviceOnline) {
            binding.tvConnectionStatus.text = "🟢 En línea"
            binding.tvConnectionStatus.setTextColor(Color.parseColor("#15803D"))
        } else {
            binding.tvConnectionStatus.text = "🔴 Desconectado"
            binding.tvConnectionStatus.setTextColor(Color.parseColor("#DC2626"))
        }

        // 7. Notificaciones locales automáticas
        if (!data.isInsideSafeZone && wasInsideSafeZoneLastTime) {
            notificationHelper.sendSafeZoneBreachAlert(data.petName)
            wasInsideSafeZoneLastTime = false
        } else if (data.isInsideSafeZone) {
            wasInsideSafeZoneLastTime = true
        }

        if (data.foodPercent <= 15 && !hasNotifiedLowFood) {
            notificationHelper.sendLowFoodAlert(data.petName, data.foodPercent)
            hasNotifiedLowFood = true
        } else if (data.foodPercent > 20) {
            hasNotifiedLowFood = false
        }
    }

    /**
     * Dibuja las calles de Carto, la base, la mascota y la geocerca en el mini-mapa del Dashboard
     */
    private fun updateMiniMap(data: SmartPetData) {
        val petPoint = GeoPoint(data.latitude, data.longitude)
        val homePoint = GeoPoint(baseLat, baseLon)

        // Marcador Hogar (Base)
        if (miniHomeMarker == null) {
            miniHomeMarker = Marker(miniMapView).apply {
                position = homePoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Hogar"
            }
            miniMapView.overlays.add(miniHomeMarker)
        } else {
            miniHomeMarker?.position = homePoint
        }

        // Marcador Mascota
        if (miniPetMarker == null) {
            miniPetMarker = Marker(miniMapView).apply {
                position = petPoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = data.petName
            }
            miniMapView.overlays.add(miniPetMarker)
            miniMapView.controller.setCenter(petPoint)
        } else {
            miniPetMarker?.position = petPoint
            miniPetMarker?.title = data.petName
        }

        // Geocerca (Círculo de seguridad)
        miniSafeZoneCircle?.let { miniMapView.overlays.remove(it) }
        val circlePoints = Polygon.pointsAsCircle(homePoint, data.safeZoneRadiusMeters.toDouble())
        miniSafeZoneCircle = Polygon(miniMapView).apply {
            points = circlePoints
            fillPaint.color = if (data.isInsideSafeZone) 0x334ADE80 else 0x33F87171
            outlinePaint.color = if (data.isInsideSafeZone) Color.parseColor("#16A34A") else Color.parseColor("#DC2626")
            outlinePaint.strokeWidth = 2.5f
        }
        miniMapView.overlays.add(0, miniSafeZoneCircle)

        miniMapView.invalidate()
    }

    private fun setupListeners() {
        binding.tvPetName.setOnClickListener {
            showEditPetDialog()
        }

        binding.btnDispenseFood.setOnClickListener {
            binding.btnDispenseFood.isEnabled = false
            binding.btnDispenseFood.text = "Dispensando..."

            databaseRef.get().addOnSuccessListener { snapshot ->
                val currentData = snapshot.getValue(SmartPetData::class.java) ?: SmartPetData()

                val maxCapacity = if (currentData.foodMaxCapacityGrams > 0) currentData.foodMaxCapacityGrams else 350.0
                val doseToAdd = if (currentData.foodDoseGrams > 0) currentData.foodDoseGrams else 50.0

                val newWeight = min(maxCapacity, currentData.foodWeightGrams + doseToAdd)
                val newPercent = ((newWeight / maxCapacity) * 100).toInt()

                val updates = mapOf(
                    "foodWeightGrams" to newWeight,
                    "foodPercent" to newPercent,
                    "dispenseFoodTrigger" to true,
                    "lastUpdated" to System.currentTimeMillis()
                )

                databaseRef.updateChildren(updates).addOnCompleteListener {
                    Handler(Looper.getMainLooper()).postDelayed({
                        binding.btnDispenseFood.isEnabled = true
                        binding.btnDispenseFood.text = "Dispensar comida"
                        Toast.makeText(this, "Se sirvieron %.0f g de alimento".format(doseToAdd), Toast.LENGTH_SHORT).show()
                    }, 1500L)
                }
            }.addOnFailureListener {
                binding.btnDispenseFood.isEnabled = true
                binding.btnDispenseFood.text = "Dispensar comida"
                Toast.makeText(this, "Error al dispensar alimento", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnDispenseWater.setOnClickListener {
            binding.btnDispenseWater.isEnabled = false
            binding.btnDispenseWater.text = "Suministrando..."

            databaseRef.get().addOnSuccessListener { snapshot ->
                val currentData = snapshot.getValue(SmartPetData::class.java) ?: SmartPetData()

                val maxCapacity = 1000.0
                val doseToAdd = 150.0
                val newWeight = min(maxCapacity, currentData.waterWeightGrams + doseToAdd)
                val newPercent = ((newWeight / maxCapacity) * 100).toInt()

                val updates = mapOf(
                    "waterWeightGrams" to newWeight,
                    "waterPercent" to newPercent,
                    "dispenseWaterTrigger" to true,
                    "lastUpdated" to System.currentTimeMillis()
                )

                databaseRef.updateChildren(updates).addOnCompleteListener {
                    Handler(Looper.getMainLooper()).postDelayed({
                        binding.btnDispenseWater.isEnabled = true
                        binding.btnDispenseWater.text = "Dispensar agua"
                        Toast.makeText(this, "Se suministraron 150ml de agua", Toast.LENGTH_SHORT).show()
                    }, 1500L)
                }
            }.addOnFailureListener {
                binding.btnDispenseWater.isEnabled = true
                binding.btnDispenseWater.text = "Dispensar agua"
                Toast.makeText(this, "Error al suministrar agua", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEditPetDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val etName = EditText(this).apply {
            hint = "Nombre de la mascota"
            setText(binding.tvPetName.text.toString().replace(" ✏️", ""))
        }
        val etBreed = EditText(this).apply {
            hint = "Raza y edad (ej. Beagle • 2 años)"
            setText(binding.tvPetBreed.text.toString())
        }

        layout.addView(etName)
        layout.addView(etBreed)

        AlertDialog.Builder(this)
            .setTitle("Editar Perfil de Mascota")
            .setView(layout)
            .setPositiveButton("Guardar") { _, _ ->
                val newName = etName.text.toString().trim()
                val newBreed = etBreed.text.toString().trim()
                if (newName.isNotEmpty()) {
                    databaseRef.child("petName").setValue(newName)
                }
                if (newBreed.isNotEmpty()) {
                    databaseRef.child("petBreed").setValue(newBreed)
                }
                Toast.makeText(this, "Perfil actualizado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun setupNavigation() {
        binding.navHome.setOnClickListener {
            binding.mainScrollView.smoothScrollTo(0, 0)
        }

        binding.navFood.setOnClickListener {
            startActivity(Intent(this, FoodActivity::class.java))
        }

        binding.navLocation.setOnClickListener {
            startActivity(Intent(this, LocationActivity::class.java))
        }

        binding.navSettings.setOnClickListener {
            startActivity(Intent(this, ActivatorActivity::class.java))
        }

        // Tocar la tarjeta de ubicación o el mapa en el Dashboard abre la pantalla completa
        binding.sectionLocation.setOnClickListener {
            startActivity(Intent(this, LocationActivity::class.java))
        }

        binding.cardMiniMapContainer.setOnClickListener {
            startActivity(Intent(this, LocationActivity::class.java))
        }
    }
}