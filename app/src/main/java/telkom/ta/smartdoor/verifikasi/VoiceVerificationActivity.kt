package telkom.ta.smartdoor.verifikasi

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.*
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import telkom.ta.smartdoor.R
import telkom.ta.smartdoor.session.SessionManager
import java.io.File
import java.io.IOException

class VoiceVerificationActivity : AppCompatActivity() {

    // Deklarasi View
    private lateinit var micButton: ImageButton
    private lateinit var instructionText: TextView
    private lateinit var resultText: TextView
    private lateinit var progressBar: ProgressBar

    // Variabel lainnya
    private lateinit var sessionManager: SessionManager
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false
    private var currentUsername: String = ""
    private val client = OkHttpClient()

    // Konstanta untuk request permission
    private val RECORD_AUDIO_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_verification)

        initializeViews()

        sessionManager = SessionManager(this)
        currentUsername = sessionManager.getUserData()["username"]?.trim() ?: ""

        setupUI()

        micButton.setOnClickListener {
            if (checkPermission()) {
                toggleRecording()
            } else {
                requestPermission()
            }
        }
    }

    private fun initializeViews() {
        micButton = findViewById(R.id.btnRecord)
        instructionText = findViewById(R.id.tvInstruction)
        resultText = findViewById(R.id.tvResult)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupUI() {
        if (currentUsername.isEmpty()) {
            instructionText.text = "Sesi tidak valid, silakan login kembali."
            micButton.isEnabled = false
        } else {
            instructionText.text = "Tekan tombol mic untuk verifikasi suara (5 detik)"
            micButton.isEnabled = true
        }
        resultText.visibility = View.GONE
        progressBar.visibility = View.GONE
    }

    private fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_REQUEST_CODE)
    }

    private fun toggleRecording() {
        if (!isRecording) {
            startRecording()
        } else {
            stopRecording()
        }
    }

    private fun startRecording() {
        try {
            // Menggunakan format .wav sesuai dengan ekspektasi backend
            audioFile = File.createTempFile("verify_${currentUsername}_${System.currentTimeMillis()}", ".wav", cacheDir)

            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                // Set sample rate ke 16kHz sesuai dengan backend
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(64000)
                setAudioChannels(1) // Mono channel sesuai backend
                setOutputFile(audioFile!!.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            micButton.setImageResource(R.drawable.ic_stop)
            instructionText.text = "Merekam... ucapkan \"Buka Pintu\" dengan jelas..."
            resultText.visibility = View.GONE

            Handler(Looper.getMainLooper()).postDelayed({
                if (isRecording) {
                    stopRecording()
                }
            }, 5000L) // Durasi rekam 5 detik

        } catch (e: IOException) {
            Log.e("VoiceVerification", "Gagal memulai perekaman", e)
            Toast.makeText(this, "Gagal memulai perekaman: ${e.message}", Toast.LENGTH_SHORT).show()
            resetUI()
        }
    }

    private fun stopRecording() {
        if (!isRecording) return
        try {
            mediaRecorder?.run {
                stop()
                release()
            }
        } catch (e: RuntimeException) {
            Log.e("VoiceVerification", "Gagal menghentikan perekaman", e)
        } finally {
            mediaRecorder = null
            isRecording = false
            progressBar.visibility = View.VISIBLE
            instructionText.text = "Memverifikasi suara..."
            micButton.isEnabled = false
            sendAudioToAPI()
        }
    }

    private fun sendAudioToAPI() {
        if (audioFile == null || !audioFile!!.exists() || audioFile!!.length() == 0L) {
            showError("File audio tidak valid atau kosong. Coba lagi.")
            cleanupFile()
            return
        }

        Log.d("VoiceVerification", "Mengirim file: ${audioFile!!.name}, Ukuran: ${audioFile!!.length()} bytes")

        // Sesuaikan dengan format backend API yang mengharapkan 'audio' dan 'username'
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("audio", audioFile!!.name, audioFile!!.asRequestBody("audio/wav".toMediaType()))
            .addFormDataPart("username", currentUsername) // Tambahkan username sesuai backend
            .build()

        val request = Request.Builder()
            .url("https://smartdoorlock.cloud/api/detect/suara")
            .post(requestBody)
            .addHeader("Accept", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    showError("Gagal menghubungi server: ${e.message}")
                    cleanupFile()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    try {
                        Log.d("VoiceVerification", "Response: [${response.code}] $responseBody")

                        if (!response.isSuccessful || responseBody == null) {
                            showError("Gagal mendapatkan respon dari server (Error ${response.code})")
                            return@runOnUiThread
                        }

                        val json = JSONObject(responseBody)
                        if (json.has("error")) {
                            showError("Server Error: ${json.getString("error")}")
                            return@runOnUiThread
                        }

                        // Backend mengembalikan struktur JSON yang berbeda
                        val prediction = json.optString("prediction", "unknown")
                        val confidence = json.optDouble("confidence", 0.0)
                        val confidencePercent = json.optDouble("confidence_percent", confidence * 100)
                        val certaintyLevel = json.optString("certainty_level", "UNKNOWN")
                        val predictionLogic = json.optString("prediction_logic", "")

                        // Log informasi tambahan untuk debugging
                        Log.d("VoiceVerification", "Prediction: $prediction")
                        Log.d("VoiceVerification", "Confidence: $confidence")
                        Log.d("VoiceVerification", "Certainty Level: $certaintyLevel")
                        Log.d("VoiceVerification", "Logic: $predictionLogic")

                        verifyPrediction(prediction, confidencePercent, certaintyLevel, predictionLogic)

                    } catch (e: Exception) {
                        Log.e("VoiceVerification", "Gagal memproses JSON", e)
                        showError("Gagal memproses respon server.")
                    } finally {
                        cleanupFile()
                    }
                }
            }
        })
    }

    /**
     * Verifikasi prediksi dengan logika yang disesuaikan dengan backend smart detector
     */
    private fun verifyPrediction(prediction: String, confidencePercent: Double, certaintyLevel: String, predictionLogic: String) {
        Log.d("VoiceVerification", "Verifying - Prediction: '$prediction' | User: '$currentUsername' | Confidence: $confidencePercent% | Level: $certaintyLevel")

        // Ekstrak nama dari prediksi (menangani format "suara tambahan nama" atau "NIM-nama")
        val extractedName = extractNameFromPrediction(prediction)

        // Cek apakah nama yang diekstrak cocok dengan username yang login
        val isMatch = currentUsername.isNotEmpty() &&
                extractedName.equals(currentUsername, ignoreCase = true)

        when {
            // KASUS 1: Verifikasi Berhasil (Nama Cocok)
            isMatch -> {
                when (certaintyLevel) {
                    "HIGH" -> {
                        showSuccess("✓ Verifikasi Berhasil!\nSelamat datang, $currentUsername\nKeyakinan: ${String.format("%.1f", confidencePercent)}% (Tinggi)")
                    }
                    "MEDIUM" -> {
                        if (confidencePercent >= 50.0) {
                            showSuccess("✓ Verifikasi Berhasil!\nSelamat datang, $currentUsername\nKeyakinan: ${String.format("%.1f", confidencePercent)}% (Sedang)")
                        } else {
                            showWarning("Keyakinan sedang (${String.format("%.1f", confidencePercent)}%).\nCoba lagi dengan suara lebih jelas.")
                        }
                    }
                    "LOW" -> {
                        showWarning("Keyakinan rendah (${String.format("%.1f", confidencePercent)}%).\nCoba lagi dengan suara lebih jelas dan lingkungan lebih tenang.")
                    }
                    else -> {
                        showWarning("Keyakinan sangat rendah.\nPastikan Anda berbicara dengan jelas.")
                    }
                }
            }

            // KASUS 2: Prediksi tidak pasti atau error
            prediction.equals("uncertain", ignoreCase = true) ||
                    prediction.equals("error", ignoreCase = true) ||
                    prediction.equals("unknown", ignoreCase = true) -> {
                showError("Suara tidak dapat diidentifikasi.\nPastikan Anda berbicara dengan jelas dan lingkungan tidak terlalu bising.")
            }

            // KASUS 3: Terdeteksi sebagai orang lain
            extractedName.isNotEmpty() && !extractedName.equals("unknown", ignoreCase = true) -> {
                showError("Verifikasi Gagal!\nSuara terdeteksi sebagai: $extractedName\n(Keyakinan: ${String.format("%.1f", confidencePercent)}%)")
            }

            // KASUS 4: Tidak ada nama yang terdeteksi atau tidak dikenal
            else -> {
                showError("Suara tidak dikenali atau tidak terdaftar.\nPastikan suara Anda sudah terdaftar dalam sistem.")
            }
        }
    }

    /**
     * Helper function untuk ekstraksi nama dari prediksi
     * Menangani format seperti "suara tambahan nama" atau "NIM-nama"
     * ===== BAGIAN INI DIPERBARUI =====
     */
    private fun extractNameFromPrediction(prediction: String): String {
        var processedPrediction = prediction.trim()

        // Hapus prefix "suara tambahan " jika ada
        if (processedPrediction.startsWith("suara tambahan ", ignoreCase = true)) {
            processedPrediction = processedPrediction.substring("suara tambahan ".length).trim()
        }

        // Jika ada format "NIM-nama", ambil bagian nama
        if (processedPrediction.contains("-")) {
            val parts = processedPrediction.split("-")
            if (parts.size >= 2) {
                // Gabungkan semua bagian setelah NIM
                processedPrediction = parts.drop(1).joinToString(" ").trim()
            }
        }

        // [PERUBAHAN] Membersihkan tambahan " WAV" dari prediksi
        processedPrediction = processedPrediction.replace(" WAV", "", ignoreCase = true)

        return processedPrediction.trim()
    }

    private fun showSuccess(msg: String) {
        progressBar.visibility = View.GONE
        resultText.visibility = View.VISIBLE
        resultText.text = msg
        resultText.setTextColor(ContextCompat.getColor(this, R.color.green_success))
        micButton.setImageResource(R.drawable.ic_mic)
        micButton.isEnabled = true
        instructionText.text = "Verifikasi berhasil!"
    }

    private fun showWarning(msg: String) {
        progressBar.visibility = View.GONE
        resultText.visibility = View.VISIBLE
        resultText.text = msg
        resultText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
        resetUI()
    }

    private fun showError(msg: String) {
        progressBar.visibility = View.GONE
        resultText.visibility = View.VISIBLE
        resultText.text = msg
        resultText.setTextColor(ContextCompat.getColor(this, R.color.red_error))
        resetUI()
    }

    private fun resetUI() {
        micButton.setImageResource(R.drawable.ic_mic)
        instructionText.text = "Tekan tombol mic untuk verifikasi suara (5 detik)"
        isRecording = false
        micButton.isEnabled = true
    }

    private fun cleanupFile() {
        try {
            audioFile?.delete()
        } catch (_: Exception) {
            // Abaikan
        }
        audioFile = null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Izin diberikan. Silakan tekan tombol mic lagi.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Izin mikrofon dibutuhkan untuk fitur ini.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaRecorder?.release()
        mediaRecorder = null
        cleanupFile()
    }
}