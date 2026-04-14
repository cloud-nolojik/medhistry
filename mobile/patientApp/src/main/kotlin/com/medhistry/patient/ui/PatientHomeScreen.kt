package com.medhistry.patient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medhistry.data.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive

/**
 * Patient home dashboard. Matches the prototype:
 *   - Greeting header + profile avatar
 *   - Active Conditions gradient card
 *   - Pending alert strip
 *   - Quick actions (Upload + Show QR)
 *   - Current Medications
 *   - Recent Lab Results
 *   - Last Visit
 *   - "Sharing as" chips (self / dependents / + Family) to pick active profile
 *
 * Condition/med/lab/visit data is currently seeded with realistic placeholders;
 * wire these to backend endpoints as they come online.
 */
@Composable
fun PatientHomeScreen(
    api: MedHistryApi,
    onUpload: () -> Unit,
    onShareQR: (patientId: String?) -> Unit,
    onShareCode: (patientId: String?) -> Unit,
    onManageFamily: () -> Unit,
    onProfile: () -> Unit,
    onNavigateToMedicines: () -> Unit = {},
    onNavigateToLabResults: () -> Unit = {},
) {
    var family by remember { mutableStateOf<FamilyListResponse?>(null) }
    var activePatientId by remember { mutableStateOf<String?>(null) } // null = self
    var healthSummary by remember { mutableStateOf<PatientHealthSummary?>(null) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            runCatching { api.listFamily() }.onSuccess { family = it }
            runCatching { api.getHealthSummary() }.onSuccess { healthSummary = it }
            loading = false
        }
    }

    // Reload health summary when active patient changes
    LaunchedEffect(activePatientId) {
        loading = true
        runCatching { api.getHealthSummary(activePatientId) }.onSuccess { healthSummary = it }
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MedHistryColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        GreetingHeader(
            name = family?.primary?.name ?: "there",
            onProfile = onProfile,
        )

        // Sharing-as chip row
        family?.let { f ->
            SharingAsRow(
                primary = f.primary,
                dependents = f.dependents,
                activeId = activePatientId,
                onSelect = { activePatientId = it },
                onAddFamily = onManageFamily,
            )
            Spacer(Modifier.height(16.dp))
        }

        // Quick actions — Upload + the two ways to share with a doctor.
        // The "Share Code" tile lets the doctor type a 6-digit code when the
        // QR camera path doesn't work (poor light, phone consult, etc.).
        //
        // When a dependent is the active "Sharing as" choice, the share button
        // labels echo their first name so the user can't accidentally share
        // their own records.
        val activeName: String? = activePatientId?.let { id ->
            family?.dependents?.firstOrNull { it.id == id }?.name?.split(" ")?.firstOrNull()
        }
        val qrLabel = activeName?.let { "Show ${it}'s QR" } ?: "Show QR"
        val codeLabel = activeName?.let { "Share ${it}'s Code" } ?: "Share Code"

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuickAction(
                label = "Upload Record",
                emoji = "\uD83D\uDCF7",
                bg = Color(0xFFEBF5FF),
                modifier = Modifier.weight(1f),
                onClick = onUpload,
            )
            QuickAction(
                label = qrLabel,
                emoji = "\uD83D\uDCF2",
                bg = Color(0xFFF0FDF4),
                modifier = Modifier.weight(1f),
                onClick = { onShareQR(activePatientId) },
            )
            QuickAction(
                label = codeLabel,
                emoji = "\uD83D\uDD22",
                bg = Color(0xFFFFF7ED),
                modifier = Modifier.weight(1f),
                onClick = { onShareCode(activePatientId) },
            )
        }

        Spacer(Modifier.height(24.dp))

        val hs = healthSummary
        if (loading) {
            Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MedHistryColors.Primary)
            }
        } else if (hs == null || hs.totalDocuments == 0) {
            EmptyStateCard(
                icon = "\uD83D\uDCCB",
                title = "No records yet",
                subtitle = "Upload a prescription or lab report to see your health summary here.",
            )
        } else {
            // Patient-friendly summary with status badge
            val displaySummary = hs.patientSummary ?: hs.overallSummary
            if (displaySummary != null) {
                SectionHeader("How You're Doing")

                // Status badge
                hs.overallStatus?.let { status ->
                    val (statusColor, statusBg, statusEmoji) = when (status) {
                        "all_good" -> Triple(Color(0xFF16A34A), Color(0xFFF0FDF4), "\u2705")
                        "critical" -> Triple(Color(0xFFDC2626), Color(0xFFFEF2F2), "\u26A0\uFE0F")
                        else -> Triple(Color(0xFFD97706), Color(0xFFFFFBEB), "\uD83D\uDCA1") // attention_needed
                    }
                    val statusMsg = hs.overallStatusMessage ?: when (status) {
                        "all_good" -> "Everything looks good!"
                        "critical" -> "Some results need attention"
                        else -> "Mostly fine, one thing to note"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(statusBg)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(statusEmoji, fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            statusMsg,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }

                WhiteCard {
                    Text(displaySummary, fontSize = 14.sp, color = MedHistryColors.TextSecondary, lineHeight = 20.sp)
                }
                Spacer(Modifier.height(16.dp))
            }

            // Medications — show top 3 with link to Medicines tab
            if (hs.medications.isNotEmpty()) {
                SectionHeader("Today's Medicines")
                WhiteCard {
                    hs.medications.take(3).forEachIndexed { i, med ->
                        if (i > 0) Divider(color = MedHistryColors.Border)
                        val name = med["name"]?.jsonPrimitive?.content ?: "Unknown"
                        val dosage = med["dosage"]?.jsonPrimitive?.content
                        val purpose = med["purpose"]?.jsonPrimitive?.content
                        Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("\uD83D\uDC8A", fontSize = 18.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    buildString {
                                        append(name)
                                        if (!dosage.isNullOrEmpty() && dosage != "null") append(" $dosage")
                                    },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MedHistryColors.TextPrimary,
                                )
                                if (!purpose.isNullOrEmpty() && purpose != "null") {
                                    Text(purpose, fontSize = 12.sp, color = MedHistryColors.Primary)
                                }
                            }
                        }
                    }
                    if (hs.medications.size > 3) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "View all ${hs.medications.size} medicines →",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MedHistryColors.Primary,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable { onNavigateToMedicines() },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Lab Results — top 3, hide null refs
            if (hs.labResults.isNotEmpty()) {
                SectionHeader("Recent Lab Results")
                WhiteCard {
                    hs.labResults.take(3).forEachIndexed { i, lab ->
                        if (i > 0) Divider(color = MedHistryColors.Border)
                        val name = lab["test_name"]?.jsonPrimitive?.content ?: lab["name"]?.jsonPrimitive?.content ?: "Test"
                        val value = lab["value"]?.jsonPrimitive?.content ?: ""
                        val unit = lab["unit"]?.jsonPrimitive?.content ?: ""
                        val ref = lab["reference_range"]?.jsonPrimitive?.content
                        val status = lab["status"]?.jsonPrimitive?.content
                        val explanation = lab["patient_explanation"]?.jsonPrimitive?.content

                        val valueColor = when (status) {
                            "normal" -> Color(0xFF16A34A)
                            "high", "low" -> Color(0xFFD97706)
                            "critical" -> Color(0xFFDC2626)
                            else -> MedHistryColors.Primary
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MedHistryColors.TextPrimary)
                                if (!ref.isNullOrEmpty() && ref != "null") {
                                    Text("Ref: $ref", fontSize = 11.sp, color = MedHistryColors.TextLight)
                                }
                                if (!explanation.isNullOrEmpty() && explanation != "null") {
                                    Text(explanation, fontSize = 12.sp, color = MedHistryColors.TextSecondary, lineHeight = 16.sp)
                                }
                            }
                            Text("$value $unit", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor)
                        }
                    }
                    if (hs.labResults.size > 3) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "View all ${hs.labResults.size} results →",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MedHistryColors.Primary,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable { onNavigateToLabResults() },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Allergies — important safety info, keep visible
            if (hs.allergies.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFEF2F2))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("\u26A0\uFE0F", fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Allergies: ${hs.allergies.joinToString(", ")}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MedHistryColors.Danger,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // Document count
            Text(
                "${hs.totalDocuments} document${if (hs.totalDocuments != 1) "s" else ""} uploaded",
                fontSize = 12.sp,
                color = MedHistryColors.TextLight,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}

@Composable
private fun GreetingHeader(name: String, onProfile: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                "Hello, ${name.split(" ").first()} \uD83D\uDC4B",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MedHistryColors.TextPrimary,
            )
            Text(
                "Here's your health summary",
                fontSize = 14.sp,
                color = MedHistryColors.TextSecondary,
            )
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MedHistryColors.Primary)
                .clickable { onProfile() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.take(1).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
        }
    }
}

