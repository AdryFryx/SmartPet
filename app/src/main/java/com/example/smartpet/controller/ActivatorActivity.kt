package com.example.smartpet.controller

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.smartpet.databinding.ActivityActivatorBinding
import com.example.smartpet.model.PetDeviceSimulator
import com.example.smartpet.model.SmartPetData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ActivatorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityActivatorBinding
    private lateinit var databaseRef: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private var simulator: PetDeviceSimulator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityActivatorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        databaseRef = FirebaseDatabase.getInstance().getReference("smartpet_simulation")
        simulator = PetDeviceSimulator(databaseRef)

        setupAccountInfo()
        setupListeners()
        setupNavigation()
        loadCurrentSettings()
    }

    private fun setupAccountInfo() {
        val user = auth.currentUser
        val displayName = user?.displayName ?: "Usuario"
        val email = user?.email ?: "No registrado"

        binding.tvAccountOwner.text = "Usuario: $displayName"
        binding.tvAccountEmail.text = "Correo: $email"

        binding.btnResetPassword.setOnClickListener {
            if (!email.contains("@")) {
                Toast.makeText(this, "Correo no válido", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    AlertDialog.Builder(this)
                        .setTitle("Restablecer Contraseña")
                        .setMessage("Se ha enviado un correo a $email para cambiar tu contraseña de forma segura.")
                        .setPositiveButton("Entendido", null)
                        .show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun loadCurrentSettings() {
        databaseRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val data = snapshot.getValue(SmartPetData::class.java) ?: return

                // Cargar radio actual de geocerca
                val radius = if (data.safeZoneRadiusMeters in 20f..150f) data.safeZoneRadiusMeters else 50f
                binding.sliderSafeZoneRadius.value = radius
                binding.tvSafeZoneRadiusLabel.text = "Radio de seguridad: %.0f metros".format(radius)

                // Cargar modo de enlace
                if (data.connectionMode == "BLE") {
                    binding.rbBluetooth.isChecked = true
                } else {
                    binding.rbWifi.isChecked = true
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun setupListeners() {
        // Slider para radio de geocerca
        binding.sliderSafeZoneRadius.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                binding.tvSafeZoneRadiusLabel.text = "Radio de seguridad: %.0f metros".format(value)
                databaseRef.child("safeZoneRadiusMeters").setValue(value)
                simulator?.setSafeZoneRadius(value)
            }
        }

        // Fijar ubicación GPS como base (Hogar)
        binding.btnSetBaseLocation.setOnClickListener {
            simulator?.setCurrentAsBase()
            Toast.makeText(this, "Coordenadas fijadas como Hogar", Toast.LENGTH_SHORT).show()
        }

        // Selección de canal WiFi vs BLE
        binding.rgChannel.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == binding.rbBluetooth.id) "BLE" else "WIFI"
            databaseRef.child("connectionMode").setValue(mode)
            Toast.makeText(this, "Modo de comunicación: $mode", Toast.LENGTH_SHORT).show()
        }

        // Switches de laboratorio / simulación
        binding.switchAutoSimulation.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                simulator?.startSimulation()
                binding.tvSyncBadge.text = "🟢 Simulando Activo"
            } else {
                simulator?.stopSimulation()
                binding.tvSyncBadge.text = "⚪ Simulación Pausada"
            }
        }

        binding.switchForceBreach.setOnCheckedChangeListener { _, isChecked ->
            simulator?.simulateBreach(isChecked)
            val msg = if (isChecked) "Mascota fuera de perímetro" else "Mascota regresó a zona segura"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // Cerrar sesión
        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Cerrar Sesión")
                .setMessage("¿Estás seguro de que deseas salir?")
                .setPositiveButton("Salir") { _, _ ->
                    simulator?.stopSimulation()
                    auth.signOut()
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun setupNavigation() {
        binding.navHomeActivator.setOnClickListener {
            finish() // Regresa al Dashboard
        }

        binding.navFoodActivator.setOnClickListener {
            finish()
        }

        binding.navLocationActivator.setOnClickListener {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        simulator?.stopSimulation()
    }
}