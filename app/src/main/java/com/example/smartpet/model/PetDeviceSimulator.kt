package com.example.smartpet.model

import android.location.Location
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

/**
 * Clase orientada a objetos para simular el comportamiento físico de los sensores
 * y actuadores del hardware SmartPet y sincronizarlos con Firebase Realtime Database.
 */
class PetDeviceSimulator(
    private val databaseRef: DatabaseReference = FirebaseDatabase.getInstance().getReference("smartpet_simulation")
) {

    // Constantes de capacidad máxima
    val maxFoodCapacityGrams: Double = 350.0
    val maxWaterCapacityMl: Double = 1000.0
    private var safeZoneRadiusMeters: Float = 50.0f // Ahora es variable modificable

    // Estado interno del dispositivo
    private var currentData = SmartPetData(
        foodWeightGrams = 240.0,
        foodPercent = 75,
        waterWeightGrams = 600.0,
        waterPercent = 60,
        latitude = -33.4569,
        longitude = -70.6483,
        isInsideSafeZone = true,
        isDeviceOnline = true,
        safeZoneRadiusMeters = 50.0f
    )

    // Coordenadas base (Centro de la zona segura)
    private var baseLatitude: Double = -33.4569
    private var baseLongitude: Double = -70.6483

    private val simulationScope = CoroutineScope(Dispatchers.Default)
    private var simulationJob: Job? = null
    var isSimulating: Boolean = false
        private set

    /**
     * Ajusta el radio de la geocerca configurado desde Ajustes.
     */
    fun setSafeZoneRadius(newRadius: Float) {
        this.safeZoneRadiusMeters = newRadius
        currentData.safeZoneRadiusMeters = newRadius
        checkSafeZone()
        pushDataToFirebase()
    }

    /**
     * Fija las coordenadas actuales de la mascota como el centro del hogar.
     */
    fun setCurrentAsBase() {
        this.baseLatitude = currentData.latitude
        this.baseLongitude = currentData.longitude
        checkSafeZone()
        pushDataToFirebase()
    }

    /**
     * Define una ubicación base arbitraria.
     */
    fun setBaseLocation(latitude: Double, longitude: Double) {
        this.baseLatitude = latitude
        this.baseLongitude = longitude
        currentData.latitude = latitude
        currentData.longitude = longitude
        checkSafeZone()
        pushDataToFirebase()
    }

    /**
     * Alias compatible para forzar escape de la mascota desde ActivatorActivity.
     */
    fun simulateBreach(forceBreach: Boolean) {
        toggleSafeZoneBreach(forceBreach)
    }

    /**
     * Fuerza la salida o entrada de la mascota de la zona segura para demostración.
     */
    fun toggleSafeZoneBreach(forceBreach: Boolean) {
        if (forceBreach) {
            // Desplaza artificialmente las coordenadas más allá del radio seguro
            currentData.latitude = baseLatitude + 0.0015
            currentData.longitude = baseLongitude + 0.0015
        } else {
            // Regresa al centro del hogar
            currentData.latitude = baseLatitude
            currentData.longitude = baseLongitude
        }
        checkSafeZone()
        pushDataToFirebase()
    }

    /**
     * Inicia el ciclo automático de simulación (consumo y movimiento paulatino).
     */
    fun startSimulation(intervalMillis: Long = 4000L) {
        if (isSimulating) return
        isSimulating = true

        simulationJob = simulationScope.launch {
            while (isActive && isSimulating) {
                delay(intervalMillis)
                simulateGradualConsumption()
                simulatePetMovement()
                pushDataToFirebase()
            }
        }
    }

    /**
     * Detiene el ciclo de simulación.
     */
    fun stopSimulation() {
        isSimulating = false
        simulationJob?.cancel()
        simulationJob = null
    }

    /**
     * Simula la dispensación de comida por el servomotor.
     */
    fun dispenseFood(gramsToAdd: Double = 50.0) {
        val newWeight = min(maxFoodCapacityGrams, currentData.foodWeightGrams + gramsToAdd)
        currentData.foodWeightGrams = newWeight
        currentData.foodPercent = ((newWeight / maxFoodCapacityGrams) * 100).toInt()
        currentData.dispenseFoodTrigger = true
        currentData.lastUpdated = System.currentTimeMillis()
        pushDataToFirebase()
    }

    /**
     * Simula el suministro de agua por la bomba DC.
     */
    fun dispenseWater(mlToAdd: Double = 150.0) {
        val newWeight = min(maxWaterCapacityMl, currentData.waterWeightGrams + mlToAdd)
        currentData.waterWeightGrams = newWeight
        currentData.waterPercent = ((newWeight / maxWaterCapacityMl) * 100).toInt()
        currentData.dispenseWaterTrigger = true
        currentData.lastUpdated = System.currentTimeMillis()
        pushDataToFirebase()
    }

    private fun simulateGradualConsumption() {
        // La mascota come entre 2g y 8g si hay comida disponible
        if (currentData.foodWeightGrams > 5.0) {
            val eaten = (2..8).random().toDouble()
            val newWeight = max(0.0, currentData.foodWeightGrams - eaten)
            currentData.foodWeightGrams = newWeight
            currentData.foodPercent = ((newWeight / maxFoodCapacityGrams) * 100).toInt()
        }

        // La mascota bebe entre 5ml y 15ml si hay agua disponible
        if (currentData.waterWeightGrams > 10.0) {
            val drunk = (5..15).random().toDouble()
            val newWeight = max(0.0, currentData.waterWeightGrams - drunk)
            currentData.waterWeightGrams = newWeight
            currentData.waterPercent = ((newWeight / maxWaterCapacityMl) * 100).toInt()
        }
    }

    private fun simulatePetMovement() {
        val latDelta = ((1..5).random() - 3) * 0.00003
        val lonDelta = ((1..5).random() - 3) * 0.00003

        currentData.latitude += latDelta
        currentData.longitude += lonDelta
        checkSafeZone()
    }

    private fun checkSafeZone() {
        val results = FloatArray(1)
        Location.distanceBetween(
            baseLatitude, baseLongitude,
            currentData.latitude, currentData.longitude,
            results
        )
        val distanceInMeters = results[0]
        currentData.distanceFromBaseMeters = distanceInMeters
        currentData.isInsideSafeZone = distanceInMeters <= safeZoneRadiusMeters
    }

    private fun pushDataToFirebase() {
        currentData.lastUpdated = System.currentTimeMillis()
        databaseRef.setValue(currentData)
    }

    fun getCurrentData(): SmartPetData = currentData
}