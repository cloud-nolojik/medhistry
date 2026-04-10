package com.medhistry.patient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medhistry.data.DocumentOut
import com.medhistry.data.FamilyListResponse
import com.medhistry.data.MedHistryApi
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray

/**
 * "Your Timeline" — chronological list of uploaded documents grouped by document date.
 *
 * Uses `document_date` (the date printed on the actual medical document) for grouping
 * and ordering. Falls back to `created_at` (upload date) if the document date wasn't
 * extracted. Shows the family member name on each entry.
 */
@Composable
fun PatientTimelineScreen(
    api: MedHistryApi,
    onDocumentClick: (documentId: String, memberName: String) -> Unit = { _, _ -> },
) {
    var documents by remember { mutableStateOf<List<DocumentOut>>(emptyList()) }
    var family by remember { mutableStateOf<FamilyListResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            runCatching { api.listFamily() }.onSuccess { family = it }
            runCatching { api.listDocuments(includeFamily = true) }
                .onSuccess { documents = it.documents }
            loading = false
        }
    }

    // Helper to resolve member name from patient_id
    fun memberNameFor(patientId: String): String {
        val f = family ?: return ""
        if (patientId == f.primary.id) return "Self"
        return f.dependents.find { it.id == patientId }?.name ?: ""
    }

    // Helper: extract displayable date from document
    // Prefer documentDate (the actual date on the medical doc), fallback to createdAt
    fun displayDate(doc: DocumentOut): String {
        return (doc.documentDate ?: doc.createdAt).take(10) // YYYY-MM-DD
    }

    // Format YYYY-MM-DD → human readable like "17 July 2025"
    fun formatDateHeader(dateStr: String): String {
        return try {
            val parts = dateStr.split("-")
            if (parts.size == 3) {
                val months = listOf(
                    "January", "February", "March", "April", "May", "June",
                    "July", "August", "September", "October", "November", "December"
                )
                val day = parts[2].trimStart('0')
                val month = months.getOrElse(parts[1].toInt() - 1) { parts[1] }
                val year = parts[0]
                "$day $month $year"
            } else dateStr
        } catch (_: Exception) { dateStr }
    }

    // Filter: null = "All", else patient_id
    var filterPatientId by remember { mutableStateOf<String?>(null) }

    // Apply filter, then sort and group
    val filteredDocs = remember(documents, filterPatientId) {
        val list = if (filterPatientId == null) documents
        else documents.filter { it.patientId == filterPatientId }
        list.sortedByDescending { it.documentDate ?: it.createdAt }
    }
    val grouped = remember(filteredDocs) {
        filteredDocs.groupBy { (it.documentDate ?: it.createdAt).take(10) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MedHistryColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            Text("Your Timeline", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MedHistryColors.TextPrimary)
            Text("Complete medical history", fontSize = 14.sp, color = MedHistryColors.TextSecondary)
        }

        // Family member filter chips
        family?.let { f ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip("All", filterPatientId == null) { filterPatientId = null }
                FilterChip("Me", filterPatientId == f.primary.id) { filterPatientId = f.primary.id }
                f.dependents.forEach { dep ->
                    FilterChip(dep.name.split(" ").first(), filterPatientId == dep.id) {
                        filterPatientId = dep.id
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MedHistryColors.Primary)
            }
        } else if (filteredDocs.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("\uD83D\uDCC5", fontSize = 48.sp)
                Spacer(Modifier.height(16.dp))
                Text("No timeline entries yet", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MedHistryColors.TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Upload a prescription or lab report to start building your timeline.",
                    fontSize = 14.sp,
                    color = MedHistryColors.TextSecondary,
                )
            }
        } else {
            grouped.entries.forEachIndexed { groupIdx, (dateKey, docs) ->
                // Date section header
                DateHeader(formatDateHeader(dateKey))

                docs.forEachIndexed { docIdx, doc ->
                    val isLastInGroup = docIdx == docs.lastIndex
                    val isLastOverall = groupIdx == grouped.size - 1 && isLastInGroup
                    TimelineRow(
                        doc = doc,
                        memberName = memberNameFor(doc.patientId),
                        showConnector = !isLastOverall,
                        onClick = { onDocumentClick(doc.id, memberNameFor(doc.patientId)) },
                    )
                }

                // Gap between date groups
                if (groupIdx < grouped.size - 1) {
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun DateHeader(date: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .height(1.dp)
                .weight(1f)
                .background(MedHistryColors.Border),
        )
        Text(
            date,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MedHistryColors.TextLight,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Box(
            modifier = Modifier
                .height(1.dp)
                .weight(1f)
                .background(MedHistryColors.Border),
        )
    }
}

@Composable
private fun TimelineRow(doc: DocumentOut, memberName: String, showConnector: Boolean, onClick: () -> Unit = {}) {
    val (icon, iconBg) = when (doc.docType) {
        "prescription" -> "\uD83D\uDC8A" to Color(0xFFEBF5FF)
        "lab_report" -> "\uD83E\uDDEA" to Color(0xFFF0FDF4)
        "discharge_summary" -> "\uD83C\uDFE5" to Color(0xFFFEF3C7)
        "imaging_report" -> "\uD83D\uDCF7" to Color(0xFFFDF2F8)
        else -> "\uD83D\uDCC4" to Color(0xFFF3F4F6)
    }
    val title = doc.docType?.replace("_", " ")?.replaceFirstChar { it.uppercase() } ?: doc.filename
    val statusLine = when (doc.processingStatus) {
        "completed" -> doc.aiSummary ?: "Analysis complete"
        "processing", "pending" -> "AI is analyzing this document..."
        "failed" -> "Processing failed"
        else -> doc.filename
    }

    // Build content tags from extracted_data — shows what types of data are inside
    val contentTags = buildContentTags(doc)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 2.dp),
    ) {
        // Left: icon + vertical connector
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) { Text(icon, fontSize = 18.sp) }
            if (showConnector) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(44.dp)
                        .background(MedHistryColors.Border),
                )
            }
        }
        Spacer(Modifier.width(16.dp))

        // Right: doc info
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MedHistryColors.TextPrimary)
                if (memberName.isNotEmpty()) {
                    Text(
                        "  \u2022  ",
                        fontSize = 12.sp,
                        color = MedHistryColors.TextLight,
                    )
                    Text(
                        memberName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MedHistryColors.Primary,
                    )
                }
            }
            if (doc.hospitalName != null || doc.doctorName != null) {
                val meta = listOfNotNull(doc.hospitalName, doc.doctorName).joinToString(" \u2022 ")
                Text(meta, fontSize = 12.sp, color = MedHistryColors.TextLight)
            }

            // Content tags — what data is inside this document
            if (contentTags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    contentTags.forEach { (emoji, label, color) ->
                        ContentTag(emoji = emoji, label = label, color = color)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                statusLine,
                fontSize = 13.sp,
                color = if (doc.processingStatus == "failed") MedHistryColors.Danger else MedHistryColors.TextSecondary,
                lineHeight = 19.sp,
                maxLines = 2,
            )
        }
    }
}

