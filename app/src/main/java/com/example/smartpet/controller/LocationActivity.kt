package com.example.smartpet.controller

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.preference.PreferenceManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.smartpet.R
import com.example.smartpet.model.SmartPetData
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
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

class LocationActivity : AppCompatActivity() {

    private val CARTO_API_KEY = "cb1_4x9i_1_046a8cb08f91bcfa56d6d119"

    private lateinit var mapView: MapView
    private lateinit var databaseRef: DatabaseReference
    private var petListener: ValueEventListener? = null

    // Proveedor GPS
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    // Marcadores
    private var myLocationMarker: Marker? = null
    private var petMarker: Marker? = null
    private var homeMarker: Marker? = null
    private var safeZoneCircle: Polygon? = null

    // Puntos geográficos y variables de estado
    private var myCurrentPoint: GeoPoint? = null
    private var petCurrentPoint: GeoPoint = GeoPoint(-33.4569, -70.6483)
    private var homePoint: GeoPoint = GeoPoint(-33.4569, -70.6483)
    private var currentSafeRadius: Float = 50.0f
    private var currentPetName: String = "Mascota"

    // Vistas
    private lateinit var tvLocationSubtitle: TextView
    private lateinit var tvLiveStatus: TextView
    private lateinit var tvLiveDistance: TextView
    private lateinit var tvLiveCoords: TextView
    private lateinit var btnMyLocation: FloatingActionButton
    private lateinit var btnSetHomeHere: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val osmConfig = Configuration.getInstance()
        osmConfig.userAgentValue = packageName
        val cachePath = File(cacheDir, "carto_tiles")
        osmConfig.osmdroidBasePath = cachePath
        osmConfig.osmdroidTileCache = File(cachePath, "tiles")
        osmConfig.load(this, PreferenceManager.getDefaultSharedPreferences(this))

        setContentView(R.layout.activity_location)

        mapView = findViewById(R.id.mapView)

        val cartoSource = XYTileSource(
            "CartoVoyagerAuth",
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
        mapView.setTileSource(cartoSource)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(17.5)

        tvLocationSubtitle = findViewById(R.id.tvLocationSubtitle)
        tvLiveStatus = findViewById(R.id.tvLiveStatus)
        tvLiveDistance = findViewById(R.id.tvLiveDistance)
        tvLiveCoords = findViewById(R.id.tvLiveCoords)
        btnMyLocation = findViewById(R.id.btnMyLocation)
        btnSetHomeHere = findViewById(R.id.btnSetHomeHere)

        findViewById<ImageButton>(R.id.btnBackFromLocation).setOnClickListener {
            finish()
        }

        // Botón flotante: Centra el mapa en tu posición actual
        btnMyLocation.setOnClickListener {
            myCurrentPoint?.let {
                mapView.controller.animateTo(it)
                mapView.controller.setZoom(18.0)
            } ?: Toast.makeText(this, "Buscando satélites GPS...", Toast.LENGTH_SHORT).show()
        }

        // Botón en tarjeta: Reubica la Geocerca/Hogar donde estés parado
        btnSetHomeHere.setOnClickListener {
            setGeofenceToCurrentLocation()
        }

        databaseRef = FirebaseDatabase.getInstance().getReference("smartpet_simulation")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupLocationUpdates()
    }

    private fun setupLocationUpdates() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val userPoint = GeoPoint(loc.latitude, loc.longitude)
                myCurrentPoint = userPoint

                // Actualizar o crear marcador del usuario (Tú)
                if (myLocationMarker == null) {
                    myLocationMarker = Marker(mapView).apply {
                        position = userPoint
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = "Mi Ubicación (Tú)"
                    }
                    mapView.overlays.add(myLocationMarker)
                    mapView.controller.animateTo(userPoint)
                } else {
                    myLocationMarker?.position = userPoint
                }

