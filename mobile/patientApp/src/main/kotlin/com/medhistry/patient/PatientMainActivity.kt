package com.medhistry.patient

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.medhistry.patient.ui.AccessHistoryScreen
import com.medhistry.patient.ui.AskAiScreen
import com.medhistry.patient.ui.FamilyMembersScreen
import com.medhistry.patient.ui.MedHistryTheme
import com.medhistry.patient.ui.MedHistryColors
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
import com.medhistry.patient.ui.PersonalDetailsScreen
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
 * Single-activity host for the patient app — redesigned navigation.
 *
 * Auth flow (unchanged):
 *   Splash → Onboarding → Phone → OTP → Profile → PIN → Home
 *
 * Main app navigation (redesigned):
 *   PatientShell hosts 3 bottom-nav tabs: Family | Dashboard | Profile
 *
 *   Sub-screens (no bottom nav, back arrow returns to Dashboard):
 *     AskAi, Share, LabReportsList, PrescriptionsList, FullTimeline,
 *     DocumentDetail, DocumentChat, Upload, AccessHistory
 *
 *   activePatientId (null = owner/primary) is lifted to MainActivityContent
 *   and passed to all tab screens + sub-screens.
 *
 * Key changes from previous nav:
 *   - Removed bottom nav tabs: Records, Medicines, LabResults
 *   - Removed Upload tab — replaced by ScanFAB on all primary screens
 *   - Share is now a Dashboard CTA, not a bottom tab
 *   - Family tab replaces FamilyMembersScreen as a separate push-screen
 *   - Profile is now a bottom tab, not a push-screen
 */
class PatientMainActivity : ComponentActivity() {

    private val api: MedHistryApi by inject()
    private val sessionManager: QRSessionManager by inject()
    private val authStore: AuthStore by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Let Compose own all insets (status bar, nav bar, IME).
        // Combined with imePadding() / statusBarsPadding() / navigationBarsPadding()
        // in composables this gives proper keyboard-avoidance without the system
        // double-resizing the window.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Extract deep-link extras set by the "report ready" notification.
        val deepLinkDocId  = intent?.getStringExtra(EXTRA_DOCUMENT_ID)
        val deepLinkPatientId = intent?.getStringExtra(EXTRA_PATIENT_ID)
        val deepLinkMemberName = intent?.getStringExtra(EXTRA_MEMBER_NAME) ?: ""
        setContent {
            MedHistryTheme {
                PatientAppRoot(
                    api = api,
                    sessionManager = sessionManager,
                    authStore = authStore,
                    deepLinkDocumentId = deepLinkDocId,
                    deepLinkPatientId = deepLinkPatientId,
                    deepLinkMemberName = deepLinkMemberName,
                )
            }
        }
    }

    companion object {
        const val EXTRA_DOCUMENT_ID  = "deep_link_document_id"
        const val EXTRA_PATIENT_ID   = "deep_link_patient_id"
        const val EXTRA_MEMBER_NAME  = "deep_link_member_name"
    }
}

private sealed class Screen {
    // ── Auth ─────────────────────────────────────────────────────────────────
    data object Splash : Screen()
    data object Onboarding1 : Screen()
    data object Onboarding2 : Screen()
    data object Onboarding3 : Screen()
    data object Signup : Screen()
    data object Login : Screen()
    data class Otp(val phone: String, val name: String = "", val fromLogin: Boolean = false) : Screen()
    data class NewUserProfile(val phone: String, val tempToken: String, val name: String = "") : Screen()
    data class SetPin(val phone: String, val tempToken: String, val name: String, val dob: String?, val gender: String?, val bloodGroup: String?) : Screen()
    data class PinLogin(val phone: String) : Screen()

    // ── Main app ──────────────────────────────────────────────────────────────
    // Home now uses PatientTab (Family / Dashboard / Profile)
    data class Home(val session: PatientSession, val tab: PatientTab = PatientTab.Dashboard) : Screen()

    // Sub-screens — all navigate back to Dashboard
    data class AskAi(
        val session: PatientSession,
        val activePatientId: String?,
        val personName: String,
        // When opened from DocumentDetail, auto-inject this document into chat
        val injectDocumentId: String? = null,
    ) : Screen()
    data class Share(val mode: ShareMode, val patientId: String?, val session: PatientSession) : Screen()
    data class AccessHistory(val session: PatientSession) : Screen()
    data class PersonalDetails(val session: PatientSession) : Screen()
    data class DocumentDetail(
        val session: PatientSession,
        val documentId: String,
        val memberName: String,
        // When opened from AskAi chat, back should return there instead of Dashboard
        val returnToAskAi: Boolean = false,
        val askAiPatientId: String? = null,
        val askAiPersonName: String = "",
    ) : Screen()
    data class Upload(val session: PatientSession) : Screen()
    // Records sub-screens (drill-in from Dashboard sections)
    data class LabReportsList(val session: PatientSession, val activePatientId: String?) : Screen()
    data class PrescriptionsList(val session: PatientSession, val activePatientId: String?) : Screen()
    data class FullTimeline(val session: PatientSession, val activePatientId: String?) : Screen()
}

