package com.medhistry.patient

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
import androidx.compose.ui.unit.dp
import com.medhistry.patient.ui.AccessHistoryScreen
import com.medhistry.patient.ui.FamilyMembersScreen
import com.medhistry.patient.ui.MedHistryTheme
import com.medhistry.patient.ui.LabResultsScreen
import com.medhistry.patient.ui.MedicinesScreen
import com.medhistry.patient.ui.NewUserProfileScreen
import com.medhistry.patient.ui.Onboarding1
import com.medhistry.patient.ui.Onboarding2
import com.medhistry.patient.ui.Onboarding3
import com.medhistry.patient.ui.OtpScreen
import com.medhistry.patient.ui.PatientBottomNav
import com.medhistry.patient.ui.DocumentDetailScreen
import com.medhistry.patient.ui.PatientHomeScreen
import com.medhistry.patient.ui.PatientLoginScreen
import com.medhistry.patient.ui.PatientProfileScreen
import com.medhistry.patient.ui.PatientSignupScreen
import com.medhistry.patient.ui.PatientTab
import com.medhistry.patient.ui.PatientTimelineScreen
import com.medhistry.patient.ui.PatientUploadScreen
import com.medhistry.patient.ui.PinScreen
import com.medhistry.patient.ui.ShareMode
import com.medhistry.patient.ui.ShareScreen
import com.medhistry.patient.ui.SplashScreen
import com.medhistry.data.CompleteRegistrationRequest
import com.medhistry.data.FamilyListResponse
import com.medhistry.data.MedHistryApi
import com.medhistry.data.MedHistryApi.Companion.friendlyMessage
import com.medhistry.domain.QRSessionManager
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Single-activity host for the patient app.
 *
 * Auth flow:
 *   Splash → Onboarding → Phone → OTP (autofilled in dev) →
 *     New user: Profile → Set PIN → Home
 *     Existing user: PIN login → Home
 *   Login: Phone → OTP → PIN → Home
 */
class PatientMainActivity : ComponentActivity() {

    private val api: MedHistryApi by inject()
    private val sessionManager: QRSessionManager by inject()
    private val authStore: AuthStore by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MedHistryTheme {
                PatientAppRoot(api = api, sessionManager = sessionManager, authStore = authStore)
            }
        }
    }
}

private sealed class Screen {
    data object Splash : Screen()
    data object Onboarding1 : Screen()
    data object Onboarding2 : Screen()
    data object Onboarding3 : Screen()
    data object Signup : Screen()
    data object Login : Screen()
    data class Otp(val phone: String, val name: String = "", val fromLogin: Boolean = false) : Screen()
    // After OTP verified
    data class NewUserProfile(val phone: String, val tempToken: String, val name: String = "") : Screen()
    data class SetPin(val phone: String, val tempToken: String, val name: String, val dob: String?, val gender: String?, val bloodGroup: String?) : Screen()
    data class PinLogin(val phone: String) : Screen()
    // Main app
    data class Home(val session: PatientSession, val tab: PatientTab = PatientTab.Home) : Screen()
    data class Share(val mode: ShareMode, val patientId: String?, val session: PatientSession) : Screen()
    data class Family(val session: PatientSession) : Screen()
    data class Profile(val session: PatientSession) : Screen()
    data class AccessHistory(val session: PatientSession) : Screen()
    data class DocumentDetail(val session: PatientSession, val documentId: String, val memberName: String) : Screen()
    data class Upload(val session: PatientSession) : Screen()
}

data class PatientSession(val name: String, val phone: String, val patientId: String = "")