/** Inspect extracted_data to determine what content types this document contains. */
private fun buildContentTags(doc: DocumentOut): List<Triple<String, String, Color>> {
    val data = doc.extractedData ?: return emptyList()
    val tags = mutableListOf<Triple<String, String, Color>>()

    try {
        val meds = data["medications"]
        if (meds is JsonArray && meds.isNotEmpty()) {
            tags.add(Triple("\uD83D\uDC8A", "${meds.size} medicines", Color(0xFF2563EB)))
        }
    } catch (_: Exception) {}

    try {
        val labs = data["lab_results"]
        if (labs is JsonArray && labs.isNotEmpty()) {
            tags.add(Triple("\uD83E\uDDEA", "${labs.size} lab results", Color(0xFF16A34A)))
        }
    } catch (_: Exception) {}

    try {
        val vitals = data["vitals"]
        if (vitals is JsonArray && vitals.isNotEmpty()) {
            tags.add(Triple("\u2764\uFE0F", "${vitals.size} vitals", Color(0xFFDC2626)))
        }
    } catch (_: Exception) {}

    try {
        val diagnoses = data["diagnoses"]
        if (diagnoses is JsonArray && diagnoses.isNotEmpty()) {
            tags.add(Triple("\uD83E\uDE7A", "${diagnoses.size} conditions", Color(0xFFD97706)))
        }
    } catch (_: Exception) {}

    return tags
}

@Composable
private fun ContentTag(emoji: String, label: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 11.sp)
        Spacer(Modifier.width(3.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
