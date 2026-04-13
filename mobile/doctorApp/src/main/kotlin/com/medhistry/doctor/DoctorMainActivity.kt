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
import com.medhistry.data.MedHistryApi
import com.medhistry.doctor.ui.DoctorBottomNav
import com.medhistry.doctor.ui.DoctorHomeScreen
import com.medhistry.doctor.ui.DoctorInviteCodeScreen
import com.medhistry.doctor.ui.DoctorLoginScreen
import com.medhistry.doctor.ui.DoctorOnboarding1
import com.medhistry.doctor.ui.DoctorOnboarding2
import com.medhistry.doctor.ui.DoctorOnboarding3
import com.medhistry.doctor.ui.DoctorOtpScreen
import com.medhistry.doctor.ui.DoctorProfileScreen
import com.medhistry.doctor.ui.DoctorScanScreen
import com.medhistry.doctor.ui.DoctorSessionEndedScreen
import com.medhistry.doctor.ui.DoctorSignupScreen
import com.medhistry.doctor.ui.DoctorSplashScreen
import com.medhistry.doctor.ui.DoctorTab
import com.medhistry.doctor.ui.EnterShareCodeScreen
import com.medhistry.doctor.ui.MedHistryDoctorTheme
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

private sealed class DoctorScreen {
    data object Splash : DoctorScreen()
    data object Onboarding1 : DoctorScreen()
    data object Onboarding2 : DoctorScreen()
    data object Onboarding3 : DoctorScreen()
    data object InviteCode : DoctorScreen()
    data class Signup(val hospital: String, val doctorName: String = "", val specialisation: String = "", val phone: String = "") : DoctorScreen()
    data class Otp(val phone: String, val hospital: String, val name: String, val specialization: String) : DoctorScreen()
    data object Login : DoctorScreen()
    data class Home(val session: DoctorSession, val tab: DoctorTab = DoctorTab.Home) : DoctorScreen()
    data class Scan(val session: DoctorSession) : DoctorScreen()
    data class EnterCode(val session: DoctorSession) : DoctorScreen()
    data class SessionEnded(val session: DoctorSession, val patientName: String) : DoctorScreen()
    data class Profile(val session: DoctorSession) : DoctorScreen()
}

/** Lightweight session info carried between screens. */
data class DoctorSession(
    val name: String,
    val specialization: String = "Internal Medicine",
    val hospital: String = "Jadeva Hospital",
)

@Composable
private fun DoctorAppRoot(api: MedHistryApi) {
    var screen: DoctorScreen by remember { mutableStateOf(DoctorScreen.Splash) }

    when (val s = screen) {
        DoctorScreen.Splash -> DoctorSplashScreen(onContinue = { screen = DoctorScreen.Onboarding1 })
        DoctorScreen.Onboarding1 -> DoctorOnboarding1(
            onSkip = { screen = DoctorScreen.InviteCode },
            onContinue = { screen = DoctorScreen.Onboarding2 },
        )
        DoctorScreen.Onboarding2 -> DoctorOnboarding2(
            onSkip = { screen = DoctorScreen.InviteCode },
            onContinue = { screen = DoctorScreen.Onboarding3 },
        )
        DoctorScreen.Onboarding3 -> DoctorOnboarding3(
            onContinue = { screen = DoctorScreen.InviteCode },
        )
        DoctorScreen.InviteCode -> DoctorInviteCodeScreen(
            api = api,
            onBack = { screen = DoctorScreen.Onboarding3 },
            onVerified = { code, hospital, doctorName, specialisation, phone ->
                screen = DoctorScreen.Signup(hospital, doctorName, specialisation, phone)
            },
        )
        is DoctorScreen.Signup -> DoctorSignupScreen(
            hospital = s.hospital,
            prefillName = s.doctorName,
            prefillSpecialisation = s.specialisation,
            prefillPhone = s.phone,
            onBack = { screen = DoctorScreen.InviteCode },
            onLogin = { screen = DoctorScreen.Login },
            onContinue = { name, spec, _, phone ->
                screen = DoctorScreen.Otp(phone, s.hospital, name, spec)
            },
        )
        is DoctorScreen.Otp -> DoctorOtpScreen(
            phoneNumber = s.phone,
            onBack = { screen = DoctorScreen.Signup(s.hospital) },
            onVerified = {
                screen = DoctorScreen.Home(
                    DoctorSession(name = s.name, specialization = s.specialization, hospital = s.hospital)
                )
            },
        )
        DoctorScreen.Login -> DoctorLoginScreen(
            api = api,
            onLoggedIn = { name -> screen = DoctorScreen.Home(DoctorSession(name = name)) },
        )
        is DoctorScreen.Home -> DoctorHomeWithNav(
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
            onLogout = { screen = DoctorScreen.Login },
        )
    }
}

@Composable
private fun DoctorHomeWithNav(
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
