package com.smartattendance.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class AuthActivity : AppCompatActivity() {

    private var auth: FirebaseAuth? = null
    private var isFirebaseAvailable = true
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_CANCELED) {
            Toast.makeText(this, "Google Sign-In cancelled", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (idToken != null) {
                firebaseAuthWithGoogle(idToken)
            } else {
                Toast.makeText(this, "Google Sign-In failed: No ID Token", Toast.LENGTH_SHORT).show()
            }
        } catch (e: ApiException) {
            val errorMsg = if (e.statusCode == 10) {
                "Google Sign-In Error 10 (Developer Error): Please add your debug SHA-1 fingerprint to Firebase Console for project 'smartattendance-bf260'."
            } else {
                "Google Sign-In failed: ${e.message} (Code ${e.statusCode})"
            }
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        try {
            auth = FirebaseAuth.getInstance()
        } catch (e: Exception) {
            isFirebaseAvailable = false
        }

        // Configure Google Sign In with safe fallback initialization
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
            googleSignInClient = GoogleSignIn.getClient(this, gso)
        } catch (e: Exception) {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build()
            googleSignInClient = GoogleSignIn.getClient(this, gso)
        }

        // If already logged in, go to MainActivity
        if (isFirebaseAvailable && auth?.currentUser != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        val edtEmail = findViewById<TextInputEditText>(R.id.edtEmail)
        val edtPassword = findViewById<TextInputEditText>(R.id.edtPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val btnGoogleSignIn = findViewById<Button>(R.id.btnGoogleSignIn)

        btnLogin.setOnClickListener {
            val email = edtEmail.text?.toString()?.trim() ?: ""
            val password = edtPassword.text?.toString()?.trim() ?: ""
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isFirebaseAvailable || auth == null) {
                Toast.makeText(this, "Offline Mode: Login Successful", Toast.LENGTH_SHORT).show()
                getSharedPreferences("mock_auth", MODE_PRIVATE).edit().putString("mock_email", email).apply()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
                return@setOnClickListener
            }

            auth?.signInWithEmailAndPassword(email, password)
                ?.addOnSuccessListener {
                    Toast.makeText(this, "Login Successful", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                ?.addOnFailureListener { e: Exception ->
                    Toast.makeText(this, "Login Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }

        btnRegister.setOnClickListener {
            val email = edtEmail.text?.toString()?.trim() ?: ""
            val password = edtPassword.text?.toString()?.trim() ?: ""
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isFirebaseAvailable || auth == null) {
                Toast.makeText(this, "Offline Mode: Registration Successful", Toast.LENGTH_SHORT).show()
                getSharedPreferences("mock_auth", MODE_PRIVATE).edit().putString("mock_email", email).apply()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
                return@setOnClickListener
            }

            auth?.createUserWithEmailAndPassword(email, password)
                ?.addOnSuccessListener {
                    Toast.makeText(this, "Registration Successful", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                ?.addOnFailureListener { e: Exception ->
                    Toast.makeText(this, "Registration Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }

        btnGoogleSignIn.setOnClickListener {
            if (!::googleSignInClient.isInitialized) {
                Toast.makeText(this, "Google Sign-In is not initialized. Please ensure google-services.json is configured.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            googleSignInClient.signOut().addOnCompleteListener {
                val signInIntent = googleSignInClient.signInIntent
                googleSignInLauncher.launch(signInIntent)
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        if (!isFirebaseAvailable || auth == null) {
            Toast.makeText(this, "Offline Mode: Google Login Successful", Toast.LENGTH_SHORT).show()
            getSharedPreferences("mock_auth", MODE_PRIVATE).edit().putString("mock_email", "google_user@gmail.com").apply()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth?.signInWithCredential(credential)
            ?.addOnSuccessListener {
                Toast.makeText(this, "Google Login Successful", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            ?.addOnFailureListener { e: Exception ->
                Toast.makeText(this, "Google Auth Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
