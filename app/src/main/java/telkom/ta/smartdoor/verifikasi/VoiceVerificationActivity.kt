package telkom.ta.smartdoor.verifikasi

import android.Manifest
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

    // ✅ Daftar kata kunci yang bisa digunakan untuk membuka pintu
    private val keywords = listOf("open", "close", "crot crot crot", "buka", "saya datang", "saya masuk")

    private val RECORD_AUDIO_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_verification)

        micButton = findViewById(R.id.btnRecord)
        instructionText = findViewById(R.id.tvInstruction)
        resultText = findViewById(R.id.tvResult)
        progressBar = findViewById(R.id.progressBar)

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition tidak tersedia", Toast.LENGTH_SHORT).show()
            finish()
        }

        micButton.setOnClickListener {
            if (checkAudioPermission()) {
                startVoiceVerification()
            } else {
                requestAudioPermission()
            }
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
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
                startVoiceVerification()
            } else {
                Toast.makeText(this, "Izin mikrofon dibutuhkan", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startVoiceVerification() {
        resultText.visibility = View.GONE
        progressBar.visibility = View.VISIBLE

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("id", "ID"))
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                progressBar.visibility = View.GONE
                resultText.visibility = View.VISIBLE
                val message = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Tidak dikenali. Coba lagi."
                    SpeechRecognizer.ERROR_NETWORK -> "Masalah jaringan."
                    else -> "Terjadi kesalahan (kode $error)."
                }
                resultText.text = message
            }

            override fun onResults(results: Bundle?) {
                progressBar.visibility = View.GONE
                resultText.visibility = View.VISIBLE

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spokenText = matches?.get(0)?.lowercase(Locale.ROOT) ?: ""

                Log.d("VoiceVerification", "Kamu mengucapkan: $spokenText")

                // ✅ Mengecek apakah spokenText mengandung salah satu kata kunci
                val matched = keywords.any { keyword -> spokenText.contains(keyword) }

                if (matched) {
                    resultText.text = "Verifikasi Berhasil!"
                    resultText.setTextColor(ContextCompat.getColor(this@VoiceVerificationActivity, android.R.color.holo_green_dark))
                } else {
                    resultText.text = "Verifikasi Gagal!"
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
