package telkom.ta.smartdoor.verifikasi // Atau package yang sesuai untuk AddKeywordActivity Anda

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText // Anda bisa hapus EditText dari layout atau sembunyikan
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*
import telkom.ta.smartdoor.R // Pastikan R diimpor dengan benar

class AddKeywordActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnRecord: ImageButton
    private lateinit var btnSaveKeyword: Button
    private lateinit var speechRecognizer: SpeechRecognizer
    private var recognizedKeywordText: String? = null // Untuk menyimpan teks hasil STT
    private var isListening = false

    private val RECORD_AUDIO_PERMISSION_CODE = 101
    private val TAG = "AddKeywordActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_keyword) // Pastikan layout sesuai

        tvStatus = findViewById(R.id.tvStatus)
        // etKeywordName = findViewById(R.id.etKeywordName) // Komentari atau hapus
        btnRecord = findViewById(R.id.btnRecord)
        btnSaveKeyword = findViewById(R.id.btnSaveKeyword)
        // tvRecordingTime = findViewById(R.id.tvRecordingTime) // Komentari atau hapus

        // Inisialisasi SpeechRecognizer
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition tidak tersedia", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        setupSpeechListener()

        btnRecord.setOnClickListener {
            if (!isListening) {
                if (checkPermissions()) {
                    startListeningForKeyword()
                } else {
                    requestPermissions()
                }
            } else {
                stopListeningAndProcess() // Atau speechRecognizer.stopListening() jika hanya ingin berhenti
            }
        }

        btnSaveKeyword.setOnClickListener {
            saveRecognizedKeyword()
        }

        // etKeywordName.visibility = View.GONE // Sembunyikan EditText jika tidak dipakai
        btnSaveKeyword.isEnabled = false // Awalnya tombol simpan tidak aktif
        tvStatus.text = "Tekan tombol mic untuk mengucapkan keyword baru"
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_PERMISSION_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListeningForKeyword()
            } else {
                Toast.makeText(this, "Izin mikrofon ditolak.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startListeningForKeyword() {
        recognizedKeywordText = null // Reset keyword sebelumnya
        btnSaveKeyword.isEnabled = false
        // etKeywordName.setText("") // Bersihkan EditText jika dipakai untuk display

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("id", "ID")) // Bahasa Indonesia
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Ucapkan keyword Anda...")
        }
        speechRecognizer.startListening(intent)
        tvStatus.text = "Mendengarkan..."
        btnRecord.setImageResource(android.R.drawable.ic_media_pause) // Ganti ikon menjadi 'stop'
        isListening = true
    }

    private fun stopListeningAndProcess() {
        // SpeechRecognizer akan berhenti sendiri setelah onResults atau onError.
        // Tombol ini bisa jadi tidak diperlukan jika auto-stop sudah cukup.
        // Namun, jika ingin memberi kontrol stop manual:
        speechRecognizer.stopListening()
        tvStatus.text = "Memproses..."
        btnRecord.setImageResource(android.R.drawable.ic_btn_speak_now) // Ganti ikon kembali
        isListening = false
        // Hasil akan ditangani di onResults atau onError
    }


    private fun setupSpeechListener() {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                tvStatus.text = "Silakan berbicara..."
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                tvStatus.text = "Memproses ucapan Anda..."
                btnRecord.setImageResource(android.R.drawable.ic_btn_speak_now)
                isListening = false
            }

            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Kesalahan audio."
                    SpeechRecognizer.ERROR_NO_MATCH -> "Tidak dapat mengenali ucapan. Coba lagi."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Tidak ada ucapan terdeteksi."
                    // Tambahkan kasus error lain jika perlu
                    else -> "Kesalahan: $error"
                }
                tvStatus.text = message
                Toast.makeText(this@AddKeywordActivity, message, Toast.LENGTH_SHORT).show()
                btnRecord.setImageResource(android.R.drawable.ic_btn_speak_now)
                isListening = false
                btnSaveKeyword.isEnabled = false
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val mostLikelySaid = matches[0] // Ambil hasil yang paling mungkin
                    recognizedKeywordText = mostLikelySaid.trim() // Simpan hasil STT

                    // Tampilkan ke pengguna untuk konfirmasi (opsional, bisa di etKeywordName jika masih ada)
                    // etKeywordName.setText(recognizedKeywordText)
                    tvStatus.text = "Anda mengucapkan: \"$recognizedKeywordText\". Tekan Simpan."
                    Log.d(TAG, "Recognized Keyword: $recognizedKeywordText")
                    btnSaveKeyword.isEnabled = true // Aktifkan tombol simpan
                } else {
                    tvStatus.text = "Tidak ada kata yang dikenali. Coba lagi."
                    btnSaveKeyword.isEnabled = false
                }
                btnRecord.setImageResource(android.R.drawable.ic_btn_speak_now)
                isListening = false
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun saveRecognizedKeyword() {
        if (recognizedKeywordText.isNullOrEmpty()) {
            Toast.makeText(this, "Tidak ada keyword untuk disimpan. Ucapkan keyword terlebih dahulu.", Toast.LENGTH_LONG).show()
            return
        }

        val keywordToSave = recognizedKeywordText!!.lowercase(Locale.ROOT) // Ambil dari hasil STT dan lowercase

        val prefs = getSharedPreferences("VoiceAppPrefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val existingKeywords = prefs.getStringSet("user_defined_keywords", HashSet<String>()) ?: HashSet()
        val newKeywordsSet = HashSet(existingKeywords)
        newKeywordsSet.add(keywordToSave)
        editor.putStringSet("user_defined_keywords", newKeywordsSet)
        editor.apply()

        Log.d(TAG, "Keyword Teks Disimpan ke SharedPreferences: $keywordToSave")
        Log.d(TAG, "Semua Keywords di SharedPreferences: $newKeywordsSet")
        Toast.makeText(this, "Keyword '$keywordToSave' disimpan!", Toast.LENGTH_LONG).show()

        // Reset untuk input keyword baru
        recognizedKeywordText = null
        // etKeywordName.text.clear() // Jika etKeywordName masih dipakai
        btnSaveKeyword.isEnabled = false
        tvStatus.text = "Tekan tombol mic untuk mengucapkan keyword baru"
        // finish() // Opsional: tutup activity setelah menyimpan
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy() // Penting untuk melepaskan resource
    }
}