@Composable
private fun PatientAppRoot(api: MedHistryApi, sessionManager: QRSessionManager, authStore: AuthStore) {
    // If user is already logged in, go straight to PIN screen
    val initialScreen = if (authStore.isLoggedIn) {
        // Restore the API token so authenticated calls work
        api.setToken(authStore.token!!)
        Screen.PinLogin(phone = authStore.phone!!.removePrefix("+91"))
    } else {
        Screen.Splash
    }

    var screen: Screen by remember { mutableStateOf<Screen>(initialScreen) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var pinLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    when (val s = screen) {
        Screen.Splash -> SplashScreen(
            onGetStarted = { screen = Screen.Onboarding1 },
            onLogin = { screen = Screen.Login },
        )
        Screen.Onboarding1 -> Onboarding1(
            onBack = { screen = Screen.Splash },
            onNext = { screen = Screen.Onboarding2 },
        )
        Screen.Onboarding2 -> Onboarding2(
            onBack = { screen = Screen.Onboarding1 },
            onNext = { screen = Screen.Onboarding3 },
        )
        Screen.Onboarding3 -> Onboarding3(
            onBack = { screen = Screen.Onboarding2 },
            onNext = { screen = Screen.Signup },
        )
        Screen.Signup -> PatientSignupScreen(
            onBack = { screen = Screen.Onboarding3 },
            onLogin = { screen = Screen.Login },
            onContinue = { name, phone -> screen = Screen.Otp(phone, name = name, fromLogin = false) },
        )
        Screen.Login -> PatientLoginScreen(
            onBack = { screen = Screen.Splash },
            onSignup = { screen = Screen.Signup },
            onContinue = { phone -> screen = Screen.Otp(phone, fromLogin = true) },
        )
        is Screen.Otp -> OtpScreen(
            api = api,
            phoneNumber = s.phone,
            onBack = { screen = if (s.fromLogin) Screen.Login else Screen.Signup },
            onVerified = { tempToken, isNewUser ->
                if (isNewUser) {
                    screen = Screen.NewUserProfile(phone = s.phone, tempToken = tempToken, name = s.name)
                } else {
                    // Existing user → go straight to PIN login
                    screen = Screen.PinLogin(phone = s.phone)
                }
            },
        )
        is Screen.NewUserProfile -> NewUserProfileScreen(
            phone = s.phone,
            initialName = s.name,
            onBack = { screen = Screen.Signup },
            onContinue = { name, dob, gender, bloodGroup ->
                screen = Screen.SetPin(
                    phone = s.phone,
                    tempToken = s.tempToken,
                    name = name,
                    dob = dob,
                    gender = gender,
                    bloodGroup = bloodGroup,
                )
            },
        )
        is Screen.SetPin -> {
            pinError = null
            PinScreen(
                mode = "set",
                phone = s.phone,
                error = pinError,
                isLoading = pinLoading,
                onBack = { screen = Screen.NewUserProfile(s.phone, s.tempToken) },
                onPinEntered = { pin ->
                    pinLoading = true
                    pinError = null
                    scope.launch {
                        try {
                            val result = api.completeRegistration(
                                CompleteRegistrationRequest(
                                    tempToken = s.tempToken,
                                    name = s.name,
                                    pin = pin,
                                    dateOfBirth = s.dob,
                                    gender = s.gender,
                                    bloodGroup = s.bloodGroup,
                                )
                            )
                            pinLoading = false
                            authStore.save(result.accessToken, "+91${s.phone}", result.patient.name)
                            screen = Screen.Home(PatientSession(
                                name = result.patient.name,
                                phone = s.phone,
                                patientId = result.patient.id,
                            ))
                        } catch (e: Exception) {
                            pinLoading = false
                            pinError = MedHistryApi.friendlyMessage(e)
                        }
                    }
                },
            )
        }
        is Screen.PinLogin -> PinScreen(
            mode = "login",
            phone = s.phone,
            error = pinError,
            isLoading = pinLoading,
            onBack = null,
            onPinEntered = { pin ->
                pinLoading = true
                pinError = null
                scope.launch {
                    try {
                        val result = api.pinLogin("+91${s.phone}", pin)
                        pinLoading = false
                        authStore.save(result.accessToken, "+91${s.phone}", result.patient.name)
                        screen = Screen.Home(PatientSession(
                            name = result.patient.name,
                            phone = s.phone,
                            patientId = result.patient.id,
                        ))
                    } catch (e: Exception) {
                        pinLoading = false
                        pinError = MedHistryApi.friendlyMessage(e)
                    }
                }
            },
        )
        is Screen.Home -> PatientShell(
            currentTab = s.tab,
            onTab = { tab ->
                screen = Screen.Home(s.session, tab)
            },
            onLogout = { authStore.clear(); screen = Screen.Splash },
        ) {
            when (s.tab) {
                PatientTab.Home -> PatientHomeScreen(
                    api = api,
                    onUpload = { screen = Screen.Upload(s.session) },
                    onShareQR = { pid -> screen = Screen.Share(ShareMode.QR, pid, s.session) },
                    onShareCode = { pid -> screen = Screen.Share(ShareMode.CODE, pid, s.session) },
                    onManageFamily = { screen = Screen.Family(s.session) },
                    onProfile = { screen = Screen.Profile(s.session) },
                    onNavigateToMedicines = { screen = Screen.Home(s.session, PatientTab.Medicines) },
                    onNavigateToLabResults = { screen = Screen.Home(s.session, PatientTab.LabResults) },
                )
                PatientTab.Timeline -> PatientTimelineScreen(
                    api = api,
                    onDocumentClick = { docId, memberName ->
                        screen = Screen.DocumentDetail(s.session, docId, memberName)
                    },
                )
                PatientTab.Medicines -> MedicinesScreen(api = api)
                PatientTab.LabResults -> LabResultsScreen(api = api)
            }
        }
        is Screen.Share -> ShareScreen(
            api = api,
            sessionManager = sessionManager,
            initialMode = s.mode,
            patientId = s.patientId,
            onClose = { screen = Screen.Home(s.session) },
        )
        is Screen.Family -> FamilyMembersScreen(
            api = api,
            onClose = { screen = Screen.Home(s.session) },
        )
        is Screen.Profile -> PatientProfileScreen(
            name = s.session.name,
            phone = s.session.phone,
            onBack = { screen = Screen.Home(s.session) },
            onManageFamily = { screen = Screen.Family(s.session) },
            onAccessHistory = { screen = Screen.AccessHistory(s.session) },
            onLogout = { authStore.clear(); screen = Screen.Splash },
        )
        is Screen.AccessHistory -> AccessHistoryScreen(
            api = api,
            patientId = s.session.patientId,
            onBack = { screen = Screen.Profile(s.session) },
        )
        is Screen.DocumentDetail -> DocumentDetailScreen(
            api = api,
            documentId = s.documentId,
            memberName = s.memberName,
            onBack = { screen = Screen.Home(s.session, PatientTab.Timeline) },
        )
        is Screen.Upload -> {
            var family by remember { mutableStateOf<FamilyListResponse?>(null) }
            LaunchedEffect(Unit) {
                runCatching { api.listFamily() }.onSuccess { family = it }
            }
            PatientUploadScreen(
                api = api,
                family = family,
                onBack = { screen = Screen.Home(s.session, PatientTab.Home) },
                onAddMember = { screen = Screen.Family(s.session) },
            )
        }
    }
}

@Composable
private fun PatientShell(
    currentTab: PatientTab,
    onTab: (PatientTab) -> Unit,
    onLogout: () -> Unit,
    content: @Composable () -> Unit,
) {
    var showLogoutDialog by remember { mutableStateOf(false) }

    // System back button handling
    BackHandler {
        if (currentTab != PatientTab.Home) {
            // Non-home tab → go back to Home
            onTab(PatientTab.Home)
        } else {
            // Home tab → ask if logging off
            showLogoutDialog = true
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logging off?") },
            text = { Text("Are you sure you want to log out of MedHistry?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) {
                    Text("Yes, log out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(bottom = 72.dp)) {
            content()
        }
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            PatientBottomNav(current = currentTab, onSelect = onTab)
        }
    }
}
