
package com.example.smartpet.controller

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.smartpet.databinding.ActivityActivatorBinding
import com.example.smartpet.model.PetDeviceSimulator
import com.example.smartpet.model.SmartPetData
import com.example.smartpet.repository.SmartPetRepository
import com.google.firebase.auth.FirebaseAuth

class ActivatorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityActivatorBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var repository: SmartPetRepository

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
                Toast.makeText(
                    this,
                    "Correo no válido",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    AlertDialog.Builder(this)
                        .setTitle("Restablecer Contraseña")
                        .setMessage(
                            "Se ha enviado un correo a $email para cambiar tu contraseña de forma segura."
                        )
                        .setPositiveButton("Entendido", null)
                        .show()
                }
                .addOnFailureListener { error ->
                    Toast.makeText(
                        this,
                        "Error: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    private fun loadCurrentSettings() {
        repository.getPetData()
            .addOnSuccessListener { snapshot ->
                val data = snapshot.toObject(SmartPetData::class.java)

                if (data == null) {
                    Toast.makeText(
                        this,
                        "No se encontraron los ajustes de la mascota",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@addOnSuccessListener
                }

                isApplyingRemoteState = true

                val radius = data.safeZoneRadiusMeters
                    .coerceIn(20f, 150f)

                binding.sliderSafeZoneRadius.value = radius

                binding.tvSafeZoneRadiusLabel.text =
                    "Radio de seguridad: %.0f metros".format(radius)

                if (data.connectionMode == "BLE") {
                    binding.rbBluetooth.isChecked = true
                } else {
                    binding.rbWifi.isChecked = true
                }

                isApplyingRemoteState = false

                binding.tvSyncBadge.text =
                    if (simulator.isSimulating) {
                        "🟢 Simulación activa"
                    } else {
                        "⚪ Simulación pausada"
                    }

                binding.switchAutoSimulation.isChecked =
                    simulator.isSimulating
            }
            .addOnFailureListener { error ->
                isApplyingRemoteState = false

                Toast.makeText(
                    this,
                    "Error al cargar ajustes: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun setupListeners() {

        binding.sliderSafeZoneRadius.addOnChangeListener { _, value, fromUser ->
            if (fromUser && !isApplyingRemoteState) {

                binding.tvSafeZoneRadiusLabel.text =
                    "Radio de seguridad: %.0f metros".format(value)

                simulator.setSafeZoneRadius(value)
            }
        }

        binding.btnSetBaseLocation.setOnClickListener {
            simulator.setCurrentAsBase()

            Toast.makeText(
                this,
                "Coordenadas fijadas como Hogar",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.rgChannel.setOnCheckedChangeListener { _, checkedId ->
            if (!isApplyingRemoteState && checkedId != -1) {

                val mode =
                    if (checkedId == binding.rbBluetooth.id) {
                        "BLE"
                    } else {
                        "WIFI"
                    }

                repository.updatePetData(
                    mapOf("connectionMode" to mode)
                ).addOnSuccessListener {
                    Toast.makeText(
                        this,
                        "Modo de comunicación: $mode",
                        Toast.LENGTH_SHORT
                    ).show()
                }.addOnFailureListener {
                    Toast.makeText(
                        this,
                        "No se pudo guardar el modo de comunicación",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        binding.switchAutoSimulation.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                simulator.startSimulation()

                binding.tvSyncBadge.text = "🟢 Simulación activa"
            } else {
                simulator.stopSimulation()

                binding.tvSyncBadge.text = "⚪ Simulación pausada"
            }
        }

        binding.switchForceBreach.setOnCheckedChangeListener { _, isChecked ->
            simulator.simulateBreach(isChecked)

            val message =
                if (isChecked) {
                    "Mascota fuera de perímetro"
                } else {
                    "Mascota regresó a zona segura"
                }

            Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Cerrar Sesión")
                .setMessage("¿Estás seguro de que deseas salir?")
                .setPositiveButton("Salir") { _, _ ->
                    simulator.stopSimulation()
                    auth.signOut()

                    val intent = Intent(
                        this,
                        LoginActivity::class.java
                    )

                    intent.flags =
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK

                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun setupNavigation() {
        binding.navHomeActivator.setOnClickListener {
            finish()
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
    }
}
