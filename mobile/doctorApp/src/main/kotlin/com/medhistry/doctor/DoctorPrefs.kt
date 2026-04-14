package com.medhistry.doctor

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistent auth state for the doctor app.
 *
 * Stores the phone of the last doctor who signed in and whether they've
 * established a PIN. Used at app start to decide whether to show PIN login
 * (returning doctor, fast path) or the full phone → OTP onboarding.
 *
 * NOTE: the PIN hash itself is never stored here. Authentication is done
 * server-side via /doctors/pin-login; this file only remembers "who to
 * prompt" and "has a PIN been set yet".
 */
class DoctorPrefs private constructor(private val prefs: SharedPreferences) {

    var savedPhone: String?
        get() = prefs.getString(KEY_PHONE, null)
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_PHONE) else putString(KEY_PHONE, value)
        }.apply()

    var hasPin: Boolean
        get() = prefs.getBoolean(KEY_HAS_PIN, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_PIN, value).apply()

    /** Save phone + hasPin atomically after a successful OTP/PIN login or PIN setup. */
    fun setLoggedIn(phone: String, hasPin: Boolean) {
        prefs.edit()
            .putString(KEY_PHONE, phone)
            .putBoolean(KEY_HAS_PIN, hasPin)
            .apply()
    }

    /** Forget PIN-set flag but keep the phone so the PIN screen shows "Welcome back". */
    fun clearPin() {
        prefs.edit().putBoolean(KEY_HAS_PIN, false).apply()
    }

    /** Full sign-out — forgets phone and PIN state. */
    fun signOut() {
        prefs.edit().remove(KEY_PHONE).putBoolean(KEY_HAS_PIN, false).apply()
    }

    companion object {
        private const val FILE = "doctor_auth_prefs"
        private const val KEY_PHONE = "phone"
        private const val KEY_HAS_PIN = "has_pin"

        fun from(context: Context): DoctorPrefs =
            DoctorPrefs(context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE))
    }
}
