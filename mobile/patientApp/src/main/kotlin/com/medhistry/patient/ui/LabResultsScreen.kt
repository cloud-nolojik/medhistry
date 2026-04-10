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

private const val LAB_ALL_MEMBERS = "__all__"

/**
 * Lab Results tab — shows all lab results and vitals with color-coded status.
 */
@Composable
fun LabResultsScreen(api: MedHistryApi) {
    var family by remember { mutableStateOf<FamilyListResponse?>(null) }
    var activePatientId by remember { mutableStateOf<String?>(LAB_ALL_MEMBERS) } // default = All
    var healthSummary by remember { mutableStateOf<PatientHealthSummary?>(null) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { api.listFamily() }.onSuccess { family = it }
    }

    LaunchedEffect(activePatientId, family) {
        loading = true
        if (activePatientId == LAB_ALL_MEMBERS) {
            // Fetch for self + all dependents and merge
            val allLabs = mutableListOf<Map<String, JsonElement>>()
            val allVitals = mutableListOf<Map<String, JsonElement>>()
            runCatching { api.getHealthSummary(null) }.onSuccess { hs ->
                allLabs.addAll(hs.labResults)
                allVitals.addAll(hs.vitals)
            }
            family?.dependents?.forEach { dep ->
                runCatching { api.getHealthSummary(dep.id) }.onSuccess { hs ->
                    allLabs.addAll(hs.labResults)
                    allVitals.addAll(hs.vitals)
                }
            }
            healthSummary = PatientHealthSummary(
                patientId = "",
                totalDocuments = 0,
                medications = emptyList(),
                diagnoses = emptyList(),
                allergies = emptyList(),
                vitals = allVitals,
                labResults = allLabs,
                overallSummary = null,
            )
        } else if (activePatientId == null) {
            runCatching { api.getHealthSummary(null) }.onSuccess { healthSummary = it }
        } else {
            runCatching { api.getHealthSummary(activePatientId) }.onSuccess { healthSummary = it }
        }
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MedHistryColors.Background),
    ) {
        Text(
            "Lab Results",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MedHistryColors.TextPrimary,
            modifier = Modifier.padding(start = 24.dp, top = 18.dp, bottom = 4.dp),
        )
        Text(
            "Your test results and vitals",
            fontSize = 14.sp,
            color = MedHistryColors.TextSecondary,
            modifier = Modifier.padding(start = 24.dp, bottom = 12.dp),
        )

        // Family member chips
        family?.let { f ->
            if (f.dependents.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LabMemberChip("All", activePatientId == LAB_ALL_MEMBERS) { activePatientId = LAB_ALL_MEMBERS }
                    LabMemberChip("Me", activePatientId == null) { activePatientId = null }
                    f.dependents.forEach { dep ->
                        LabMemberChip(dep.name.split(" ").first(), activePatientId == dep.id) {
                            activePatientId = dep.id
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MedHistryColors.Primary)
            }
        } else {
            val labs = healthSummary?.labResults ?: emptyList()
            val vitals = healthSummary?.vitals ?: emptyList()

            if (labs.isEmpty() && vitals.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("\uD83E\uDDEA", fontSize = 48.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("No lab results yet", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MedHistryColors.TextPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Upload a lab report and your results will appear here with easy-to-understand explanations.",
                        fontSize = 14.sp,
                        color = MedHistryColors.TextSecondary,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                    )
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
                        // Date header
                        val dateLabel = if (date == "unknown") "Other Results"
                            else "\uD83D\uDCC5 ${labFormatDate(date)}"
                        Text(
                            dateLabel,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MedHistryColors.TextPrimary,
                            modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 6.dp),
                        )

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
                Text("Reference: $ref", fontSize = 11.sp, color = MedHistryColors.TextLight)
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

@Composable
private fun LabMemberChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) MedHistryColors.Primary else MedHistryColors.Surface)
            .border(1.dp, if (selected) MedHistryColors.Primary else MedHistryColors.Border, RoundedCornerShape(20.dp))
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
