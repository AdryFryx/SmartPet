package com.example.smartpet.controller

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.smartpet.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        )
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Verificación de sesión previa activa
        val currentUser = auth.currentUser
        if (currentUser != null) {
            goToMonitoring()
            return
        }

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (validateInputs(email, password)) {
                loginUser(email, password)
            }
        }

        binding.btnRegister.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (validateInputs(email, password)) {
                registerUser(email, password)
            }
        }
    }

    private fun validateInputs(email: String, password: String): Boolean {
        if (email.isEmpty()) {
            binding.tilEmail.error = "Ingresa tu correo"
            binding.etEmail.requestFocus()
            return false
        }
        binding.tilEmail.error = null

        if (password.isEmpty()) {
            binding.tilPassword.error = "Ingresa tu contraseña"
            binding.etPassword.requestFocus()
            return false
        }
        if (password.length < 6) {
            binding.tilPassword.error = "Mínimo 6 caracteres"
            binding.etPassword.requestFocus()
            return false
        }
        binding.tilPassword.error = null
        return true
    }

    private fun loginUser(email: String, password: String) {
        setLoading(true)
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                setLoading(false)
                if (task.isSuccessful) {
                    Toast.makeText(this, "Bienvenido a SmartPet", Toast.LENGTH_SHORT).show()
                    goToMonitoring()
                } else {
                    Toast.makeText(
                        this,
                        "Error al autenticar: ${task.exception?.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun registerUser(email: String, password: String) {
        setLoading(true)
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                setLoading(false)
                if (task.isSuccessful) {
                    Toast.makeText(this, "Cuenta registrada exitosamente", Toast.LENGTH_SHORT).show()
                    goToMonitoring()
                } else {
                    Toast.makeText(
                        this,
                        "Error al registrar: ${task.exception?.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !isLoading
        binding.btnRegister.isEnabled = !isLoading
    }

    private fun goToMonitoring() {
        val intent = Intent(this, MonitoringActivity::class.java)
        startActivity(intent)
        finish()
    }
}