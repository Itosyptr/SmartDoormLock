package telkom.ta.smartdoor.admin

import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import telkom.ta.smartdoor.R

class AdminLoginActivity : AppCompatActivity() {

    // Deklarasi komponen UI
    private lateinit var usernameEditText: TextInputEditText
    private lateinit var passwordEditText: TextInputEditText
    private lateinit var loginButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var btnBack: ImageButton // [BARU] Deklarasi tombol kembali

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_login)

        // Inisialisasi komponen UI dari layout
        initializeViews()

        // Menetapkan semua listener untuk komponen UI
        setupListeners()
    }

    /**
     * Menginisialisasi semua view dari file layout menggunakan findViewById.
     */
    private fun initializeViews() {
        // Pastikan ID ini sesuai dengan yang ada di activity_admin_login.xml
        usernameEditText = findViewById(R.id.admin_username)
        passwordEditText = findViewById(R.id.admin_password)
        loginButton = findViewById(R.id.btn_admin_login)
        progressBar = findViewById(R.id.login_progress)
        btnBack = findViewById(R.id.btnBack) // [BARU] Inisialisasi tombol kembali
    }

    /**
     * Mengatur semua listener untuk view yang interaktif.
     */
    private fun setupListeners() {
        loginButton.setOnClickListener {
            handleLogin()
        }

        // [BARU] Menetapkan listener untuk tombol kembali
        btnBack.setOnClickListener {
            // Menjalankan aksi kembali standar
            onBackPressedDispatcher.onBackPressed()
        }
    }

    /**
     * Menangani logika saat tombol login ditekan.
     */
    private fun handleLogin() {
        // Mengambil input dari EditText
        val enteredUsername = usernameEditText.text.toString().trim()
        val enteredPassword = passwordEditText.text.toString().trim()

        // Validasi input agar tidak kosong
        if (enteredUsername.isEmpty()) {
            usernameEditText.error = "Username tidak boleh kosong"
            usernameEditText.requestFocus()
            return
        }
        if (enteredPassword.isEmpty()) {
            passwordEditText.error = "Password tidak boleh kosong"
            passwordEditText.requestFocus()
            return
        }

        // Tampilkan loading
        showLoading(true)

        // Langsung bandingkan input dengan kredensial yang sudah ditentukan (hardcoded).
        if (enteredUsername == "admin1" && enteredPassword == "admin1") {
            // Jika login berhasil, hentikan loading setelah jeda singkat
            android.os.Handler(Looper.getMainLooper()).postDelayed({
                showLoading(false)
                Toast.makeText(this, "Login Berhasil!", Toast.LENGTH_SHORT).show()

                // Pindah ke halaman log aktivitas admin
                val intent = Intent(this, AdminLogActivity::class.java)
                startActivity(intent)

                // Tutup activity ini agar tidak bisa kembali dengan tombol back
                finish()
            }, 500) // Jeda 0.5 detik
        } else {
            // Jika login gagal
            showLoading(false)
            Toast.makeText(this, "Username atau Password salah.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Mengatur visibilitas ProgressBar dan menonaktifkan tombol saat loading.
     * @param isLoading True jika ingin menampilkan loading, false jika sebaliknya.
     */
    private fun showLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        loginButton.isEnabled = !isLoading
        // [BARU] Nonaktifkan juga tombol kembali saat loading untuk mencegah aksi ganda
        btnBack.isEnabled = !isLoading
    }
}
