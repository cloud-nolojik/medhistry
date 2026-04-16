package com.medhistry.doctor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.medhistry.data.FollowUpNote
import com.medhistry.data.PatientBriefing
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

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
    onViewDocument: (documentId: String) -> Unit = {},
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

        briefing.medicalSummary?.takeIf { it.isNotBlank() }?.let { summary ->
            // Freshness label — summaries regenerate on every upload, so the
            // newest-doc timestamp tells the doctor whether they're reading
            // a current snapshot or a stale one. Absent when there's no
            // doc timestamp to anchor the summary against.
            val freshness = relativeTimeAgo(briefing.medicalSummaryUpdatedAt)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SectionLabel("SUMMARY")
                if (freshness != null) {
                    Text(
                        "Updated $freshness",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DoctorColors.TextLight,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            SurfaceCard {
                Text(summary, fontSize = 14.sp, color = DoctorColors.TextPrimary)
            }
        }

        SectionLabel("ACTIVE CONDITIONS")
        ActiveConditionsCard(briefing.diagnoses)

        SectionLabel("CURRENT MEDICATIONS")
        MedicationsCard(briefing.medications)

        SectionLabel("RECENT LAB VALUES")
        LabValuesCard(briefing.criticalLabs, briefing.totalDocuments)

        if (briefing.documentNotes.isNotEmpty()) {
            SectionLabel("RECENT REPORTS")
            DocumentNotesCard(
                notes = briefing.documentNotes,
                totalDocuments = briefing.totalDocuments,
                onViewDocument = onViewDocument,
            )
        }

        SectionLabel("DOCUMENTS ON FILE")
        SurfaceCard {
            Text(
                if (briefing.totalDocuments > 0)
                    "${briefing.totalDocuments} document${if (briefing.totalDocuments == 1) "" else "s"} shared — tap any report above to open the original"
                else "No documents shared",
                fontSize = 13.sp,
                color = DoctorColors.TextSecondary,
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
                // Dependent indicator — only rendered when the briefing is
                // for someone-else's-record (father, spouse, child). Keeps
                // the doctor from accidentally reading the wrong patient's
                // history when the primary account holder shares a
                // dependent's QR. Styled as a pill so it reads as metadata,
                // not as the patient's own name.
                briefing.relationship?.takeIf { it.isNotBlank() }?.let { rel ->
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.18f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "Dependent \u00B7 ${rel.replaceFirstChar { it.uppercase() }}",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
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

/** Turn an ISO-8601 timestamp into a short human phrase ("3 hours ago",
 *  "5 days ago"). Returns null for null / unparseable input so callers can
 *  use `?.let { … }` to hide the label entirely rather than showing a
 *  cryptic raw timestamp. Kept deliberately coarse — the doctor only needs
 *  "is this briefing current or stale?", not sub-minute precision. */
private fun relativeTimeAgo(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    val instant = runCatching { OffsetDateTime.parse(iso).toInstant() }.getOrNull()
        ?: return null
    val now = OffsetDateTime.now(ZoneOffset.UTC).toInstant()
    val minutes = ChronoUnit.MINUTES.between(instant, now)
    if (minutes < 1) return "just now"
    if (minutes < 60) return "$minutes min ago"
    val hours = minutes / 60
    if (hours < 24) return "$hours hr${if (hours == 1L) "" else "s"} ago"
    val days = hours / 24
    if (days < 30) return "$days day${if (days == 1L) "" else "s"} ago"
    val months = days / 30
    if (months < 12) return "$months mo ago"
    val years = months / 12
    return "$years yr${if (years == 1L) "" else "s"} ago"
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
            // Structured fields Gemini extracts but we used to drop:
            //   time_of_day      → "morning" / "morning_and_night" / "as_needed"
            //   food_instruction → "before_food" / "after_food" / "with_food" / "empty_stomach"
            //   category         → "antibiotic" / "painkiller" / "antacid" / …
            // Shown together on one line so the doctor can verify dosing at
            // a glance; category gets a small pill so it's visually obvious
            // (helpful when scanning for drug classes / interactions).
            val timeOfDay = med.str("time_of_day")
            val foodInstruction = med.str("food_instruction")
            val category = med.str("category")

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
                // Category pill — useful for spotting drug classes fast (e.g.
                // two antibiotics in the same list, or spotting an NSAID for
                // a bleeder). Only rendered when Gemini classified the med.
                if (!category.isNullOrBlank() && category.lowercase() != "unknown") {
                    Spacer(Modifier.height(3.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(DoctorColors.NavyDark.copy(alpha = 0.08f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            category.replace("_", " ").replaceFirstChar { it.uppercase() },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DoctorColors.NavyDark,
                        )
                    }
                }
                val metaLine = listOfNotNull(dosage, duration).joinToString(" \u00B7 ")
                if (metaLine.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(metaLine, fontSize = 12.sp, color = DoctorColors.TextSecondary)
                }
                // Timing + food instruction — read fast as a single natural line.
                // Examples: "Morning · before food", "Morning and night · with food",
                // "As needed". Both are sanitized from enum-style strings.
                val timingLine = listOfNotNull(
                    timeOfDay?.takeIf { it.isNotBlank() && it.lowercase() != "unknown" }
                        ?.replace("_", " ")
                        ?.replace(" and ", " & ")
                        ?.replaceFirstChar { it.uppercase() },
                    foodInstruction?.takeIf { it.isNotBlank() && it.lowercase() != "unknown" }
                        ?.replace("_", " "),
                ).joinToString(" \u00B7 ")
                if (timingLine.isNotBlank()) {
                    Text(
                        timingLine,
                        fontSize = 11.sp,
                        color = DoctorColors.TextSecondary,
                    )
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
 *
 *  The backend caps this list at 5 most-recent reports even when the patient
 *  has many more documents. If there are older ones, we show a "+N older
 *  reports" footer so the doctor knows the full history is summarised into
 *  the top-level SUMMARY / conditions / meds sections above.
 *
 *  Each card is tappable — the caller resolves a SAS URL and opens the
 *  original document (PDF / image) in the system viewer.
 */
@Composable
private fun DocumentNotesCard(
    notes: List<DocumentNote>,
    totalDocuments: Int,
    onViewDocument: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        notes.forEach { note ->
            SurfaceCardClickable(onClick = { onViewDocument(note.documentId) }) {
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

                // Status pill (clinical, terse). We intentionally do NOT render
                // `overall_status_message` because Gemini writes that field in
                // patient-facing language ("Your blood tests are normal…")
                // which is wrong for a doctor audience — the clinical summary
                // below already conveys the same info in medical terms.
                val status = note.overallStatus?.lowercase()
                if (status == "attention_needed" || status == "urgent") {
                    val (bg, fg, label) = if (status == "urgent")
                        Triple(Color(0xFFFEE2E2), DoctorColors.Danger, "URGENT")
                    else
                        Triple(Color(0xFFFFEDD5), DoctorColors.Warn, "ATTENTION NEEDED")
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(bg)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = fg)
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

                // Vitals — rendered before symptoms because they're usually
                // the most clinically load-bearing numbers in a visit note
                // (BP / pulse / temp / SpO₂). Each row: [name] [value unit]
                // [STATUS?], with abnormal values colored like lab rows so
                // "high" / "low" reads the same across the card. Silently
                // skipped when the doc had no vitals extracted.
                if (note.vitals.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "VITALS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = DoctorColors.TextLight,
                    )
                    Spacer(Modifier.height(4.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        note.vitals.forEach { v -> VitalRow(v) }
                    }
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

                // Follow-ups. Gemini now returns a structured list (kind +
                // title + due hint + who + urgency) instead of a single free-
                // text line. Render one row per follow-up, color-coded by
                // urgency so the doctor can triage the card at a glance:
                // urgent → red, soon → amber, routine → primary tint.
                if (note.followUps.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "FOLLOW-UPS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = DoctorColors.TextLight,
                    )
                    Spacer(Modifier.height(4.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        note.followUps.forEach { fu ->
                            FollowUpRow(fu)
                        }
                    }
                }

                // Footer prompt — makes it obvious the card is tappable.
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tap to view original \u2192",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DoctorColors.Primary,
                )
            }
        }
        // Scalability: when there are more documents than we showed notes for,
        // tell the doctor the history above is a summary of ALL of them.
        val olderCount = (totalDocuments - notes.size).coerceAtLeast(0)
        if (olderCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DoctorColors.PrimaryLight)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("\uD83D\uDCDA ", fontSize = 14.sp)
                Text(
                    "+ $olderCount older report${if (olderCount == 1) "" else "s"} — " +
                        "factored into the summary and condition/medication lists above.",
                    fontSize = 12.sp,
                    color = DoctorColors.PrimaryDark,
                )
            }
        }
    }
}

/** Render a single follow-up entry inside a document-note card.
 *
 *  Urgency drives background + text color so the doctor can triage at a
 *  glance without reading the full row: urgent = red, soon = amber,
 *  routine (or unknown) = primary tint — matches the status-pill palette
 *  used above so urgency reads as the same language across the card.
 *
 *  Layout: [kind-emoji] [title]  — [due hint · with whom]
 *  Title falls back to a humanized `kind` label when Gemini didn't
 *  produce one so the row is never empty. Due-hint prefers the relative
 *  phrase (`dueHint`, e.g. "in 2 weeks") over the raw `dueOn` date — it
 *  reads faster, and the doctor can tap through for specifics.
 */
@Composable
private fun FollowUpRow(fu: FollowUpNote) {
    val emoji = when (fu.kind?.lowercase()) {
        "lab_test" -> "\uD83E\uDDEA"          // 🧪
        "appointment" -> "\uD83D\uDDD3\uFE0F"  // 🗓️
        "procedure" -> "\u2695\uFE0F"          // ⚕️
        "review" -> "\uD83D\uDCCB"             // 📋
        "vaccination" -> "\uD83D\uDC89"        // 💉
        "medication_review" -> "\uD83D\uDC8A"  // 💊
        else -> "\uD83D\uDCC5"                 // 📅
    }
    val (bg, fg) = when (fu.urgency?.lowercase()) {
        "urgent" -> Color(0xFFFEE2E2) to DoctorColors.Danger
        "soon" -> Color(0xFFFFEDD5) to DoctorColors.Warn
        else -> DoctorColors.PrimaryLight to DoctorColors.PrimaryDark
    }
    val label = fu.title?.takeIf { it.isNotBlank() }
        ?: fu.kind?.replace("_", " ")?.replaceFirstChar { it.uppercase() }
        ?: "Follow-up"
    val dueText = fu.dueHint?.takeIf { it.isNotBlank() }
        ?: fu.dueOn?.takeIf { it.isNotBlank() }
    val meta = listOfNotNull(dueText, fu.withWhom?.takeIf { it.isNotBlank() })
        .joinToString(" \u00B7 ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$emoji ", fontSize = 12.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = fg,
            )
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    fontSize = 11.sp,
                    color = fg.copy(alpha = 0.85f),
                )
            }
            // Prep/context notes from the source doc — e.g. "fasting
            // required", "bring old reports". Useful for the doctor to
            // relay back to the patient. Kept subtle (smaller + faded).
            fu.notes?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    fontSize = 11.sp,
                    color = fg.copy(alpha = 0.75f),
                )
            }
        }
    }
}