@Composable
private fun SharingAsRow(
    primary: PatientProfile,
    dependents: List<DependentOut>,
    activeId: String?,
    onSelect: (String?) -> Unit,
    onAddFamily: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Text(
            "SHARING AS",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MedHistryColors.TextLight,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProfileChip(
                label = "Me",
                selected = activeId == null,
                onClick = { onSelect(null) },
            )
            dependents.forEach { dep ->
                ProfileChip(
                    label = dep.name.split(" ").first(),
                    selected = activeId == dep.id,
                    onClick = { onSelect(dep.id) },
                )
            }
            AddFamilyButton(onClick = onAddFamily)
        }
    }
}

@Composable
private fun ProfileChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) MedHistryColors.Primary else MedHistryColors.Surface)
            .border(
                1.dp,
                if (selected) MedHistryColors.Primary else MedHistryColors.Border,
                RoundedCornerShape(20.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else MedHistryColors.TextPrimary,
        )
    }
}

@Composable
private fun AddFamilyButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MedHistryColors.PrimaryLight)
            .border(
                1.dp,
                MedHistryColors.Primary,
                RoundedCornerShape(20.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            "+ Add",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MedHistryColors.Primary,
        )
    }
}

@Composable
private fun EmptyStateCard(icon: String, title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MedHistryColors.Surface)
            .border(1.dp, MedHistryColors.Border, RoundedCornerShape(16.dp))
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, fontSize = 48.sp)
        Spacer(Modifier.height(16.dp))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MedHistryColors.TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            fontSize = 14.sp,
            color = MedHistryColors.TextSecondary,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = MedHistryColors.TextPrimary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
    )
}

@Composable
private fun WhiteCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MedHistryColors.Surface)
            .border(1.dp, MedHistryColors.Border, RoundedCornerShape(16.dp))
            .padding(20.dp),
        content = content,
    )
}

@Composable
private fun QuickAction(
    label: String,
    emoji: String,
    bg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MedHistryColors.Surface)
            .border(1.dp, MedHistryColors.Border, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, fontSize = 22.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MedHistryColors.TextPrimary,
        )
    }
}

