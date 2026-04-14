package com.medhistry.doctor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medhistry.data.DocumentNote
import com.medhistry.data.PatientBriefing
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Rich patient briefing shown after a QR scan or code redeem.
 * Renders (top to bottom):
 *   - Patient header card with gradient avatar + live session badge
 *   - CRITICAL allergy banner
 *   - Active Conditions chips (from briefing.diagnoses)
 *   - Current Medications rows with frequency/timing badges (from briefing.medications)
 *   - Recent Lab Values with normal/high/low coloring (from briefing.criticalLabs)
 *   - Total documents summary
 *   - Done button
 */
@Composable
fun PatientBriefingCard(
    briefing: PatientBriefing,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DoctorColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PatientHeaderCard(briefing)

        CriticalAllergyBanner(briefing.allergies ?: "No known allergies")

        briefing.medicalSummary?.takeIf { it.isNotBlank() }?.let {
            SectionLabel("SUMMARY")
            SurfaceCard {
                Text(it, fontSize = 14.sp, color = DoctorColors.TextPrimary)
            }
        }

        SectionLabel("ACTIVE CONDITIONS")
        ActiveConditionsCard(briefing.diagnoses)

        SectionLabel("CURRENT MEDICATIONS")
        MedicationsCard(briefing.medications)

        SectionLabel("RECENT LAB VALUES")
        LabValuesCard(briefing.criticalLabs)

        if (briefing.documentNotes.isNotEmpty()) {
            SectionLabel("DOCUMENT NOTES")
            DocumentNotesCard(briefing.documentNotes)
        }

        SectionLabel("DOCUMENTS ON FILE")
        SurfaceCard {
            Text(
                if (briefing.totalDocuments > 0)
                    "${briefing.totalDocuments} document${if (briefing.totalDocuments == 1) "" else "s"} shared"
                else "No documents shared",
                fontSize = 14.sp,
                color = DoctorColors.TextPrimary,
            )
        }

        Text(
            "Session expires: ${briefing.sessionExpiresAt}",
            fontSize = 11.sp,
            color = DoctorColors.TextLight,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(containerColor = DoctorColors.Primary),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
        ) {
            Text("Done", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PatientHeaderCard(briefing: PatientBriefing) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(DoctorColors.NavyDark, DoctorColors.NavyLight)
                )
            )
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    briefing.name.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    briefing.name,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    briefing.age?.let {
                        Text(it, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                    }
                    briefing.gender?.let {
                        Text("\u00B7 $it", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                    }
                    briefing.bloodGroup?.let {
                        Text("\u00B7 $it", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                    }
                }
            }
            // Live session badge
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF2ECC71).copy(alpha = 0.9f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
                Spacer(Modifier.width(6.dp))
                Text("LIVE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CriticalAllergyBanner(allergies: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFFEE2E2))
            .border(1.5.dp, DoctorColors.Danger, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("\u26A0\uFE0F", fontSize = 22.sp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "CRITICAL \u2014 ALLERGIES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = DoctorColors.Danger,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                allergies,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF7F1D1D),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = DoctorColors.TextLight,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun SurfaceCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DoctorColors.Surface)
            .border(1.dp, DoctorColors.Border, RoundedCornerShape(14.dp))
            .padding(14.dp),
        content = content,
    )
}

/** Safely pull a string from a JSON map whose values are JsonElements. */
private fun Map<String, JsonElement>.str(key: String): String? {
    val v = this[key] ?: return null
    if (v is JsonPrimitive) {
        if (v.isString) return v.content
        // numbers / booleans — render as content too
        return v.content.takeIf { it != "null" }
    }
    return runCatching { v.jsonPrimitive.content }.getOrNull()?.takeIf { it != "null" }
}

@Composable
private fun ActiveConditionsCard(diagnoses: List<String>) = SurfaceCard {
    if (diagnoses.isEmpty()) {
        Text("No conditions recorded", fontSize = 14.sp, color = DoctorColors.TextSecondary)
        return@SurfaceCard
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        diagnoses.forEach { dx ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(DoctorColors.PrimaryLight)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    dx,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DoctorColors.PrimaryDark,
                )
            }
        }
    }
}

@Composable
private fun MedicationsCard(medications: List<Map<String, JsonElement>>) = SurfaceCard {
    if (medications.isEmpty()) {
        Text("No medications recorded", fontSize = 14.sp, color = DoctorColors.TextSecondary)
        return@SurfaceCard
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        medications.forEachIndexed { idx, med ->
            if (idx > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(DoctorColors.Border)
                )
            }
            val name = med.str("name") ?: "Unknown"
            val brand = med.str("brand_name")
            val dosage = med.str("dosage")
            val frequency = med.str("frequency")
            val duration = med.str("duration")
            val instructions = med.str("instructions")
            val purpose = med.str("purpose")

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = DoctorColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (frequency != null) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(DoctorColors.PrimaryLight)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                frequency,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DoctorColors.PrimaryDark,
                            )
                        }
                    }
                }
                if (brand != null) {
                    Text(
                        brand,
                        fontSize = 11.sp,
                        color = DoctorColors.TextLight,
                    )
                }
                val metaLine = listOfNotNull(dosage, duration).joinToString(" \u00B7 ")
                if (metaLine.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(metaLine, fontSize = 12.sp, color = DoctorColors.TextSecondary)
                }
                if (!instructions.isNullOrBlank()) {
                    Text(
                        instructions,
                        fontSize = 11.sp,
                        color = DoctorColors.TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (!purpose.isNullOrBlank()) {
                    Text(purpose, fontSize = 11.sp, color = DoctorColors.TextLight)
                }
            }
        }
    }
}

