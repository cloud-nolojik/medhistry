package com.medhistry.patient.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.medhistry.data.DocumentOut
import com.medhistry.data.MedHistryApi
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

// Status colors
private val StatusGreen = Color(0xFF16A34A)
private val StatusGreenBg = Color(0xFFF0FDF4)
private val StatusGreenBorder = Color(0xFFBBF7D0)
private val StatusYellow = Color(0xFFCA8A04)
private val StatusYellowBg = Color(0xFFFEFCE8)
private val StatusYellowBorder = Color(0xFFFDE68A)
private val StatusRed = Color(0xFFDC2626)
private val StatusRedBg = Color(0xFFFEF2F2)
private val StatusRedBorder = Color(0xFFFECACA)

/**
 * Patient-friendly document detail screen.
 *
 * Shows a warm, reassuring summary with color-coded lab results.
 * Each result has a simple patient explanation.
 * "View Original" opens the file externally.
 */
@Composable
fun DocumentDetailScreen(
    api: MedHistryApi,
    documentId: String,
    memberName: String,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    var doc by remember { mutableStateOf<DocumentOut?>(null) }
    var fileUrl by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(documentId) {
        scope.launch {
            try {
                doc = api.getDocument(documentId)
                runCatching { api.getDocumentFileUrl(documentId) }
                    .onSuccess { fileUrl = it.url }
            } catch (e: Exception) {
                error = e.message
            }
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MedHistryColors.Background),
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MedHistryColors.Surface)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "\u2039",
                fontSize = 28.sp,
                color = MedHistryColors.TextPrimary,
                modifier = Modifier.clickable { onBack() },
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    doc?.docType?.replace("_", " ")?.replaceFirstChar { it.uppercase() }
                        ?: doc?.filename ?: "Document",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MedHistryColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (memberName.isNotEmpty()) {
                    Text(memberName, fontSize = 13.sp, color = MedHistryColors.Primary)
                }
            }
            fileUrl?.let { url ->
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MedHistryColors.Primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("View Original", fontSize = 12.sp, color = MedHistryColors.Primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Content
        when {
            loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MedHistryColors.Primary)
                }
            }
            error != null -> {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Failed to load: $error", color = MedHistryColors.Danger, fontSize = 14.sp)
                }
            }
            doc != null -> PatientFriendlyContent(doc!!)
        }
    }
}

// ── Patient-friendly content ─────────────────────────────────────

