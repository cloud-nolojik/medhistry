package com.medhistry.patient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medhistry.data.*
import kotlinx.serialization.json.jsonPrimitive

/**
 * Dashboard — the centrepiece of the redesigned patient app.
 *
 * Layout (top to bottom per the approved brief):
 *   1. Sticky header: avatar, name (tappable → person-switcher dropdown)
 *   2. "How [name] is doing" AI summary card
 *   3. Ask AI CTA (teal gradient, opens person-scoped chat)
 *   4. Share with Doctor CTA (outlined, opens QR share)
 *   5. Current Medications (top 3 medicines + "View all →" link)
 *   6. Latest Results (top 3 lab values + "View all →" link)
 *   7. Recent Activity (top 3 recent docs + "Full timeline →" link)
 *   8. All Records 2×2 grid
 *   9. Empty-state variant when zero records
 *
 * activePatientId (null = primary/owner) is lifted to PatientShell.
 */
@Composable
fun PatientHomeScreen(
    api: MedHistryApi,
    family: FamilyListResponse?,
    activePatientId: String?,
    onSetActivePerson: (String?) -> Unit,
    onAskAi: () -> Unit,
    onShareDoctor: () -> Unit,
    onViewTimeline: () -> Unit,
    onViewAllPrescriptions: () -> Unit,
    onScanReport: () -> Unit,
    onManageFamily: () -> Unit,
) {
    var healthSummary by remember { mutableStateOf<PatientHealthSummary?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showSwitcher by remember { mutableStateOf(false) }

    LaunchedEffect(activePatientId) {
        loading = true
        runCatching { api.getHealthSummary(activePatientId) }.onSuccess { healthSummary = it }
        loading = false
    }

    val activeName: String = when {
        activePatientId == null -> family?.primary?.name?.split(" ")?.first() ?: "you"
        else -> family?.dependents?.firstOrNull { it.id == activePatientId }
            ?.name?.split(" ")?.first() ?: "them"
    }
    val activeRelationship: String? = family?.dependents
        ?.firstOrNull { it.id == activePatientId }
        ?.relationship?.replaceFirstChar { it.uppercase() }
    val headerInitial = activeName.take(1).uppercase()

    val hs = healthSummary
    val hasRecords = hs != null && hs.totalDocuments > 0

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MedHistryColors.Background)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 96.dp),
        ) {
            // ── 1. Header ─────────────────────────────────────────────────────
            DashboardHeader(
                initial = headerInitial,
                name = activeName,
                subtitle = activeRelationship,
                onNameTap = { showSwitcher = true },
            )

            Spacer(Modifier.height(12.dp))

            // ── 2. AI Summary card ────────────────────────────────────────────
            if (loading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MedHistryColors.Primary)
                }
            } else if (!hasRecords) {
                    // ── Empty state — no records yet, keep it clean ───────────
                    EmptyDashboard(personName = activeName, onScan = onScanReport)
            } else {
                // ── Records exist — show full dashboard ───────────────────────

                // ── 3. Ask AI CTA ─────────────────────────────────────────────
                AskAiCard(personName = activeName, onClick = onAskAi)
                Spacer(Modifier.height(10.dp))

                // ── 4. Share with Doctor CTA ──────────────────────────────────
                ShareDoctorCard(personName = activeName, onClick = onShareDoctor)
                Spacer(Modifier.height(10.dp))

                // ── 5. Timeline CTA ───────────────────────────────────────────
                TimelineCard(onClick = onViewTimeline)
                Spacer(Modifier.height(20.dp))

                    // ── 5. Current Medications ────────────────────────────────
                    val meds = hs?.medications ?: emptyList()
                    if (meds.isNotEmpty()) {
                        DashSectionHeader(
                            title = "Current Medications",
                            linkLabel = "View all (${meds.size}) →",
                            onLink = onViewAllPrescriptions,
                        )
                        DashCard {
                            meds.take(3).forEachIndexed { i, med ->
                                if (i > 0) Divider(color = MedHistryColors.Border)
                                val medName = med["name"]?.jsonPrimitive?.content ?: "Medicine"
                                val purpose = med["purpose"]?.jsonPrimitive?.content?.takeIf { it != "null" }
                                val dosage = med["dosage"]?.jsonPrimitive?.content?.takeIf { it != "null" }
                                val timing = med["timing"]?.jsonPrimitive?.content?.takeIf { it != "null" }
                                val timingStr = listOfNotNull(dosage, timing).joinToString(" · ").ifBlank { null }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Outlined.Medication,
                                        contentDescription = null,
                                        tint = MedHistryColors.Primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(medName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MedHistryColors.TextPrimary)
                                        if (!purpose.isNullOrBlank()) {
                                            Text(purpose, fontSize = 12.sp, color = MedHistryColors.Primary, fontWeight = FontWeight.Medium)
                                        }
                                        if (!timingStr.isNullOrBlank()) {
                                            Text(timingStr, fontSize = 12.sp, color = MedHistryColors.TextSecondary)
                                        }
                                    }
                                    StatusChip("Active", Color(0xFF16A34A), Color(0xFFF0FDF4))
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

            }
        }

        // Person-switcher overlay
        if (showSwitcher && family != null) {
            PersonSwitcherDropdown(
                family = family,
                activePatientId = activePatientId,
                onSelect = { id ->
                    onSetActivePerson(id)
                    showSwitcher = false
                },
                onAddFamily = {
                    showSwitcher = false
                    onManageFamily()
                },
                onDismiss = { showSwitcher = false },
            )
        }
    }
}

