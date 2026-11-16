package com.example.smartpet.controller

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.smartpet.databinding.ActivityLoginBinding
import com.example.smartpet.repository.SmartPetRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.example.smartpet.R

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val repository = SmartPetRepository()

    // Lanzador para la ventana de selección de cuenta de Google
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Toast.makeText(this, "Error de Google: ${e.message}", Toast.LENGTH_SHORT).show()
                resetGoogleButton()
            }
        } else {
            resetGoogleButton()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        )

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        if (auth.currentUser != null) {
            navigateToDashboard()
            return
        }

        // Configuración de Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setupButtons()
    }

    private fun setupButtons() {
        // Login normal por correo
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Ingresa correo y contraseña", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnLogin.isEnabled = false
            binding.btnLogin.text = "Iniciando..."

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        navigateToDashboard()
                    } else {
                        binding.btnLogin.isEnabled = true
                        binding.btnLogin.text = "Iniciar Sesión"
                        Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        // Navegar a Registro normal
        binding.btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Login / Registro con Google
        binding.btnGoogleSignIn.setOnClickListener {
            binding.btnGoogleSignIn.isEnabled = false
            binding.btnGoogleSignIn.text = "Conectando..."
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val isNewUser = task.result?.additionalUserInfo?.isNewUser ?: false

                    if (isNewUser && user != null) {
                        // Si es primera vez, creamos su perfil en Firestore con valores por defecto
                        createDefaultProfile(user.displayName ?: "Usuario Google")
                    } else {
                        // Usuario existente
                        navigateToDashboard()
                    }
                } else {
                    resetGoogleButton()
                    Toast.makeText(this, "Error autenticando en Firebase", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun createDefaultProfile(ownerName: String) {
        val initialPetData = mapOf(
            "ownerName" to ownerName,
            "petName" to "Mascota (Editar)",
            "petBreed" to "Raza (Editar)",
            "petAge" to 1,
            "petSizeCategory" to "Perros medianos (10 a 20 kg): 150 a 300 g al día",
            "foodMaxCapacityGrams" to 450.0,
            "foodDoseGrams" to 150.0,
            "foodWeightGrams" to 0.0,
            "foodPercent" to 0,
            "waterMaxCapacityMl" to 1000.0,
            "waterWeightGrams" to 0.0,
            "waterPercent" to 0,
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

        repository.updatePetData(initialPetData)
            .addOnCompleteListener {
                Toast.makeText(this, "¡Bienvenido $ownerName!", Toast.LENGTH_SHORT).show()
                navigateToDashboard()
            }
    }

    private fun resetGoogleButton() {
        binding.btnGoogleSignIn.isEnabled = true
        binding.btnGoogleSignIn.text = "Continuar con Google"
    }

    private fun navigateToDashboard() {
        val intent = Intent(this, MonitoringActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}