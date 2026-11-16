package com.example.smartpet.controller

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.smartpet.databinding.ActivityActivatorBinding
import com.example.smartpet.model.PetDeviceSimulator
import com.example.smartpet.repository.SmartPetRepository
import com.example.smartpet.util.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

class ActivatorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityActivatorBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var repository: SmartPetRepository
    private lateinit var notificationHelper: NotificationHelper

    private var settingsListener: ListenerRegistration? = null
    private var isApplyingRemoteState = false
    private var wasInsideSafeZoneLastTime = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityActivatorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        repository = SmartPetRepository()
        notificationHelper = NotificationHelper(this)

        setupAccountInfo()
        setupListeners()
        setupNavigation()
    }

    override fun onStart() {
        super.onStart()
        settingsListener = repository.observePetData { data ->

            // EL DISPARADOR DE NOTIFICACIONES QUE FALTABA
            if (!data.isInsideSafeZone && wasInsideSafeZoneLastTime) {
                notificationHelper.sendSafeZoneBreachAlert(data.petName)
                wasInsideSafeZoneLastTime = false
            } else if (data.isInsideSafeZone) {
                wasInsideSafeZoneLastTime = true
            }

            // SOLO ACTUALIZAMOS LA INTERFAZ SI EL USUARIO NO LA ESTÁ TOCANDO
            if (!isApplyingRemoteState) {
                val radius = data.safeZoneRadiusMeters.coerceIn(20f, 150f)
                binding.sliderSafeZoneRadius.value = radius
                binding.tvSafeZoneRadiusLabel.text = "Radio de seguridad: %.0f metros".format(radius)
                binding.rbBluetooth.isChecked = (data.connectionMode == "BLE")
                binding.rbWifi.isChecked = (data.connectionMode != "BLE")

                binding.switchForceBreach.isChecked = !data.isInsideSafeZone

                binding.switchAutoSimulation.isChecked = PetDeviceSimulator.isSimulating
                binding.tvSyncBadge.text = if (PetDeviceSimulator.isSimulating) "🟢 Simulación activa" else "⚪ Simulación pausada"
            }
        }
    }

    override fun onStop() {
        super.onStop()
        settingsListener?.remove()
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
            auth.sendPasswordResetEmail(email).addOnSuccessListener {
                AlertDialog.Builder(this)
                    .setTitle("Restablecer Contraseña")
                    .setMessage("Se ha enviado un correo a $email para cambiar tu contraseña.")
                    .setPositiveButton("Entendido", null)
                    .show()
            }.addOnFailureListener { error ->
                Toast.makeText(this, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupListeners() {
        binding.sliderSafeZoneRadius.addOnChangeListener { _, value, fromUser ->
            if (fromUser && !isApplyingRemoteState) {
                binding.tvSafeZoneRadiusLabel.text = "Radio de seguridad: %.0f metros".format(value)
                PetDeviceSimulator.setSafeZoneRadius(value)
            }
        }

        binding.btnSetBaseLocation.setOnClickListener {
            PetDeviceSimulator.setCurrentAsBase()
            Toast.makeText(this, "Coordenadas fijadas como Hogar", Toast.LENGTH_SHORT).show()
        }

        binding.rgChannel.setOnCheckedChangeListener { _, checkedId ->
            if (!isApplyingRemoteState && checkedId != -1) {
                val mode = if (checkedId == binding.rbBluetooth.id) "BLE" else "WIFI"
                repository.updatePetData(mapOf("connectionMode" to mode)).addOnSuccessListener {
                    Toast.makeText(this, "Modo de comunicación: $mode", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.switchAutoSimulation.setOnClickListener {
            val isChecked = binding.switchAutoSimulation.isChecked
            if (isChecked) {
                PetDeviceSimulator.startSimulation()
                binding.tvSyncBadge.text = "🟢 Simulación activa"
            } else {
                PetDeviceSimulator.stopSimulation()
                binding.tvSyncBadge.text = "⚪ Simulación pausada"
            }
        }

        binding.switchForceBreach.setOnClickListener {
            val isChecked = binding.switchForceBreach.isChecked

            isApplyingRemoteState = true

            if (isChecked) {
                binding.switchAutoSimulation.isChecked = false
                binding.tvSyncBadge.text = "⚪ Simulación pausada"
            }

            PetDeviceSimulator.simulateBreach(isChecked)

            val message = if (isChecked) "¡Alerta de escape forzada!" else "Mascota devuelta a base"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

            // Descongelar visualmente en 2 segundos
            binding.switchForceBreach.postDelayed({ isApplyingRemoteState = false }, 2000)
        }

        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Cerrar Sesión")
                .setMessage("¿Estás seguro de que deseas salir?")
                .setPositiveButton("Salir") { _, _ ->
                    PetDeviceSimulator.stopSimulation()
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
        binding.navHomeActivator.setOnClickListener { finish() }
        binding.navFoodActivator.setOnClickListener { finish() }
        binding.navLocationActivator.setOnClickListener { finish() }
    }
}