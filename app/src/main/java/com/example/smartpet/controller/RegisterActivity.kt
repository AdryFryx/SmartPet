package com.example.smartpet.controller

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.smartpet.R
import com.example.smartpet.model.SmartPetData
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var selectedFoodCapacity = 450.0 // Por defecto grande
    private var selectedSizeText = "Perros grandes (20 a 40 kg): 300 a 600 g al día"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()

        setupSizeSpinner()

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

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sizeOptions)
        spinner.adapter = adapter
        spinner.setSelection(3) // Selecciona 'Perros grandes' por defecto

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedSizeText = sizeOptions[position]
                selectedFoodCapacity = when (position) {
                    0 -> 60.0   // Promedio mini
                    1 -> 110.0  // Promedio pequeño
                    2 -> 225.0  // Promedio mediano
                    3 -> 450.0  // Promedio grande
                    4 -> 600.0  // Promedio gigante
                    else -> 350.0
                }
                tvRecommendation.text = "Capacidad sugerida de plato: %.0f g diarios".format(selectedFoodCapacity)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun handleRegister() {
        val etOwnerName = findViewById<TextInputEditText>(R.id.etRegisterOwnerName)
        val etEmail = findViewById<TextInputEditText>(R.id.etRegisterEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etRegisterPassword)
        val etPetName = findViewById<TextInputEditText>(R.id.etRegisterPetName)
        val etPetBreed = findViewById<TextInputEditText>(R.id.etRegisterPetBreed)
        val btnDoRegister = findViewById<MaterialButton>(R.id.btnDoRegister)

        val ownerName = etOwnerName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val petName = etPetName.text.toString().trim()
        val petBreed = etPetBreed.text.toString().trim()

        if (ownerName.isEmpty() || email.isEmpty() || password.isEmpty() || petName.isEmpty()) {
            Toast.makeText(this, "Completa todos los campos obligatorios", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.length < 6) {
            Toast.makeText(this, "La contraseña debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show()
            return
        }

        btnDoRegister.isEnabled = false
        btnDoRegister.text = "Registrando..."

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser

                    // 1. Guardar el nombre de usuario en el perfil de Firebase Auth
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(ownerName)
                        .build()

                    user?.updateProfile(profileUpdates)

                    // 2. Crear los datos iniciales personalizados en Realtime Database
                    val initialPetData = SmartPetData(
                        ownerName = ownerName,
                        petName = petName,
                        petBreed = if (petBreed.isNotEmpty()) petBreed else "Mascota",
                        petSizeCategory = selectedSizeText,
                        foodMaxCapacityGrams = selectedFoodCapacity,
                        foodDoseGrams = (selectedFoodCapacity / 3.0), // Ración recomendada de 1/3 del total diario
                        foodWeightGrams = selectedFoodCapacity * 0.75, // Comienza con 75%
                        foodPercent = 75,
                        waterMaxCapacityMl = 1000.0,
                        waterWeightGrams = 750.0,
                        waterPercent = 75
                    )

                    val databaseRef = FirebaseDatabase.getInstance().getReference("smartpet_simulation")
                    databaseRef.setValue(initialPetData).addOnCompleteListener {
                        Toast.makeText(this, "¡Bienvenido $ownerName! Cuenta creada.", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this, MonitoringActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                } else {
                    btnDoRegister.isEnabled = true
                    btnDoRegister.text = "Completar Registro"
                    Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }
}