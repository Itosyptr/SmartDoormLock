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

class ProfileActivity : AppCompatActivity() {

    private lateinit var backButton: ImageView
    private lateinit var saveButton: ImageView
    private lateinit var profileImage: CircleImageView
    private lateinit var uploadImage: ImageView
    private lateinit var userName: EditText
    private lateinit var userBio: EditText
    private lateinit var userPhone: EditText

    companion object {
        private const val PICK_IMAGE_REQUEST = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // Inisialisasi View
        backButton = findViewById(R.id.back_button)
        saveButton = findViewById(R.id.save_button)
        profileImage = findViewById(R.id.profile_image)
        uploadImage = findViewById(R.id.upload_image)
        userName = findViewById(R.id.user_name)
        userBio = findViewById(R.id.user_bio)
        userPhone = findViewById(R.id.user_phone)

        // Tombol Kembali
        backButton.setOnClickListener {
            onBackPressed()
        }

        // Tombol Simpan
        saveButton.setOnClickListener {
            val name = userName.text.toString()
            val bio = userBio.text.toString()
            val phone = userPhone.text.toString()

            if (name.isEmpty() || bio.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "Harap isi semua kolom!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Profil berhasil disimpan!", Toast.LENGTH_SHORT).show()
            }
        }

        // Ganti Foto Profil
        uploadImage.setOnClickListener {
            openGallery()
        }
    }

    // Buka Galeri untuk Memilih Foto
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    // Menangani Hasil Pilihan Gambar dari Galeri
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            val imageUri: Uri? = data.data
            imageUri?.let {
                val inputStream = contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                profileImage.setImageBitmap(bitmap)
            }
        }
    }
}
