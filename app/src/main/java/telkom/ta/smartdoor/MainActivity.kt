package telkom.ta.smartdoor

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import de.hdodenhof.circleimageview.CircleImageView
import telkom.ta.smartdoor.login.LoginActivity
import telkom.ta.smartdoor.session.SessionManager
import telkom.ta.smartdoor.ui.ProfileActivity
import telkom.ta.smartdoor.verifikasi.VoiceVerificationActivity
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var imgProfile: CircleImageView
    private lateinit var imgBackground: ImageView
    private lateinit var tvName: TextView
    private lateinit var tvNIM: TextView
    private lateinit var tvWelcome: TextView
    private lateinit var btnVerify: Button
    private lateinit var btnLogout: ImageButton

    private lateinit var sessionManager: SessionManager

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sessionManager = SessionManager(this)

        // Inisialisasi View
        imgProfile = findViewById(R.id.imgProfile)
        imgBackground = findViewById(R.id.imgBackground)
        tvName = findViewById(R.id.tvName)
        tvNIM = findViewById(R.id.tvNIM)
        tvWelcome = findViewById(R.id.tvWelcome)
        btnVerify = findViewById(R.id.btnVerify)
        btnLogout = findViewById(R.id.btnLogout)

        // Cek apakah user sudah login
        if (!sessionManager.isLoggedIn()) {
            redirectToLogin()
            return
        }
        // Tidak perlu memanggil displayUserProfile() di sini, cukup di onResume()
        // karena onResume() akan dipanggil setelah onCreate() selesai.

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

    override fun onResume() {
        super.onResume()
        // Ini adalah tempat terbaik untuk memuat dan menampilkan data profil
        // karena akan dipanggil setiap kali Activity aktif atau kembali dari background/Activity lain.
        displayUserProfile()
    }

    private fun redirectToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun displayUserProfile() {
        val profileData = sessionManager.getProfileData() // Data profil (diubah di ProfileActivity)
        val userData = sessionManager.getUserData()     // Data sesi login (NIM awal saat login)

        val nameToDisplay = profileData["name"] ?: userData["name"] ?: "Pengguna"
        tvName.text = nameToDisplay

        // FIX: Prioritaskan NIM dari data profil, jika tidak ada, gunakan NIM dari data login
        val nimToDisplay = profileData["nim"] ?: userData["nim"] ?: "NIM Tidak Tersedia"
        tvNIM.text = "Mahasiswa : $nimToDisplay"

        tvWelcome.text = "Selamat datang, $nameToDisplay!"

        val imageUriString = profileData["imageUri"]
        if (!imageUriString.isNullOrEmpty()) {
            try {
                val imageUri = Uri.parse(imageUriString)
                val inputStream = contentResolver.openInputStream(imageUri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                imgProfile.setImageBitmap(bitmap)
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(this, "Gagal memuat gambar profil", Toast.LENGTH_SHORT).show()
                imgProfile.setImageResource(R.drawable.ic_profile)
            } catch (e: SecurityException) {
                e.printStackTrace()
                Toast.makeText(this, "Izin akses gambar tidak diberikan.", Toast.LENGTH_SHORT).show()
                imgProfile.setImageResource(R.drawable.ic_profile)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Kesalahan tak terduga saat memuat gambar.", Toast.LENGTH_SHORT).show()
                imgProfile.setImageResource(R.drawable.ic_profile)
            }
        } else {
            imgProfile.setImageResource(R.drawable.ic_profile)
        }
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
        sessionManager.logout()

        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()

        Toast.makeText(this, "Anda telah logout", Toast.LENGTH_SHORT).show()
    }
}