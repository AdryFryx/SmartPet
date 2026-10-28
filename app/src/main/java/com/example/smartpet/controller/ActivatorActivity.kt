package com.example.smartpet.controller

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.smartpet.databinding.ActivityActivatorBinding
import com.example.smartpet.model.PetDeviceSimulator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class ActivatorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityActivatorBinding
    private val simulator = PetDeviceSimulator()
    private val databaseRef = FirebaseDatabase.getInstance().getReference("smartpet_simulation")
    private val LOCATION_PERMISSION_REQ_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityActivatorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSimulationControls()
        setupActuatorControls()
        setupNavigationAndSession()
    }

    private fun setupSimulationControls() {
        // Switch para activar/detener simulación automática (Consumo progresivo y movimiento)
        binding.switchAutoSimulation.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                simulator.startSimulation(intervalMillis = 3500L)
                binding.tvSyncBadge.text = "🟢 Simulando en Vivo"
                Toast.makeText(this, "Simulador IoT iniciado", Toast.LENGTH_SHORT).show()
            } else {
                simulator.stopSimulation()
                binding.tvSyncBadge.text = "⏸️ Simulación Pausada"
                Toast.makeText(this, "Simulador IoT detenido", Toast.LENGTH_SHORT).show()
            }
        }

        // Switch para forzar la fuga de la mascota (disparar la alerta en el otro teléfono)
        binding.switchForceBreach.setOnCheckedChangeListener { _, isBreach ->
            simulator.toggleSafeZoneBreach(isBreach)
            val msg = if (isBreach) "¡Simulando escape fuera de zona segura!" else "Mascota reingresó a zona segura"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // Botón para fijar coordenadas reales de la sala como base
        binding.btnSetBaseLocation.setOnClickListener {
            captureCurrentGpsLocation()
        }
    }

    private fun setupActuatorControls() {
        // Slider de comida (SG90)
        binding.sliderFood.addOnChangeListener { _, value, _ ->
            binding.tvDispenseQuantity.text = "Cantidad a dispensar: ${value.toInt()} gramos"
        }

        binding.btnDispenseFoodCustom.setOnClickListener {
            val amount = binding.sliderFood.value.toDouble()
            simulator.dispenseFood(amount)
            Toast.makeText(this, "Simulando dispensado de ${amount.toInt()}g con SG90", Toast.LENGTH_SHORT).show()
        }

        // Switch Bomba de Agua (MOSFET)
        binding.switchWaterPump.setOnCheckedChangeListener { _, isChecked ->
            databaseRef.child("dispenseWaterTrigger").setValue(isChecked)
            if (isChecked) {
                simulator.dispenseWater(150.0)
                Toast.makeText(this, "Bomba DC activada (+150ml suministrados)", Toast.LENGTH_SHORT).show()
            }
        }

        // Switch Buzzer / LEDs
        binding.switchBuzzerLeds.setOnCheckedChangeListener { _, isChecked ->
            databaseRef.child("buzzerAlert").setValue(isChecked)
            val estado = if (isChecked) "Alarma y LEDs activados" else "Alarma apagada"
            Toast.makeText(this, estado, Toast.LENGTH_SHORT).show()
        }
    }

    private fun captureCurrentGpsLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQ_CODE
            )
            return
        }

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

        if (lastLocation != null) {
            simulator.setBaseLocation(lastLocation.latitude, lastLocation.longitude)
            Toast.makeText(this, "Hogar fijado en: %.4f, %.4f".format(lastLocation.latitude, lastLocation.longitude), Toast.LENGTH_LONG).show()
        } else {
            // Coordenadas de contingencia si el emulador no tiene señal GPS
            simulator.setBaseLocation(-33.4569, -70.6483)
            Toast.makeText(this, "GPS no disponible. Ubicación base fijada por defecto", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupNavigationAndSession() {
        // Regresar al Dashboard al presionar Inicio
        binding.navHomeActivator.setOnClickListener {
            finish()
        }

        // Cerrar sesión
        binding.btnLogout.setOnClickListener {
            simulator.stopSimulation()
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        simulator.stopSimulation()
    }
}