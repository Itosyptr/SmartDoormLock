package telkom.ta.smartdoor.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import de.hdodenhof.circleimageview.CircleImageView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import telkom.ta.smartdoor.MainActivity
import telkom.ta.smartdoor.R
import telkom.ta.smartdoor.session.SessionManager
import java.io.IOException

class ProfileActivity : AppCompatActivity() {

    // --- Komponen UI ---
    private lateinit var backButton: ImageView
    private lateinit var saveButton: ImageView
    private lateinit var profileImage: CircleImageView
    private lateinit var uploadImage: ImageView
    private lateinit var userName: EditText
    private lateinit var userBio: EditText
    private lateinit var userNim: EditText
    private lateinit var progressBar: ProgressBar // ProgressBar untuk feedback

    // --- Properti Lain ---
    private lateinit var sessionManager: SessionManager
    private var currentImageUri: Uri? = null
    private val client = OkHttpClient()
    private lateinit var currentUsername: String

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaTypeOrNull()
    }

    // --- Launcher modern untuk memilih gambar ---
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                // Menyimpan URI gambar yang dipilih
                currentImageUri = uri
                // Tampilkan gambar yang baru dipilih menggunakan Glide (lebih efisien)
                Glide.with(this).load(uri).into(profileImage)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        sessionManager = SessionManager(this)
        currentUsername = sessionManager.getUserData()["username"] ?: ""

        initializeViews()
        setupButtonListeners()
        loadProfileData()
    }

    private fun initializeViews() {
        backButton = findViewById(R.id.back_button)
        saveButton = findViewById(R.id.save_button)
        profileImage = findViewById(R.id.profile_image)
        uploadImage = findViewById(R.id.upload_image)
        userName = findViewById(R.id.user_name)
        userBio = findViewById(R.id.user_bio)
        userNim = findViewById(R.id.user_nim)
        progressBar = findViewById(R.id.profile_progress_bar) // Inisialisasi ProgressBar
    }

    private fun setupButtonListeners() {
        backButton.setOnClickListener { finish() } // Lebih baik gunakan finish() untuk kembali
        saveButton.setOnClickListener { saveProfileData() }
        uploadImage.setOnClickListener { openGallery() }
        profileImage.setOnClickListener { openGallery() }
    }

    private fun openGallery() {
        // Menggunakan launcher modern untuk membuka galeri
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
        galleryLauncher.launch(intent)
    }

    private fun loadProfileData() {
        // Tampilkan data lokal dulu agar UI tidak kosong
        val profileData = sessionManager.getProfileData()
        userName.setText(profileData[SessionManager.PROFILE_NAME])
        userBio.setText(profileData[SessionManager.PROFILE_BIO])
        userNim.setText(profileData[SessionManager.PROFILE_NIM])
        profileData[SessionManager.PROFILE_IMAGE_URI]?.let { uriString ->
            // Gunakan Glide untuk memuat gambar dari URL (lebih baik dari BitmapFactory)
            Glide.with(this).load(uriString).placeholder(R.drawable.ic_profile).into(profileImage)
        }

        // Ambil data terbaru dari server
        fetchProfileFromApi()
    }

    private fun fetchProfileFromApi() {
        if (currentUsername.isEmpty()) return
        showLoading(true) // Tampilkan loading

        val request = Request.Builder()
            .url("https://backendapi-roan.vercel.app/auth/profile/$currentUsername")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    showLoading(false)
                    Toast.makeText(this@ProfileActivity, "Gagal memuat profil dari server", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    showLoading(false)
                    if (!response.isSuccessful) {
                        Toast.makeText(this@ProfileActivity, "Gagal memuat profil: ${response.message}", Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }
                    try {
                        val responseBody = response.body?.string()
                        val jsonResponse = JSONObject(responseBody ?: "{}")
                        val profile = jsonResponse.getJSONObject("profile")

                        val newUsername = profile.getString("username")
                        val newNim = profile.getString("nim")
                        // Catatan: API Anda sepertinya tidak mengembalikan 'bio'. Jika ada, tambahkan di sini.
                        val avatarUrl = profile.optString("avatarUrl", null)

                        // Update UI
                        userName.setText(newUsername)
                        userNim.setText(newNim)
                        Glide.with(this@ProfileActivity).load(avatarUrl).placeholder(R.drawable.ic_profile).into(profileImage)

                        // Simpan data terbaru ke session
                        sessionManager.saveProfileData(name = newUsername, bio = userBio.text.toString(), nim = newNim, imageUri = avatarUrl)
                    } catch (e: Exception) {
                        Toast.makeText(this@ProfileActivity, "Error parsing profile data", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun saveProfileData() {
        // Validasi input
        val newUsername = userName.text.toString().trim()
        val bio = userBio.text.toString().trim()
        val nim = userNim.text.toString().trim()

        if (newUsername.isEmpty() || nim.isEmpty()) {
            Toast.makeText(this, "Username dan NIM tidak boleh kosong", Toast.LENGTH_SHORT).show()
            return
        }
        if (!nim.matches(Regex("\\d+"))) {
            userNim.error = "NIM harus berupa angka"
            return
        }

        showLoading(true) // Mulai loading

        // Cek apakah pengguna memilih gambar baru
        if (currentImageUri != null) {
            uploadImageAndProfileData(newUsername, nim, bio)
        } else {
            updateProfileDataOnly(newUsername, nim, bio)
        }
    }

    private fun uploadImageAndProfileData(newUsername: String, nim: String, bio: String) {
        val imageBytes = contentResolver.openInputStream(currentImageUri!!)?.readBytes()
        if (imageBytes == null) {
            showLoading(false)
            Toast.makeText(this, "Gagal membaca file gambar", Toast.LENGTH_SHORT).show()
            return
        }

        // Dapatkan tipe file (MIME type) secara dinamis
        val mimeType = contentResolver.getType(currentImageUri!!) ?: "image/*"
        val fileName = getFileName(currentImageUri!!)

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("username", newUsername)
            .addFormDataPart("nim", nim)
            .addFormDataPart("bio", bio) // Pastikan backend Anda bisa menerima 'bio'
            .addFormDataPart("avatar", fileName, imageBytes.toRequestBody(mimeType.toMediaTypeOrNull()))
            .build()

        val request = Request.Builder()
            .url("https://backendapi-roan.vercel.app/auth/profile/$currentUsername")
            .put(requestBody)
            .build()

        client.newCall(request).enqueue(createProfileUpdateCallback(newUsername, nim, bio))
    }

    private fun updateProfileDataOnly(newUsername: String, nim: String, bio: String) {
        val json = JSONObject().apply {
            put("username", newUsername)
            put("nim", nim)
            put("bio", bio) // Pastikan backend Anda bisa menerima 'bio'
        }
        val requestBody = json.toString().toRequestBody(JSON)
        val request = Request.Builder()
            .url("https://backendapi-roan.vercel.app/auth/profile/$currentUsername")
            .put(requestBody)
            .build()

        client.newCall(request).enqueue(createProfileUpdateCallback(newUsername, nim, bio))
    }

    private fun createProfileUpdateCallback(newUsername: String, nim: String, bio: String): Callback {
        return object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    showLoading(false)
                    Toast.makeText(this@ProfileActivity, "Update gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    showLoading(false)
                    if (!response.isSuccessful) {
                        Toast.makeText(this@ProfileActivity, "Update gagal: ${response.code} ${response.message}", Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }
                    try {
                        val responseBody = response.body?.string()
                        val jsonResponse = JSONObject(responseBody ?: "{}")

                        if (jsonResponse.optString("message") == "Profile berhasil diperbarui") {
                            Toast.makeText(this@ProfileActivity, "Profil berhasil diperbarui", Toast.LENGTH_SHORT).show()

                            // Update data di session manager dengan data terbaru dari server
                            val profile = jsonResponse.getJSONObject("profile")
                            val avatarUrl = profile.optString("avatarUrl", currentImageUri?.toString())
                            sessionManager.saveProfileData(newUsername, bio, nim, avatarUrl)

                            // Jika username berubah, update juga sesi login utama
                            if (newUsername != currentUsername) {
                                val userData = sessionManager.getUserData()
                                sessionManager.saveLoginSession(
                                    userId = userData["userId"] ?: "",
                                    profileId = userData["profileId"] ?: "",
                                    username = newUsername,
                                    email = userData["email"] ?: ""
                                )
                            }

                            // Kembali ke MainActivity dengan data yang sudah diperbarui
                            startActivity(Intent(this@ProfileActivity, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            })
                            finish()
                        } else {
                            Toast.makeText(this@ProfileActivity, jsonResponse.optString("error", "Terjadi kesalahan"), Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@ProfileActivity, "Error parsing response", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (columnIndex != -1) {
                        result = cursor.getString(columnIndex)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result!!.lastIndexOf('/')
            if (cut != -1) {
                result = result.substring(cut + 1)
            }
        }
        return result ?: "unknown_file"
    }

    private fun showLoading(isLoading: Boolean) {
        // Mengatur visibilitas ProgressBar dan mengaktifkan/menonaktifkan tombol simpan
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        saveButton.isEnabled = !isLoading
    }

    // Hapus @Deprecated dan fungsi onActivityResult karena sudah tidak digunakan
}