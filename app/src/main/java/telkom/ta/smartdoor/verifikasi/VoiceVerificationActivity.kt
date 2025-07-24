package telkom.ta.smartdoor.verifikasi

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import org.json.JSONObject
import telkom.ta.smartdoor.R
import telkom.ta.smartdoor.database.AppDatabase
import telkom.ta.smartdoor.database.VerificationLog
import telkom.ta.smartdoor.session.SessionManager
import java.io.File
import java.io.IOException
import java.util.UUID

class VoiceVerificationActivity : AppCompatActivity() {

    // --- Properti UI ---
    private lateinit var statusIndicator: View
    private lateinit var micButton: ImageButton
    private lateinit var instructionText: TextView
    private lateinit var resultText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnBack: ImageButton
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false
    private val client = OkHttpClient()
    private lateinit var sessionManager: SessionManager
    private var currentUsername: String = ""
    private var confidencePercent: Double = 0.0

    // --- Properti MQTT HiveMQ ---
    private lateinit var mqttClient: Mqtt5AsyncClient
    private val mqttBrokerHost = "broker.hivemq.com"
    private val mqttTopic = "smartdoor/voiceverification/result"

    // --- Inisialisasi Database Room ---
    private val db by lazy { AppDatabase.getDatabase(this) }
 
