package telkom.ta.smartdoor.verifikasi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
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

    private lateinit var micButton: ImageButton
    private lateinit var instructionText: TextView
    private lateinit var resultText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var sessionManager: SessionManager

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false
    private var currentUsername: String = ""

    private val client = OkHttpClient()
    private val RECORD_AUDIO_REQUEST_CODE = 1
    private val RECORDING_DURATION = 3000L // 3 detik

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_verification)

        // Initialize views
        micButton = findViewById(R.id.btnRecord)
        instructionText = findViewById(R.id.tvInstruction)
        resultText = findViewById(R.id.tvResult)
        progressBar = findViewById(R.id.progressBar)

        // Initialize SessionManager
        sessionManager = SessionManager(this)

        // Load username from session
        loadUsername()

        setupUI()
        setupButtonListener()
    }

    private fun loadUsername() {
        // Get username from SessionManager which uses SharedPreferences
        val userData = sessionManager.getUserData()
        currentUsername = userData["username"] ?: ""

        Log.d("VoiceVerification", "Loaded username: $currentUsername")
    }

    private fun setupUI() {
        if (currentUsername.isEmpty()) {
            instructionText.text = "Silakan login terlebih dahulu"
            micButton.isEnabled = false
            Toast.makeText(this, "Username tidak ditemukan. Silakan login.", Toast.LENGTH_LONG).show()
        } else {
            instructionText.text = "Tekan tombol mic untuk verifikasi suara (3 detik)"
            micButton.isEnabled = true
        }
        resultText.visibility = View.GONE
        progressBar.visibility = View.GONE
    }

    private fun setupButtonListener() {
        micButton.setOnClickListener {
            if (currentUsername.isEmpty()) {
                Toast.makeText(this, "Silakan login terlebih dahulu", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (checkAudioPermission()) {
                toggleRecording()
            } else {
                requestAudioPermission()
            }
        }
    }

    private fun toggleRecording() {
        if (!isRecording) {
            startRecording()
        } else {
            stopRecording()
        }
    }

    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            RECORD_AUDIO_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            RECORD_AUDIO_REQUEST_CODE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    if (currentUsername.isNotEmpty()) {
                        startRecording()
                    }
                } else {
                    Toast.makeText(this, "Izin akses mikrofon diperlukan", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startRecording() {
        try {
            // Create dengan format yang lebih baik
            audioFile = File.createTempFile(
                "voice_${currentUsername}_${System.currentTimeMillis()}",
                ".3gp",
                cacheDir
            )

            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB) // Wide Band untuk kualitas lebih baik
                setAudioSamplingRate(16000) // 16kHz
                setAudioEncodingBitRate(23850) // Higher bitrate
                setOutputFile(audioFile!!.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            micButton.setImageResource(R.drawable.ic_mic)
            instructionText.text = "Merekam... ucapkan nama Anda dengan jelas (5 detik)"
            resultText.visibility = View.GONE

            // Perpanjang durasi recording
            Handler(Looper.getMainLooper()).postDelayed({
                if (isRecording) {
                    stopRecording()
                }
            }, 5000L) // 5 detik lebih baik

        } catch (e: Exception) {
            Log.e("VoiceVerification", "Gagal memulai perekaman: ${e.message}")
            Toast.makeText(this, "Gagal memulai perekaman: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            resetUI()
        }
    }




    private fun stopRecording() {
        if (!isRecording) return

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false

            instructionText.text = "Memverifikasi suara..."
            progressBar.visibility = View.VISIBLE

            // Send audio to verification API
            sendAudioToAPI()

        } catch (e: Exception) {
            Log.e("VoiceVerification", "Gagal menghentikan perekaman: ${e.message}")
            Toast.makeText(this, "Gagal menghentikan perekaman", Toast.LENGTH_SHORT).show()
            resetUI()
        }
    }

    private fun sendAudioToAPI() {
        if (audioFile == null || !audioFile!!.exists()) {
            showError("File audio tidak valid")
            return
        }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "audio",
                audioFile!!.name,
                audioFile!!.asRequestBody("audio/3gpp".toMediaType())
            )
            .addFormDataPart("username", currentUsername) // Send username for verification
            .build()

        val request = Request.Builder()
            .url("https://smartdoorlock.cloud/api/detect/suara")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Log.e("VoiceVerification", "API call failed: ${e.message}")
                    showError("Gagal terhubung ke server")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    handleAPIResponse(response)
                }
            }
        })
    }

    private fun handleAPIResponse(response: Response) {
        try {
            val responseBody = response.body?.string()
            Log.d("VoiceVerification", "API Response: $responseBody")

            if (!response.isSuccessful || responseBody == null) {
                showError("Gagal memverifikasi suara (${response.code})")
                return
            }

            val jsonResponse = JSONObject(responseBody)

            when {
                jsonResponse.has("error") -> {
                    showError(jsonResponse.getString("error"))
                }
                jsonResponse.has("prediction") -> {
                    val prediction = jsonResponse.getString("prediction")
                    verifyPrediction(prediction)
                }
                else -> {
                    showError("Respon tidak dikenali dari server")
                }
            }
        } catch (e: Exception) {
            Log.e("VoiceVerification", "Error parsing response: ${e.message}")
            showError("Error memproses respon")
        } finally {
            cleanupAudioFile()
        }
    }

    private fun verifyPrediction(prediction: String) {
        Log.d("VoiceVerification", "Predicted: '$prediction', Expected: '$currentUsername'")

        when {
            prediction == "unknown" -> {
                showError("Suara tidak dapat dikenali. Coba lagi dengan suara yang lebih jelas.")
            }
            prediction.contains("Error") -> {
                showError("Terjadi kesalahan saat memproses audio. Coba lagi.")
            }
            prediction.equals(currentUsername, ignoreCase = true) -> {
                showSuccess("✓ Verifikasi suara berhasil! Selamat datang, $currentUsername")
                // Add success actions here
            }
            else -> {
                showError("Suara tidak cocok. Terdeteksi: '$prediction', Diharapkan: '$currentUsername'")
            }
        }
    }

    private fun showSuccess(message: String) {
        progressBar.visibility = View.GONE
        resultText.visibility = View.VISIBLE
        resultText.text = message
        resultText.setTextColor(ContextCompat.getColor(this, R.color.green_success))
        resetUI()
    }

    private fun showError(message: String) {
        progressBar.visibility = View.GONE
        resultText.visibility = View.VISIBLE
        resultText.text = message
        resultText.setTextColor(ContextCompat.getColor(this, R.color.red_error))
        resetUI()
    }

    private fun resetUI() {
        micButton.setImageResource(R.drawable.ic_mic)
        instructionText.text = "Tekan tombol mic untuk verifikasi suara (3 detik)"
        isRecording = false
        micButton.isEnabled = true
    }

    private fun cleanupAudioFile() {
        try {
            audioFile?.delete()
        } catch (e: Exception) {
            Log.e("VoiceVerification", "Gagal menghapus file audio: ${e.message}")
        }
        audioFile = null
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaRecorder?.release()
        cleanupAudioFile()
    }

    override fun onStop() {
        super.onStop()
        if (isRecording) {
            stopRecording()
        }
    }
}