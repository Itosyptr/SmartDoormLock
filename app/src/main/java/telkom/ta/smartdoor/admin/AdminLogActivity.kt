package telkom.ta.smartdoor.admin

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import telkom.ta.smartdoor.R
import telkom.ta.smartdoor.database.AppDatabase

class AdminLogActivity : AppCompatActivity() {

    // Inisialisasi database menggunakan lazy delegate
    private val db by lazy { AppDatabase.getDatabase(this) }

    // Deklarasi komponen UI baru untuk panel ringkasan
    private lateinit var totalUsersTextView: TextView
    private lateinit var buildingNameTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_log)

        // Setup Toolbar
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            // Aksi saat tombol back di toolbar ditekan
            onBackPressedDispatcher.onBackPressed()
        }

        // Inisialisasi semua view
        totalUsersTextView = findViewById(R.id.tv_total_users_count)
        buildingNameTextView = findViewById(R.id.tv_building_name)
        val recyclerView: RecyclerView = findViewById(R.id.rv_logs)
        val emptyLogTextView: TextView = findViewById(R.id.tv_empty_log)

        // Setup data statis untuk panel ringkasan
        buildingNameTextView.text = "Gedung Asrama" // Anda bisa mengubah ini sesuai kebutuhan

        // Setup RecyclerView
        val logAdapter = LogAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = logAdapter

        // Mengamati perubahan data dari database
        db.logDao().getAllLogs().observe(this, Observer { logs ->
            // `it` adalah List<VerificationLog> yang didapat dari LiveData
            logs?.let { logList ->
                // Perbarui UI berdasarkan apakah daftar log kosong atau tidak
                if (logList.isEmpty()) {
                    recyclerView.visibility = View.GONE
                    emptyLogTextView.visibility = View.VISIBLE
                } else {
                    recyclerView.visibility = View.VISIBLE
                    emptyLogTextView.visibility = View.GONE
                    logAdapter.submitList(logList)
                }

                // Hitung jumlah user unik dari log dan perbarui UI
                val totalUniqueUsers = logList.map { it.username }.toSet().size
                totalUsersTextView.text = totalUniqueUsers.toString()
            }
        })
    }
}
