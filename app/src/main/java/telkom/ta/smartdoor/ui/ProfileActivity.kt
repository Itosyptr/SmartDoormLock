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
import telkom.ta.smartdoor.R
import telkom.ta.smartdoor.session.SessionManager // Pastikan import ini benar
import java.io.IOException
import telkom.ta.smartdoor.MainActivity // Pastikan import ini benar

class ProfileActivity : AppCompatActivity() {

    private lateinit var backButton: ImageView
    private lateinit var saveButton: ImageView
    private lateinit var profileImage: CircleImageView
    private lateinit var uploadImage: ImageView
    private lateinit var userName: EditText
    private lateinit var userBio: EditText
    private lateinit var userNim: EditText // Mengganti userPhone menjadi userNim

    private lateinit var sessionManager: SessionManager
    private var currentImageUri: String? = null

    companion object {
        private const val PICK_IMAGE_REQUEST = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        sessionManager = SessionManager(this)

        // Inisialisasi View
        backButton = findViewById(R.id.back_button)
        saveButton = findViewById(R.id.save_button)
        profileImage = findViewById(R.id.profile_image)
        uploadImage = findViewById(R.id.upload_image)
        userName = findViewById(R.id.user_name)
        userBio = findViewById(R.id.user_bio)
        userNim = findViewById(R.id.user_nim) // ID ini harus ada di activity_profile.xml untuk NIM

        // Memuat data profil saat Activity dibuat
        loadProfileData()

        // Tombol Kembali
        backButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Tombol Simpan
        saveButton.setOnClickListener {
            saveProfileData()
        }

        // Ganti Foto Profil
        uploadImage.setOnClickListener {
            openGallery()
        }
        profileImage.setOnClickListener {
            openGallery()
        }
    }

    // Fungsi untuk memuat data profil dari SharedPreferences
    private fun loadProfileData() {
        val profileData = sessionManager.getProfileData()
        // Menggunakan konstanta dari SessionManager
        userName.setText(profileData[SessionManager.PROFILE_NAME])
        userBio.setText(profileData[SessionManager.PROFILE_BIO])
        userNim.setText(profileData[SessionManager.PROFILE_NIM]) // Menggunakan KEY_NIM

        val imageUriString = profileData[SessionManager.PROFILE_IMAGE_URI]
        if (!imageUriString.isNullOrEmpty()) {
            currentImageUri = imageUriString
            try {
                val imageUri = Uri.parse(imageUriString)
                val inputStream = contentResolver.openInputStream(imageUri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                profileImage.setImageBitmap(bitmap)
                inputStream?.close()
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(this, "Gagal memuat gambar profil.", Toast.LENGTH_SHORT).show()
                profileImage.setImageResource(R.drawable.ic_profile)
            } catch (e: SecurityException) {
                e.printStackTrace()
                Toast.makeText(this, "Izin akses gambar tidak diberikan.", Toast.LENGTH_SHORT).show()
                profileImage.setImageResource(R.drawable.ic_profile)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Kesalahan memuat gambar.", Toast.LENGTH_SHORT).show()
                profileImage.setImageResource(R.drawable.ic_profile)
            }
        } else {
            profileImage.setImageResource(R.drawable.ic_profile) // Pastikan ic_profile ada di drawable
        }
    }

    // Fungsi untuk menyimpan data profil ke SharedPreferences
    private fun saveProfileData() {
        val name = userName.text.toString().trim()
        val bio = userBio.text.toString().trim()
        val nim = userNim.text.toString().trim() // Mengambil nilai dari userNim

        if (name.isEmpty()) {
            userName.error = "Nama tidak boleh kosong"
            userName.requestFocus()
            return
        }
        if (bio.isEmpty()) {
            userBio.error = "Bio tidak boleh kosong"
            userBio.requestFocus()
            return
        }
        if (nim.isEmpty()) {
            userNim.error = "NIM tidak boleh kosong" // Pesan error untuk NIM
            userNim.requestFocus()
            return
        }
        // Anda bisa menambahkan validasi spesifik untuk NIM di sini jika perlu
        // if (nim.length != 10 || !nim.all { it.isDigit() }) { // Contoh validasi panjang dan numerik
        //     userNim.error = "Format NIM tidak valid"
        //     userNim.requestFocus()
        //     return
        // }

        // Menggunakan konstanta dari SessionManager saat menyimpan
        sessionManager.saveProfileData(name, bio, nim, currentImageUri) // Menyimpan nim
        Toast.makeText(this, "Profil berhasil disimpan!", Toast.LENGTH_SHORT).show()

        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        try {
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
        } catch (e: Exception) {
            Toast.makeText(this, "Tidak dapat membuka galeri.", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, takeFlags)
                    currentImageUri = uri.toString()

                    val inputStream = contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    profileImage.setImageBitmap(bitmap)
                    inputStream?.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                    Toast.makeText(this, "Gagal memuat gambar dari galeri.", Toast.LENGTH_SHORT).show()
                } catch (e: SecurityException) {
                    e.printStackTrace()
                    Toast.makeText(this, "Izin akses gambar tidak diberikan.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Kesalahan saat memproses gambar.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
