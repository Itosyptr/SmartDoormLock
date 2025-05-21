package telkom.ta.smartdoor.session

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private var prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_NAME = "user_session"
        const val USER_TOKEN = "user_token"
        const val USER_EMAIL = "user_email"
        const val USER_NAME = "user_name"
        const val USER_NIM = "user_nim"
        const val IS_LOGIN = "is_login"
    }

    /**
     * Menyimpan data login user
     */
    fun saveLoginSession(token: String, email: String, name: String, nim: String) {
        with(prefs.edit()) {
            putString(USER_TOKEN, token)
            putString(USER_EMAIL, email)
            putString(USER_NAME, name)
            putString(USER_NIM, nim)
            putBoolean(IS_LOGIN, true)
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
     * Mendapatkan data user
     */
    fun getUserData(): Map<String, String?> {
        return mapOf(
            "email" to prefs.getString(USER_EMAIL, null),
            "name" to prefs.getString(USER_NAME, null),
            "nim" to prefs.getString(USER_NIM, null)
        )
    }

    /**
     * Mengecek status login
     */
    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(IS_LOGIN, false)
    }

    /**
     * Logout dan hapus semua data session
     */
    fun logout() {
        with(prefs.edit()) {
            clear()
            apply()
        }
    }
}