    private val RECORD_AUDIO_REQUEST_CODE = 1
    private val RECORDING_DURATION_MS = 5000L


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_verification)

        initializeViews()
        initializeSession()
        initializeMqttClient()
        setupUI()
        setupListeners()

        micButton.setOnClickListener {
            if (checkPermission()) {
                toggleRecording()
            } else {
                requestPermission()
            }
        }
    }

    /**
     * Menyimpan hasil verifikasi ke dalam database Room.
     * Dijalankan di background thread menggunakan coroutine.
     * @param status Status verifikasi ("Berhasil" atau "Gagal").
     * @param message Pesan detail mengenai hasil verifikasi.
     */
    private fun saveVerificationLog(status: String, message: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val log = VerificationLog(
                username = currentUsername,
                status = status,
                message = message,
                timestamp = System.currentTimeMillis()
            )
            db.logDao().insertLog(log)
        }
    }

    private fun updateConnectionStatusIndicator(isConnected: Boolean) {
        val colorRes = if (isConnected) R.color.status_connected else R.color.status_disconnected
        val color = ContextCompat.getColor(this, colorRes)
        statusIndicator.background.setTint(color)
    }

    private fun initializeMqttClient() {
        updateConnectionStatusIndicator(false)
        mqttClient = MqttClient.builder()
            .useMqttVersion5()
            .identifier(UUID.randomUUID().toString())
            .serverHost(mqttBrokerHost)
            .automaticReconnectWithDefaultConfig()
            .addDisconnectedListener { context ->
                Log.e("MQTT-HiveMQ", "Client terputus.", context.cause)
                runOnUiThread {
                    updateConnectionStatusIndicator(false)
                    Toast.makeText(applicationContext, "Koneksi perangkat terputus", Toast.LENGTH_SHORT).show()
                }
            }
            .buildAsync()

        mqttClient.connect()
            .whenComplete { connAck, throwable ->
                runOnUiThread {
                    if (throwable != null) {
                        Log.e("MQTT-HiveMQ", "Koneksi awal gagal!", throwable)
                        updateConnectionStatusIndicator(false)
                    } else {
                        Log.d("MQTT-HiveMQ", "Koneksi awal berhasil: $connAck")
                        updateConnectionStatusIndicator(true)
                        Toast.makeText(applicationContext, "Terhubung ke perangkat", Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

    private fun initializeViews() {
        statusIndicator = findViewById(R.id.statusIndicator)
        micButton = findViewById(R.id.btnRecord)
        instructionText = findViewById(R.id.tvInstruction)
        resultText = findViewById(R.id.tvResult)
        progressBar = findViewById(R.id.progressBar)
        btnBack = findViewById(R.id.btnBack)
    }
    private fun setupListeners() {
        // Listener untuk tombol mic
        micButton.setOnClickListener {
            if (checkPermission()) {
                toggleRecording()
            } else {
                requestPermission()
            }
        }

        // Listener untuk tombol kembali
        btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

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
            mqttClient.publish(publishMessage).whenComplete { _, throwable ->
                if (throwable != null) {
                    Log.e("MQTT-HiveMQ", "Gagal mengirim pesan!", throwable)
                    runOnUiThread { Toast.makeText(this, "Gagal mengirim ke perangkat", Toast.LENGTH_SHORT).show() }
                } else {
                    Log.d("MQTT-HiveMQ", "Pesan berhasil terkirim ke topik: $mqttTopic")
                }
            }
        } else {
            Log.e("MQTT-HiveMQ", "Tidak bisa mengirim, client tidak terhubung")
            runOnUiThread { Toast.makeText(this, "Perangkat offline - hasil tidak terkirim", Toast.LENGTH_SHORT).show() }
        }
    }

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
        if (!isRecording) startRecording() else stopRecording()
    }

    private fun startRecording() {
        try {
            audioFile = File.createTempFile("verify_${currentUsername}_${System.currentTimeMillis()}", ".m4a", cacheDir)
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
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
                if (isRecording) stopRecording()
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
                runOnUiThread { showError("Koneksi server gagal: ${e.localizedMessage}"); cleanupFile() }
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
                        confidencePercent = json.optDouble("confidence_percent", 0.0)
                        val certaintyLevel = json.optString("certainty_level", "UNKNOWN")
                        verifyPrediction(prediction, confidencePercent, certaintyLevel)
                    } catch (e: Exception) {
                        showError("Gagal memproses respons server")
                    } finally {
                        cleanupFile()
                    }
                }
            }
        })
    }

    // --- FUNGSI YANG DIPERBARUI ---
    private fun verifyPrediction(prediction: String, confidencePercent: Double, certaintyLevel: String) {
        val extractedName = extractNameFromPrediction(prediction)
        val isMatch = currentUsername.isNotEmpty() && extractedName.equals(currentUsername, ignoreCase = true)

        when {
            // Kasus 1: Verifikasi berhasil (username cocok)
            isMatch -> {
                handleMatchCase(certaintyLevel, confidencePercent)
            }

            // Kasus 2: Prediksi adalah suara lain yang terdaftar (bukan user saat ini)
            extractedName.isNotEmpty() && !extractedName.equals("unknown", ignoreCase = true) && !isMatch -> {
                handleWrongUserCase(extractedName, confidencePercent)
            }

            // Kasus 3: Suara tidak pasti atau tidak dikenal (mis: "unknown", "tidak dikenal 1", dll.)
            else -> {
                handleUnknownOrUncertainCase(prediction, confidencePercent)
            }
        }
    }

    private fun handleMatchCase(certaintyLevel: String, confidencePercent: Double) {
        val message: String
        val status: String
        when (certaintyLevel) {
            "HIGH" -> {
                message = "Verifikasi Berhasil! Keyakinan: ${String.format("%.1f", confidencePercent)}% (Tinggi)"
                status = "Berhasil"
                sendMqttResult(true, "Verifikasi suara berhasil untuk $currentUsername")
                showSuccess("✓ $message\nSelamat Datang, $currentUsername")
            }
            "MEDIUM" -> {
                if (confidencePercent >= 50.0) {
                    message = "Verifikasi Berhasil! Keyakinan: ${String.format("%.1f", confidencePercent)}% (Sedang)"
                    status = "Berhasil"
                    sendMqttResult(true, "Verifikasi suara berhasil (keyakinan sedang)")
                    showSuccess("✓ $message\nSelamat Datang, $currentUsername")
                } else {
                    message = "Keyakinan sedang (${String.format("%.1f", confidencePercent)}%). Coba lagi dengan suara lebih jelas."
                    status = "Gagal"
                    sendMqttResult(false, message)
                    showWarning(message)
                }
            }
            "LOW" -> {
                message = "Keyakinan rendah (${String.format("%.1f", confidencePercent)}%). Coba lagi di lingkungan lebih hening."
                status = "Gagal"
                sendMqttResult(false, message)
                showWarning(message)
            }
            else -> {
                message = "Keyakinan sangat rendah. Mohon bicara lebih jelas."
                status = "Gagal"
                sendMqttResult(false, message)
                showWarning(message)
            }
        }
        saveVerificationLog(status, message) // Simpan log
    }

    // --- FUNGSI BARU ---
    /**
     * Menangani kasus ketika suara tidak dapat diverifikasi sebagai pengguna saat ini.
     * Ini bisa karena suara tidak dikenal, tidak jelas, atau terdeteksi sebagai "unknown".
     *
     * @param prediction Nama prediksi mentah dari API (mis: "unknown", "tidak dikenal 1").
     * @param confidencePercent Persentase keyakinan dari API untuk prediksi tersebut.
     */
    private fun handleUnknownOrUncertainCase(prediction: String, confidencePercent: Double) {
        // Mempercantik output prediksi, mengganti underscore dengan spasi dan membuat huruf kapital di awal
        val finalPrediction = prediction.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        val message = "Verifikasi Gagal. Suara Anda tidak dikenali.\n" +
                "Prediksi sistem: $finalPrediction (Keyakinan: ${String.format("%.1f", confidencePercent)}%)"

        sendMqttResult(false, "Suara tidak dikenali, prediksi: $finalPrediction")
        showError(message)
        saveVerificationLog("Gagal", message)
    }

    // --- FUNGSI YANG DIPERBARUI ---
    private fun handleWrongUserCase(extractedName: String, confidencePercent: Double) {
        val message = "Verifikasi Gagal! Suara terdeteksi sebagai pengguna lain: $extractedName\n" +
                "(Keyakinan: ${String.format("%.1f", confidencePercent)}%)"

        sendMqttResult(false, "Suara terdeteksi sebagai $extractedName")
        showError(message)
        saveVerificationLog("Gagal", message)
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