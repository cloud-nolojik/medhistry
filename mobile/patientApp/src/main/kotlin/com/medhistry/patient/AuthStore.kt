package com.medhistry.patient

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists auth state (token, phone, name) across app restarts.
 * Uses SharedPreferences for simplicity.
 */
class AuthStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("medhistry_auth", Context.MODE_PRIVATE)

    var token: String?
        get() = prefs.getString("token", null)
        set(value) = prefs.edit().putString("token", value).apply()

    var phone: String?
        get() = prefs.getString("phone", null)
        set(value) = prefs.edit().putString("phone", value).apply()

    var name: String?
        get() = prefs.getString("name", null)
        set(value) = prefs.edit().putString("name", value).apply()

    /** True if the user has completed registration and has a saved session. */
    val isLoggedIn: Boolean get() = token != null && phone != null

    fun save(token: String, phone: String, name: String) {
        this.token = token
        this.phone = phone
        this.name = name
    }

    /**
     * Log out — clears the session token but keeps the phone number and name
     * so the app can go straight to the PIN screen next time instead of
     * asking for OTP again.
     */
    fun logout() {
        this.token = null
    }

    /**
     * Full reset — used only when the user explicitly wants to switch accounts
     * or when the account is deleted. Clears everything including phone.
     */
    fun clear() {
        prefs.edit().clear().apply()
    }
}
