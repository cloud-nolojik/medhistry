package com.medhistry.doctor

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.medhistry.data.DoctorCompleteRegistrationRequest
import com.medhistry.data.DoctorProfile
import com.medhistry.data.MedHistryApi
import com.medhistry.doctor.ui.DoctorBottomNav
import com.medhistry.doctor.ui.DoctorHomeScreen
import com.medhistry.doctor.ui.DoctorInviteCodeScreen
import com.medhistry.doctor.ui.DoctorOnboarding1
import com.medhistry.doctor.ui.DoctorOnboarding2
import com.medhistry.doctor.ui.DoctorOnboarding3
import com.medhistry.doctor.ui.DoctorOtpScreen
import com.medhistry.doctor.ui.DoctorPhoneEntryScreen
import com.medhistry.doctor.ui.DoctorPinScreen
import com.medhistry.doctor.ui.DoctorProfileScreen
import com.medhistry.doctor.ui.DoctorScanScreen
import com.medhistry.doctor.ui.DoctorSessionEndedScreen
import com.medhistry.doctor.ui.DoctorSignupScreen
import com.medhistry.doctor.ui.DoctorSplashScreen
import com.medhistry.doctor.ui.DoctorTab
import com.medhistry.doctor.ui.EnterShareCodeScreen
import com.medhistry.doctor.ui.MedHistryDoctorTheme
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class DoctorMainActivity : ComponentActivity() {
    private val api: MedHistryApi by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MedHistryDoctorTheme {
                DoctorAppRoot(api = api)
            }
        }
    }
}

/**
 * Doctor app navigation flow (OTP + PIN auth):
 *
 *   Cold start:
 *     ├─ (returning doctor w/ PIN saved) → PinLogin → Home
 *     │                                      └─ "Forgot PIN" → PhoneEntry → Otp → ...
 *     └─ (fresh install)                  → Splash → Onboarding1/2/3 → PhoneEntry → Otp
 *                                             ├─ existing w/ PIN     → PinLogin
 *                                             ├─ existing w/o PIN    → PinSetup → Home
 *                                             └─ new (temp)          → InviteCode → Signup
 *                                                                       → PinSetup → Home
 *
 *   Background >5 min → PIN lock overlay → back to current location
 */
private sealed class DoctorScreen {
    data object Splash : DoctorScreen()
    data object Onboarding1 : DoctorScreen()
    data object Onboarding2 : DoctorScreen()
    data object Onboarding3 : DoctorScreen()

    data object PhoneEntry : DoctorScreen()
    data class Otp(val phoneE164: String, val devOtp: String?) : DoctorScreen()

    // PIN flow
    /** Returning doctor — enter existing PIN to unlock. */
    data class PinLogin(val phoneE164: String) : DoctorScreen()
    /** After fresh OTP login (or new registration) — set the 6-digit PIN. */
    data class PinSetup(val phoneE164: String, val session: DoctorSession) : DoctorScreen()

    // New-user registration leg
    data class InviteCode(val phoneE164: String, val tempToken: String) : DoctorScreen()
    data class Signup(
        val phoneE164: String,
        val tempToken: String,
        val inviteCode: String,
        val hospital: String,
        val doctorName: String = "",
        val specialisation: String = "",
    ) : DoctorScreen()

    data class Home(val session: DoctorSession, val tab: DoctorTab = DoctorTab.Home) : DoctorScreen()
    data class Scan(val session: DoctorSession) : DoctorScreen()
    data class EnterCode(val session: DoctorSession) : DoctorScreen()
    data class AllPatients(val session: DoctorSession) : DoctorScreen()
    data class SessionEnded(val session: DoctorSession, val patientName: String) : DoctorScreen()
    data class Profile(val session: DoctorSession) : DoctorScreen()
}

/** Lightweight session info carried between screens. Everything sourced from the backend. */
data class DoctorSession(
    val name: String,
    val specialization: String,
    val hospital: String,
)

private fun sessionFromProfile(profile: DoctorProfile): DoctorSession = DoctorSession(
    name = profile.name,
    specialization = profile.specialisation ?: "",
    hospital = profile.hospitalName ?: "",
)

private fun prettyPhone(phoneE164: String): String =
    if (phoneE164.startsWith("+91") && phoneE164.length == 13)
        "+91 ${phoneE164.substring(3, 8)} ${phoneE164.substring(8)}"
    else phoneE164

