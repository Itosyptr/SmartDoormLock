package telkom.ta.smartdoor.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Kelas utama database Room untuk aplikasi.
 * Ini adalah singleton untuk memastikan hanya ada satu instance database yang terbuka.
 */
@Database(entities = [VerificationLog::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun logDao(): LogDao

    companion object {
        // @Volatile memastikan nilai INSTANCE selalu up-to-date dan sama
        // untuk semua thread eksekusi.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            // Jika INSTANCE tidak null, kembalikan.
            // Jika null, buat database baru.
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smartdoor_log_database" // Nama file database
                ).build()
                INSTANCE = instance
                // return instance
                instance
            }
        }
    }
}
