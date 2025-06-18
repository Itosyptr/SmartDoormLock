package telkom.ta.smartdoor.ui

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import de.hdodenhof.circleimageview.CircleImageView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import telkom.ta.smartdoor.R
import telkom.ta.smartdoor.MainActivity
import telkom.ta.smartdoor.session.SessionManager
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

class ProfileActivity : AppCompatActivity() {

    private lateinit var backButton: ImageView
    private lateinit var saveButton: ImageView
    private lateinit var profileImage: CircleImageView
    private lateinit var uploadImage: ImageView
    private lateinit var userName: EditText
    private lateinit var userBio: EditText
    private lateinit var userNim: EditText

    private lateinit var sessionManager: SessionManager
    private var currentImageUri: Uri? = null
    private val client = OkHttpClient()
    private lateinit var currentUsername: String

    companion object {
        private const val PICK_IMAGE_REQUEST = 1
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        sessionManager = SessionManager(this)
        currentUsername = sessionManager.getUserData()["username"] ?: ""

        initializeViews()
        loadProfileData()

        setupButtonListeners()
    }

    private fun initializeViews() {
        backButton = findViewById(R.id.back_button)
        saveButton = findViewById(R.id.save_button)
        profileImage = findViewById(R.id.profile_image)
        uploadImage = findViewById(R.id.upload_image)
        userName = findViewById(R.id.user_name)
        userBio = findViewById(R.id.user_bio)
        userNim = findViewById(R.id.user_nim)
    }

    private fun setupButtonListeners() {
        backButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        saveButton.setOnClickListener {
            saveProfileData()
        }

        uploadImage.setOnClickListener {
            openGallery()
        }
        profileImage.setOnClickListener {
            openGallery()
        }
    }

    private fun loadProfileData() {
        fetchProfileFromApi()

        // Load from local cache as fallback
        val profileData = sessionManager.getProfileData()
        userName.setText(profileData[SessionManager.PROFILE_NAME])
        userBio.setText(profileData[SessionManager.PROFILE_BIO])
        userNim.setText(profileData[SessionManager.PROFILE_NIM])

        profileData[SessionManager.PROFILE_IMAGE_URI]?.let { uriString ->
            loadProfileImage(Uri.parse(uriString))
        }
    }

    private fun fetchProfileFromApi() {
        if (currentUsername.isEmpty()) return

        val request = Request.Builder()
            .url("https://backendapi-roan.vercel.app/auth/profile/$currentUsername")
            .get()
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(
                        this@ProfileActivity,
                        "Gagal memuat profil dari server",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    try {
                        if (!response.isSuccessful) {
                            Toast.makeText(
                                this@ProfileActivity,
                                "Gagal memuat profil: ${response.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@runOnUiThread
                        }

                        val responseBody = response.body?.string()
                        val jsonResponse = JSONObject(responseBody ?: "{}")
                        val profile = jsonResponse.getJSONObject("profile")

                        userName.setText(profile.getString("username"))
                        userNim.setText(profile.getString("nim"))

                        sessionManager.saveProfileData(
                            name = profile.getString("username"),
                            bio = userBio.text.toString(),
                            nim = profile.getString("nim"),
                            imageUri = profile.optString("avatarUrl", null)
                        )

                    } catch (e: Exception) {
                        Toast.makeText(
                            this@ProfileActivity,
                            "Error parsing profile data",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }

    private fun saveProfileData() {
        val newUsername = userName.text.toString().trim()
        val bio = userBio.text.toString().trim()
        val nim = userNim.text.toString().trim()

        if (newUsername.isEmpty()) {
            userName.error = "Username tidak boleh kosong"
            return
        }

        if (nim.isEmpty()) {
            userNim.error = "NIM tidak boleh kosong"
            return
        }

        if (!nim.matches(Regex("\\d+"))) {
            userNim.error = "NIM harus berupa angka"
            return
        }

        if (currentImageUri != null) {
            uploadImageWithProfileData(newUsername, nim, bio)
        } else {
            updateProfileData(newUsername, nim, bio)
        }
    }

    private fun uploadImageWithProfileData(newUsername: String, nim: String, bio: String) {
        try {
            val inputStream = contentResolver.openInputStream(currentImageUri!!)
            val imageBytes = inputStream?.readBytes()
            inputStream?.close()

            if (imageBytes == null) {
                Toast.makeText(this, "Gagal membaca gambar", Toast.LENGTH_SHORT).show()
                return
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("username", newUsername)
                .addFormDataPart("nim", nim)
                .addFormDataPart(
                    "avatar",
                    "profile.jpg",
                    RequestBody.create("image/jpeg".toMediaType(), imageBytes)
                )
                .build()

            val request = Request.Builder()
                .url("https://backendapi-roan.vercel.app/auth/profile/$currentUsername")
                .put(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        Toast.makeText(
                            this@ProfileActivity,
                            "Gagal mengupload profil: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    runOnUiThread {
                        handleProfileUpdateResponse(response, newUsername, nim, bio)
                    }
                }
            })

        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateProfileData(newUsername: String, nim: String, bio: String) {
        val json = JSONObject().apply {
            put("username", newUsername)
            put("nim", nim)
        }

        val requestBody = json.toString().toRequestBody(JSON)

        val request = Request.Builder()
            .url("https://backendapi-roan.vercel.app/auth/profile/$currentUsername")
            .put(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(
                        this@ProfileActivity,
                        "Gagal mengupdate profil: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    handleProfileUpdateResponse(response, newUsername, nim, bio)
                }
            }
        })
    }

    private fun handleProfileUpdateResponse(
        response: Response,
        newUsername: String,
        nim: String,
        bio: String
    ) {
        try {
            if (!response.isSuccessful) {
                Toast.makeText(
                    this@ProfileActivity,
                    "Gagal mengupdate profil: ${response.message}",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            val responseBody = response.body?.string()
            val jsonResponse = JSONObject(responseBody ?: "{}")

            if (jsonResponse.getString("message") == "Profile berhasil diperbarui") {
                val profile = jsonResponse.getJSONObject("profile")
                val avatarUrl = profile.optString("avatarUrl", currentImageUri?.toString())

                sessionManager.saveProfileData(
                    name = newUsername,
                    bio = bio,
                    nim = nim,
                    imageUri = avatarUrl
                )

                if (newUsername != currentUsername) {
                    val userData = sessionManager.getUserData()
                    sessionManager.saveLoginSession(
                        userId = userData["userId"] ?: "",
                        profileId = userData["profileId"] ?: "",
                        username = newUsername,
                        email = userData["email"] ?: ""
                    )
                }

                Toast.makeText(
                    this@ProfileActivity,
                    "Profil berhasil diperbarui",
                    Toast.LENGTH_SHORT
                ).show()

                startActivity(Intent(this@ProfileActivity, MainActivity::class.java))
                finish()
            }
        } catch (e: Exception) {
            Toast.makeText(
                this@ProfileActivity,
                "Error processing response",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun loadProfileImage(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                profileImage.setImageBitmap(bitmap)
                currentImageUri = uri
            }
        } catch (e: Exception) {
            profileImage.setImageResource(R.drawable.ic_profile)
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    loadProfileImage(uri)
                } catch (e: Exception) {
                    Toast.makeText(this, "Gagal memuat gambar", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}