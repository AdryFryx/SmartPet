package com.example.smartpet.controller

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.smartpet.R
import com.example.smartpet.model.SmartPetData
import com.example.smartpet.repository.SmartPetRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val repository = SmartPetRepository()

    private var selectedFoodCapacity = 450.0
    private var selectedSizeText =
        "Perros grandes (20 a 40 kg): 300 a 600 g al día"

    private var passwordConfirmationAttempted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()

        setupSizeSpinner()
        setupPasswordConfirmationValidation()

        val btnDoRegister = findViewById<MaterialButton>(R.id.btnDoRegister)
        val tvBackToLogin = findViewById<TextView>(R.id.tvBackToLogin)

        btnDoRegister.setOnClickListener {
            handleRegister()
        }

        tvBackToLogin.setOnClickListener {
            finish()
        }
    }

    private fun setupSizeSpinner() {
        val spinner = findViewById<Spinner>(R.id.spinnerPetSize)
        val tvRecommendation = findViewById<TextView>(R.id.tvSizeRecommendation)

        val sizeOptions = arrayOf(
            "Perros mini (2 a 3 kg): 50 a 70 g al día",
            "Perros pequeños (3 a 10 kg): 70 a 150 g al día",
            "Perros medianos (10 a 20 kg): 150 a 300 g al día",
            "Perros grandes (20 a 40 kg): 300 a 600 g al día",
            "Perros gigantes (más de 50 kg): 500 a 700 g al día"
        )

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            sizeOptions
        )

        spinner.adapter = adapter
        spinner.setSelection(3)

        spinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    selectedSizeText = sizeOptions[position]

                    selectedFoodCapacity = when (position) {
                        0 -> 60.0
                        1 -> 110.0
                        2 -> 225.0
                        3 -> 450.0
                        4 -> 600.0
                        else -> 350.0
                    }

                    tvRecommendation.text =
                        "Capacidad sugerida de plato: %.0f g diarios"
                            .format(selectedFoodCapacity)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
    }

    private fun setupPasswordConfirmationValidation() {
        val etPassword = findViewById<TextInputEditText>(R.id.etRegisterPassword)
        val etPasswordConfirm = findViewById<TextInputEditText>(R.id.etRegisterPasswordConfirm)

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (passwordConfirmationAttempted) {
                    validatePasswordConfirmation()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        etPassword.addTextChangedListener(watcher)
        etPasswordConfirm.addTextChangedListener(watcher)
    }

    private fun validatePasswordConfirmation(): Boolean {
        val etPassword = findViewById<TextInputEditText>(R.id.etRegisterPassword)
        val etPasswordConfirm = findViewById<TextInputEditText>(R.id.etRegisterPasswordConfirm)
        val tilPasswordConfirm = findViewById<TextInputLayout>(R.id.tilPasswordConfirm)

        val password = etPassword.text.toString()
        val passwordConfirm = etPasswordConfirm.text.toString()

        val passwordsMatch =
            password.isNotEmpty() &&
                    passwordConfirm.isNotEmpty() &&
                    password == passwordConfirm

        if (passwordConfirm.isEmpty()) {
            tilPasswordConfirm.error = "Confirma tu contraseña"
            return false
        }

        if (!passwordsMatch) {
            tilPasswordConfirm.error = "Las contraseñas no coinciden"
            return false
        }

        tilPasswordConfirm.error = null
        tilPasswordConfirm.isErrorEnabled = false
        return true
    }

    private fun handleRegister() {
        val etOwnerName = findViewById<TextInputEditText>(R.id.etRegisterOwnerName)
        val etEmail = findViewById<TextInputEditText>(R.id.etRegisterEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etRegisterPassword)
        val etPasswordConfirm = findViewById<TextInputEditText>(R.id.etRegisterPasswordConfirm)
        val etPetName = findViewById<TextInputEditText>(R.id.etRegisterPetName)
        val etPetBreed = findViewById<TextInputEditText>(R.id.etRegisterPetBreed)
        val etPetAge = findViewById<TextInputEditText>(R.id.etRegisterPetAge)
        val btnDoRegister = findViewById<MaterialButton>(R.id.btnDoRegister)

        val ownerName = etOwnerName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()
        val passwordConfirm = etPasswordConfirm.text.toString()
        val petName = etPetName.text.toString().trim()
        val petBreed = etPetBreed.text.toString().trim()
        val petAge = etPetAge.text.toString().trim().toIntOrNull()

        if (
            ownerName.isEmpty() ||
            email.isEmpty() ||
            password.isEmpty() ||
            petName.isEmpty() ||
            petBreed.isEmpty() ||
            petAge == null
        ) {
            Toast.makeText(this, "Completa todos los campos obligatorios", Toast.LENGTH_SHORT).show()
            return
        }

        if (petAge !in 0..30) {
            Toast.makeText(this, "La edad debe estar entre 0 y 30 años", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.length < 6) {
            Toast.makeText(this, "La contraseña debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show()
            return
        }

        passwordConfirmationAttempted = true

        if (!validatePasswordConfirmation()) {
            etPasswordConfirm.requestFocus()
            return
        }

        btnDoRegister.isEnabled = false
        btnDoRegister.text = "Registrando..."

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser

                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(ownerName)
                        .build()

                    user?.updateProfile(profileUpdates)

                    // Objeto de inicialización guardado directamente en Firestore
                    val initialPetData = mapOf(
                        "ownerName" to ownerName,
                        "petName" to petName,
                        "petBreed" to petBreed,
                        "petAge" to petAge,
                        "petSizeCategory" to selectedSizeText,
                        "foodMaxCapacityGrams" to selectedFoodCapacity,
                        "foodDoseGrams" to (selectedFoodCapacity / 3.0),
                        "foodWeightGrams" to (selectedFoodCapacity * 0.75),
                        "foodPercent" to 75,
                        "waterMaxCapacityMl" to 1000.0,
                        "waterWeightGrams" to 750.0,
                        "waterPercent" to 75,
                        "latitude" to -33.4569,
                        "longitude" to -70.6483,
                        "homeLatitude" to -33.4569,
                        "homeLongitude" to -70.6483,
                        "isInsideSafeZone" to true,
                        "distanceFromBaseMeters" to 0.0f,
                        "safeZoneRadiusMeters" to 50.0f,
                        "connectionMode" to "WIFI",
                        "isDeviceOnline" to true,
                        "userPhotoBase64" to "",
                        "petPhotoBase64" to "",
                        "lastUpdated" to System.currentTimeMillis()
                    )

                    // Guardar en Cloud Firestore a través del repositorio
                    repository.updatePetData(initialPetData)
                        .addOnCompleteListener { firestoreTask ->
                            if (firestoreTask.isSuccessful) {
                                Toast.makeText(
                                    this,
                                    "¡Bienvenido $ownerName! Cuenta creada.",
                                    Toast.LENGTH_SHORT
                                ).show()

                                val intent = Intent(this, MonitoringActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                            } else {
                                btnDoRegister.isEnabled = true
                                btnDoRegister.text = "Completar Registro"
                                Toast.makeText(
                                    this,
                                    "Cuenta creada, pero hubo un error al guardar los datos en Firestore: ${firestoreTask.exception?.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                } else {
                    btnDoRegister.isEnabled = true
                    btnDoRegister.text = "Completar Registro"
                    Toast.makeText(
                        this,
                        "Error: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }
}