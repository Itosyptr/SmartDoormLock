package telkom.ta.smartdoor

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import de.hdodenhof.circleimageview.CircleImageView
import okhttp3.*
import org.json.JSONObject
import telkom.ta.smartdoor.admin.AdminLoginActivity // --- PERUBAHAN: Import AdminLoginActivity
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
    private lateinit var btnAdmin: Button

    private lateinit var sessionManager: SessionManager
    private lateinit var sharedPreferences: SharedPreferences
    private val client = OkHttpClient()

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sessionManager = SessionManager(this)
        sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)

        initializeViews()
        checkLoginStatus()

        setupButtonListeners()
    }

    private fun initializeViews() {
        imgProfile = findViewById(R.id.imgProfile)
        imgBackground = findViewById(R.id.imgBackground)
        tvName = findViewById(R.id.tvName)
        tvNIM = findViewById(R.id.tvNIM)
        tvWelcome = findViewById(R.id.tvWelcome)
        btnVerify = findViewById(R.id.btnVerify)
        btnLogout = findViewById(R.id.btnLogout)
        // Pastikan ID 'btn_admin_panel' atau 'btn_admin_login' sesuai dengan yang Anda tambahkan di XML
        btnAdmin = findViewById(R.id.btn_admin_login) // Ganti jika ID Anda berbeda
    }

    private fun checkLoginStatus() {
        if (!sessionManager.isLoggedIn()) {
            redirectToLogin()
            return
        }
    }

    override fun onResume() {
        super.onResume()
        if (sessionManager.isLoggedIn()) {
            fetchUserProfile()
        }
    }

    private fun setupButtonListeners() {
        btnVerify.setOnClickListener {
            startActivity(Intent(this, VoiceVerificationActivity::class.java))
        }

        imgProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        btnLogout.setOnClickListener {
            showLogoutConfirmationDialog()
        }

        // --- KODE DIPERBARUI: Listener untuk Tombol Admin ---
        btnAdmin.setOnClickListener {
            // Langsung pindah ke halaman login admin
            startActivity(Intent(this, AdminLoginActivity::class.java))
        }
    }

    private fun fetchUserProfile() {
        val userData = sessionManager.getUserData()
        val username = userData["username"] ?: run {
            Log.e("MainActivity", "Username not found in session")
            displayLocalProfile()
            return
        }

        val request = Request.Builder()
            .url("https://backendapi-roan.vercel.app/auth/profile/$username")
            .get()
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Log.e("ProfileAPI", "Failed to fetch profile: ${e.message}")
                    displayLocalProfile()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    try {
                        if (!response.isSuccessful) {
                            handleProfileError(response)
                            return@runOnUiThread
                        }
                        val responseBody = response.body?.string()
                        Log.d("ProfileAPI", "Response: $responseBody")
                        val jsonResponse = JSONObject(responseBody ?: "{}")
                        val profileObject = jsonResponse.getJSONObject("profile")
                        val name = profileObject.optString("username", "User")
                        val nim = profileObject.getString("nim")
                        displayProfile(name, nim)
                        sessionManager.saveProfileData(
                            name = name,
                            bio = "",
                            nim = nim,
                            imageUri = null
                        )
                        Log.d("ProfileData", "Successfully loaded profile: $name, NIM: $nim")
                    } catch (e: Exception) {
                        Log.e("ProfileAPI", "Error parsing profile: ${e.message}")
                        displayLocalProfile()
                    }
                }
            }
        })
    }

    private fun handleProfileError(response: Response) {
        val errorCode = response.code
        Log.e("ProfileAPI", "Error code: $errorCode")
        when (errorCode) {
            401 -> {
                Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_SHORT).show()
                redirectToLogin()
            }
            404 -> {
                Toast.makeText(this, "Profile not found", Toast.LENGTH_SHORT).show()
                displayLocalProfile()
            }
            else -> {
                Toast.makeText(this, "Failed to load profile (Error $errorCode)", Toast.LENGTH_SHORT).show()
                displayLocalProfile()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun displayProfile(name: String, nim: String) {
        runOnUiThread {
            tvName.text = name
            tvNIM.text = "NIM: $nim"
            tvWelcome.text = "Selamat datang, $name!"
            loadProfileImage()
            Toast.makeText(this, "Profile loaded successfully", Toast.LENGTH_SHORT).show()
        }
    }

    private fun displayLocalProfile() {
        val profileData = sessionManager.getProfileData()
        val userData = sessionManager.getUserData()
        val name = when {
            !profileData["name"].isNullOrEmpty() -> profileData["name"]!!
            !userData["username"].isNullOrEmpty() -> userData["username"]!!
            else -> "User"
        }
        val nim = when {
            !profileData["nim"].isNullOrEmpty() -> profileData["nim"]!!
            !userData["nim"].isNullOrEmpty() -> userData["nim"]!!
            else -> "N/A"
        }
        displayProfile(name, nim)
        Toast.makeText(this, "Using local profile data", Toast.LENGTH_SHORT).show()
    }

    private fun loadProfileImage() {
        val profileData = sessionManager.getProfileData()
        val imageUriString = profileData["imageUri"]
        if (!imageUriString.isNullOrEmpty()) {
            try {
                val imageUri = imageUriString.toUri()
                contentResolver.openInputStream(imageUri)?.use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    imgProfile.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                Log.e("ProfileImage", "Error loading profile image", e)
                imgProfile.setImageResource(R.drawable.ic_profile)
            }
        } else {
            imgProfile.setImageResource(R.drawable.ic_profile)
        }
    }

    private fun showLogoutConfirmationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_logout_confirmation, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())
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
        redirectToLogin()
        Toast.makeText(this, "Anda telah logout", Toast.LENGTH_SHORT).show()
    }

    private fun redirectToLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }
}
