package com.example.smartpet.controller

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.smartpet.R
import com.example.smartpet.model.FeedingLog
import com.example.smartpet.model.SmartPetData
import com.google.android.material.button.MaterialButton
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

class FoodActivity : AppCompatActivity() {

    private lateinit var databaseRef: DatabaseReference
    private lateinit var historyRef: DatabaseReference

    private lateinit var tvFoodPetName: TextView
    private lateinit var tvFoodStatus: TextView
    private lateinit var tvHistoryLogs: TextView
    private lateinit var btnQuickFood: MaterialButton
    private lateinit var btnQuickWater: MaterialButton
    private lateinit var btnTarePlate: MaterialButton

    private var currentData: SmartPetData = SmartPetData()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_food)

        databaseRef = FirebaseDatabase.getInstance().getReference("smartpet_simulation")
        historyRef = FirebaseDatabase.getInstance().getReference("feeding_history")

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
        listenToFirebase()
        listenToHistory()
    }

    private fun listenToFirebase() {
        databaseRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentData = snapshot.getValue(SmartPetData::class.java) ?: SmartPetData()
                tvFoodPetName.text = "Plan para ${currentData.petName} (${currentData.petBreed})"
                tvFoodStatus.text = "Comida: %.1fg (%d%%) | Agua: %.0fml (%d%%)".format(
                    currentData.foodWeightGrams,
                    currentData.foodPercent,
                    currentData.waterWeightGrams,
                    currentData.waterPercent
                )
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun listenToHistory() {
        historyRef.limitToLast(10).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    tvHistoryLogs.text = "No hay registros de raciones el día de hoy."
                    return
                }

                val logBuilder = StringBuilder()
                val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

                for (child in snapshot.children.reversed()) {
                    val log = child.getValue(FeedingLog::class.java) ?: continue
                    val time = dateFormat.format(Date(log.timestamp))
                    val icon = if (log.type == "Comida") "🥣" else "💧"
                    logBuilder.append("$icon [$time] ${log.type}: ${log.amount}\n")
                }

                tvHistoryLogs.text = logBuilder.toString().trimEnd()
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun setupListeners() {
        btnQuickFood.setOnClickListener {
            val maxCap = if (currentData.foodMaxCapacityGrams > 0) currentData.foodMaxCapacityGrams else 350.0
            val dose = if (currentData.foodDoseGrams > 0) currentData.foodDoseGrams else 50.0

            val newWeight = min(maxCap, currentData.foodWeightGrams + dose)
            val newPercent = ((newWeight / maxCap) * 100).toInt()

            val updates = mapOf(
                "foodWeightGrams" to newWeight,
                "foodPercent" to newPercent,
                "dispenseFoodTrigger" to true,
                "lastUpdated" to System.currentTimeMillis()
            )

            databaseRef.updateChildren(updates).addOnSuccessListener {
                logAction("Comida", "%.0fg".format(dose))
                Toast.makeText(this, "Ración de alimento dispensada", Toast.LENGTH_SHORT).show()
            }
        }

        btnQuickWater.setOnClickListener {
            val maxCap = 1000.0
            val dose = 150.0

            val newWeight = min(maxCap, currentData.waterWeightGrams + dose)
            val newPercent = ((newWeight / maxCap) * 100).toInt()

            val updates = mapOf(
                "waterWeightGrams" to newWeight,
                "waterPercent" to newPercent,
                "dispenseWaterTrigger" to true,
                "lastUpdated" to System.currentTimeMillis()
            )

            databaseRef.updateChildren(updates).addOnSuccessListener {
                logAction("Agua", "%.0fml".format(dose))
                Toast.makeText(this, "Suministro de agua dispensado", Toast.LENGTH_SHORT).show()
            }
        }

        btnTarePlate.setOnClickListener {
            val updates = mapOf(
                "foodWeightGrams" to 0.0,
                "foodPercent" to 0,
                "lastUpdated" to System.currentTimeMillis()
            )
            databaseRef.updateChildren(updates).addOnSuccessListener {
                Toast.makeText(this, "Plato calibrado en 0g (tara reiniciada)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun logAction(type: String, amount: String) {
        val key = historyRef.push().key ?: return
        val log = FeedingLog(
            id = key,
            type = type,
            amount = amount,
            timestamp = System.currentTimeMillis()
        )
        historyRef.child(key).setValue(log)
    }
}