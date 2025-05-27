package telkom.ta.smartdoor.verifikasi

import android.Manifest
import android.content.Context // Import Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import telkom.ta.smartdoor.R
import java.util.*

class VoiceVerificationActivity : AppCompatActivity() {

    private lateinit var micButton: ImageButton
    private lateinit var instructionText: TextView
    private lateinit var resultText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var speechRecognizer: SpeechRecognizer

    // Daftar kata kunci akan dimuat dari SharedPreferences
    private var loadedKeywords: List<String> = emptyList() // Variabel untuk menyimpan keyword yang dimuat

    private val RECORD_AUDIO_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_verification)

        micButton = findViewById(R.id.btnRecord)
        instructionText = findViewById(R.id.tvInstruction)
        resultText = findViewById(R.id.tvResult)
        progressBar = findViewById(R.id.progressBar)

        // Muat keywords dari SharedPreferences
        loadKeywordsFromPreferences()

        if (loadedKeywords.isEmpty()) {
            instructionText.text = "Belum ada keyword suara yang ditambahkan. Silakan tambahkan keyword terlebih dahulu."
            micButton.isEnabled = false // Nonaktifkan tombol jika tidak ada keyword
            Toast.makeText(this, "Tidak ada keyword yang terdaftar.", Toast.LENGTH_LONG).show()
        } else {
            instructionText.text = "Tekan tombol mic dan ucapkan salah satu keyword Anda."
            Log.d("VoiceVerification", "Keywords yang dimuat: $loadedKeywords")
        }


        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition tidak tersedia", Toast.LENGTH_SHORT).show()
            finish()
            return // Tambahkan return agar tidak melanjutkan jika tidak tersedia
        }

        micButton.setOnClickListener {
            if (loadedKeywords.isEmpty()) {
                Toast.makeText(this, "Silakan tambahkan keyword suara terlebih dahulu.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (checkAudioPermission()) {
                startVoiceVerification()
            } else {
                requestAudioPermission()
            }
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
    }

    private fun loadKeywordsFromPreferences() {
        val prefs = getSharedPreferences("VoiceAppPrefs", Context.MODE_PRIVATE)
        val keywordsSet = prefs.getStringSet("user_defined_keywords", HashSet<String>()) ?: HashSet()
        // Kita simpan sebagai lowercase, jadi tidak perlu konversi lagi di sini
        loadedKeywords = keywordsSet.toList()
    }

    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_REQUEST_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                if (loadedKeywords.isNotEmpty()) { // Pastikan ada keyword sebelum memulai
                    startVoiceVerification()
                } else {
                    Toast.makeText(this, "Tidak ada keyword untuk diverifikasi.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Izin mikrofon dibutuhkan", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startVoiceVerification() {
        resultText.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        micButton.isEnabled = false // Nonaktifkan tombol selama proses
        instructionText.text = "Mendengarkan..."


        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // Menggunakan bahasa default perangkat atau bisa spesifik ke Indonesia
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault()) // Atau Locale("id", "ID")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Ucapkan keyword Anda...") // Pesan prompt opsional
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                instructionText.text = "Silakan berbicara..."
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                instructionText.text = "Memproses..."
            }

            override fun onError(error: Int) {
                progressBar.visibility = View.GONE
                resultText.visibility = View.VISIBLE
                micButton.isEnabled = true // Aktifkan kembali tombol
                instructionText.text = "Tekan tombol mic dan ucapkan salah satu keyword Anda."

                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Kesalahan input audio."
                    SpeechRecognizer.ERROR_CLIENT -> "Kesalahan pada sisi klien."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Izin tidak cukup."
                    SpeechRecognizer.ERROR_NETWORK -> "Masalah jaringan."
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Waktu jaringan habis."
                    SpeechRecognizer.ERROR_NO_MATCH -> "Tidak dikenali. Coba lagi."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Layanan pengenal sibuk."
                    SpeechRecognizer.ERROR_SERVER -> "Kesalahan server."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Tidak ada ucapan terdeteksi. Coba lagi."
                    else -> "Terjadi kesalahan (kode $error)."
                }
                resultText.text = message
                resultText.setTextColor(ContextCompat.getColor(this@VoiceVerificationActivity, android.R.color.holo_red_dark))
            }

            override fun onResults(results: Bundle?) {
                progressBar.visibility = View.GONE
                resultText.visibility = View.VISIBLE
                micButton.isEnabled = true // Aktifkan kembali tombol
                instructionText.text = "Tekan tombol mic dan ucapkan salah satu keyword Anda."


                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches.isNullOrEmpty()) {
                    resultText.text = "Tidak ada hasil. Coba lagi."
                    resultText.setTextColor(ContextCompat.getColor(this@VoiceVerificationActivity, android.R.color.holo_red_dark))
                    return
                }

                val spokenText = matches[0].lowercase(Locale.ROOT) // Ambil hasil pertama dan ubah ke lowercase

                Log.d("VoiceVerification", "Kamu mengucapkan: $spokenText")
                Log.d("VoiceVerification", "Keywords yang dicari: $loadedKeywords")


                // Mengecek apakah spokenText mengandung salah satu kata kunci yang dimuat
                // Perbaikan: Pencocokan yang lebih baik. Bisa menggunakan 'equals' jika ingin persis,
                // atau 'contains' jika keyword bisa menjadi bagian dari kalimat.
                // Untuk smart door lock, 'equals' atau pencocokan frasa yang lebih ketat mungkin lebih baik.
                var matchedKeywordFound = false
                for (keyword in loadedKeywords) {
                    // Jika ingin keyword harus sama persis:
                    if (spokenText == keyword) {
                        matchedKeywordFound = true
                        break
                    }
                    // Jika ingin keyword terkandung dalam ucapan (seperti sebelumnya):
                    // if (spokenText.contains(keyword)) {
                    //      matchedKeywordFound = true
                    //      break
                    // }
                }


                if (matchedKeywordFound) {
                    resultText.text = "Verifikasi Berhasil!"
                    resultText.setTextColor(ContextCompat.getColor(this@VoiceVerificationActivity, android.R.color.holo_green_dark))
                    // Di sini Anda bisa menambahkan logika untuk "membuka pintu"
                    // contoh: sendCommandToDoor("OPEN")
                } else {
                    resultText.text = "Verifikasi Gagal! Keyword tidak cocok."
                    resultText.setTextColor(ContextCompat.getColor(this@VoiceVerificationActivity, android.R.color.holo_red_dark))
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer.startListening(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
    }
}