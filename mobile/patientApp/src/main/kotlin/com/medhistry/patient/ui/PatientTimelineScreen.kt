package com.medhistry.patient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    onScanReport: () -> Unit = {},
    onManageFamily: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    initialActivePatientId: String? = null,
    onSetActivePerson: ((String?) -> Unit)? = null,
) {
    var documents by remember { mutableStateOf<List<DocumentOut>>(emptyList()) }
    var family by remember { mutableStateOf<FamilyListResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    // Reload trigger — bumped after a successful delete to re-run the
    // LaunchedEffect below and refresh the list.
    var reloadTrigger by remember { mutableStateOf(0) }
    // Document pending delete confirmation.
    var pendingDeleteDoc by remember { mutableStateOf<DocumentOut?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reloadTrigger) {
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

    // Active filter — initialised from Dashboard's active person if provided,
    // otherwise falls back to the primary once family loads.
    var filterPatientId by remember { mutableStateOf(initialActivePatientId) }

    LaunchedEffect(family) {
        val f = family ?: return@LaunchedEffect
        if (filterPatientId == null) filterPatientId = f.primary.id
    }

    // Apply filter, then sort and group. The null branch only runs for the
    // brief pre-init window before family loads.
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
        // Header row — with optional back arrow when used as a sub-screen
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (onBack != null) 16.dp else 24.dp, end = 24.dp, top = 20.dp, bottom = 4.dp),
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
                Text("Timeline", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MedHistryColors.TextPrimary)
                Text("Complete medical history", fontSize = 14.sp, color = MedHistryColors.TextSecondary)
            }
        }

        // Inline delete error — rendered in the header strip so users see
        // failures without losing their scroll position.
        deleteError?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFEF2F2))
                    .padding(horizontal = 24.dp, vertical = 10.dp),
            ) {
                Text(
                    "Delete failed: $msg",
                    fontSize = 13.sp,
                    color = MedHistryColors.Danger,
                )
            }
        }

        // Family member picker. Adapts to family size (chips for ≤4,
        // bottom-sheet for 5+). filterPatientId is the concrete selected
        // id (set to f.primary.id once family loads), so we pass it through
        // directly.
        MemberPicker(
            family = family,
            selectedId = filterPatientId,
            onSelect = { id ->
                filterPatientId = id
                // Normalise: primary → null so global state uses the same convention
                val normalized = if (id == family?.primary?.id) null else id
                onSetActivePerson?.invoke(normalized)
            },
            onAddFamilyMember = onManageFamily,
        )

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
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    tint = MedHistryColors.TextLight,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text("No records yet", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MedHistryColors.TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Scan a prescription or lab report to start building your records.",
                    fontSize = 14.sp,
                    color = MedHistryColors.TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onScanReport,
                    colors = ButtonDefaults.buttonColors(containerColor = MedHistryColors.Primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(44.dp),
                ) {
                    Text(
                        "Scan your first report",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                }
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
                        onDelete = { pendingDeleteDoc = doc },
                    )
                }

                // Gap between date groups
                if (groupIdx < grouped.size - 1) {
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    // --- Delete confirmation dialog ---
    pendingDeleteDoc?.let { doc ->
        AlertDialog(
            onDismissRequest = { if (!deleting) pendingDeleteDoc = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = MedHistryColors.Surface,
            title = { Text("Delete document?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will permanently delete \"${doc.docType?.replace("_", " ")?.replaceFirstChar { it.uppercase() } ?: doc.filename}\" and update the health summary.",
                    color = MedHistryColors.TextSecondary,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val docToDelete = doc
                        deleting = true
                        deleteError = null
                        scope.launch {
                            try {
                                api.deleteDocument(docToDelete.id)
                                pendingDeleteDoc = null
                                reloadTrigger++
                            } catch (e: Exception) {
                                deleteError = e.message ?: "Unknown error"
                                pendingDeleteDoc = null
                            } finally {
                                deleting = false
                            }
                        }
                    },
                    enabled = !deleting,
                    colors = ButtonDefaults.buttonColors(containerColor = MedHistryColors.Danger),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        if (deleting) "Deleting…" else "Delete",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDeleteDoc = null },
                    enabled = !deleting,
                ) {
                    Text("Cancel", color = MedHistryColors.TextSecondary)
                }
            },
        )
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
private fun TimelineRow(
    doc: DocumentOut,
    memberName: String,
    showConnector: Boolean,
    onClick: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    val (icon, iconBg, iconTint) = when (doc.docType) {
        "prescription" -> Triple(Icons.Outlined.Medication, Color(0xFFEBF5FF), Color(0xFF2563EB))
        "lab_report" -> Triple(Icons.Outlined.Science, Color(0xFFF0FDF4), Color(0xFF16A34A))
        "discharge_summary" -> Triple(Icons.Outlined.LocalHospital, Color(0xFFFEF3C7), Color(0xFFD97706))
        "imaging_report" -> Triple(Icons.Outlined.PhotoCamera, Color(0xFFFDF2F8), Color(0xFFDB2777))
        else -> Triple(Icons.Outlined.Description, Color(0xFFF3F4F6), MedHistryColors.TextSecondary)
    }
    val title = doc.docType?.replace("_", " ")?.replaceFirstChar { it.uppercase() } ?: doc.filename
    val statusLine = when (doc.processingStatus) {
        "completed" -> doc.aiSummary ?: "Ready"
        "processing", "pending" -> "Organizing your records…"
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
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
            }
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
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MedHistryColors.TextPrimary,
                    modifier = Modifier.weight(1f, fill = false),
                )
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
                Spacer(Modifier.weight(1f))
                // Delete icon — stops click propagation so tapping trash
                // doesn't also open the doc detail screen.
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete",
                    tint = MedHistryColors.TextLight,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onDelete() }
                        .padding(6.dp)
                        .size(18.dp),
                )
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
                    contentTags.forEach { tag ->
                        ContentTag(icon = tag.icon, label = tag.label, color = tag.color)
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

/** Small holder describing one chip shown under a document row. */
private data class ContentTagData(val icon: ImageVector, val label: String, val color: Color)

/** Inspect extracted_data to determine what content types this document contains. */
private fun buildContentTags(doc: DocumentOut): List<ContentTagData> {
    val data = doc.extractedData ?: return emptyList()
    val tags = mutableListOf<ContentTagData>()

    try {
        val meds = data["medications"]
        if (meds is JsonArray && meds.isNotEmpty()) {
            tags.add(ContentTagData(Icons.Outlined.Medication, "${meds.size} medicines", Color(0xFF2563EB)))
        }
    } catch (_: Exception) {}

    try {
        val labs = data["lab_results"]
        if (labs is JsonArray && labs.isNotEmpty()) {
            tags.add(ContentTagData(Icons.Outlined.Science, "${labs.size} lab results", Color(0xFF16A34A)))
        }
    } catch (_: Exception) {}

    try {
        val vitals = data["vitals"]
        if (vitals is JsonArray && vitals.isNotEmpty()) {
            tags.add(ContentTagData(Icons.Outlined.Favorite, "${vitals.size} vitals", Color(0xFFDC2626)))
        }
    } catch (_: Exception) {}

    try {
        val diagnoses = data["diagnoses"]
        if (diagnoses is JsonArray && diagnoses.isNotEmpty()) {
            tags.add(ContentTagData(Icons.Outlined.Healing, "${diagnoses.size} conditions", Color(0xFFD97706)))
        }
    } catch (_: Exception) {}

    return tags
}

@Composable
private fun ContentTag(icon: ImageVector, label: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

