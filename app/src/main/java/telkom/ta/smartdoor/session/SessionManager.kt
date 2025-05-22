package telkom.ta.smartdoor.session

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private var prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_NAME = "user_session"
        const val USER_TOKEN = "user_token"
        const val USER_EMAIL = "user_email"
        const val USER_NAME = "user_name" // Nama awal yang disimpan saat login
        const val USER_NIM = "user_nim"   // NIM awal yang disimpan saat login
        const val IS_LOGIN = "is_login"

        // --- Konstanta untuk data profil (diperbarui: PHONE diganti NIM) ---
        const val PROFILE_NAME = "profile_name"
        const val PROFILE_BIO = "profile_bio"
        const val PROFILE_NIM = "profile_nim" // <-- PERUBAHAN: Dulu PROFILE_PHONE, sekarang PROFILE_NIM
        const val PROFILE_IMAGE_URI = "profile_image_uri" // Menyimpan URI gambar
    }

    /**
     * Menyimpan data login user
     * Ini adalah data yang didapatkan saat user pertama kali login.
     */
    fun saveLoginSession(token: String, email: String, name: String, nim: String) {
        with(prefs.edit()) {
            putString(USER_TOKEN, token)
            putString(USER_EMAIL, email)
            putString(USER_NAME, name) // Nama dari sesi login
            putString(USER_NIM, nim)   // NIM dari sesi login
            putBoolean(IS_LOGIN, true)
            apply()
        }
    }

    /**
     * Menyimpan data profil user
     * Ini adalah data yang bisa diedit oleh user di ProfileActivity.
     * NIM di sini akan menimpa/memperbarui NIM yang mungkin sudah ada dari sesi login.
     */
    fun saveProfileData(name: String, bio: String, nim: String?, imageUri: String?) { // <-- PERUBAHAN: Parameter 'phone' diganti 'nim'
        with(prefs.edit()) {
            putString(PROFILE_NAME, name)
            putString(PROFILE_BIO, bio)
            putString(PROFILE_NIM, nim) // <-- PERUBAHAN: Menyimpan NIM ke kunci PROFILE_NIM
            putString(PROFILE_IMAGE_URI, imageUri) // imageUri bisa null
            apply()
        }
    }

    /**
     * Mendapatkan token user
     */
    fun getUserToken(): String? {
        return prefs.getString(USER_TOKEN, null)
    }

    /**
     * Mendapatkan data user (login session data)
     * Data ini merepresentasikan informasi awal saat user login.
     */
    fun getUserData(): Map<String, String?> {
        return mapOf(
            "email" to prefs.getString(USER_EMAIL, null),
            "name" to prefs.getString(USER_NAME, null), // Nama dari sesi login
            "nim" to prefs.getString(USER_NIM, null)    // NIM dari sesi login
        )
    }

    /**
     * Mendapatkan data profil user
     * Data ini merepresentasikan informasi profil yang mungkin sudah diedit oleh user.
     * Prioritaskan data dari sini untuk tampilan profil/dashboard.
     */
    fun getProfileData(): Map<String, String?> {
        return mapOf(
            "name" to prefs.getString(PROFILE_NAME, null),
            "bio" to prefs.getString(PROFILE_BIO, null),
            "nim" to prefs.getString(PROFILE_NIM, null), // <-- PERUBAHAN: Mengambil NIM dari kunci PROFILE_NIM
            "imageUri" to prefs.getString(PROFILE_IMAGE_URI, null)
        )
    }

    /**
     * Mengecek status login
     */
    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(IS_LOGIN, false)
    }

    /**
     * Logout dan hapus semua data session dan profil
     * Ini akan menghapus SEMUA data di file SharedPreferences 'user_session'.
     */
    fun logout() {
        with(prefs.edit()) {
            clear()
            apply()
        }
    }
}