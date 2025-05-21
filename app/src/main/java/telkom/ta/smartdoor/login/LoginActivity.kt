package telkom.ta.smartdoor.login

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import telkom.ta.smartdoor.MainActivity
import telkom.ta.smartdoor.R
import telkom.ta.smartdoor.register.RegisterActivity
import java.io.IOException

class LoginActivity : AppCompatActivity() {
    private val client = OkHttpClient()

    companion object {
        const val PREFS_NAME = "secure_prefs"

        fun logout(sharedPreferences: SharedPreferences) {
            sharedPreferences.edit().clear().apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Cek apakah user sudah login
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val sharedPreferences = EncryptedSharedPreferences.create(
            this,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        if (sharedPreferences.contains("name")) {
            // User sudah login, langsung ke MainActivity
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        // Inisialisasi komponen UI
        val edtEmail = findViewById<EditText>(R.id.edt_Email)
        val edtPassword = findViewById<EditText>(R.id.edt_Password)
        val btnLogin = findViewById<Button>(R.id.btnlogin)
        val tvRegister = findViewById<TextView>(R.id.tv_Register)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        btnLogin.setOnClickListener {
            val email = edtEmail.text.toString().trim()
            val password = edtPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Email dan Password harus diisi!", Toast.LENGTH_SHORT).show()
            } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                edtEmail.error = "Format email tidak valid"
            } else {
                progressBar.visibility = View.VISIBLE
                performLogin(email, password, progressBar, sharedPreferences)
            }
        }

        tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun performLogin(email: String, password: String, progressBar: ProgressBar, sharedPreferences: SharedPreferences) {
        val json = JSONObject().apply {
            put("email", email)
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

                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        Log.d("LoginSuccess", "Response: $responseBody")

                        try {
                            val jsonResponse = JSONObject(responseBody ?: "{}")
                            sharedPreferences.edit().apply {
                                putString("name", jsonResponse.optString("name", "User"))
                                putString("nim", jsonResponse.optString("nim", "000000000"))
                                putString("email", email)  // Simpan email
                                apply()
                            }

                            Toast.makeText(
                                this@LoginActivity,
                                "Login Berhasil",
                                Toast.LENGTH_SHORT
                            ).show()

                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                            finish()

                        } catch (e: Exception) {
                            Log.e("LoginParseError", "Gagal parsing JSON: ${e.message}")
                            Toast.makeText(this@LoginActivity, "Terjadi kesalahan pada data login", Toast.LENGTH_SHORT).show()
                        }

                    } else {
                        val errorCode = response.code
                        val errorMessage = when (errorCode) {
                            401 -> "Email atau password salah"
                            404 -> "User tidak ditemukan"
                            500 -> "Server error. Coba lagi nanti."
                            else -> "Login gagal (Error $errorCode)"
                        }

                        Toast.makeText(
                            this@LoginActivity,
                            errorMessage,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }
}
