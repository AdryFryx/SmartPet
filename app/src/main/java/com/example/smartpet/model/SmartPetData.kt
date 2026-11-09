package com.example.smartpet.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class SmartPetData(
    // Datos del Usuario y Mascota
    var ownerName: String = "Gabriel",
    var petName: String = "Max",
    var petBreed: String = "Golden Retriever",
    var petAge: Int = 3,
    var petSizeCategory: String = "Grande (20 a 40 kg)",

    // Fotos en Base64
    var userPhotoBase64: String = "",
    var petPhotoBase64: String = "",

    // Capacidades calculadas y porciones
    var foodMaxCapacityGrams: Double = 450.0,
    var foodDoseGrams: Double = 50.0,
    var waterMaxCapacityMl: Double = 1000.0,

    // Sensores de Comida y Agua
    var foodWeightGrams: Double = 337.5,
    var foodPercent: Int = 75,
    var waterWeightGrams: Double = 750.0,
    var waterPercent: Int = 75,

    // GPS y Geocerca
    var latitude: Double = -33.4569,
    var longitude: Double = -70.6483,
    var homeLatitude: Double = -33.4569,
    var homeLongitude: Double = -70.6483,
    var isInsideSafeZone: Boolean = true,
    var distanceFromBaseMeters: Float = 0.0f,
    var safeZoneRadiusMeters: Float = 50.0f,

    // Actuadores y Conectividad
    var dispenseFoodTrigger: Boolean = false,
    var dispenseWaterTrigger: Boolean = false,
    var connectionMode: String = "WIFI",

    // Estado del dispositivo
    var isDeviceOnline: Boolean = true,
    var lastUpdated: Long = System.currentTimeMillis()
)