package com.example.smartpet.controller

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.smartpet.R
import com.example.smartpet.model.FeedingLog
import com.example.smartpet.model.SmartPetData
import com.example.smartpet.repository.SmartPetRepository
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

class FoodActivity : AppCompatActivity() {

    private val repository = SmartPetRepository()

    private lateinit var tvFoodPetName: TextView
    private lateinit var tvFoodStatus: TextView
    private lateinit var tvHistoryLogs: TextView

    private lateinit var btnQuickFood: MaterialButton
    private lateinit var btnQuickWater: MaterialButton
    private lateinit var btnTarePlate: MaterialButton

    private var currentData: SmartPetData = SmartPetData()

    private var petDataListener: ListenerRegistration? = null
    private var historyListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_food)

        tvFoodPetName = findViewById(R.id.tvFoodPetName)
        tvFoodStatus = findViewById(R.id.tvFoodStatus)
        tvHistoryLogs = findViewById(R.id.tvHistoryLogs)

        btnQuickFood = findViewById(R.id.btnQuickFood)
        btnQuickWater = findViewById(R.id.btnQuickWater)
        btnTarePlate = findViewById(R.id.btnTarePlate)

        findViewById<ImageButton>(R.id.btnBackFromFood).setOnClickListener {
            finish()
        }

        setupListeners()
    }

    override fun onStart() {
        super.onStart()
        listenToFirebase()
        listenToHistory()
    }

    override fun onStop() {
        super.onStop()

        petDataListener?.let {
            repository.removePetDataListener(it)
        }

        historyListener?.let {
            repository.removeHistoryListener(it)
        }

        petDataListener = null
        historyListener = null
    }

    private fun listenToFirebase() {
        petDataListener = repository.observePetData { data ->
            currentData = data

            tvFoodPetName.text =
                "Plan para ${currentData.petName} (${currentData.petBreed})"

            tvFoodStatus.text =
                "Comida: %.1fg (%d%%) | Agua: %.0fml (%d%%)".format(
                    currentData.foodWeightGrams,
                    currentData.foodPercent,
                    currentData.waterWeightGrams,
                    currentData.waterPercent
                )
        }
    }

    private fun listenToHistory() {
        historyListener = repository.observeFeedingHistory { logs ->
            if (logs.isEmpty()) {
                tvHistoryLogs.text =
                    "No hay registros de raciones el día de hoy."
                return@observeFeedingHistory
            }

            val logBuilder = StringBuilder()
            val dateFormat =
                SimpleDateFormat("HH:mm:ss", Locale.getDefault())

            for (log in logs) {
                val time = dateFormat.format(Date(log.timestamp))
                val icon = if (log.type == "Comida") "🥣" else "💧"

                logBuilder.append(
                    "$icon [$time] ${log.type}: ${log.amount}\n"
                )
            }

            tvHistoryLogs.text = logBuilder.toString().trimEnd()
        }
    }

    private fun setupListeners() {

        btnQuickFood.setOnClickListener {
            val maxCap =
                if (currentData.foodMaxCapacityGrams > 0)
                    currentData.foodMaxCapacityGrams
                else 350.0

            val dose =
                if (currentData.foodDoseGrams > 0)
                    currentData.foodDoseGrams
                else 50.0

            val newWeight =
                min(maxCap, currentData.foodWeightGrams + dose)

            val newPercent = ((newWeight / maxCap) * 100).toInt()

            val updates = mapOf(
                "foodWeightGrams" to newWeight,
                "foodPercent" to newPercent,
                "dispenseFoodTrigger" to true,
                "lastUpdated" to System.currentTimeMillis()
            )

            repository.updatePetData(updates)
                .addOnSuccessListener {
                    repository.addFeedingLog(
                        "Comida",
                        "%.0fg".format(dose)
                    )

                    Toast.makeText(
                        this,
                        "Ración de alimento dispensada",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }

        btnQuickWater.setOnClickListener {
            val maxCap = 1000.0
            val dose = 150.0

            val newWeight =
                min(maxCap, currentData.waterWeightGrams + dose)

            val newPercent = ((newWeight / maxCap) * 100).toInt()

            val updates = mapOf(
                "waterWeightGrams" to newWeight,
                "waterPercent" to newPercent,
                "dispenseWaterTrigger" to true,
                "lastUpdated" to System.currentTimeMillis()
            )

            repository.updatePetData(updates)
                .addOnSuccessListener {
                    repository.addFeedingLog(
                        "Agua",
                        "%.0fml".format(dose)
                    )

                    Toast.makeText(
                        this,
                        "Suministro de agua dispensado",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }

        btnTarePlate.setOnClickListener {
            val updates = mapOf(
                "foodWeightGrams" to 0.0,
                "foodPercent" to 0,
                "lastUpdated" to System.currentTimeMillis()
            )

            repository.updatePetData(updates)
                .addOnSuccessListener {
                    Toast.makeText(
                        this,
                        "Plato calibrado en 0g (tara reiniciada)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }
}