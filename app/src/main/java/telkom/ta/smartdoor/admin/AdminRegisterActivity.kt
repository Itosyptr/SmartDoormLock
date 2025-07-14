package telkom.ta.smartdoor.admin

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import telkom.ta.smartdoor.R

class AdminRegisterActivity : AppCompatActivity() {

    // Deklarasi komponen UI
    private lateinit var usernameEditText: TextInputEditText
    private lateinit var passwordEditText: TextInputEditText
    private lateinit var registerButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_register)

        // Inisialisasi komponen UI dari layout
        initializeViews()

        // Menetapkan listener untuk tombol register
        registerButton.setOnClickListener {
            handleRegistration()
        }
    }

    /**
     * Menginisialisasi semua view dari file layout.
     */
    private fun initializeViews() {
        usernameEditText = findViewById(R.id.et_admin_username_register)
        passwordEditText = findViewById(R.id.et_admin_password_register)
        registerButton = findViewById(R.id.btn_admin_register)
    }

    /**
     * Menangani logika saat tombol register ditekan.
     */
    private fun handleRegistration() {
        // Ambil data admin yang mungkin sudah tersimpan
        val prefs = getSharedPreferences("ADMIN_PREFS", MODE_PRIVATE)
        val savedUsername = prefs.getString("ADMIN_USERNAME", null)

        // Cek apakah sudah ada admin yang terdaftar
        if (savedUsername != null) {
            Toast.makeText(this, "Admin sudah terdaftar. Tidak bisa mendaftar lagi.", Toast.LENGTH_LONG).show()
            return
        }

        // Mengambil input dari EditText
        val newUsername = usernameEditText.text.toString().trim()
        val newPassword = passwordEditText.text.toString().trim()

        // Validasi input agar tidak kosong
        if (newUsername.isEmpty()) {
            usernameEditText.error = "Username tidak boleh kosong"
            usernameEditText.requestFocus()
            return
        }
        if (newPassword.isEmpty()) {
            passwordEditText.error = "Password tidak boleh kosong"
            passwordEditText.requestFocus()
            return
        }
        if (newPassword.length < 6) {
            passwordEditText.error = "Password minimal 6 karakter"
            passwordEditText.requestFocus()
            return
        }

        // Simpan data admin baru ke SharedPreferences
        with(prefs.edit()) {
            putString("ADMIN_USERNAME", newUsername)
            // PENTING: Untuk aplikasi nyata, jangan simpan password sebagai plain text.
            // Gunakan library enkripsi seperti Jetpack Security untuk mengamankannya.
            putString("ADMIN_PASSWORD", newPassword)
            apply()
        }

        // Tampilkan pesan sukses dan tutup activity
        Toast.makeText(this, "Admin berhasil didaftarkan!", Toast.LENGTH_SHORT).show()
        finish() // Kembali ke halaman sebelumnya
    }
}
