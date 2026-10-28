package com.example.smartpet.controller

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.smartpet.databinding.ActivityMonitoringBinding
import com.example.smartpet.model.SmartPetData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class MonitoringActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMonitoringBinding
    private lateinit var databaseRef: DatabaseReference
    private var petDataListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMonitoringBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Referencia compartida en Firebase Realtime Database
        databaseRef = FirebaseDatabase.getInstance().getReference("smartpet_simulation")

        setupListeners()
        setupNavigation()
    }

    override fun onStart() {
        super.onStart()
        listenToRealtimeData()
    }

    override fun onStop() {
        super.onStop()
        // Limpieza de listener para evitar fugas de memoria
        petDataListener?.let { databaseRef.removeEventListener(it) }
    }

    private fun listenToRealtimeData() {
        petDataListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val data = snapshot.getValue(SmartPetData::class.java)
                if (data != null) {
                    updateUI(data)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MonitoringActivity, "Error de sincronización: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }
        databaseRef.addValueEventListener(petDataListener!!)
    }

    private fun updateUI(data: SmartPetData) {
        // 1. Comida
        binding.tvFoodPercent.text = "${data.foodPercent}%"
        binding.pbFood.progress = data.foodPercent
        binding.tvFoodWeight.text = "%.1f g disponibles".format(data.foodWeightGrams)

        // 2. Agua
        binding.tvWaterPercent.text = "${data.waterPercent}%"
        binding.pbWater.progress = data.waterPercent
        binding.tvWaterWeight.text = "%.0f ml restantes".format(data.waterWeightGrams)

        // 3. Ubicación GPS
        binding.tvGpsCoords.text = "GPS: %.4f, %.4f".format(data.latitude, data.longitude)

        // 4. Zona Segura (Dinámica y reactiva)
        if (data.isInsideSafeZone) {
            binding.layoutSafeZoneStatus.setBackgroundColor(Color.parseColor("#F0FDF4")) // Verde claro
            binding.tvSafeZoneStatus.text = "✔ Todo en orden"
            binding.tvSafeZoneStatus.setTextColor(Color.parseColor("#166534"))
            binding.tvSafeZoneSubtitle.text = "Tu mascota se encuentra dentro"
        } else {
            binding.layoutSafeZoneStatus.setBackgroundColor(Color.parseColor("#FEF2F2")) // Rojo claro
            binding.tvSafeZoneStatus.text = "⚠️ ¡FUERA DE ZONA SEGURA!"
            binding.tvSafeZoneStatus.setTextColor(Color.parseColor("#B91C1C"))
            binding.tvSafeZoneSubtitle.text = "¡Alerta: La mascota abandonó el perímetro!"
        }

        // 5. Estado del Dispositivo
        if (data.isDeviceOnline) {
            binding.tvConnectionStatus.text = "🟢 En línea"
            binding.tvConnectionStatus.setTextColor(Color.parseColor("#15803D"))
        } else {
            binding.tvConnectionStatus.text = "🔴 Desconectado"
            binding.tvConnectionStatus.setTextColor(Color.parseColor("#DC2626"))
        }
    }

    private fun setupListeners() {
        // Dispensación remota de comida
        binding.btnDispenseFood.setOnClickListener {
            databaseRef.child("dispenseFoodTrigger").setValue(true)
            Toast.makeText(this, "Enviando orden: Dispensar comida", Toast.LENGTH_SHORT).show()
        }

        // Dispensación remota de agua
        binding.btnDispenseWater.setOnClickListener {
            databaseRef.child("dispenseWaterTrigger").setValue(true)
            Toast.makeText(this, "Enviando orden: Suministrar agua", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupNavigation() {
        // Al tocar Ajustes, abre la consola técnica / simulador
        binding.navSettings.setOnClickListener {
            val intent = Intent(this, ActivatorActivity::class.java)
            startActivity(intent)
        }

        binding.navFood.setOnClickListener {
            Toast.makeText(this, "Vista actual: Monitoreo de alimentación", Toast.LENGTH_SHORT).show()
        }

        binding.navLocation.setOnClickListener {
            Toast.makeText(this, "Vista actual: Monitoreo de ubicación GPS", Toast.LENGTH_SHORT).show()
        }
    }
}