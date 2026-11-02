
package com.example.smartpet.model

import android.location.Location
import android.util.Log
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

class PetDeviceSimulator(
    private val databaseRef: DatabaseReference =
        FirebaseDatabase.getInstance()
            .getReference("smartpet_simulation")
) {

    companion object {
        private const val TAG = "SMARTPET_ESCAPE"
    }

    val maxFoodCapacityGrams = 350.0
    val maxWaterCapacityMl = 1000.0

    private val simulationScope =
        CoroutineScope(Dispatchers.Default)

    private var simulationJob: Job? = null

    @Volatile
    var isSimulating = false
        private set

    private var currentData = SmartPetData()

    private var safeZoneRadiusMeters = 50f

    private var baseLatitude = -33.4569
    private var baseLongitude = -70.6483

    private fun loadLatestData(
        onLoaded: (SmartPetData) -> Unit
    ) {
        databaseRef.get()
            .addOnSuccessListener { snapshot ->

                val data =
                    snapshot.getValue(SmartPetData::class.java)
                        ?: SmartPetData()

                currentData = data.copy()

                safeZoneRadiusMeters =
                    data.safeZoneRadiusMeters

                baseLatitude = data.homeLatitude
                baseLongitude = data.homeLongitude

                Log.d(
                    TAG,
                    "Datos cargados: hogar=${data.homeLatitude},${data.homeLongitude} " +
                            "radio=${data.safeZoneRadiusMeters}"
                )

                onLoaded(data.copy())
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Error al leer Firebase", error)
            }
    }

    private fun saveUpdates(
        updates: Map<String, Any>,
        onSuccess: (() -> Unit)? = null
    ) {
        val finalUpdates = updates.toMutableMap()

        finalUpdates["lastUpdated"] =
            System.currentTimeMillis()

        databaseRef.updateChildren(finalUpdates)
            .addOnSuccessListener {

                Log.d(
                    TAG,
                    "Firebase actualizado: $finalUpdates"
                )

                onSuccess?.invoke()
            }
            .addOnFailureListener { error ->

                Log.e(
                    TAG,
                    "Error al guardar en Firebase",
                    error
                )
            }
    }

    fun setSafeZoneRadius(newRadius: Float) {

        loadLatestData { data ->

            safeZoneRadiusMeters = newRadius

            val distance = calculateDistance(
                data.homeLatitude,
                data.homeLongitude,
                data.latitude,
                data.longitude
            )

            val inside = distance <= newRadius

            Log.d(
                TAG,
                "Cambio de radio: distancia=$distance radio=$newRadius dentro=$inside"
            )

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

            baseLatitude = data.latitude
            baseLongitude = data.longitude

            safeZoneRadiusMeters = data.safeZoneRadiusMeters

            saveUpdates(
                mapOf(
                    "homeLatitude" to baseLatitude,
                    "homeLongitude" to baseLongitude,
                    "distanceFromBaseMeters" to 0f,
                    "isInsideSafeZone" to true
                )
            )
        }
    }

    fun setBaseLocation(
        latitude: Double,
        longitude: Double
    ) {

        loadLatestData { data ->

            baseLatitude = latitude
            baseLongitude = longitude

            val distance = calculateDistance(
                latitude,
                longitude,
                data.latitude,
                data.longitude
            )

            val inside =
                distance <= data.safeZoneRadiusMeters

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

        loadLatestData { data ->

            val homeLat = data.homeLatitude
            val homeLon = data.homeLongitude
            val radius = data.safeZoneRadiusMeters

            val latitude: Double
            val longitude: Double

            if (forceBreach) {

                // Desplazar la mascota aproximadamente 200 metros.
                latitude = homeLat + 0.0015
                longitude = homeLon + 0.0015

            } else {

                // Regresar la mascota al hogar.
                latitude = homeLat
                longitude = homeLon
            }

            val distance = calculateDistance(
                homeLat,
                homeLon,
                latitude,
                longitude
            )

            val isInside = distance <= radius

            Log.d(
                TAG,
                "ESCAPE: forzar=$forceBreach " +
                        "distancia=$distance " +
                        "radio=$radius " +
                        "dentro=$isInside"
            )

            val updates = mapOf(
                "latitude" to latitude,
                "longitude" to longitude,
                "distanceFromBaseMeters" to distance,
                "isInsideSafeZone" to isInside
            )

            saveUpdates(updates) {

                Log.d(
                    TAG,
                    "ESCAPE GUARDADO: isInsideSafeZone=$isInside"
                )
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

                    val foodWeight =
                        if (data.foodWeightGrams > 5.0) {
                            max(
                                0.0,
                                data.foodWeightGrams -
                                        (2..8).random().toDouble()
                            )
                        } else {
                            data.foodWeightGrams
                        }

                    val waterWeight =
                        if (data.waterWeightGrams > 10.0) {
                            max(
                                0.0,
                                data.waterWeightGrams -
                                        (5..15).random().toDouble()
                            )
                        } else {
                            data.waterWeightGrams
                        }

                    val latitude =
                        data.latitude +
                                ((1..5).random() - 3) * 0.00003

                    val longitude =
                        data.longitude +
                                ((1..5).random() - 3) * 0.00003

                    val distance = calculateDistance(
                        data.homeLatitude,
                        data.homeLongitude,
                        latitude,
                        longitude
                    )

                    val inside =
                        distance <= data.safeZoneRadiusMeters

                    saveUpdates(
                        mapOf(
                            "foodWeightGrams" to foodWeight,
                            "foodPercent" to
                                    ((foodWeight / maxFoodCapacityGrams) * 100).toInt(),
                            "waterWeightGrams" to waterWeight,
                            "waterPercent" to
                                    ((waterWeight / maxWaterCapacityMl) * 100).toInt(),
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

    fun dispenseFood(gramsToAdd: Double = 50.0) {

        loadLatestData { data ->

            val weight = min(
                maxFoodCapacityGrams,
                data.foodWeightGrams + gramsToAdd
            )

            saveUpdates(
                mapOf(
                    "foodWeightGrams" to weight,
                    "foodPercent" to
                            ((weight / maxFoodCapacityGrams) * 100).toInt(),
                    "dispenseFoodTrigger" to true
                )
            )
        }
    }

    fun dispenseWater(mlToAdd: Double = 150.0) {

        loadLatestData { data ->

            val weight = min(
                maxWaterCapacityMl,
                data.waterWeightGrams + mlToAdd
            )

            saveUpdates(
                mapOf(
                    "waterWeightGrams" to weight,
                    "waterPercent" to
                            ((weight / maxWaterCapacityMl) * 100).toInt(),
                    "dispenseWaterTrigger" to true
                )
            )
        }
    }

    private fun calculateDistance(
        baseLat: Double,
        baseLon: Double,
        petLat: Double,
        petLon: Double
    ): Float {

        val results = FloatArray(1)

        Location.distanceBetween(
            baseLat,
            baseLon,
            petLat,
            petLon,
            results
        )

        return results[0]
    }

    fun getCurrentData(): SmartPetData {
        return currentData.copy()
    }
}
