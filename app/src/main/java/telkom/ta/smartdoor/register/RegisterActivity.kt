package telkom.ta.smartdoor.register

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import telkom.ta.smartdoor.R
import telkom.ta.smartdoor.login.LoginActivity
import java.io.IOException

class RegisterActivity : AppCompatActivity() {
    private val client = OkHttpClient()

    // Deklarasikan edtNim di sini agar bisa diakses di seluruh kelas jika diperlukan nanti
    private lateinit var edtNim: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val btnBackToLogin: ImageButton = findViewById(R.id.btn_back_to_login)
        val edtName: TextInputEditText = findViewById(R.id.edt_name)
        edtNim = findViewById(R.id.edt_nim) // Inisialisasi edtNim
        val edtEmail: TextInputEditText = findViewById(R.id.edt_Email)
        val edtPassword: TextInputEditText = findViewById(R.id.edt_Password)
        val btnRegister: Button = findViewById(R.id.btn_register)
        val progressBar: ProgressBar = findViewById(R.id.progress_bar)

        btnBackToLogin.setOnClickListener {
            finish()
        }

        btnRegister.setOnClickListener {
            val name = edtName.text.toString().trim()
            val nim = edtNim.text.toString().trim() // Ambil nilai NIM
            val email = edtEmail.text.toString().trim()
            val password = edtPassword.text.toString().trim()

            // Validasi Nama
            if (name.isEmpty()) {
                edtName.error = "Nama tidak boleh kosong"
                edtName.requestFocus()
                return@setOnClickListener
            }

            // Validasi NIM
            if (nim.isEmpty()) {
                edtNim.error = "NIM tidak boleh kosong"
                edtNim.requestFocus()
                return@setOnClickListener
            }
            // Contoh validasi tambahan untuk NIM (misalnya harus angka dan panjang tertentu)
            if (!nim.all { it.isDigit() }) {
                edtNim.error = "NIM harus berupa angka"
                edtNim.requestFocus()
                return@setOnClickListener
            }
            // if (nim.length != 10) { // Ganti 10 dengan panjang NIM yang sesuai
            //     edtNim.error = "Panjang NIM tidak sesuai"
            //     edtNim.requestFocus()
            //     return@setOnClickListener
            // }


            // Validasi Email
            if (email.isEmpty()) {
                edtEmail.error = "Email tidak boleh kosong"
                edtEmail.requestFocus()
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                edtEmail.error = "Format email tidak valid"
                edtEmail.requestFocus()
                return@setOnClickListener
            }

            // Validasi Password
            if (password.isEmpty()) {
                edtPassword.error = "Password tidak boleh kosong"
                edtPassword.requestFocus()
                return@setOnClickListener
            }
            if (password.length < 6) {
                edtPassword.error = "Password minimal 6 karakter"
                edtPassword.requestFocus()
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            // Sertakan nim dalam pemanggilan registerUser
            registerUser(name, nim, email, password, progressBar)
        }
    }

    // Update fungsi registerUser untuk menerima parameter nim
    private fun registerUser(username: String, nim: String, email: String, password: String, progressBar: ProgressBar) {
        val json = JSONObject().apply {
            put("username", username) // Anda menggunakan "username" untuk field nama
            put("nim", nim)           // Tambahkan NIM ke JSON payload
            put("email", email)
            put("password", password)
        }

        val mediaType = "application/json; charset=utf-f".toMediaType() // charset=utf-8 seringkali penting
        val requestBody = json.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url("https://backendapi-roan.vercel.app/auth/register") // Pastikan URL ini benar
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        this@RegisterActivity,
                        "Gagal terhubung ke server: ${e.message}",
                        Toast.LENGTH_LONG // Ubah ke LENGTH_LONG agar pesan lebih terlihat
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBodyString = response.body?.string() ?: ""

                runOnUiThread {
                    progressBar.visibility = View.GONE

                    if (response.isSuccessful) { // Lebih baik menggunakan response.isSuccessful
                        Toast.makeText(
                            this@RegisterActivity,
                            "Registrasi berhasil! Silakan login.",
                            Toast.LENGTH_SHORT
                        ).show()

                        val intent = Intent(this@RegisterActivity, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                        finish()
                    } else {
                        // Coba parsing response error jika ada
                        var specificErrorMessage: String? = null
                        try {
                            val errorJson = JSONObject(responseBodyString)
                            if (errorJson.has("message")) {
                                specificErrorMessage = errorJson.getString("message")
                            } else if (errorJson.has("error")) {
                                specificErrorMessage = errorJson.getString("error")
                            }
                        } catch (e: Exception) {
                            // Gagal parse JSON, gunakan pesan default
                        }

                        val errorMessage = when (response.code) {
                            400 -> specificErrorMessage ?: "Data tidak valid. Periksa kembali input Anda."
                            409 -> specificErrorMessage ?: "Email, username, atau NIM sudah terdaftar." // Tambahkan NIM di sini jika relevan
                            else -> specificErrorMessage ?: "Registrasi gagal (Kode: ${response.code}). Coba lagi nanti."
                        }

                        Toast.makeText(
                            this@RegisterActivity,
                            errorMessage,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        })
    }
}