data class PatientSession(val name: String, val phone: String, val patientId: String = "")

@Composable
private fun PatientAppRoot(
    api: MedHistryApi,
    sessionManager: QRSessionManager,
    authStore: AuthStore,
    deepLinkDocumentId: String? = null,
    deepLinkPatientId: String? = null,
    deepLinkMemberName: String = "",
) {
    val initialScreen = when {
        authStore.isLoggedIn -> {
            // Active session — go straight to PIN screen (quick re-auth)
            api.setToken(authStore.token!!)
            Screen.PinLogin(phone = authStore.phone!!.removePrefix("+91"))
        }
        authStore.phone != null -> {
            // Logged out but phone remembered — go straight to PIN, skip OTP
            Screen.PinLogin(phone = authStore.phone!!.removePrefix("+91"))
        }
        else -> Screen.Splash
    }

    var screen: Screen by remember { mutableStateOf<Screen>(initialScreen) }
    // Global active patient — persists across all screen navigations so
    // switching family member on any screen carries through everywhere.
    var activePatientId by remember { mutableStateOf<String?>(null) }
    // Family loaded once here so Upload screen and all sub-screens get it
    // immediately without a fresh API call (avoids the timing bug where
    // the upload dialog opens before dependents have loaded).
    var globalFamily by remember { mutableStateOf<FamilyListResponse?>(null) }
    var globalFamilyTrigger by remember { mutableStateOf(0) }
    val globalScope = rememberCoroutineScope()
    LaunchedEffect(globalFamilyTrigger) {
        if (authStore.isLoggedIn) {
            runCatching { api.listFamily() }.onSuccess { globalFamily = it }
        }
    }
    var pinError by remember { mutableStateOf<String?>(null) }
    var pinLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showExitDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = screen !is Screen.Home) {
        when (val s = screen) {
            Screen.Splash -> showExitDialog = true
            Screen.Onboarding1 -> screen = Screen.Splash
            Screen.Onboarding2 -> screen = Screen.Onboarding1
            Screen.Onboarding3 -> screen = Screen.Onboarding2
            Screen.Signup -> screen = Screen.Onboarding3
            Screen.Login -> screen = Screen.Splash
            is Screen.Otp -> screen = if (s.fromLogin) Screen.Login else Screen.Signup
            is Screen.NewUserProfile -> screen = Screen.Signup
            is Screen.SetPin -> screen = Screen.NewUserProfile(s.phone, s.tempToken)
            is Screen.PinLogin -> showExitDialog = true
            is Screen.AskAi -> screen = Screen.Home(s.session, PatientTab.Dashboard)
            is Screen.Share -> screen = Screen.Home(s.session, PatientTab.Dashboard)
            is Screen.AccessHistory -> screen = Screen.Home(s.session, PatientTab.Profile)
            is Screen.PersonalDetails -> screen = Screen.Home(s.session, PatientTab.Profile)
            is Screen.DocumentDetail -> screen = if (s.returnToAskAi)
                Screen.AskAi(s.session, s.askAiPatientId, s.askAiPersonName)
            else
                Screen.Home(s.session, PatientTab.Dashboard)
            is Screen.Upload -> screen = Screen.Home(s.session, PatientTab.Dashboard)
            is Screen.LabReportsList -> screen = Screen.Home(s.session, PatientTab.Dashboard)
            is Screen.PrescriptionsList -> screen = Screen.Home(s.session, PatientTab.Dashboard)
            is Screen.FullTimeline -> screen = Screen.Home(s.session, PatientTab.Dashboard)
            is Screen.Home -> { /* handled by PatientShell */ }
        }
    }

    if (showExitDialog) {
        val isLoggedIn = screen is Screen.PinLogin
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
                        authStore.clear()
                        screen = Screen.Splash
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
                            screen = Screen.Home(
                                PatientSession(
                                    name = result.patient.name,
                                    phone = s.phone,
                                    patientId = result.patient.id,
                                )
                            )
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
                        val session = PatientSession(
                            name = result.patient.name,
                            phone = s.phone,
                            patientId = result.patient.id,
                        )
                        // If launched via a "report ready" notification, go straight
                        // to that document. Otherwise land on the home dashboard.
                        if (deepLinkDocumentId != null) {
                            activePatientId = deepLinkPatientId
                            screen = Screen.DocumentDetail(
                                session = session,
                                documentId = deepLinkDocumentId,
                                memberName = deepLinkMemberName,
                            )
                        } else {
                            screen = Screen.Home(session)
                        }
                        // Load family immediately after login so Upload and
                        // other sub-screens have it ready without extra API calls.
                        globalFamilyTrigger++
                    } catch (e: Exception) {
                        pinLoading = false
                        pinError = MedHistryApi.friendlyMessage(e)
                    }
                }
            },
        )

        // ── Main app shell ──────────────────────────────────────────────────
        is Screen.Home -> PatientShell(
            currentTab = s.tab,
            session = s.session,
            api = api,
            activePatientId = activePatientId,
            onSetActivePerson = { activePatientId = it },
            initialFamily = globalFamily,
            onFamilyChanged = { globalFamilyTrigger++ },
            onTab = { tab -> screen = Screen.Home(s.session, tab) },
            onLogout = {
                authStore.logout()
                screen = Screen.PinLogin(phone = authStore.phone!!.removePrefix("+91"))
            },
            onNavigate = { nextScreen -> screen = nextScreen },
        )

        // ── Sub-screens (no bottom nav) ─────────────────────────────────────
        is Screen.AskAi -> AskAiScreen(
            api = api,
            personName = s.personName,
            activePatientId = s.activePatientId,
            injectDocumentId = s.injectDocumentId,
            onBack = { screen = Screen.Home(s.session, PatientTab.Dashboard) },
            onUpload = { screen = Screen.Upload(s.session) },
            onViewTimeline = {
                screen = Screen.FullTimeline(s.session, s.activePatientId)
            },
            onViewDocument = { docId, memberName ->
                screen = Screen.DocumentDetail(
                    session = s.session,
                    documentId = docId,
                    memberName = memberName,
                    returnToAskAi = true,
                    askAiPatientId = s.activePatientId,
                    askAiPersonName = s.personName,
                )
            },
        )
        is Screen.Share -> ShareScreen(
            api = api,
            sessionManager = sessionManager,
            initialMode = s.mode,
            patientId = s.patientId,
            onClose = { screen = Screen.Home(s.session, PatientTab.Dashboard) },
        )
        is Screen.AccessHistory -> AccessHistoryScreen(
            api = api,
            patientId = s.session.patientId,
            onBack = { screen = Screen.Home(s.session, PatientTab.Profile) },
        )
        is Screen.PersonalDetails -> PersonalDetailsScreen(
            api = api,
            onBack = { screen = Screen.Home(s.session, PatientTab.Profile) },
            onSaved = { newName ->
                screen = Screen.Home(
                    s.session.copy(name = newName),
                    PatientTab.Profile,
                )
            },
        )
        is Screen.DocumentDetail -> {
            DocumentDetailScreen(
                api = api,
                documentId = s.documentId,
                memberName = s.memberName,
                onBack = {
                    screen = if (s.returnToAskAi)
                        Screen.AskAi(s.session, s.askAiPatientId, s.askAiPersonName)
                    else
                        Screen.Home(s.session, PatientTab.Dashboard)
                },
                // "Ask questions about this record" → open patient-level AI chat
                onOpenChat = {
                    val personName = s.askAiPersonName.ifBlank { s.memberName }
                    screen = Screen.AskAi(
                        session = s.session,
                        activePatientId = s.askAiPatientId ?: activePatientId,
                        personName = personName,
                    )
                },
            )
        }
        is Screen.Upload -> PatientUploadScreen(
            api = api,
            family = globalFamily,  // already loaded — no timing gap
            onBack = { screen = Screen.Home(s.session, PatientTab.Dashboard) },
            onAddMember = {
                globalFamilyTrigger++ // refresh after adding
                screen = Screen.Home(s.session, PatientTab.Family)
            },
            initialActivePatientId = activePatientId,
            onSetActivePerson = { activePatientId = it },
        )
        is Screen.LabReportsList -> LabResultsScreen(
            api = api,
            onScanReport = { screen = Screen.Upload(s.session) },
            onManageFamily = { screen = Screen.Home(s.session, PatientTab.Family) },
            onBack = { screen = Screen.Home(s.session, PatientTab.Dashboard) },
            initialActivePatientId = activePatientId,
            onSetActivePerson = { activePatientId = it },
        )
        is Screen.PrescriptionsList -> MedicinesScreen(
            api = api,
            onScanReport = { screen = Screen.Upload(s.session) },
            onManageFamily = { screen = Screen.Home(s.session, PatientTab.Family) },
            onBack = { screen = Screen.Home(s.session, PatientTab.Dashboard) },
            initialActivePatientId = activePatientId,
            onSetActivePerson = { activePatientId = it },
        )
        is Screen.FullTimeline -> PatientTimelineScreen(
            api = api,
            onDocumentClick = { docId, memberName ->
                screen = Screen.DocumentDetail(s.session, docId, memberName)
            },
            onScanReport = { screen = Screen.Upload(s.session) },
            onManageFamily = { screen = Screen.Home(s.session, PatientTab.Family) },
            onBack = { screen = Screen.Home(s.session, PatientTab.Dashboard) },
            initialActivePatientId = activePatientId,
            onSetActivePerson = { activePatientId = it },
        )
    }
}

