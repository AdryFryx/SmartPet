package com.example.smartpet.model

import com.google.firebase.database.IgnoreExtraProperties

/**
 * Modelo de datos para la sincronización en Firebase Realtime Database
 * compatible con la arquitectura MVC.
 */
@IgnoreExtraProperties
data class SmartPetData(
    // Sensores de Comida (Celda de carga 1)
    var foodWeightGrams: Double = 0.0,
    var foodPercent: Int = 0,

    // Sensores de Agua (Celda de carga 2)
    var waterWeightGrams: Double = 0.0,
    var waterPercent: Int = 0,

    // Sensor GPS Collar
    var latitude: Double = -33.4569,
    var longitude: Double = -70.6483,
    var isInsideSafeZone: Boolean = true,

    // Actuadores / Comandos de Control remoto
    var dispenseFoodTrigger: Boolean = false,
    var dispenseWaterTrigger: Boolean = false,

    // Estado general del dispositivo
    var isDeviceOnline: Boolean = true,
    var lastUpdated: Long = System.currentTimeMillis()
)