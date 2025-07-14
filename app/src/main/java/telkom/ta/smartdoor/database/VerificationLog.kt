package telkom.ta.smartdoor.database // Anda bisa buat package baru bernama 'database'

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Mendefinisikan tabel 'verification_logs' untuk database Room.
 * Setiap instance dari kelas ini merepresentasikan satu baris log.
 */
@Entity(tableName = "verification_logs")
data class VerificationLog(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val username: String,
    val status: String, // Contoh: "Berhasil" atau "Gagal"
    val message: String,
    val timestamp: Long // Waktu kejadian dalam milidetik
)
