
package com.example.smartpet.controller

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.smartpet.databinding.ActivityMonitoringBinding
import com.example.smartpet.model.SmartPetData
import com.example.smartpet.repository.SmartPetRepository
import com.example.smartpet.util.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
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

    // Coloca aquí tu nueva clave de Carto.
    private val CARTO_API_KEY = ""

    private lateinit var binding: ActivityMonitoringBinding
    private lateinit var smartPetRepository: SmartPetRepository
    private lateinit var auth: FirebaseAuth
    private var petDataListener: ValueEventListener? = null
    private lateinit var notificationHelper: NotificationHelper

    private lateinit var miniMapView: MapView
    private var miniPetMarker: Marker? = null
    private var miniHomeMarker: Marker? = null
    private var miniSafeZoneCircle: Polygon? = null

    private var baseLat = -33.4569
    private var baseLon = -70.6483

    // Detección de salida de zona segura.
    private var wasInsideSafeZoneLastTime = true
    private var hasNotifiedLowFood = false

    private val PREFS_NAME = "SmartPetProfilePrefs"
    private val KEY_PET_PHOTO = "uri_pet_photo"
    private val KEY_USER_PHOTO = "uri_user_photo"

    private val requestMultiplePermissionsLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val locationGranted =
                permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true

            if (!locationGranted) {
                Toast.makeText(
                    this,
                    "La ubicación es necesaria para el collar satelital",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val selectPetImageLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.GetContent()
        ) { uri: android.net.Uri? ->

            uri?.let {
                saveImageUriLocally(it, KEY_PET_PHOTO)
                binding.ivPetAvatar.setImageURI(it)

                Toast.makeText(
                    this,
                    "Foto de mascota actualizada",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val selectUserImageLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.GetContent()
        ) { uri: android.net.Uri? ->

            uri?.let {
                saveImageUriLocally(it, KEY_USER_PHOTO)
                binding.ivUserAvatar.setImageURI(it)

                Toast.makeText(
                    this,
                    "Foto de perfil actualizada",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val osmConfig = Configuration.getInstance()
        osmConfig.userAgentValue = packageName

        val cachePath = File(cacheDir, "carto_mini_tiles")
        osmConfig.osmdroidBasePath = cachePath
        osmConfig.osmdroidTileCache = File(cachePath, "tiles")

        osmConfig.load(
            this,
            PreferenceManager.getDefaultSharedPreferences(this)
        )

        binding = ActivityMonitoringBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
        smartPetRepository = SmartPetRepository()
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

        petDataListener = smartPetRepository.observePetData { data ->
            runOnUiThread {
                updateUI(data)
            }
        }
    }

    override fun onStop() {
        super.onStop()

        petDataListener?.let {
            smartPetRepository.removePetDataListener(it)
        }

        petDataListener = null
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
            ContextCompat.checkSelfPermission(this, it) !=
                    PackageManager.PERMISSION_GRANTED
        }

        if (listToRequest.isNotEmpty()) {
            requestMultiplePermissionsLauncher.launch(
                listToRequest.toTypedArray()
            )
        }
    }

    private fun saveImageUriLocally(uri: android.net.Uri, key: String) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(key, uri.toString())
            .apply()
    }

    private fun loadSavedAvatars() {

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val petUriStr = prefs.getString(KEY_PET_PHOTO, null)

        if (!petUriStr.isNullOrEmpty()) {
            try {
                binding.ivPetAvatar.setImageURI(
                    android.net.Uri.parse(petUriStr)
                )
            } catch (e: Exception) {
                prefs.edit().remove(KEY_PET_PHOTO).apply()
            }
        }

        val userUriStr = prefs.getString(KEY_USER_PHOTO, null)

        if (!userUriStr.isNullOrEmpty()) {
            try {
                binding.ivUserAvatar.setImageURI(
                    android.net.Uri.parse(userUriStr)
                )
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

            val fallback = email
                ?.substringBefore("@")
                ?.replaceFirstChar { it.uppercase() }
                ?: "Propietario"

            binding.tvWelcomeUser.text = "Hola, $fallback"
        }
    }

    private fun updateUI(data: SmartPetData) {
        android.util.Log.d(
            "SMARTPET_MONITOR",
            "ESTADO RECIBIDO: isInsideSafeZone=${data.isInsideSafeZone}, distancia=${data.distanceFromBaseMeters}, radio=${data.safeZoneRadiusMeters}"
        )

        binding.tvPetName.text = "${data.petName} ✏️"

        binding.tvPetBreed.text =
            "Raza: ${data.petBreed}  •  Edad: ${data.petAge} años"

        binding.tvFoodPercent.text = "${data.foodPercent}%"
        binding.pbFood.progress = data.foodPercent

        binding.tvFoodWeight.text =
            "%.1f g disponibles".format(data.foodWeightGrams)

        binding.tvWaterPercent.text = "${data.waterPercent}%"
        binding.pbWater.progress = data.waterPercent

        binding.tvWaterWeight.text =
            "%.0f ml restantes".format(data.waterWeightGrams)

        binding.tvGpsCoords.text =
            "GPS: %.4f, %.4f".format(data.latitude, data.longitude)

        binding.tvGpsDistance.text =
            "Distancia: %.1f m".format(data.distanceFromBaseMeters)

        // Actualizar coordenadas reales del hogar.
        baseLat = data.homeLatitude
        baseLon = data.homeLongitude

        updateMiniMap(data)

        // Estado de la geocerca.
        if (data.isInsideSafeZone) {

            binding.layoutSafeZoneStatus.setBackgroundColor(
                Color.parseColor("#F0FDF4")
            )

            binding.tvSafeZoneStatus.text = "✔ Dentro de Zona Segura"

            binding.tvSafeZoneStatus.setTextColor(
                Color.parseColor("#166534")
            )

            binding.tvSafeZoneSubtitle.text =
                "Radio activo: %.0f m | Tu mascota está segura"
                    .format(data.safeZoneRadiusMeters)

        } else {

            binding.layoutSafeZoneStatus.setBackgroundColor(
                Color.parseColor("#FEF2F2")
            )

            binding.tvSafeZoneStatus.text =
                "⚠️ ¡FUERA DE ZONA SEGURA!"

            binding.tvSafeZoneStatus.setTextColor(
                Color.parseColor("#B91C1C")
            )

            binding.tvSafeZoneSubtitle.text =
                "¡Alerta: Superó los %.0f m permitidos!"
                    .format(data.safeZoneRadiusMeters)
        }

        // Estado de conexión.
        if (data.isDeviceOnline) {
            binding.tvConnectionStatus.text = "🟢 En línea"
            binding.tvConnectionStatus.setTextColor(
                Color.parseColor("#15803D")
            )
        } else {
            binding.tvConnectionStatus.text = "🔴 Desconectado"
            binding.tvConnectionStatus.setTextColor(
                Color.parseColor("#DC2626")
            )
        }

        // Notificación original de salida de zona segura.
        if (!data.isInsideSafeZone && wasInsideSafeZoneLastTime) {

            notificationHelper.sendSafeZoneBreachAlert(data.petName)

            wasInsideSafeZoneLastTime = false

        } else if (data.isInsideSafeZone) {

            wasInsideSafeZoneLastTime = true
        }

        // Notificación de comida baja.
        if (data.foodPercent <= 15 && !hasNotifiedLowFood) {

            notificationHelper.sendLowFoodAlert(
                data.petName,
                data.foodPercent
            )

            hasNotifiedLowFood = true

        } else if (data.foodPercent > 20) {

            hasNotifiedLowFood = false
        }
    }

    private fun updateMiniMap(data: SmartPetData) {

        val petPoint = GeoPoint(data.latitude, data.longitude)
        val homePoint = GeoPoint(baseLat, baseLon)

        // Marcador del hogar.
        if (miniHomeMarker == null) {
            miniHomeMarker = Marker(miniMapView).apply {
                position = homePoint
                setAnchor(
                    Marker.ANCHOR_CENTER,
                    Marker.ANCHOR_BOTTOM
                )
                title = "Hogar"
            }

            miniMapView.overlays.add(miniHomeMarker)
        } else {
            miniHomeMarker?.position = homePoint
        }

        // Marcador de la mascota.
        if (miniPetMarker == null) {
            miniPetMarker = Marker(miniMapView).apply {
                position = petPoint
                setAnchor(
                    Marker.ANCHOR_CENTER,
                    Marker.ANCHOR_CENTER
                )
                title = data.petName
            }

            miniMapView.overlays.add(miniPetMarker)
            miniMapView.controller.setCenter(petPoint)
        } else {
            miniPetMarker?.position = petPoint
            miniPetMarker?.title = data.petName
        }

        // Geocerca.
        miniSafeZoneCircle?.let {
            miniMapView.overlays.remove(it)
        }

        val circlePoints = Polygon.pointsAsCircle(
            homePoint,
            data.safeZoneRadiusMeters.toDouble()
        )

        miniSafeZoneCircle = Polygon(miniMapView).apply {

            points = circlePoints

            fillPaint.color =
                if (data.isInsideSafeZone) 0x334ADE80 else 0x33F87171

            outlinePaint.color =
                if (data.isInsideSafeZone) {
                    Color.parseColor("#16A34A")
                } else {
                    Color.parseColor("#DC2626")
                }

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

            smartPetRepository.getPetData()
                .addOnSuccessListener { snapshot ->

                    val currentData =
                        snapshot.getValue(SmartPetData::class.java)
                            ?: SmartPetData()

                    val maxCapacity =
                        if (currentData.foodMaxCapacityGrams > 0) {
                            currentData.foodMaxCapacityGrams
                        } else {
                            350.0
                        }

                    val doseToAdd =
                        if (currentData.foodDoseGrams > 0) {
                            currentData.foodDoseGrams
                        } else {
                            50.0
                        }

                    val newWeight = min(
                        maxCapacity,
                        currentData.foodWeightGrams + doseToAdd
                    )

                    val newPercent =
                        ((newWeight / maxCapacity) * 100).toInt()

                    val updates = mapOf(
                        "foodWeightGrams" to newWeight,
                        "foodPercent" to newPercent,
                        "dispenseFoodTrigger" to true,
                        "lastUpdated" to System.currentTimeMillis()
                    )

                    smartPetRepository.updatePetData(updates)
                        .addOnCompleteListener { task ->

                            binding.btnDispenseFood.isEnabled = true
                            binding.btnDispenseFood.text = "Dispensar comida"

                            if (task.isSuccessful) {
                                Toast.makeText(
                                    this,
                                    "Se sirvieron %.0f g de alimento"
                                        .format(doseToAdd),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    this,
                                    "Error al dispensar alimento",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                }
                .addOnFailureListener {

                    binding.btnDispenseFood.isEnabled = true
                    binding.btnDispenseFood.text = "Dispensar comida"

                    Toast.makeText(
                        this,
                        "Error al dispensar alimento",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }

        binding.btnDispenseWater.setOnClickListener {

            binding.btnDispenseWater.isEnabled = false
            binding.btnDispenseWater.text = "Suministrando..."

            smartPetRepository.getPetData()
                .addOnSuccessListener { snapshot ->

                    val currentData =
                        snapshot.getValue(SmartPetData::class.java)
                            ?: SmartPetData()

                    val maxCapacity = 1000.0
                    val doseToAdd = 150.0

                    val newWeight = min(
                        maxCapacity,
                        currentData.waterWeightGrams + doseToAdd
                    )

                    val newPercent =
                        ((newWeight / maxCapacity) * 100).toInt()

                    val updates = mapOf(
                        "waterWeightGrams" to newWeight,
                        "waterPercent" to newPercent,
                        "dispenseWaterTrigger" to true,
                        "lastUpdated" to System.currentTimeMillis()
                    )

                    smartPetRepository.updatePetData(updates)
                        .addOnCompleteListener { task ->

                            binding.btnDispenseWater.isEnabled = true
                            binding.btnDispenseWater.text = "Dispensar agua"

                            if (task.isSuccessful) {
                                Toast.makeText(
                                    this,
                                    "Se suministraron 150ml de agua",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    this,
                                    "Error al suministrar agua",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                }
                .addOnFailureListener {

                    binding.btnDispenseWater.isEnabled = true
                    binding.btnDispenseWater.text = "Dispensar agua"

                    Toast.makeText(
                        this,
                        "Error al suministrar agua",
                        Toast.LENGTH_SHORT
                    ).show()
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
            setText(
                binding.tvPetName.text.toString()
                    .replace(" ✏️", "")
            )
        }

        val etBreed = EditText(this).apply {
            hint = "Raza de la mascota"
            setText(
                binding.tvPetBreed.text.toString()
                    .substringAfter("Raza: ")
                    .substringBefore("  •")
            )
        }

        val etAge = EditText(this).apply {
            hint = "Edad de la mascota (años)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

        layout.addView(etName)
        layout.addView(etBreed)
        layout.addView(etAge)

        smartPetRepository.getPetAge()
            .addOnSuccessListener { snapshot ->
                etAge.setText(
                    snapshot.getValue(Int::class.java)?.toString() ?: ""
                )
            }

        AlertDialog.Builder(this)
            .setTitle("Editar Perfil de Mascota")
            .setView(layout)
            .setPositiveButton("Guardar") { _, _ ->

                val newName = etName.text.toString().trim()
                val newBreed = etBreed.text.toString().trim()
                val newAge = etAge.text.toString().trim().toIntOrNull()

                if (newName.isEmpty() || newBreed.isEmpty() || newAge == null) {

                    Toast.makeText(
                        this,
                        "Completa el nombre, la raza y la edad correctamente",
                        Toast.LENGTH_SHORT
                    ).show()

                } else if (newAge !in 0..30) {

                    Toast.makeText(
                        this,
                        "La edad debe estar entre 0 y 30 años",
                        Toast.LENGTH_SHORT
                    ).show()

                } else {

                    val updates = mapOf(
                        "petName" to newName,
                        "petBreed" to newBreed,
                        "petAge" to newAge,
                        "lastUpdated" to System.currentTimeMillis()
                    )

                    smartPetRepository.updatePetData(updates)
                        .addOnSuccessListener {
                            Toast.makeText(
                                this,
                                "Perfil actualizado correctamente",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .addOnFailureListener { error ->
                            Toast.makeText(
                                this,
                                "Error al actualizar: ${error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
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

        binding.sectionLocation.setOnClickListener {
            startActivity(Intent(this, LocationActivity::class.java))
        }

        binding.cardMiniMapContainer.setOnClickListener {
            startActivity(Intent(this, LocationActivity::class.java))
        }
    }
}
