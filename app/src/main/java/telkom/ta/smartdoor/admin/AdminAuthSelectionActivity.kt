package telkom.ta.smartdoor.admin

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import telkom.ta.smartdoor.R

class AdminAuthSelectionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_auth_selection)

        findViewById<Button>(R.id.btn_to_admin_login).setOnClickListener {
            startActivity(Intent(this, AdminLoginActivity::class.java))
        }

        findViewById<Button>(R.id.btn_to_admin_register).setOnClickListener {
            startActivity(Intent(this, AdminRegisterActivity::class.java))
        }
    }
}