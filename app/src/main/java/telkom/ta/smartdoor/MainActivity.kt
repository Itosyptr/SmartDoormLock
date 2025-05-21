package telkom.ta.smartdoor

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import telkom.ta.smartdoor.login.LoginActivity
import telkom.ta.smartdoor.ui.ProfileActivity
import telkom.ta.smartdoor.verifikasi.VoiceVerificationActivity

class MainActivity : AppCompatActivity() {

    private lateinit var imgProfile: ImageView
    private lateinit var imgBackground: ImageView
    private lateinit var tvName: TextView
    private lateinit var tvNIM: TextView
    private lateinit var tvWelcome: TextView
    private lateinit var btnVerify: Button
    private lateinit var btnLogout: ImageButton
    private lateinit var sharedPreferences: SharedPreferences

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inisialisasi EncryptedSharedPreferences
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        sharedPreferences = EncryptedSharedPreferences.create(
            this,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        // Inisialisasi View
        imgProfile = findViewById(R.id.imgProfile)
        imgBackground = findViewById(R.id.imgBackground)
        tvName = findViewById(R.id.tvName)
        tvNIM = findViewById(R.id.tvNIM)
        tvWelcome = findViewById(R.id.tvWelcome)
        btnVerify = findViewById(R.id.btnVerify)
        btnLogout = findViewById(R.id.btnLogout)

        // Cek apakah user sudah login
        if (!isUserLoggedIn()) {
            redirectToLogin()
            return
        }

        // Load user data
        loadUserData()

        // Event: Tombol verifikasi suara
        btnVerify.setOnClickListener {
            Toast.makeText(this, "Verifikasi suara dimulai...", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, VoiceVerificationActivity::class.java))
        }

        // Event: Menuju ke halaman profil
        imgProfile.setOnClickListener {
            Toast.makeText(this, "Membuka Profil...", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // Event: Logout
        btnLogout.setOnClickListener {
            showLogoutConfirmationDialog()
        }

    }


    private fun isUserLoggedIn(): Boolean {
        val name = sharedPreferences.getString("name", null)
        val nim = sharedPreferences.getString("nim", null)
        return !name.isNullOrEmpty() && !nim.isNullOrEmpty()
    }

    private fun redirectToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun loadUserData() {
        val name = sharedPreferences.getString("name", "DJAROT") ?: "DJAROT"
        val nim = sharedPreferences.getString("nim", "1191219911") ?: "1191219911"

        tvName.text = name
        tvNIM.text = "Mahasiswa : $nim"
        tvWelcome.text = "Selamat datang, $name!"
    }

    private fun showLogoutConfirmationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_logout_confirmation, null)
        val dialogBuilder = AlertDialog.Builder(this).setView(dialogView)
        val dialog = dialogBuilder.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        dialogView.findViewById<Button>(R.id.btnYa).setOnClickListener {
            logoutUser()
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btnTidak).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun logoutUser() {
        sharedPreferences.edit().clear().apply()

        // Redirect to login activity
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()

        Toast.makeText(this, "Anda telah logout", Toast.LENGTH_SHORT).show()
    }
}
