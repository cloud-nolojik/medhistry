package com.medhistry.doctor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
 * Doctor app navigation flow (OTP-only auth):
 *
 *   Splash → Onboarding1/2/3 → PhoneEntry → Otp
 *     ├─ existing doctor      → Home
 *     └─ new doctor (temp)    → InviteCode → Signup → complete-registration → Home
 */
private sealed class DoctorScreen {
    data object Splash : DoctorScreen()
    data object Onboarding1 : DoctorScreen()
    data object Onboarding2 : DoctorScreen()
    data object Onboarding3 : DoctorScreen()

    data object PhoneEntry : DoctorScreen()
    data class Otp(val phoneE164: String, val devOtp: String?) : DoctorScreen()

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

@Composable
private fun DoctorAppRoot(api: MedHistryApi) {
    var screen: DoctorScreen by remember { mutableStateOf(DoctorScreen.Splash) }
    val scope = rememberCoroutineScope()

    // Signup-submission state (owned here because /complete-registration lives
    // at the navigation layer: its result determines the next screen).
    var signupSubmitting by remember { mutableStateOf(false) }
    var signupError by remember { mutableStateOf<String?>(null) }

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
            onBack = { screen = DoctorScreen.Onboarding3 },
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
                if (!resp.isNewUser && doctor != null) {
                    // Existing doctor: straight to home.
                    screen = DoctorScreen.Home(sessionFromProfile(doctor))
                } else if (resp.isNewUser && resp.tempToken != null) {
                    // New doctor: collect invite code, then profile.
                    signupSubmitting = false
                    signupError = null
                    screen = DoctorScreen.InviteCode(
                        phoneE164 = s.phoneE164,
                        tempToken = resp.tempToken,
                    )
                }
                // Any other shape (verified=true but neither branch populated)
                // is treated as a no-op; the user can retry.
            },
        )

        is DoctorScreen.InviteCode -> DoctorInviteCodeScreen(
            api = api,
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
                        screen = DoctorScreen.Home(sessionFromProfile(resp.doctor))
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
        )
        is DoctorScreen.Scan -> DoctorScanScreen(
            api = api,
            onBack = { screen = DoctorScreen.Home(s.session) },
        )
        is DoctorScreen.EnterCode -> EnterShareCodeScreen(
            api = api,
            onBack = { screen = DoctorScreen.Home(s.session) },
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
            onBack = { screen = DoctorScreen.Home(s.session) },
            onLogout = { screen = DoctorScreen.PhoneEntry },
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
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(bottom = 72.dp)) {
            DoctorHomeScreen(
                api = api,
                doctorName = session.name,
                onScanQR = onScanQR,
                onEnterCode = onEnterCode,
                onProfile = onProfile,
            )
        }
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            DoctorBottomNav(current = currentTab, onSelect = onTab)
        }
    }
}
