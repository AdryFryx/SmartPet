package com.example.smartpet.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class SmartPetData(
    // Datos del Usuario y Mascota
    var ownerName: String = "Usuario",
    var petName: String = "Max",
    var petBreed: String = "Golden Retriever • 3 años",
    var petSizeCategory: String = "Grande (20 a 40 kg)",

    // Capacidades calculadas según el tamaño del perro
    var foodMaxCapacityGrams: Double = 450.0,
    var foodDoseGrams: Double = 50.0,

    // Sensores de Comida (Celda de carga 1)
    var foodWeightGrams: Double = 337.5,
    var foodPercent: Int = 75,

    // Sensores de Agua (Celda de carga 2)
    var waterMaxCapacityMl: Double = 1000.0,
    var waterWeightGrams: Double = 750.0,
    var waterPercent: Int = 75,

    // Sensor GPS Collar y Geocerca
    var latitude: Double = -33.4569,
    var longitude: Double = -70.6483,
    var isInsideSafeZone: Boolean = true,
    var distanceFromBaseMeters: Float = 0.0f,
    var safeZoneRadiusMeters: Float = 50.0f, // Radio configurable

    // Actuadores / Comandos
    var dispenseFoodTrigger: Boolean = false,
    var dispenseWaterTrigger: Boolean = false,

    // Conectividad
    var connectionMode: String = "WIFI", // "WIFI" o "BLE"

    // Estado del dispositivo
    var isDeviceOnline: Boolean = true,
    var lastUpdated: Long = System.currentTimeMillis()

)