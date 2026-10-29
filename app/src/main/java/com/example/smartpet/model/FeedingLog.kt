package com.example.smartpet.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class FeedingLog(
    var id: String = "",
    var type: String = "Comida", // "Comida" o "Agua"
    var amount: String = "50g",
    var timestamp: Long = System.currentTimeMillis()
)