/**
 * Shell for the main app — hosts the 3-tab bottom nav and the ScanFAB.
 *
 * Receives [activePatientId] from [MainActivityContent] so the selection
 * persists across all screens and tabs. Family list is loaded once here
 * and passed down to avoid redundant API calls per-tab.
 */
@Composable
private fun PatientShell(
    currentTab: PatientTab,
    session: PatientSession,
    api: MedHistryApi,
    activePatientId: String?,
    onSetActivePerson: (String?) -> Unit,
    initialFamily: FamilyListResponse?,
    onFamilyChanged: () -> Unit,
    onTab: (PatientTab) -> Unit,
    onLogout: () -> Unit,
    onNavigate: (Screen) -> Unit,
) {
    // Seed from the globally-loaded family so it's immediately available;
    // keep a local copy so tabs can trigger refreshes without full recompose.
    var family by remember { mutableStateOf(initialFamily) }
    var familyLoadTrigger by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    var showLogoutDialog by remember { mutableStateOf(false) }

    // Sync if parent's global family updates (e.g. after adding a member)
    LaunchedEffect(initialFamily) { if (family == null) family = initialFamily }

    // Reload family when triggered (add/edit/remove member)
    LaunchedEffect(familyLoadTrigger) {
        if (familyLoadTrigger > 0) {
            runCatching { api.listFamily() }.onSuccess {
                family = it
                onFamilyChanged() // keep global copy in sync
            }
        }
    }

    BackHandler {
        if (currentTab != PatientTab.Dashboard) {
            onTab(PatientTab.Dashboard)
        } else {
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
                }) { Text("Yes, log out") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            },
        )
    }

    // Nav bar height so content + FAB clear the gesture strip on all devices
    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentBottomPad = 72.dp + navBarHeight   // tab bar (72) + gesture strip

    Box(modifier = Modifier.fillMaxSize()) {
        // Tab content — pad top for status bar, bottom for nav bar + gesture strip
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(bottom = contentBottomPad)) {
            when (currentTab) {
                PatientTab.Dashboard -> PatientHomeScreen(
                    api = api,
                    family = family,
                    activePatientId = activePatientId,
                    onSetActivePerson = { onSetActivePerson(it) },
                    onAskAi = {
                        val f = family
                        val personName = if (activePatientId == null) {
                            f?.primary?.name?.split(" ")?.first() ?: session.name.split(" ").first()
                        } else {
                            f?.dependents?.firstOrNull { it.id == activePatientId }
                                ?.name?.split(" ")?.first() ?: "them"
                        }
                        onNavigate(Screen.AskAi(session, activePatientId, personName))
                    },
                    onShareDoctor = {
                        onNavigate(Screen.Share(ShareMode.QR, activePatientId, session))
                    },
                    onViewTimeline = {
                        onNavigate(Screen.FullTimeline(session, activePatientId))
                    },
                    onViewAllPrescriptions = {
                        onNavigate(Screen.PrescriptionsList(session, activePatientId))
                    },
                    onScanReport = { onNavigate(Screen.Upload(session)) },
                    onManageFamily = { onTab(PatientTab.Family) },
                )
                PatientTab.Family -> FamilyMembersScreen(
                    api = api,
                    family = family,
                    onRefreshFamily = { familyLoadTrigger++ },
                    onSelectMember = { onSetActivePerson(it) },
                    onNavigateToDashboard = { onTab(PatientTab.Dashboard) },
                )
                PatientTab.Profile -> PatientProfileScreen(
                    name = session.name,
                    phone = session.phone,
                    onBack = { onTab(PatientTab.Dashboard) },
                    onPersonalDetails = { onNavigate(Screen.PersonalDetails(session)) },
                    onManageFamily = { onTab(PatientTab.Family) },
                    onAccessHistory = { onNavigate(Screen.AccessHistory(session)) },
                    onLogout = onLogout,
                    showBackArrow = false,
                )
            }
        }

        // Bottom nav bar
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            PatientBottomNav(current = currentTab, onSelect = onTab)
        }

        // ScanFAB — pinned bottom-right on all primary screens
        FloatingActionButton(
            onClick = { onNavigate(Screen.Upload(session)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 88.dp + navBarHeight),
            containerColor = MedHistryColors.Primary,
            contentColor = Color.White,
            shape = CircleShape,
        ) {
            Icon(
                Icons.Outlined.PhotoCamera,
                contentDescription = "Scan a report",
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

