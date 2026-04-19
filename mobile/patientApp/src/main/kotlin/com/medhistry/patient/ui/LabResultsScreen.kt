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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medhistry.data.FamilyListResponse
import com.medhistry.data.MedHistryApi
import com.medhistry.data.PatientHealthSummary
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Lab Results tab — shows all lab results and vitals with color-coded status.
 */
@Composable
fun LabResultsScreen(
    api: MedHistryApi,
    onScanReport: () -> Unit = {},
    onManageFamily: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    initialActivePatientId: String? = null,
    onSetActivePerson: ((String?) -> Unit)? = null,
) {
    var family by remember { mutableStateOf<FamilyListResponse?>(null) }
    // Initialise from the Dashboard's active person so the sub-screen opens
    // pre-scoped to the right family member.
    var activePatientId by remember { mutableStateOf(initialActivePatientId) }
    var healthSummary by remember { mutableStateOf<PatientHealthSummary?>(null) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { api.listFamily() }.onSuccess { family = it }
    }

    LaunchedEffect(activePatientId, family) {
        loading = true
        runCatching { api.getHealthSummary(activePatientId) }.onSuccess { healthSummary = it }
        loading = false
    }

    // Resolve display name for subtitle
    val memberName: String = when {
        activePatientId == null -> family?.primary?.name?.split(" ")?.first() ?: ""
        else -> family?.dependents?.firstOrNull { it.id == activePatientId }?.name?.split(" ")?.first() ?: ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MedHistryColors.Background),
    ) {
        // Header row with optional back arrow (when used as sub-screen)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = if (onBack != null) 16.dp else 24.dp, end = 24.dp, top = 18.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MedHistryColors.TextPrimary,
                    modifier = Modifier.size(24.dp).clickable { onBack() },
                )
                Spacer(Modifier.width(12.dp))
            }
            Column {
                val isOwner = activePatientId == null
                Text(
                    if (isOwner) "My lab reports" else "$memberName's lab reports",
                    fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MedHistryColors.TextPrimary,
                )
                Text(
                    if (isOwner) "Your test results in plain language" else "$memberName's test results in plain language",
                    fontSize = 13.sp, color = MedHistryColors.TextSecondary,
                )
            }
        }

        // Family member picker. Adapts to family size (chips for ≤4,
        // bottom-sheet for 5+). Null activePatientId means "me".
        MemberPicker(
            family = family,
            selectedId = activePatientId ?: family?.primary?.id,
            onSelect = { id ->
                val newId = if (id == family?.primary?.id) null else id
                activePatientId = newId
                onSetActivePerson?.invoke(newId)
            },
            onAddFamilyMember = onManageFamily,
        )

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MedHistryColors.Primary)
            }
        } else {
            val labs = healthSummary?.labResults ?: emptyList()
            val vitals = healthSummary?.vitals ?: emptyList()

            if (labs.isEmpty() && vitals.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Science,
                        contentDescription = null,
                        tint = MedHistryColors.Primary,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (activePatientId == null) "No lab reports yet" else "No lab reports for $memberName yet",
                        fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MedHistryColors.TextPrimary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (activePatientId == null)
                            "Scan a lab report and we'll lay out your results in plain language with colour-coded highlights."
                        else
                            "Scan a lab report for $memberName and we'll explain the results in plain language.",
                        fontSize = 14.sp,
                        color = MedHistryColors.TextSecondary,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = onScanReport,
                        colors = ButtonDefaults.buttonColors(containerColor = MedHistryColors.Primary),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Scan a lab report", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                // Merge all items (vitals + labs) and group by report_date,
                // most recent first. Items without a date go last.
                val allItems = buildList {
                    vitals.forEach { add("vital" to it) }
                    labs.forEach { add("lab" to it) }
                }
                val grouped = allItems.groupBy { (_, item) ->
                    item["report_date"]?.jsonPrimitive?.content ?: "unknown"
                }.toSortedMap(compareByDescending { if (it == "unknown") "" else it })

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
                ) {
                    grouped.forEach { (date, items) ->
                        // Date header — calendar glyph replaced with a
                        // Material Symbols icon so it renders consistently
                        // across OEM emoji fonts.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 6.dp),
                        ) {
                            if (date != "unknown") {
                                Icon(
                                    imageVector = Icons.Outlined.CalendarMonth,
                                    contentDescription = null,
                                    tint = MedHistryColors.TextSecondary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                if (date == "unknown") "Other Results" else labFormatDate(date),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MedHistryColors.TextPrimary,
                            )
                        }

                        // Split into vitals and labs within this date
                        val dateVitals = items.filter { it.first == "vital" }.map { it.second }
                        val dateLabs = items.filter { it.first == "lab" }.map { it.second }

                        if (dateVitals.isNotEmpty()) {
                            Text(
                                "Vitals",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MedHistryColors.Primary,
                                modifier = Modifier.padding(start = 24.dp, top = 6.dp, bottom = 4.dp),
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MedHistryColors.Surface)
                                    .border(1.dp, MedHistryColors.Border, RoundedCornerShape(16.dp))
                                    .padding(vertical = 4.dp),
                            ) {
                                dateVitals.forEachIndexed { i, vital ->
                                    if (i > 0) Divider(color = MedHistryColors.Border, modifier = Modifier.padding(horizontal = 16.dp))
                                    LabResultRow(vital)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        if (dateLabs.isNotEmpty()) {
                            Text(
                                "Test Results",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MedHistryColors.Primary,
                                modifier = Modifier.padding(start = 24.dp, top = 6.dp, bottom = 4.dp),
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MedHistryColors.Surface)
                                    .border(1.dp, MedHistryColors.Border, RoundedCornerShape(16.dp))
                                    .padding(vertical = 4.dp),
                            ) {
                                dateLabs.forEachIndexed { i, lab ->
                                    if (i > 0) Divider(color = MedHistryColors.Border, modifier = Modifier.padding(horizontal = 16.dp))
                                    LabResultRow(lab)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LabResultRow(item: Map<String, JsonElement>) {
    val name = item["test_name"]?.jsonPrimitive?.content
        ?: item["name"]?.jsonPrimitive?.content ?: "Test"
    val value = item["value"]?.jsonPrimitive?.content ?: ""
    val unit = item["unit"]?.jsonPrimitive?.content ?: ""
    val ref = item["reference_range"]?.jsonPrimitive?.content
    val status = item["status"]?.jsonPrimitive?.content
    val explanation = item["patient_explanation"]?.jsonPrimitive?.content

    val (statusColor, statusBg, statusDot) = when (status) {
        "normal" -> Triple(Color(0xFF16A34A), Color(0xFFF0FDF4), Color(0xFF16A34A))
        "high", "low" -> Triple(Color(0xFFD97706), Color(0xFFFFFBEB), Color(0xFFD97706))
        "critical" -> Triple(Color(0xFFDC2626), Color(0xFFFEF2F2), Color(0xFFDC2626))
        else -> Triple(MedHistryColors.TextPrimary, Color.Transparent, MedHistryColors.TextLight)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(statusDot),
        )
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MedHistryColors.TextPrimary)
            if (!ref.isNullOrEmpty() && ref != "null") {
                Text("Normal range: $ref", fontSize = 11.sp, color = MedHistryColors.TextLight)
            }
            if (!explanation.isNullOrEmpty() && explanation != "null") {
                Spacer(Modifier.height(3.dp))
                Text(explanation, fontSize = 12.sp, color = MedHistryColors.TextSecondary, lineHeight = 16.sp)
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                "$value $unit",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor,
            )
            if (status != null && status != "null" && status != "normal") {
                Text(
                    status.replaceFirstChar { it.uppercase() },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusBg)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
    }
}

/** Format "2025-04-11" → "Apr 11, 2025" */
private fun labFormatDate(isoDate: String): String {
    return try {
        val date = LocalDate.parse(isoDate, DateTimeFormatter.ISO_LOCAL_DATE)
        date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    } catch (_: Exception) {
        isoDate
    }
}