/** Render the per-document clinical narrative. One card per uploaded doc with:
 *   - Header chip: doc_type · date · hospital · doctor + specialisation
 *   - Status banner if `overall_status` is "attention_needed" or "urgent"
 *   - The doctor-targeted `clinical_summary`
 *   - Optional follow-up line and symptoms chips
 */
@Composable
private fun DocumentNotesCard(notes: List<DocumentNote>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        notes.forEach { note ->
            SurfaceCard {
                // Header line — type / date / source
                val headerBits = listOfNotNull(
                    note.docType?.replace("_", " ")?.replaceFirstChar { it.uppercase() },
                    note.documentDate,
                    note.hospitalName,
                ).filter { it.isNotBlank() }
                if (headerBits.isNotEmpty()) {
                    Text(
                        headerBits.joinToString("  \u00B7  "),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DoctorColors.TextLight,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                val byLine = listOfNotNull(
                    note.doctorName?.let { "Dr. $it" },
                    note.doctorSpecialisation,
                ).joinToString(" \u00B7 ")
                if (byLine.isNotBlank()) {
                    Text(byLine, fontSize = 11.sp, color = DoctorColors.TextLight)
                    Spacer(Modifier.height(8.dp))
                }

                // Status banner
                val status = note.overallStatus?.lowercase()
                if (status == "attention_needed" || status == "urgent") {
                    val (bg, fg) = if (status == "urgent")
                        Color(0xFFFEE2E2) to DoctorColors.Danger
                    else
                        Color(0xFFFFEDD5) to DoctorColors.Warn
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(bg)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (status == "urgent") "\u26A0\uFE0F" else "\u2139\uFE0F", fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            note.overallStatusMessage ?: status.uppercase().replace("_", " "),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = fg,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // The actual doctor-targeted summary
                note.clinicalSummary?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        fontSize = 13.sp,
                        color = DoctorColors.TextPrimary,
                    )
                }

                // Symptoms chips
                if (note.symptoms.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "SYMPTOMS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = DoctorColors.TextLight,
                    )
                    Spacer(Modifier.height(4.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        note.symptoms.forEach { sym ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DoctorColors.PrimaryLight)
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text(sym, fontSize = 11.sp, color = DoctorColors.PrimaryDark)
                            }
                        }
                    }
                }

                // Follow-up
                note.followUp?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DoctorColors.PrimaryLight)
                            .padding(10.dp),
                    ) {
                        Text("\uD83D\uDCC5 ", fontSize = 12.sp)
                        Text(
                            it,
                            fontSize = 12.sp,
                            color = DoctorColors.PrimaryDark,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LabValuesCard(labs: List<Map<String, JsonElement>>) = SurfaceCard {
    if (labs.isEmpty()) {
        Text("No critical lab results", fontSize = 14.sp, color = DoctorColors.TextSecondary)
        return@SurfaceCard
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labs.forEach { lab ->
            // Backend extraction stores `test_name` (Gemini schema). Older docs
            // may use `name`/`test`, so fall back through all of them.
            val name = lab.str("test_name") ?: lab.str("name") ?: lab.str("test") ?: "Lab"
            val value = lab.str("value")
            val unit = lab.str("unit")
            val status = lab.str("status")?.lowercase()
            val color = when (status) {
                "critical", "high" -> DoctorColors.Danger
                "low" -> DoctorColors.Warn
                else -> DoctorColors.TextPrimary
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DoctorColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    listOfNotNull(value, unit).joinToString(" "),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
                if (status != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        status.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                    )
                }
            }
        }
    }
}
