package com.example.smartpet.model

import android.location.Location
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

class PetDeviceSimulator {

    private val firestore = FirebaseFirestore.getInstance()
    private val petDocRef = firestore.collection("smartpet").document("telemetry")

    companion object {
        private const val TAG = "SMARTPET_ESCAPE"
    }

    val maxFoodCapacityGrams = 350.0
    val maxWaterCapacityMl = 1000.0

    private val simulationScope = CoroutineScope(Dispatchers.Default)
    private var simulationJob: Job? = null

    @Volatile
    var isSimulating = false
        private set

    private var currentData = SmartPetData()
    private var safeZoneRadiusMeters = 50f
    private var baseLatitude = -33.4569
    private var baseLongitude = -70.6483

    private fun loadLatestData(onLoaded: (SmartPetData) -> Unit) {
        petDocRef.get().addOnSuccessListener { snapshot ->
            val data = snapshot.toObject(SmartPetData::class.java) ?: SmartPetData()
            currentData = data.copy()
            safeZoneRadiusMeters = data.safeZoneRadiusMeters
            baseLatitude = data.homeLatitude
            baseLongitude = data.homeLongitude
            onLoaded(data.copy())
        }.addOnFailureListener { error ->
            Log.e(TAG, "Error al leer Firestore", error)
        }
    }

    private fun saveUpdates(updates: Map<String, Any>, onSuccess: (() -> Unit)? = null) {
        val finalUpdates = updates.toMutableMap()
        finalUpdates["lastUpdated"] = System.currentTimeMillis()

        petDocRef.update(finalUpdates).addOnSuccessListener {
            onSuccess?.invoke()
        }
    }

    fun setSafeZoneRadius(newRadius: Float) {
        loadLatestData { data ->
            safeZoneRadiusMeters = newRadius
            val distance = calculateDistance(data.homeLatitude, data.homeLongitude, data.latitude, data.longitude)
            val inside = distance <= newRadius

            saveUpdates(
                mapOf(
                    "safeZoneRadiusMeters" to newRadius,
                    "distanceFromBaseMeters" to distance,
                    "isInsideSafeZone" to inside
                )
            )
        }
    }

    fun setCurrentAsBase() {
        loadLatestData { data ->
            saveUpdates(
                mapOf(
                    "homeLatitude" to data.latitude,
                    "homeLongitude" to data.longitude,
                    "distanceFromBaseMeters" to 0f,
                    "isInsideSafeZone" to true
                )
            )
        }
    }

    fun setBaseLocation(latitude: Double, longitude: Double) {
        loadLatestData { data ->
            val distance = calculateDistance(latitude, longitude, data.latitude, data.longitude)
            val inside = distance <= data.safeZoneRadiusMeters

            saveUpdates(
                mapOf(
                    "homeLatitude" to latitude,
                    "homeLongitude" to longitude,
                    "distanceFromBaseMeters" to distance,
                    "isInsideSafeZone" to inside
                )
            )
        }
    }

    fun simulateBreach(forceBreach: Boolean) {
        toggleSafeZoneBreach(forceBreach)
    }

    fun toggleSafeZoneBreach(forceBreach: Boolean) {
        // BLOQUEO DE CONDICIÓN DE CARRERA:
        // Si forzamos el escape, detenemos la simulación automática para que no lo sobreescriba.
        if (forceBreach && isSimulating) {
            stopSimulation()
        }

        loadLatestData { data ->
            // Distancia de 800m para asegurar que rompa cualquier geocerca
            val latitude = if (forceBreach) data.homeLatitude + 0.0080 else data.homeLatitude
            val longitude = if (forceBreach) data.homeLongitude + 0.0080 else data.homeLongitude
            val distance = if (forceBreach) 800.0f else 0.0f
            val isInside = !forceBreach

            val updates = mapOf(
                "latitude" to latitude,
                "longitude" to longitude,
                "distanceFromBaseMeters" to distance,
                "isInsideSafeZone" to isInside,
                "lastUpdated" to System.currentTimeMillis() // Forzamos actualización de tiempo
            )

            petDocRef.update(updates).addOnSuccessListener {
                Log.d(TAG, "ESCAPE GUARDADO: Fuera=$forceBreach")
            }
        }
    }

    fun startSimulation(intervalMillis: Long = 4000L) {
        if (isSimulating) return
        isSimulating = true

        simulationJob = simulationScope.launch {
            while (isActive && isSimulating) {
                delay(intervalMillis)
                if (!isActive || !isSimulating) break

                loadLatestData { data ->
                    // CANDADO: Si activaste el escape manual, no queremos que el simulador mueva
                    // la mascota de regreso accidentalmente hasta que tú lo apagues.
                    if (!data.isInsideSafeZone) return@loadLatestData

                    val foodWeight = if (data.foodWeightGrams > 5.0) {
                        max(0.0, data.foodWeightGrams - (2..8).random().toDouble())
                    } else data.foodWeightGrams

                    val waterWeight = if (data.waterWeightGrams > 10.0) {
                        max(0.0, data.waterWeightGrams - (5..15).random().toDouble())
                    } else data.waterWeightGrams

                    val latitude = data.latitude + ((1..5).random() - 3) * 0.00003
                    val longitude = data.longitude + ((1..5).random() - 3) * 0.00003
                    val distance = calculateDistance(data.homeLatitude, data.homeLongitude, latitude, longitude)
                    val inside = distance <= data.safeZoneRadiusMeters

                    saveUpdates(
                        mapOf(
                            "foodWeightGrams" to foodWeight,
                            "foodPercent" to ((foodWeight / maxFoodCapacityGrams) * 100).toInt(),
                            "waterWeightGrams" to waterWeight,
                            "waterPercent" to ((waterWeight / maxWaterCapacityMl) * 100).toInt(),
                            "latitude" to latitude,
                            "longitude" to longitude,
                            "distanceFromBaseMeters" to distance,
                            "isInsideSafeZone" to inside
                        )
                    )
                }
            }
        }
    }

    fun stopSimulation() {
        isSimulating = false
        simulationJob?.cancel()
        simulationJob = null
    }

    private fun calculateDistance(baseLat: Double, baseLon: Double, petLat: Double, petLon: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(baseLat, baseLon, petLat, petLon, results)
        return results[0]
    }
}