/** Any screen past PIN auth — used by the background-lock gate. */
private fun DoctorScreen.isAuthenticated(): Boolean = when (this) {
    is DoctorScreen.Home,
    is DoctorScreen.Scan,
    is DoctorScreen.EnterCode,
    is DoctorScreen.AllPatients,
    is DoctorScreen.SessionEnded,
    is DoctorScreen.Profile -> true
    else -> false
}

/** Background lockout: require PIN again after this much idle time. */
private const val BG_LOCK_MILLIS = 5L * 60L * 1000L

@Composable
private fun DoctorAppRoot(api: MedHistryApi) {
    val context = LocalContext.current
    val prefs = remember { DoctorPrefs.from(context) }
    val scope = rememberCoroutineScope()

    // Initial screen: returning doctor with PIN → PinLogin, else Splash.
    var screen: DoctorScreen by remember {
        val saved = prefs.savedPhone
        mutableStateOf(
            if (saved != null && prefs.hasPin) DoctorScreen.PinLogin(saved)
            else DoctorScreen.Splash
        )
    }

    // PIN lock overlay — shown on top of the current screen after returning
    // from >5 min in the background. Dismissing it (PIN ok) returns us to
    // wherever we were.
    var pinLockActive by remember { mutableStateOf(false) }

    // Signup-submission state (owned here because /complete-registration lives
    // at the navigation layer: its result determines the next screen).
    var signupSubmitting by remember { mutableStateOf(false) }
    var signupError by remember { mutableStateOf<String?>(null) }

    // PIN-login state (for both the PinLogin screen and the bg-lock overlay)
    var pinLoginError by remember { mutableStateOf<String?>(null) }
    var pinLoginLoading by remember { mutableStateOf(false) }

    // PIN-setup state
    var pinSetupError by remember { mutableStateOf<String?>(null) }
    var pinSetupLoading by remember { mutableStateOf(false) }

    // Track backgrounding so we can re-lock when the user returns.
    var backgroundedAt by remember { mutableStateOf<Long?>(null) }
    DisposableEffect(Unit) {
        val owner = ProcessLifecycleOwner.get()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    backgroundedAt = System.currentTimeMillis()
                }
                Lifecycle.Event.ON_START -> {
                    val stoppedAt = backgroundedAt
                    backgroundedAt = null
                    if (
                        stoppedAt != null &&
                        System.currentTimeMillis() - stoppedAt >= BG_LOCK_MILLIS &&
                        screen.isAuthenticated() &&
                        prefs.savedPhone != null &&
                        prefs.hasPin
                    ) {
                        pinLoginError = null
                        pinLoginLoading = false
                        pinLockActive = true
                    }
                }
                else -> {}
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    // "Log out / exit app?" confirmation. Shown when the user presses the
    // system back button on a top-level / entry screen (Home, PinLogin,
    // Splash, etc.) where there's nowhere else to go.
    var showExitDialog by remember { mutableStateOf(false) }

    // System back button / gesture handler.
    //
    // Rule: every screen should go back to a sensible parent. Only the
    // root entry screens ask "are you logging off?" — everything else
    // navigates to its parent. This replaces the old behaviour where back
    // would slam-dunk the user out of the app from anywhere.
    BackHandler(enabled = !pinLockActive) {
        when (val s = screen) {
            DoctorScreen.Splash,
            is DoctorScreen.PinLogin -> showExitDialog = true
            is DoctorScreen.Home -> showExitDialog = true
            // Forced / interstitial states — swallow back so the user
            // can't accidentally bail out mid-flow.
            is DoctorScreen.PinSetup -> { /* can't leave — must set PIN */ }
            // Onboarding chain
            DoctorScreen.Onboarding1 -> showExitDialog = true
            DoctorScreen.Onboarding2 -> screen = DoctorScreen.Onboarding1
            DoctorScreen.Onboarding3 -> screen = DoctorScreen.Onboarding2
            // Auth chain
            DoctorScreen.PhoneEntry -> {
                val saved = prefs.savedPhone
                screen = if (saved != null && prefs.hasPin) DoctorScreen.PinLogin(saved)
                        else DoctorScreen.Onboarding3
            }
            is DoctorScreen.Otp -> screen = DoctorScreen.PhoneEntry
            is DoctorScreen.InviteCode -> screen = DoctorScreen.PhoneEntry
            is DoctorScreen.Signup -> {
                signupError = null
                screen = DoctorScreen.InviteCode(s.phoneE164, s.tempToken)
            }
            // Post-login child screens — all parent to Home.
            is DoctorScreen.Scan -> screen = DoctorScreen.Home(s.session)
            is DoctorScreen.EnterCode -> screen = DoctorScreen.Home(s.session)
            is DoctorScreen.AllPatients -> screen = DoctorScreen.Home(s.session)
            is DoctorScreen.SessionEnded -> screen = DoctorScreen.Home(s.session)
            is DoctorScreen.Profile -> screen = DoctorScreen.Home(s.session)
        }
    }

    if (showExitDialog) {
        val isLoggedIn = screen is DoctorScreen.Home
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(if (isLoggedIn) "Log out?" else "Exit app?") },
            text = {
                Text(
                    if (isLoggedIn) "Are you sure you want to log out of MedHistry?"
                    else "Are you sure you want to close the app?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    if (isLoggedIn) {
                        prefs.signOut()
                        screen = DoctorScreen.PhoneEntry
                    } else {
                        (context as? Activity)?.finish()
                    }
                }) {
                    Text(if (isLoggedIn) "Yes, log out" else "Exit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("Cancel") }
            },
        )
    }

    // Background-lock overlay takes over all input until PIN is entered.
    if (pinLockActive) {
        val lockPhone = prefs.savedPhone ?: ""
        DoctorPinScreen(
            mode = "login",
            phonePretty = prettyPhone(lockPhone),
            error = pinLoginError,
            isLoading = pinLoginLoading,
            onBack = null,
            onForgot = {
                // Forgot PIN during bg-lock: back to phone entry (OTP reset).
                pinLockActive = false
                pinLoginError = null
                prefs.clearPin()
                screen = DoctorScreen.PhoneEntry
            },
            onPinEntered = { pin ->
                pinLoginLoading = true
                pinLoginError = null
                scope.launch {
                    try {
                        api.doctorPinLogin(lockPhone, pin)
                        pinLoginLoading = false
                        pinLockActive = false
                    } catch (e: Exception) {
                        pinLoginLoading = false
                        pinLoginError = MedHistryApi.friendlyMessage(e)
                        // If backend wiped the PIN (too many attempts), drop
                        // back to phone entry for a fresh OTP.
                        if (!prefs.hasPin) {
                            pinLockActive = false
                            screen = DoctorScreen.PhoneEntry
                        }
                    }
                }
            },
        )
        return
    }

    when (val s = screen) {
        DoctorScreen.Splash -> DoctorSplashScreen(onContinue = { screen = DoctorScreen.Onboarding1 })

        DoctorScreen.Onboarding1 -> DoctorOnboarding1(
            onSkip = { screen = DoctorScreen.PhoneEntry },
            onContinue = { screen = DoctorScreen.Onboarding2 },
        )
        DoctorScreen.Onboarding2 -> DoctorOnboarding2(
            onSkip = { screen = DoctorScreen.PhoneEntry },
            onContinue = { screen = DoctorScreen.Onboarding3 },
        )
        DoctorScreen.Onboarding3 -> DoctorOnboarding3(
            onContinue = { screen = DoctorScreen.PhoneEntry },
        )

        DoctorScreen.PhoneEntry -> DoctorPhoneEntryScreen(
            api = api,
            onBack = {
                // If we have a PIN saved, bounce back to PIN login instead of
                // stranding the user in onboarding-ville.
                val saved = prefs.savedPhone
                screen = if (saved != null && prefs.hasPin) DoctorScreen.PinLogin(saved)
                        else DoctorScreen.Onboarding3
            },
            onOtpSent = { phoneE164, devOtp ->
                screen = DoctorScreen.Otp(phoneE164, devOtp)
            },
        )

        is DoctorScreen.Otp -> DoctorOtpScreen(
            api = api,
            phoneE164 = s.phoneE164,
            devOtpForAutofill = s.devOtp,
            onBack = { screen = DoctorScreen.PhoneEntry },
            onVerified = { resp ->
                val doctor = resp.doctor
                val tempToken = resp.tempToken
                if (!resp.isNewUser && doctor != null) {
                    val session = sessionFromProfile(doctor)
                    if (doctor.hasPin) {
                        // Returning doctor with an active PIN — straight to home.
                        prefs.setLoggedIn(s.phoneE164, hasPin = true)
                        screen = DoctorScreen.Home(session)
                    } else {
                        // Existing doctor, no PIN yet (first time after this
                        // feature shipped, or coming back from 5-wrong-tries
                        // lockout). Force them to establish one.
                        prefs.setLoggedIn(s.phoneE164, hasPin = false)
                        pinSetupError = null
                        pinSetupLoading = false
                        screen = DoctorScreen.PinSetup(s.phoneE164, session)
                    }
                } else if (resp.isNewUser && tempToken != null) {
                    // New doctor: collect invite code, then profile.
                    signupSubmitting = false
                    signupError = null
                    screen = DoctorScreen.InviteCode(
                        phoneE164 = s.phoneE164,
                        tempToken = tempToken,
                    )
                }
                // Any other shape (verified=true but neither branch populated)
                // is treated as a no-op; the user can retry.
            },
        )

        is DoctorScreen.PinLogin -> DoctorPinScreen(
            mode = "login",
            phonePretty = prettyPhone(s.phoneE164),
            error = pinLoginError,
            isLoading = pinLoginLoading,
            onBack = null,
            onForgot = {
                // Clear the PIN flag locally — the doctor has to re-OTP to set
                // a new one (backend will also clear hash on successful OTP-set).
                prefs.clearPin()
                pinLoginError = null
                screen = DoctorScreen.PhoneEntry
            },
            onPinEntered = { pin ->
                pinLoginLoading = true
                pinLoginError = null
                scope.launch {
                    try {
                        val resp = api.doctorPinLogin(s.phoneE164, pin)
                        pinLoginLoading = false
                        prefs.setLoggedIn(s.phoneE164, hasPin = true)
                        screen = DoctorScreen.Home(sessionFromProfile(resp.doctor))
                    } catch (e: Exception) {
                        pinLoginLoading = false
                        pinLoginError = MedHistryApi.friendlyMessage(e)
                    }
                }
            },
        )

        is DoctorScreen.PinSetup -> DoctorPinScreen(
            mode = "set",
            phonePretty = prettyPhone(s.phoneE164),
            error = pinSetupError,
            isLoading = pinSetupLoading,
            onBack = null, // forced step — can't go back
            onPinEntered = { pin ->
                pinSetupLoading = true
                pinSetupError = null
                scope.launch {
                    try {
                        api.setDoctorPin(pin)
                        pinSetupLoading = false
                        prefs.setLoggedIn(s.phoneE164, hasPin = true)
                        screen = DoctorScreen.Home(s.session)
                    } catch (e: Exception) {
                        pinSetupLoading = false
                        pinSetupError = MedHistryApi.friendlyMessage(e)
                    }
                }
            },
        )

        is DoctorScreen.InviteCode -> DoctorInviteCodeScreen(
            api = api,
            tempToken = s.tempToken,
            onBack = { screen = DoctorScreen.PhoneEntry },
            onVerified = { code, hospital, doctorName, specialisation, _phone ->
                screen = DoctorScreen.Signup(
                    phoneE164 = s.phoneE164,
                    tempToken = s.tempToken,
                    inviteCode = code,
                    hospital = hospital,
                    doctorName = doctorName,
                    specialisation = specialisation,
                )
            },
        )

        is DoctorScreen.Signup -> DoctorSignupScreen(
            hospital = s.hospital,
            phoneE164 = s.phoneE164,
            prefillName = s.doctorName,
            prefillSpecialisation = s.specialisation,
            submitting = signupSubmitting,
            errorMessage = signupError,
            onBack = {
                signupError = null
                screen = DoctorScreen.InviteCode(s.phoneE164, s.tempToken)
            },
            onContinue = { name, spec, regNumber ->
                signupError = null
                signupSubmitting = true
                scope.launch {
                    try {
                        val resp = api.completeDoctorRegistration(
                            DoctorCompleteRegistrationRequest(
                                tempToken = s.tempToken,
                                inviteCode = s.inviteCode,
                                name = name,
                                specialisation = spec,
                                licenseNumber = regNumber,
                            )
                        )
                        signupSubmitting = false
                        val session = sessionFromProfile(resp.doctor)
                        // Every newly registered doctor must set a PIN before
                        // they can reach home.
                        prefs.setLoggedIn(s.phoneE164, hasPin = false)
                        pinSetupError = null
                        pinSetupLoading = false
                        screen = DoctorScreen.PinSetup(s.phoneE164, session)
                    } catch (e: Exception) {
                        signupSubmitting = false
                        signupError = MedHistryApi.friendlyMessage(e)
                    }
                }
            },
        )

        is DoctorScreen.Home -> DoctorHomeWithNav(
            api = api,
            session = s.session,
            currentTab = s.tab,
            onTab = { tab ->
                screen = when (tab) {
                    DoctorTab.Home -> DoctorScreen.Home(s.session, DoctorTab.Home)
                    DoctorTab.Scan -> DoctorScreen.Scan(s.session)
                    DoctorTab.Profile -> DoctorScreen.Profile(s.session)
                }
            },
            onScanQR = { screen = DoctorScreen.Scan(s.session) },
            onEnterCode = { screen = DoctorScreen.EnterCode(s.session) },
            onProfile = { screen = DoctorScreen.Profile(s.session) },
            onSeeAllPatients = { screen = DoctorScreen.AllPatients(s.session) },
        )
        is DoctorScreen.Scan -> DoctorScanScreen(
            api = api,
            onBack = { screen = DoctorScreen.Home(s.session) },
        )
        is DoctorScreen.EnterCode -> EnterShareCodeScreen(
            api = api,
            onBack = { screen = DoctorScreen.Home(s.session) },
        )
        is DoctorScreen.AllPatients -> AllPatientsWithSheet(
            api = api,
            onBack = { screen = DoctorScreen.Home(s.session) },
            onScanQR = { screen = DoctorScreen.Scan(s.session) },
            onEnterCode = { screen = DoctorScreen.EnterCode(s.session) },
        )
        is DoctorScreen.SessionEnded -> DoctorSessionEndedScreen(
            patientName = s.patientName,
            onBackToDashboard = { screen = DoctorScreen.Home(s.session) },
            onScanNext = { screen = DoctorScreen.Scan(s.session) },
        )
        is DoctorScreen.Profile -> DoctorProfileScreen(
            doctorName = s.session.name,
            specialization = s.session.specialization,
            hospital = s.session.hospital,
            api = api,
            onBack = { screen = DoctorScreen.Home(s.session) },
            onLogout = {
                prefs.signOut()
                screen = DoctorScreen.PhoneEntry
            },
        )
    }
}

