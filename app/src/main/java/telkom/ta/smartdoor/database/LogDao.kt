package telkom.ta.smartdoor.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data Access Object (DAO) untuk tabel verification_logs.
 * Berisi metode untuk mengakses data log dari database.
 */
@Dao
interface LogDao {

    /**
     * Memasukkan satu log baru ke dalam database.
     * Jika ada konflik, data yang ada akan diganti.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: VerificationLog)

    /**
     * Mengambil semua log dari database, diurutkan dari yang terbaru.
     * Menggunakan LiveData agar UI dapat secara otomatis diperbarui saat ada data baru.
     */
    @Query("SELECT * FROM verification_logs ORDER BY timestamp DESC")
    fun getAllLogs(): LiveData<List<VerificationLog>>
}
