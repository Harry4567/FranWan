package com.example.franwan.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.franwan.MainActivity
import com.example.franwan.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var loader: ProgressBar
    private lateinit var registerLink: TextView
    private lateinit var guestButton: Button

    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + job)

    private lateinit var sessionManager: SessionManager
    private lateinit var repository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        sessionManager = SessionManager(this)
        repository = AuthRepository(sessionManager)

        if (sessionManager.isLoggedIn()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        loginButton = findViewById(R.id.loginButton)
        loader = findViewById(R.id.loginLoader)
        registerLink = findViewById(R.id.registerLink)
        guestButton = findViewById(R.id.guestButton)

        loginButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Veuillez remplir email et mot de passe", Toast.LENGTH_SHORT).show()
            } else {
                doLogin(email, password)
            }
        }

        registerLink.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://franwan.rf.gd/user/register.html"))
            startActivity(intent)
        }

        guestButton.setOnClickListener {
            showGuestModeConfirmation()
        }
    }

    private fun doLogin(email: String, password: String) {
        setLoading(true)
        uiScope.launch {
            val result = repository.login(email, password)
            setLoading(false)
            result.onSuccess {
                Toast.makeText(this@LoginActivity, "Connexion réussie", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                finish()
            }.onFailure { e ->
                val errorMsg = when {
                    e.message?.contains("malformed JSON") == true -> "Erreur: L'API renvoie du HTML (anti-bot InfinityFree)"
                    e.message?.contains("400") == true -> "Erreur 400: Requête invalide"
                    e.message?.contains("401") == true -> "Erreur 401: Identifiants incorrects"
                    else -> "Échec: ${e.message}"
                }
                Toast.makeText(this@LoginActivity, errorMsg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        loader.visibility = if (isLoading) View.VISIBLE else View.GONE
        loginButton.isEnabled = !isLoading
    }

    private fun showGuestModeConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Mode invité")
            .setMessage("Vous allez accéder à l'application en mode invité. Vous pourrez toujours vous connecter plus tard depuis les paramètres de l'application.")
            .setPositiveButton("Continuer") { _, _ ->
                sessionManager.setGuestMode(true)
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}