@Composable
private fun DoctorHomeWithNav(
    api: MedHistryApi,
    session: DoctorSession,
    currentTab: DoctorTab,
    onTab: (DoctorTab) -> Unit,
    onScanQR: () -> Unit,
    onEnterCode: () -> Unit,
    onProfile: () -> Unit,
    onSeeAllPatients: () -> Unit,
) {
    // The last-access info sheet is owned at this level so tapping a row on
    // either the Home screen or the All Patients screen opens the same UX.
    var tapped by remember { mutableStateOf<com.medhistry.data.DoctorDashboardBriefing?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(bottom = 72.dp)) {
            DoctorHomeScreen(
                api = api,
                doctorName = session.name,
                onScanQR = onScanQR,
                onEnterCode = onEnterCode,
                onProfile = onProfile,
                onSeeAllPatients = onSeeAllPatients,
                onPatientTap = { tapped = it },
            )
        }
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            DoctorBottomNav(current = currentTab, onSelect = onTab)
        }

        tapped?.let { b ->
            com.medhistry.doctor.ui.PatientLastAccessSheet(
                patientName = b.patientName,
                accessedAtIso = b.accessedAt,
                method = b.method,
                onScanQR = onScanQR,
                onEnterCode = onEnterCode,
                onDismiss = { tapped = null },
            )
        }
    }
}

@Composable
private fun AllPatientsWithSheet(
    api: MedHistryApi,
    onBack: () -> Unit,
    onScanQR: () -> Unit,
    onEnterCode: () -> Unit,
) {
    var tapped by remember { mutableStateOf<com.medhistry.data.DoctorDashboardBriefing?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        com.medhistry.doctor.ui.AllPatientsScreen(
            api = api,
            onBack = onBack,
            onPatientTap = { tapped = it },
        )

        tapped?.let { b ->
            com.medhistry.doctor.ui.PatientLastAccessSheet(
                patientName = b.patientName,
                accessedAtIso = b.accessedAt,
                method = b.method,
                onScanQR = onScanQR,
                onEnterCode = onEnterCode,
                onDismiss = { tapped = null },
            )
        }
    }
}
