package telkom.ta.smartdoor.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import telkom.ta.smartdoor.R
import telkom.ta.smartdoor.login.LoginActivity

class SplashScreen : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)

        val videoView: VideoView = findViewById(R.id.videoSplash)

        // Uri video dari folder raw
        val videoUri = Uri.parse("android.resource://${packageName}/${R.raw.splashvideoo}")
        videoView.setVideoURI(videoUri)

        // Mulai putar video
        videoView.setOnPreparedListener {
            videoView.start()
        }

        // Setelah selesai, pindah ke LoginActivity
        videoView.setOnCompletionListener {
            val intent = Intent(this@SplashScreen, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