                mapView.invalidate()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationTracking() {
        val fineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        if (fineLocation == PackageManager.PERMISSION_GRANTED) {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
                .setMinUpdateIntervalMillis(1500L)
                .build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }

    /**
     * Mueve el punto de la Base (Hogar) al punto GPS actual del teléfono
     */
    private fun setGeofenceToCurrentLocation() {
        val userLoc = myCurrentPoint
        if (userLoc == null) {
            Toast.makeText(this, "Obteniendo señal GPS... Intenta en unos segundos", Toast.LENGTH_SHORT).show()
            return
        }

        homePoint = GeoPoint(userLoc.latitude, userLoc.longitude)

        // Calcular nueva distancia de la mascota hacia este nuevo hogar
        val results = FloatArray(1)
        Location.distanceBetween(
            homePoint.latitude, homePoint.longitude,
            petCurrentPoint.latitude, petCurrentPoint.longitude,
            results
        )
        val newDistanceMeters = results[0]
        val isInside = newDistanceMeters <= currentSafeRadius

        // Guardar en Firebase para reflejarlo en toda la app
        val updates = mapOf(
            "distanceFromBaseMeters" to newDistanceMeters,
            "isInsideSafeZone" to isInside,
            "lastUpdated" to System.currentTimeMillis()
        )

        databaseRef.updateChildren(updates).addOnSuccessListener {
            // Mover el marcador de Hogar y redibujar el perímetro
            homeMarker?.position = homePoint
            safeZoneCircle?.let { mapView.overlays.remove(it) }

            val circlePoints = Polygon.pointsAsCircle(homePoint, currentSafeRadius.toDouble())
            safeZoneCircle = Polygon(mapView).apply {
                points = circlePoints
                fillPaint.color = if (isInside) 0x334ADE80 else 0x33F87171
                outlinePaint.color = if (isInside) Color.parseColor("#16A34A") else Color.parseColor("#DC2626")
                outlinePaint.strokeWidth = 3f
            }
            mapView.overlays.add(0, safeZoneCircle)
            mapView.invalidate()

            Toast.makeText(this, "Geocerca reubicada en tu posición actual", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStart() {
        super.onStart()
        startLocationTracking()
        listenToFirebase()
    }

    override fun onStop() {
        super.onStop()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        petListener?.let { databaseRef.removeEventListener(it) }
    }

    private fun listenToFirebase() {
        petListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val data = snapshot.getValue(SmartPetData::class.java) ?: return
                updatePetData(data)
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        databaseRef.addValueEventListener(petListener!!)
    }

    private fun updatePetData(data: SmartPetData) {
        petCurrentPoint = GeoPoint(data.latitude, data.longitude)
        currentSafeRadius = data.safeZoneRadiusMeters
        currentPetName = data.petName

        tvLocationSubtitle.text = "Mascota: ${data.petName}"
        tvLiveCoords.text = "Lat: %.4f | Lon: %.4f".format(data.latitude, data.longitude)
        tvLiveDistance.text = "Distancia a base: %.1f m | Radio: %.0f m".format(
            data.distanceFromBaseMeters,
            data.safeZoneRadiusMeters
        )

        if (data.isInsideSafeZone) {
            tvLiveStatus.text = "🟢 ${data.petName} dentro de Zona Segura"
            tvLiveStatus.setTextColor(Color.parseColor("#166534"))
        } else {
            tvLiveStatus.text = "⚠️ ¡ALERTA! ${data.petName} FUERA DE LA ZONA"
            tvLiveStatus.setTextColor(Color.parseColor("#DC2626"))
        }

        // Marcador del Hogar (Base fija o reubicada)
        if (homeMarker == null) {
            homeMarker = Marker(mapView).apply {
                position = homePoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Hogar (Base)"
            }
            mapView.overlays.add(homeMarker)
        } else {
            homeMarker?.position = homePoint
        }

        // Marcador de la Mascota
        if (petMarker == null) {
            petMarker = Marker(mapView).apply {
                position = petCurrentPoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = data.petName
            }
            mapView.overlays.add(petMarker)
        } else {
            petMarker?.position = petCurrentPoint
            petMarker?.title = data.petName
        }

        // Dibujar Geocerca (Círculo)
        safeZoneCircle?.let { mapView.overlays.remove(it) }
        val circlePoints = Polygon.pointsAsCircle(homePoint, data.safeZoneRadiusMeters.toDouble())
        safeZoneCircle = Polygon(mapView).apply {
            points = circlePoints
            fillPaint.color = if (data.isInsideSafeZone) 0x334ADE80 else 0x33F87171
            outlinePaint.color = if (data.isInsideSafeZone) Color.parseColor("#16A34A") else Color.parseColor("#DC2626")
            outlinePaint.strokeWidth = 3f
        }
        mapView.overlays.add(0, safeZoneCircle)

        mapView.invalidate()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}