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
import androidx.lifecycle.lifecycleScope
// Impor HiveMQ yang diperlukan
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import telkom.ta.smartdoor.R
import telkom.ta.smartdoor.session.SessionManager
import java.io.File
import java.io.IOException
import java.util.UUID

class VoiceVerificationActivity : AppCompatActivity() {

    // --- Properti UI (DITAMBAHKAN 1) ---
    private lateinit var statusIndicator: View
    // ------
    private lateinit var micButton: ImageButton
    private lateinit var instructionText: TextView
    private lateinit var resultText: TextView
    private lateinit var progressBar: ProgressBar
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false
    private val client = OkHttpClient()
    private lateinit var sessionManager: SessionManager
    private var currentUsername: String = ""
    private var confidencePercent: Double = 0.0

    // Properti MQTT HiveMQ
    private lateinit var mqttClient: Mqtt5AsyncClient
    private val mqttBrokerHost = "broker.hivemq.com"
    private val mqttTopic = "smartdoor/voiceverification/result"

    private val RECORD_AUDIO_REQUEST_CODE = 1
    private val RECORDING_DURATION_MS = 5000L


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_verification)

        initializeViews()
        initializeSession()
        initializeMqttClient()
        setupUI()

        micButton.setOnClickListener {
            if (checkPermission()) {
                toggleRecording()
            } else {
                requestPermission()
            }
        }
    }

    // --- FUNGSI BARU UNTUK UPDATE UI INDIKATOR ---
    private fun updateConnectionStatusIndicator(isConnected: Boolean) {
        val colorRes = if (isConnected) R.color.status_connected else R.color.status_disconnected
        val color = ContextCompat.getColor(this, colorRes)
        statusIndicator.background.setTint(color)
    }

    private fun initializeMqttClient() {
        // Set status awal ke merah
        updateConnectionStatusIndicator(false)

        mqttClient = MqttClient.builder()
            .useMqttVersion5()
            .identifier(UUID.randomUUID().toString())
            .serverHost(mqttBrokerHost)
            .automaticReconnectWithDefaultConfig()
            // --- DITAMBAHKAN: Listener untuk saat koneksi terputus ---
            .addDisconnectedListener { context ->
                Log.e("MQTT-HiveMQ", "Client terputus.", context.cause)
                runOnUiThread {
                    updateConnectionStatusIndicator(false) // Ubah ke merah
                    Toast.makeText(applicationContext, "Koneksi perangkat terputus", Toast.LENGTH_SHORT).show()
                }
            }
            .buildAsync()

        mqttClient.connect()
            .whenComplete { connAck, throwable ->
                runOnUiThread {
                    if (throwable != null) {
                        Log.e("MQTT-HiveMQ", "Koneksi awal gagal!", throwable)
                        updateConnectionStatusIndicator(false) // Pastikan merah
                    } else {
                        Log.d("MQTT-HiveMQ", "Koneksi awal berhasil: $connAck")
                        updateConnectionStatusIndicator(true) // Ubah ke hijau
                        Toast.makeText(applicationContext, "Terhubung ke perangkat", Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

    // Sisa kode di bawah ini sebagian besar tidak berubah
    // ...
    // --- FUNGSI INISIALISASI (DITAMBAHKAN 1) ---
    private fun initializeViews() {
        statusIndicator = findViewById(R.id.statusIndicator) // Inisialisasi view baru
        micButton = findViewById(R.id.btnRecord)
        instructionText = findViewById(R.id.tvInstruction)
        resultText = findViewById(R.id.tvResult)
        progressBar = findViewById(R.id.progressBar)
    }

    // --- FUNGSI PENGIRIMAN MQTT BARU ---
    private fun sendMqttResult(isSuccess: Boolean, message: String) {
        if (mqttClient.state.isConnectedOrReconnect) {
            val payload = JSONObject().apply {
                put("username", currentUsername)
                put("status", if (isSuccess) "success" else "failed")
                put("message", message)
                put("confidence", confidencePercent)
                put("timestamp", System.currentTimeMillis())
            }.toString()

            val publishMessage = Mqtt5Publish.builder()
                .topic(mqttTopic)
                .qos(MqttQos.AT_LEAST_ONCE)
                .payload(payload.toByteArray())
                .build()

            mqttClient.publish(publishMessage).whenComplete { result, throwable ->
                if (throwable != null) {
                    Log.e("MQTT-HiveMQ", "Gagal mengirim pesan!", throwable)
                    runOnUiThread {
                        Toast.makeText(this, "Gagal mengirim ke perangkat", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.d("MQTT-HiveMQ", "Pesan berhasil terkirim ke topik: $mqttTopic")
                }
            }
        } else {
            Log.e("MQTT-HiveMQ", "Tidak bisa mengirim, client tidak terhubung")
            runOnUiThread {
                Toast.makeText(this, "Perangkat offline - hasil tidak terkirim", Toast.LENGTH_SHORT).show()
            }
        }
    }


    // --- SISA KODE DI BAWAH INI TIDAK ADA PERUBAHAN ---

    private fun initializeSession() {
        sessionManager = SessionManager(this)
        currentUsername = sessionManager.getUserData()["username"]?.trim() ?: ""
    }

    private fun setupUI() {
        if (currentUsername.isEmpty()) {
            instructionText.text = "Sesi tidak valid, silakan login kembali"
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
            audioFile = File.createTempFile("verify_${currentUsername}_${System.currentTimeMillis()}", ".m4a", cacheDir)
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(96000)
                setAudioChannels(1)
                setOutputFile(audioFile!!.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            micButton.setImageResource(R.drawable.ic_stop)
            instructionText.text = "Merekam... katakan \"Buka Pintu\" dengan jelas..."
            resultText.visibility = View.GONE
            lifecycleScope.launch {
                delay(RECORDING_DURATION_MS)
                if (isRecording) {
                    stopRecording()
                }
            }
        } catch (e: IOException) {
            Log.e("VoiceVerification", "Gagal memulai rekaman", e)
            showError("Gagal memulai rekaman: ${e.localizedMessage}")
            resetUI()
        }
    }

    private fun stopRecording() {
        if (!isRecording) return
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: RuntimeException) {
            Log.e("VoiceVerification", "Gagal menghentikan rekaman", e)
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
            showError("File audio tidak valid atau kosong. Silakan coba lagi.")
            cleanupFile()
            resetUI()
            return
        }
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("audio", audioFile!!.name, audioFile!!.asRequestBody("audio/mp4".toMediaType()))
            .addFormDataPart("username", currentUsername)
            .build()
        val request = Request.Builder()
            .url("https://smartdoorlock.cloud/api/detect/suara")
            .post(requestBody)
            .addHeader("Accept", "application/json")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    showError("Koneksi server gagal: ${e.localizedMessage}")
                    cleanupFile()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    try {
                        if (!response.isSuccessful || responseBody == null) {
                            showError("Server error (${response.code})")
                            return@runOnUiThread
                        }
                        val json = JSONObject(responseBody)
                        if (json.has("error")) {
                            showError("Server error: ${json.getString("error")}")
                            return@runOnUiThread
                        }
                        val prediction = json.optString("prediction", "unknown")
                        val confidence = json.optDouble("confidence", 0.0)
                        confidencePercent = json.optDouble("confidence_percent", confidence * 100)
                        val certaintyLevel = json.optString("certainty_level", "UNKNOWN")
                        val predictionLogic = json.optString("prediction_logic", "")
                        verifyPrediction(prediction, confidencePercent, certaintyLevel, predictionLogic)
                    } catch (e: Exception) {
                        showError("Gagal memproses respons server")
                    } finally {
                        cleanupFile()
                    }
                }
            }
        })
    }

    private fun verifyPrediction(prediction: String, confidencePercent: Double, certaintyLevel: String, predictionLogic: String) {
        val extractedName = extractNameFromPrediction(prediction)
        val isMatch = currentUsername.isNotEmpty() && extractedName.equals(currentUsername, ignoreCase = true)
        when {
            isMatch -> handleMatchCase(certaintyLevel, confidencePercent)
            prediction.equals("uncertain", ignoreCase = true) || prediction.equals("error", ignoreCase = true) || prediction.equals("unknown", ignoreCase = true) -> handleUncertainCase()
            extractedName.isNotEmpty() && !extractedName.equals("unknown", ignoreCase = true) -> handleWrongUserCase(extractedName, confidencePercent)
            else -> handleUnknownVoiceCase()
        }
    }

    private fun handleMatchCase(certaintyLevel: String, confidencePercent: Double) {
        when (certaintyLevel) {
            "HIGH" -> {
                sendMqttResult(true, "Verifikasi suara berhasil untuk $currentUsername")
                showSuccess("✓ Verifikasi Berhasil!\nSelamat Datang, $currentUsername\nKeyakinan: ${String.format("%.1f", confidencePercent)}% (Tinggi)")
            }
            "MEDIUM" -> {
                if (confidencePercent >= 50.0) {
                    sendMqttResult(true, "Verifikasi suara berhasil (keyakinan sedang)")
                    showSuccess("✓ Verifikasi Berhasil!\nSelamat Datang, $currentUsername\nKeyakinan: ${String.format("%.1f", confidencePercent)}% (Sedang)")
                } else {
                    sendMqttResult(false, "Keyakinan sedang (${String.format("%.1f", confidencePercent)}%)")
                    showWarning("Keyakinan sedang (${String.format("%.1f", confidencePercent)}%).\nCoba lagi dengan suara lebih jelas.")
                }
            }
            "LOW" -> {
                sendMqttResult(false, "Keyakinan rendah (${String.format("%.1f", confidencePercent)}%)")
                showWarning("Keyakinan rendah (${String.format("%.1f", confidencePercent)}%).\nCoba lagi di lingkungan lebih hening.")
            }
            else -> {
                sendMqttResult(false, "Keyakinan sangat rendah")
                showWarning("Keyakinan sangat rendah.\nMohon bicara lebih jelas.")
            }
        }
    }

    private fun handleUncertainCase() {
        sendMqttResult(false, "Suara tidak dikenali")
        showError("Suara tidak dikenali.\nMohon bicara dengan jelas di lingkungan yang hening.")
    }

    private fun handleWrongUserCase(extractedName: String, confidencePercent: Double) {
        sendMqttResult(false, "Suara terdeteksi sebagai $extractedName")
        showError("Verifikasi Gagal!\nSuara terdeteksi sebagai: $extractedName\n(Keyakinan: ${String.format("%.1f", confidencePercent)}%)")
    }

    private fun handleUnknownVoiceCase() {
        sendMqttResult(false, "Suara tidak terdaftar")
        showError("Suara tidak terdaftar.\nPastikan suara Anda sudah terdaftar di sistem.")
    }

    private fun extractNameFromPrediction(prediction: String): String {
        var processed = prediction.trim().removePrefix("suara tambahan ").replace(" WAV", "", ignoreCase = true)
        if (processed.contains("-")) {
            processed = processed.split("-").drop(1).joinToString(" ").trim()
        }
        return processed
    }

    private fun showSuccess(msg: String) {
        runOnUiThread {
            progressBar.visibility = View.GONE
            resultText.visibility = View.VISIBLE
            resultText.text = msg
            resultText.setTextColor(ContextCompat.getColor(this, R.color.green_success))
            micButton.setImageResource(R.drawable.ic_mic)
            micButton.isEnabled = true
            instructionText.text = "Verifikasi berhasil!"
        }
    }

    private fun showWarning(msg: String) {
        runOnUiThread {
            progressBar.visibility = View.GONE
            resultText.visibility = View.VISIBLE
            resultText.text = msg
            resultText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
            resetUI()
        }
    }

    private fun showError(msg: String) {
        runOnUiThread {
            progressBar.visibility = View.GONE
            resultText.visibility = View.VISIBLE
            resultText.text = msg
            resultText.setTextColor(ContextCompat.getColor(this, R.color.red_error))
            resetUI()
        }
    }

    private fun resetUI() {
        runOnUiThread {
            progressBar.visibility = View.GONE
            micButton.setImageResource(R.drawable.ic_mic)
            instructionText.text = "Tekan tombol mic untuk verifikasi suara (5 detik)"
            isRecording = false
            micButton.isEnabled = true
        }
    }

    private fun cleanupFile() {
        try {
            audioFile?.delete()
        } catch (_: Exception) {}
        audioFile = null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Izin diberikan. Silakan tekan mic lagi.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Izin mikrofon diperlukan", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaRecorder?.release()
        mediaRecorder = null
        cleanupFile()
        try {
            if (::mqttClient.isInitialized && mqttClient.state.isConnected) {
                mqttClient.disconnect()
            }
        } catch (e: Exception) {
            Log.e("MQTT-HiveMQ", "Error saat disconnect di onDestroy", e)
        }
    }
}