package telkom.ta.smartdoor.session

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private var prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_NAME = "user_session"
        const val USER_ID = "user_id"
        const val PROFILE_ID = "profile_id"
        const val USERNAME = "username"
        const val USER_EMAIL = "user_email"
        const val USER_TOKEN = "user_token"
        const val IS_LOGIN = "is_login"

        // Profile data constants
        const val PROFILE_NAME = "profile_name"
        const val PROFILE_BIO = "profile_bio"
        const val PROFILE_NIM = "profile_nim"
        const val PROFILE_IMAGE_URI = "profile_image_uri"
    }

    /**
     * Save login session data (updated to match new API structure)
     * @param userId The user's unique ID from database
     * @param profileId The profile's unique ID from database
     * @param username The username used for login
     * @param email The user's email address
     * @param token Optional authentication token (if used)
     */
    fun saveLoginSession(userId: String, profileId: String, username: String, email: String, token: String? = null) {
        with(prefs.edit()) {
            putString(USER_ID, userId)
            putString(PROFILE_ID, profileId)
            putString(USERNAME, username)
            putString(USER_EMAIL, email)
            token?.let { putString(USER_TOKEN, it) }
            putBoolean(IS_LOGIN, true)
            apply()
        }
    }

    /**
     * Save profile data that can be edited by user
     * @param name User's display name
     * @param bio User's biography
     * @param nim User's NIM (student ID)
     * @param imageUri URI of profile image (optional)
     */
    fun saveProfileData(name: String, bio: String, nim: String?, imageUri: String?) {
        with(prefs.edit()) {
            putString(PROFILE_NAME, name)
            putString(PROFILE_BIO, bio)
            putString(PROFILE_NIM, nim)
            putString(PROFILE_IMAGE_URI, imageUri)
            apply()
        }
    }

    /**
     * Get basic user data from login session
     */
    fun getUserData(): Map<String, String?> {
        return mapOf(
            "userId" to prefs.getString(USER_ID, null),
            "profileId" to prefs.getString(PROFILE_ID, null),
            "username" to prefs.getString(USERNAME, null),
            "email" to prefs.getString(USER_EMAIL, null),
            "token" to prefs.getString(USER_TOKEN, null)
        )
    }

    /**
     * Get profile data (editable user information)
     */
    fun getProfileData(): Map<String, String?> {
        return mapOf(
            "name" to prefs.getString(PROFILE_NAME, null),
            "bio" to prefs.getString(PROFILE_BIO, null),
            "nim" to prefs.getString(PROFILE_NIM, null),
            "imageUri" to prefs.getString(PROFILE_IMAGE_URI, null)
        )
    }

    /**
     * Get combined user data (login session + profile data)
     * Profile data takes precedence over login session data
     */
    fun getCompleteUserData(): Map<String, String?> {
        val userData = getUserData()
        val profileData = getProfileData()

        return mutableMapOf<String, String?>().apply {
            putAll(userData)
            // Profile name overrides username if available
            profileData["name"]?.let { put("displayName", it) } ?: put("displayName", userData["username"])
            putAll(profileData)
        }
    }

    /**
     * Get authentication token if available
     */
    fun getUserToken(): String? {
        return prefs.getString(USER_TOKEN, null)
    }

    /**
     * Check if user is logged in
     */
    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(IS_LOGIN, false)
    }

    /**
     * Clear all session data
     */
    fun logout() {
        with(prefs.edit()) {
            clear()
            apply()
        }
    }

    /**
     * Get the current username (either from login or profile)
     */
    fun getUsername(): String? {
        return prefs.getString(PROFILE_NAME, null) ?: prefs.getString(USERNAME, null)
    }

    /**
     * Get the user ID
     */
    fun getUserId(): String? {
        return prefs.getString(USER_ID, null)
    }

    /**
     * Get the profile ID
     */
    fun getProfileId(): String? {
        return prefs.getString(PROFILE_ID, null)
    }
}