/** Render one vital-sign row inside a document-note card.
 *
 *  Backend shape (Gemini extraction, per-document):
 *    { "name": "Blood Pressure", "value": "120/80", "unit": "mmHg",
 *      "status": "normal" | "high" | "low" | "critical" }
 *
 *  We mirror the LabValuesCard visual language on purpose — normal values
 *  are neutral text, "high"/"critical" go red, "low" goes amber — so the
 *  doctor learns one color code and applies it to both vitals and labs.
 *  Rows with no parseable value are skipped (nothing useful to show).
 */
@Composable
private fun VitalRow(v: Map<String, JsonElement>) {
    val name = v.str("name") ?: return
    val value = v.str("value") ?: return
    val unit = v.str("unit")
    val status = v.str("status")?.lowercase()
    val color = when (status) {
        "critical", "high" -> DoctorColors.Danger
        "low" -> DoctorColors.Warn
        else -> DoctorColors.TextPrimary
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            name,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = DoctorColors.TextSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            listOfNotNull(value, unit).joinToString(" "),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        if (status != null && status != "normal") {
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

@Composable
private fun SurfaceCardClickable(
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DoctorColors.Surface)
            .border(1.dp, DoctorColors.Border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        content = content,
    )
}

/** The backend filters `critical_labs` to only high/low/critical rows — a
 *  "normal" result never arrives here. So an empty list is ambiguous: either
 *  this patient has zero labs, or they have labs but all of them are normal.
 *  We pass [totalDocuments] so we can tell which state it is and show the
 *  less scary "All lab values within normal range" when documents exist. */
@Composable
private fun LabValuesCard(
    labs: List<Map<String, JsonElement>>,
    totalDocuments: Int,
) = SurfaceCard {
    if (labs.isEmpty()) {
        Text(
            if (totalDocuments > 0) "All lab values within normal range"
            else "No lab results yet",
            fontSize = 14.sp,
            color = DoctorColors.TextSecondary,
        )
        return@SurfaceCard
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labs.forEach { lab ->
            // Backend extraction stores `test_name` (Gemini schema). Older docs
            // may use `name`/`test`, so fall back through all of them.
            val name = lab.str("test_name") ?: lab.str("name") ?: lab.str("test") ?: "Lab"
            val value = lab.str("value")
            val unit = lab.str("unit")
            val refRange = lab.str("reference_range")
            val status = lab.str("status")?.lowercase()
            val color = when (status) {
                "critical", "high" -> DoctorColors.Danger
                "low" -> DoctorColors.Warn
                else -> DoctorColors.TextPrimary
            }
            Column {
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
                // Reference range on its own line — lets the doctor compare
                // the value above without having to recall normals. Indented
                // so it reads as subordinate metadata, not another reading.
                if (!refRange.isNullOrBlank()) {
                    Text(
                        "Ref: $refRange",
                        fontSize = 11.sp,
                        color = DoctorColors.TextLight,
                    )
                }
            }
        }
    }
}
