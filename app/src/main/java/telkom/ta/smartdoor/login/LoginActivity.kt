package telkom.ta.smartdoor.login

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import telkom.ta.smartdoor.MainActivity
import telkom.ta.smartdoor.R
import telkom.ta.smartdoor.register.RegisterActivity
import telkom.ta.smartdoor.session.SessionManager
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class LoginActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private lateinit var sessionManager: SessionManager
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        sessionManager = SessionManager(this)
        sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)

        if (sessionManager.isLoggedIn()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        val edtUsername = findViewById<EditText>(R.id.edt_Username)
        val edtPassword = findViewById<EditText>(R.id.edt_Password)
        val btnLogin = findViewById<Button>(R.id.btnlogin)
        val tvRegister = findViewById<TextView>(R.id.tv_Register)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        loadSavedUsername(edtUsername)

        btnLogin.setOnClickListener {
            val username = edtUsername.text.toString().trim()
            val password = edtPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Username dan Password harus diisi!", Toast.LENGTH_SHORT).show()
            } else {
                progressBar.visibility = View.VISIBLE
                performLogin(username, password, progressBar)
            }
        }

        tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun loadSavedUsername(edtUsername: EditText) {
        val savedUsername = sharedPreferences.getString("username", "")
        if (!savedUsername.isNullOrEmpty()) {
            edtUsername.setText(savedUsername)
        }
    }

    private fun saveLoginData(username: String, userId: String, profileId: String, email: String) {
        val editor = sharedPreferences.edit()
        editor.putString("username", username)
        editor.putString("userId", userId)
        editor.putString("profileId", profileId)
        editor.putString("email", email)
        editor.apply()

        // Also save to session manager
        sessionManager.saveLoginSession(userId, profileId, username, email)
    }

    private fun performLogin(username: String, password: String, progressBar: ProgressBar) {
        val json = JSONObject().apply {
            put("username", username)
            put("password", password)
        }

        val mediaType = "application/json".toMediaType()
        val requestBody = json.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url("https://backendapi-roan.vercel.app/auth/login")
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    Log.e("LoginError", "Koneksi gagal: ${e.message}")
                    Toast.makeText(
                        this@LoginActivity,
                        "Gagal terhubung ke server. Cek koneksi internet Anda.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    val responseBody = response.body?.string()

                    if (response.isSuccessful) {
                        try {
                            val jsonResponse = JSONObject(responseBody ?: "{}")
                            val message = jsonResponse.optString("message", "")

                            if (message == "Login successful") {
                                val userObject = jsonResponse.getJSONObject("user")
                                val userId = userObject.getString("id")
                                val profileId = userObject.getString("profileId")
                                val username = userObject.getString("username")
                                val email = userObject.getString("email")

                                // Save all necessary data
                                saveLoginData(username, userId, profileId, email)

                                Toast.makeText(
                                    this@LoginActivity,
                                    "Login Berhasil - Selamat datang, $username!",
                                    Toast.LENGTH_SHORT
                                ).show()

                                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                                finish()
                            } else {
                                Toast.makeText(
                                    this@LoginActivity,
                                    "Login gagal: $message",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (e: Exception) {
                            Log.e("LoginParseError", "Gagal parsing JSON: ${e.message}")
                            Toast.makeText(
                                this@LoginActivity,
                                "Terjadi kesalahan saat memproses data login",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        try {
                            val errorResponse = JSONObject(responseBody ?: "{}")
                            val errorMessage = errorResponse.optString("error",
                                when (response.code) {
                                    400 -> "Username atau password salah"
                                    500 -> "Server error. Coba lagi nanti."
                                    else -> "Login gagal (Error ${response.code})"
                                }
                            )
                            Toast.makeText(
                                this@LoginActivity,
                                errorMessage,
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                this@LoginActivity,
                                "Terjadi kesalahan saat login",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        })
    }
}