// ── Header ─────────────────────────────────────────────────────────────────────

@Composable
private fun DashboardHeader(
    initial: String,
    name: String,
    subtitle: String?,
    onNameTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MedHistryColors.Surface)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(MedHistryColors.Primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(initial, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f).clickable { onNameTap() },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MedHistryColors.TextPrimary)
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "Switch person",
                    tint = MedHistryColors.TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = MedHistryColors.TextSecondary)
            }
        }
    }
}

// ── Person-switcher overlay ─────────────────────────────────────────────────────

@Composable
private fun PersonSwitcherDropdown(
    family: FamilyListResponse,
    activePatientId: String?,
    onSelect: (String?) -> Unit,
    onAddFamily: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable { onDismiss() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(MedHistryColors.Surface)
                .clickable(enabled = false) {} // absorb taps — don't propagate to dimmer
                .padding(bottom = 8.dp),
        ) {
            Text(
                "Viewing health for",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MedHistryColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            SwitcherRow(
                name = family.primary.name,
                subtitle = "You",
                selected = activePatientId == null,
                onClick = { onSelect(null) },
            )
            family.dependents.forEach { dep ->
                SwitcherRow(
                    name = dep.name,
                    subtitle = dep.relationship?.replaceFirstChar { it.uppercase() },
                    selected = activePatientId == dep.id,
                    onClick = { onSelect(dep.id) },
                )
            }
            Divider(color = MedHistryColors.Border, modifier = Modifier.padding(vertical = 4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAddFamily() }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(MedHistryColors.PrimaryLight),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.PersonAddAlt, contentDescription = null, tint = MedHistryColors.Primary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text("Add family member", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MedHistryColors.Primary)
            }
        }
    }
}

@Composable
private fun SwitcherRow(name: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MedHistryColors.PrimaryLight else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (selected) MedHistryColors.Primary else MedHistryColors.Border),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.take(1).uppercase(),
                color = if (selected) Color.White else MedHistryColors.TextSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MedHistryColors.TextPrimary)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, fontSize = 12.sp, color = MedHistryColors.TextSecondary)
            }
        }
        if (selected) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MedHistryColors.Primary))
        }
    }
}

// ── AI Summary card ─────────────────────────────────────────────────────────────


// ── Ask AI CTA ──────────────────────────────────────────────────────────────────

@Composable
private fun AskAiCard(personName: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(colors = listOf(Color(0xFF0D9488), Color(0xFF0891B2))),
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Ask about $personName's reports", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Get plain-language answers from all records", fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

// ── Share with Doctor CTA ───────────────────────────────────────────────────────

@Composable
private fun ShareDoctorCard(personName: String, onClick: () -> Unit) {
    val isOwner = personName == "You" || personName.isBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MedHistryColors.Surface)
            .border(1.5.dp, MedHistryColors.Primary, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.MedicalServices, contentDescription = null, tint = MedHistryColors.Primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (isOwner) "Share with Doctor" else "Share $personName's records with Doctor",
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MedHistryColors.Primary,
            )
            Text("Show QR for instant access — expires after the visit", fontSize = 12.sp, color = MedHistryColors.TextSecondary)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MedHistryColors.Primary, modifier = Modifier.size(20.dp))
    }
}

// ── Timeline CTA ───────────────────────────────────────────────────────────────

@Composable
private fun TimelineCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MedHistryColors.Surface)
            .border(1.dp, MedHistryColors.Border, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.History, contentDescription = null, tint = MedHistryColors.TextSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Timeline", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MedHistryColors.TextPrimary)
            Text("All records in chronological order", fontSize = 12.sp, color = MedHistryColors.TextSecondary)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MedHistryColors.TextSecondary, modifier = Modifier.size(20.dp))
    }
}

// ── Empty-state dashboard ───────────────────────────────────────────────────────

@Composable
private fun EmptyDashboard(personName: String, onScan: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MedHistryColors.Surface)
            .border(1.dp, MedHistryColors.Border, RoundedCornerShape(16.dp))
            .padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("📋", fontSize = 40.sp)
        Spacer(Modifier.height(16.dp))
        Text("No records yet", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MedHistryColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Scan a prescription or lab report and I'll lay out $personName's health in plain language.",
            fontSize = 14.sp,
            color = MedHistryColors.TextSecondary,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onScan,
            colors = ButtonDefaults.buttonColors(containerColor = MedHistryColors.Primary),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Scan your first report", fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Shared helpers ───────────────────────────────────────────────────────────────

@Composable
private fun DashSectionHeader(title: String, linkLabel: String?, onLink: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MedHistryColors.TextPrimary, modifier = Modifier.weight(1f))
        if (!linkLabel.isNullOrBlank() && onLink != null) {
            Text(linkLabel, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MedHistryColors.Primary, modifier = Modifier.clickable { onLink() })
        }
    }
}

@Composable
private fun DashCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MedHistryColors.Surface)
            .border(1.dp, MedHistryColors.Border, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 4.dp),
        content = content,
    )
}

@Composable
private fun StatusChip(label: String, textColor: Color, bgColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = textColor)
    }
}

