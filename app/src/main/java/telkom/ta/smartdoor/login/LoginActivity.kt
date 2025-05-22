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
import telkom.ta.smartdoor.session.SessionManager // Import SessionManager
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class LoginActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private lateinit var sessionManager: SessionManager // Deklarasi SessionManager

    // Hapus companion object yang tidak perlu di sini, karena logout ditangani SessionManager
    // companion object {
    //     const val PREFS_NAME = "secure_prefs"
    //     fun logout(sharedPreferences: SharedPreferences) {
    //         sharedPreferences.edit().clear().apply()
    //     }
    // }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Inisialisasi SessionManager
        sessionManager = SessionManager(this)

        // Pindahkan cek login ke sini dan gunakan SessionManager
        if (sessionManager.isLoggedIn()) {
            // User sudah login, langsung ke MainActivity
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return // Penting: Hentikan eksekusi onCreate agar tidak inisialisasi UI login
        }

        // Inisialisasi komponen UI (hanya jika user BELUM login)
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
                // Kirim SessionManager ke performLogin agar bisa menyimpan data
                performLogin(email, password, progressBar)
            }
        }

        tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    // Hapus parameter SharedPreferences dari sini karena sudah ada sessionManager sebagai member
    private fun performLogin(email: String, password: String, progressBar: ProgressBar) {
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
                            val name = jsonResponse.optString("name", "User")
                            val nim = jsonResponse.optString("nim", "000000000")
                            val token = jsonResponse.optString("token", "") // Pastikan respons API mengembalikan token

                            // PENTING: Gunakan sessionManager untuk menyimpan data login
                            // Ini akan mengatur IS_LOGIN = true
                            sessionManager.saveLoginSession(token, email, name, nim)

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