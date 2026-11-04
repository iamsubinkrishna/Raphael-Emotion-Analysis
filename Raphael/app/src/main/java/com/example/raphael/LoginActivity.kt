package com.example.raphael

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.raphael.api.NetworkClient
import com.example.raphael.databinding.ActivityLoginBinding
import com.example.raphael.models.*
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text.toString()
            val password = binding.etPassword.text.toString()

            if (username.isBlank() || password.isBlank()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    val response = NetworkClient.authService.login(LoginRequest(username, password))
                    if (response.isSuccessful) {
                        response.body()?.let { authResponse ->
                            // Save token
                            getSharedPreferences("auth", MODE_PRIVATE).edit()
                                .putString("token", authResponse.token)
                                .putString("username", authResponse.username)
                                .apply()

                            // Navigate to main activity
                            handleLoginSuccess(authResponse)
                        }
                    } else {
                        Toast.makeText(this@LoginActivity, "Login failed", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@LoginActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun handleLoginSuccess(response: AuthResponse) {
        // Store token if needed for other purposes
        val token = response.token
        
        // Start MainActivity with token
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("token", token)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}