@Composable
private fun PatientFriendlyContent(doc: DocumentOut) {
    val data = doc.extractedData

    // Extract overall status from extracted_data
    val overallStatus = data?.get("overall_status")?.stringOrNull() ?: "all_good"
    val overallMessage = data?.get("overall_status_message")?.stringOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        // Overall status banner
        OverallStatusBanner(overallStatus, overallMessage)
        Spacer(Modifier.height(16.dp))

        // Patient-friendly summary
        doc.aiSummary?.let { summary ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MedHistryColors.Surface)
                    .border(1.dp, MedHistryColors.Border, RoundedCornerShape(14.dp))
                    .padding(16.dp),
            ) {
                Text("What this means for you", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MedHistryColors.TextPrimary)
                Spacer(Modifier.height(8.dp))
                Text(summary, fontSize = 14.sp, color = MedHistryColors.TextPrimary, lineHeight = 22.sp)
            }
            Spacer(Modifier.height(14.dp))
        }

        // Details (hospital, doctor, date)
        val metaItems = listOfNotNull(
            doc.hospitalName?.let { "Hospital" to it },
            doc.doctorName?.let { "Doctor" to it },
            doc.documentDate?.let { "Date" to formatDate(it.take(10)) },
        )
        if (metaItems.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MedHistryColors.Surface)
                    .border(1.dp, MedHistryColors.Border, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                metaItems.forEach { (label, value) ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MedHistryColors.TextLight)
                        Text(value, fontSize = 13.sp, color = MedHistryColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        if (data == null) return@Column

        // Lab Results — color coded
        data["lab_results"]?.jsonArrayOrNull()?.let { arr ->
            if (arr.isNotEmpty()) {
                Text("Your Results", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MedHistryColors.TextPrimary)
                Spacer(Modifier.height(8.dp))
                arr.forEach { item ->
                    if (item is JsonObject) {
                        LabResultCard(item)
                        Spacer(Modifier.height(8.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // Vitals — color coded
        data["vitals"]?.jsonArrayOrNull()?.let { arr ->
            if (arr.isNotEmpty()) {
                Text("Vitals", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MedHistryColors.TextPrimary)
                Spacer(Modifier.height(8.dp))
                arr.forEach { item ->
                    if (item is JsonObject) {
                        VitalCard(item)
                        Spacer(Modifier.height(8.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // Medications
        data["medications"]?.jsonArrayOrNull()?.let { arr ->
            if (arr.isNotEmpty()) {
                SectionCard(title = "Medications", icon = "\uD83D\uDC8A") {
                    arr.forEach { item ->
                        if (item is JsonObject) {
                            val name = item["name"]?.stringOrNull() ?: "\u2014"
                            val dosage = item["dosage"]?.stringOrNull() ?: ""
                            val frequency = item["frequency"]?.stringOrNull() ?: ""
                            val duration = item["duration"]?.stringOrNull() ?: ""
                            val instructions = item["instructions"]?.stringOrNull() ?: ""

                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MedHistryColors.TextPrimary)
                                val details = listOfNotNull(
                                    dosage.ifEmpty { null },
                                    frequency.ifEmpty { null },
                                    duration.ifEmpty { null },
                                ).joinToString(" \u2022 ")
                                if (details.isNotEmpty()) {
                                    Text(details, fontSize = 13.sp, color = MedHistryColors.TextSecondary)
                                }
                                if (instructions.isNotEmpty()) {
                                    Text(instructions, fontSize = 12.sp, color = MedHistryColors.TextLight, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }

        // Diagnoses
        data["diagnoses"]?.jsonArrayOrNull()?.let { arr ->
            if (arr.isNotEmpty()) {
                SectionCard(title = "Diagnoses", icon = "\uD83E\uDE7A") {
                    arr.forEach { item -> BulletItem(item.stringOrContent()) }
                }
                Spacer(Modifier.height(14.dp))
            }
        }

        // Allergies
        data["allergies_mentioned"]?.jsonArrayOrNull()?.let { arr ->
            if (arr.isNotEmpty()) {
                SectionCard(title = "Allergies", icon = "\u26A0\uFE0F") {
                    arr.forEach { item -> BulletItem(item.stringOrContent()) }
                }
                Spacer(Modifier.height(14.dp))
            }
        }

        // Follow-up
        data["follow_up"]?.stringOrNull()?.let { followUp ->
            if (followUp.isNotBlank() && followUp != "null") {
                SectionCard(title = "Next Steps", icon = "\uD83D\uDCC5") {
                    Text(followUp, fontSize = 14.sp, color = MedHistryColors.TextPrimary, lineHeight = 21.sp)
                }
                Spacer(Modifier.height(14.dp))
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ── Overall status banner ────────────────────────────────────────

@Composable
private fun OverallStatusBanner(status: String, message: String?) {
    val (bg, border, textColor, emoji, defaultMsg) = when (status) {
        "all_good" -> StatusColors(StatusGreenBg, StatusGreenBorder, StatusGreen, "\u2705", "Everything looks good!")
        "attention_needed" -> StatusColors(StatusYellowBg, StatusYellowBorder, StatusYellow, "\u26A0\uFE0F", "Most results are fine, some need attention")
        "critical" -> StatusColors(StatusRedBg, StatusRedBorder, StatusRed, "\uD83D\uDEA8", "Some results need prompt medical attention")
        else -> StatusColors(StatusGreenBg, StatusGreenBorder, StatusGreen, "\u2705", "Results reviewed")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 24.sp)
        Spacer(Modifier.width(12.dp))
        Text(
            message ?: defaultMsg,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            lineHeight = 21.sp,
        )
    }
}

private data class StatusColors(val bg: Color, val border: Color, val text: Color, val emoji: String, val defaultMsg: String)

// ── Lab result card ──────────────────────────────────────────────

@Composable
private fun LabResultCard(item: JsonObject) {
    val testName = item["test_name"]?.stringOrNull() ?: item["name"]?.stringOrNull() ?: "\u2014"
    val value = item["value"]?.stringOrNull() ?: ""
    val unit = item["unit"]?.stringOrNull() ?: ""
    val refRange = item["reference_range"]?.stringOrNull() ?: ""
    val status = item["status"]?.stringOrNull() ?: "normal"
    val explanation = item["patient_explanation"]?.stringOrNull()

    val (dotColor, bg, border) = when (status) {
        "normal" -> Triple(StatusGreen, StatusGreenBg, StatusGreenBorder)
        "high", "low" -> Triple(StatusYellow, StatusYellowBg, StatusYellowBorder)
        "critical" -> Triple(StatusRed, StatusRedBg, StatusRedBorder)
        else -> Triple(StatusGreen, StatusGreenBg, StatusGreenBorder)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Spacer(Modifier.width(10.dp))
            Text(testName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MedHistryColors.TextPrimary, modifier = Modifier.weight(1f))
            Text(
                "$value $unit".trim(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = dotColor,
            )
        }
        if (refRange.isNotEmpty()) {
            Text(
                "Normal range: $refRange",
                fontSize = 11.sp,
                color = MedHistryColors.TextLight,
                modifier = Modifier.padding(start = 20.dp, top = 2.dp),
            )
        }
        explanation?.let {
            Text(
                it,
                fontSize = 13.sp,
                color = MedHistryColors.TextSecondary,
                lineHeight = 19.sp,
                modifier = Modifier.padding(start = 20.dp, top = 4.dp),
            )
        }
    }
}

// ── Vital card ───────────────────────────────────────────────────

@Composable
private fun VitalCard(item: JsonObject) {
    val name = item["name"]?.stringOrNull() ?: item["vital"]?.stringOrNull() ?: "\u2014"
    val value = item["value"]?.stringOrNull() ?: ""
    val unit = item["unit"]?.stringOrNull() ?: ""
    val status = item["status"]?.stringOrNull() ?: "normal"
    val explanation = item["patient_explanation"]?.stringOrNull()

    val (dotColor, bg, border) = when (status) {
        "normal" -> Triple(StatusGreen, StatusGreenBg, StatusGreenBorder)
        "high", "low" -> Triple(StatusYellow, StatusYellowBg, StatusYellowBorder)
        "critical" -> Triple(StatusRed, StatusRedBg, StatusRedBorder)
        else -> Triple(StatusGreen, StatusGreenBg, StatusGreenBorder)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(dotColor))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MedHistryColors.TextPrimary)
            explanation?.let {
                Text(it, fontSize = 12.sp, color = MedHistryColors.TextSecondary)
            }
        }
        Text("$value $unit".trim(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = dotColor)
    }
}

// ── Shared components ────────────────────────────────────────────

@Composable
private fun SectionCard(title: String, icon: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MedHistryColors.Surface)
            .border(1.dp, MedHistryColors.Border, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MedHistryColors.TextPrimary)
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun BulletItem(text: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text("\u2022  ", fontSize = 13.sp, color = MedHistryColors.Primary)
        Text(text, fontSize = 13.sp, color = MedHistryColors.TextPrimary, lineHeight = 19.sp)
    }
}

// ── Helpers ──────────────────────────────────────────────────────

private fun formatDate(dateStr: String): String {
    return try {
        val parts = dateStr.split("-")
        if (parts.size == 3) {
            val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            "${parts[2].trimStart('0')} ${months.getOrElse(parts[1].toInt() - 1) { parts[1] }} ${parts[0]}"
        } else dateStr
    } catch (_: Exception) { dateStr }
}

private fun JsonElement.jsonArrayOrNull(): JsonArray? = (this as? JsonArray)

private fun JsonElement.stringOrNull(): String? =
    (this as? JsonPrimitive)?.contentOrNull

private fun JsonElement.stringOrContent(): String = when (this) {
    is JsonPrimitive -> content
    is JsonObject -> this.entries.joinToString(", ") { "${it.key}: ${it.value.stringOrContent()}" }
    is JsonArray -> this.joinToString(", ") { it.stringOrContent() }
}
