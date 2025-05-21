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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val btnBackToLogin: ImageButton = findViewById(R.id.btn_back_to_login)
        val edtName: TextInputEditText = findViewById(R.id.edt_name)
        val edtEmail: TextInputEditText = findViewById(R.id.edt_Email)
        val edtPassword: TextInputEditText = findViewById(R.id.edt_Password)
        val btnRegister: Button = findViewById(R.id.btn_register)
        val progressBar: ProgressBar = findViewById(R.id.progress_bar)

        btnBackToLogin.setOnClickListener {
            finish()
        }

        btnRegister.setOnClickListener {
            val name = edtName.text.toString().trim()
            val email = edtEmail.text.toString().trim()
            val password = edtPassword.text.toString().trim()

            if (name.isEmpty()) {
                edtName.error = "Nama tidak boleh kosong"
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                edtEmail.error = "Format email tidak valid"
                return@setOnClickListener
            }
            if (password.length < 6) {
                edtPassword.error = "Password minimal 6 karakter"
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            registerUser(name, email, password, progressBar)
        }
    }

    private fun registerUser(username: String, email: String, password: String, progressBar: ProgressBar) {
        val json = JSONObject().apply {
            put("username", username)
            put("email", email)
            put("password", password)
        }

        val mediaType = "application/json".toMediaType()
        val requestBody = json.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url("https://backendapi-roan.vercel.app/auth/register")
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
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBodyString = response.body?.string() ?: ""

                runOnUiThread {
                    progressBar.visibility = View.GONE

                    if (response.code == 200 || response.code == 201) {
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
                        val errorMessage = when (response.code) {
                            400 -> "Data tidak valid. Periksa kembali input Anda."
                            409 -> "Email atau username sudah terdaftar."
                            else -> "Registrasi gagal (${response.code}): $responseBodyString"
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
