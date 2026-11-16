package com.example.smartpet.controller

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.smartpet.databinding.ActivityActivatorBinding
import com.example.smartpet.model.PetDeviceSimulator
import com.example.smartpet.repository.SmartPetRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

class ActivatorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityActivatorBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var repository: SmartPetRepository

    private var settingsListener: ListenerRegistration? = null
    private var isApplyingRemoteState = false

    companion object {
        private val simulator = PetDeviceSimulator()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityActivatorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        repository = SmartPetRepository()

        setupAccountInfo()
        setupListeners()
        setupNavigation()
    }

    override fun onStart() {
        super.onStart()
        settingsListener = repository.observePetData { data ->
            // Solo actualizamos la pantalla si NO estamos en medio de un toque manual
            if (!isApplyingRemoteState) {
                val radius = data.safeZoneRadiusMeters.coerceIn(20f, 150f)
                binding.sliderSafeZoneRadius.value = radius
                binding.tvSafeZoneRadiusLabel.text = "Radio de seguridad: %.0f metros".format(radius)
                binding.rbBluetooth.isChecked = (data.connectionMode == "BLE")
                binding.rbWifi.isChecked = (data.connectionMode != "BLE")

                binding.switchForceBreach.isChecked = !data.isInsideSafeZone

                binding.switchAutoSimulation.isChecked = simulator.isSimulating
                binding.tvSyncBadge.text = if (simulator.isSimulating) "🟢 Simulación activa" else "⚪ Simulación pausada"
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
                simulator.setSafeZoneRadius(value)
            }
        }

        binding.btnSetBaseLocation.setOnClickListener {
            simulator.setCurrentAsBase()
            Toast.makeText(this, "Coordenadas fijadas como Hogar", Toast.LENGTH_SHORT).show()
        }

        // RadioButtons para WIFI/BLE
        binding.rgChannel.setOnCheckedChangeListener { _, checkedId ->
            if (!isApplyingRemoteState && checkedId != -1) {
                val mode = if (checkedId == binding.rbBluetooth.id) "BLE" else "WIFI"
                repository.updatePetData(mapOf("connectionMode" to mode)).addOnSuccessListener {
                    Toast.makeText(this, "Modo de comunicación: $mode", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // SWITCH SIMULACIÓN: Solo reacciona al toque físico del dedo
        binding.switchAutoSimulation.setOnClickListener {
            val isChecked = binding.switchAutoSimulation.isChecked
            if (isChecked) {
                simulator.startSimulation()
                binding.tvSyncBadge.text = "🟢 Simulación activa"
            } else {
                simulator.stopSimulation()
                binding.tvSyncBadge.text = "⚪ Simulación pausada"
            }
        }

        // SWITCH ESCAPE: Solo reacciona al toque físico del dedo
        binding.switchForceBreach.setOnClickListener {
            val isChecked = binding.switchForceBreach.isChecked

            // Congelamos la lectura remota por 2 segundos para ignorar el rebote visual de Firestore
            isApplyingRemoteState = true

            if (isChecked) {
                binding.switchAutoSimulation.isChecked = false // Apaga visualmente el auto-simulador
                binding.tvSyncBadge.text = "⚪ Simulación pausada"
            }

            simulator.simulateBreach(isChecked)

            val message = if (isChecked) "¡Alerta de escape forzada!" else "Mascota devuelta a base"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

            // Descongelamos la UI después de que la nube ya confirmó el dato (2 segundos)
            binding.switchForceBreach.postDelayed({ isApplyingRemoteState = false }, 2000)
        }

        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Cerrar Sesión")
                .setMessage("¿Estás seguro de que deseas salir?")
                .setPositiveButton("Salir") { _, _ ->
                    simulator.stopSimulation()
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