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
import com.medhistry.data.DependentOut
import com.medhistry.data.FamilyListResponse
import com.medhistry.data.MedHistryApi
import com.medhistry.data.PatientHealthSummary
import com.medhistry.data.PatientProfile
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Medicines tab — shows the patient's current medication schedule
 * grouped by time of day: Morning, Afternoon, Night.
 *
 * Each medicine displays: name, dosage, purpose (what it's for),
 * food instruction, and category icon.
 */
private const val ALL_MEMBERS = "__all__"

@Composable
fun MedicinesScreen(api: MedHistryApi) {
    var family by remember { mutableStateOf<FamilyListResponse?>(null) }
    var activePatientId by remember { mutableStateOf<String?>(ALL_MEMBERS) } // default = All
    var healthSummary by remember { mutableStateOf<PatientHealthSummary?>(null) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { api.listFamily() }.onSuccess { family = it }
    }

    // Reload when family member changes
    LaunchedEffect(activePatientId, family) {
        loading = true
        if (activePatientId == ALL_MEMBERS) {
            // Fetch for self + all dependents and merge
            val allMeds = mutableListOf<Map<String, JsonElement>>()
            runCatching { api.getHealthSummary(null) }.onSuccess { hs ->
                allMeds.addAll(hs.medications)
            }
            family?.dependents?.forEach { dep ->
                runCatching { api.getHealthSummary(dep.id) }.onSuccess { hs ->
                    allMeds.addAll(hs.medications)
                }
            }
            // Build a merged summary with just medications
            healthSummary = PatientHealthSummary(
                patientId = "",
                totalDocuments = 0,
                medications = allMeds,
                diagnoses = emptyList(),
                allergies = emptyList(),
                vitals = emptyList(),
                labResults = emptyList(),
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
        // Header
        Text(
            "My Medicines",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MedHistryColors.TextPrimary,
            modifier = Modifier.padding(start = 24.dp, top = 18.dp, bottom = 4.dp),
        )
        Text(
            "Your daily medication schedule",
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
                    MemberChip("All", activePatientId == ALL_MEMBERS) { activePatientId = ALL_MEMBERS }
                    MemberChip("Me", activePatientId == null) { activePatientId = null }
                    f.dependents.forEach { dep ->
                        MemberChip(dep.name.split(" ").first(), activePatientId == dep.id) {
                            activePatientId = dep.id
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MedHistryColors.Primary)
            }
        } else {
            val meds = healthSummary?.medications ?: emptyList()
            if (meds.isEmpty()) {
                EmptyMedicinesState()
            } else {
                val (activeMeds, expiredMeds) = meds.partition { !isMedicineExpired(it) }
                MedicinesByTimeOfDay(activeMeds, expiredMeds)
            }
        }
    }
}

@Composable
private fun MemberChip(label: String, selected: Boolean, onClick: () -> Unit) {
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

@Composable
private fun EmptyMedicinesState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("\uD83D\uDC8A", fontSize = 48.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "No medicines yet",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = MedHistryColors.TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Upload a prescription and your medicine schedule will appear here.",
            fontSize = 14.sp,
            color = MedHistryColors.TextSecondary,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MedicinesByTimeOfDay(
    activeMeds: List<Map<String, JsonElement>>,
    expiredMeds: List<Map<String, JsonElement>>,
) {
    // Group active medications by time of day
    fun groupByTime(meds: List<Map<String, JsonElement>>): Map<String, List<Map<String, JsonElement>>> {
        val morning = mutableListOf<Map<String, JsonElement>>()
        val afternoon = mutableListOf<Map<String, JsonElement>>()
        val night = mutableListOf<Map<String, JsonElement>>()
        val asNeeded = mutableListOf<Map<String, JsonElement>>()

        for (med in meds) {
            val timeOfDay = med.str("time_of_day") ?: "unknown"
            when (timeOfDay) {
                "morning" -> morning.add(med)
                "afternoon" -> afternoon.add(med)
                "night" -> night.add(med)
                "morning_and_night" -> { morning.add(med); night.add(med) }
                "morning_afternoon_night" -> { morning.add(med); afternoon.add(med); night.add(med) }
                "as_needed" -> asNeeded.add(med)
                else -> {
                    val freq = (med.str("frequency") ?: "").lowercase()
                    when {
                        "morning" in freq -> morning.add(med)
                        "night" in freq || "bedtime" in freq -> night.add(med)
                        "twice" in freq || "bd" in freq -> { morning.add(med); night.add(med) }
                        "thrice" in freq || "tds" in freq -> { morning.add(med); afternoon.add(med); night.add(med) }
                        "sos" in freq || "needed" in freq -> asNeeded.add(med)
                        else -> morning.add(med)
                    }
                }
            }
        }
        return mapOf("morning" to morning, "afternoon" to afternoon, "night" to night, "as_needed" to asNeeded)
    }

    val activeGrouped = groupByTime(activeMeds)
    var showExpired by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        // Active medicines header
        if (activeMeds.isNotEmpty()) {
            Text(
                "Current Medicines",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MedHistryColors.Primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }

        activeGrouped["morning"]?.takeIf { it.isNotEmpty() }?.let {
            TimeSection("\u2600\uFE0F", "Morning", "Take with breakfast", it, Color(0xFFFFA726), Color(0xFFFFF8E1))
        }
        activeGrouped["afternoon"]?.takeIf { it.isNotEmpty() }?.let {
            TimeSection("\u26C5", "Afternoon", "Take with lunch", it, Color(0xFF42A5F5), Color(0xFFE3F2FD))
        }
        activeGrouped["night"]?.takeIf { it.isNotEmpty() }?.let {
            TimeSection("\uD83C\uDF19", "Night", "Take after dinner", it, Color(0xFF7E57C2), Color(0xFFF3E5F5))
        }
        activeGrouped["as_needed"]?.takeIf { it.isNotEmpty() }?.let {
            TimeSection("\u26A1", "As Needed", "Take when required", it, MedHistryColors.TextSecondary, Color(0xFFF5F5F5))
        }

        // Expired medicines (collapsible)
        if (expiredMeds.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showExpired = !showExpired }
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (showExpired) "\u25BC" else "\u25B6",
                    fontSize = 12.sp,
                    color = MedHistryColors.TextLight,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Past Medicines (${expiredMeds.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MedHistryColors.TextLight,
                )
            }

            if (showExpired) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MedHistryColors.Surface.copy(alpha = 0.6f))
                        .border(1.dp, MedHistryColors.Border, RoundedCornerShape(16.dp))
                        .padding(vertical = 4.dp),
                ) {
                    expiredMeds.forEachIndexed { i, med ->
                        if (i > 0) {
                            Divider(
                                color = MedHistryColors.Border,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        MedicineCard(med, MedHistryColors.TextLight, expired = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeSection(
    emoji: String,
    title: String,
    subtitle: String,
    meds: List<Map<String, JsonElement>>,
    accentColor: Color,
    bgColor: Color,
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        // Time header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(bgColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, fontSize = 18.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MedHistryColors.TextPrimary)
                Text(subtitle, fontSize = 12.sp, color = MedHistryColors.TextSecondary)
            }
        }

        Spacer(Modifier.height(10.dp))

        // Medicine cards
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MedHistryColors.Surface)
                .border(1.dp, MedHistryColors.Border, RoundedCornerShape(16.dp))
                .padding(vertical = 4.dp),
        ) {
            meds.forEachIndexed { i, med ->
                if (i > 0) {
                    Divider(
                        color = MedHistryColors.Border,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                MedicineCard(med, accentColor)
            }
        }
    }
}

@Composable
private fun MedicineCard(med: Map<String, JsonElement>, accentColor: Color, expired: Boolean = false) {
    val name = med.str("name") ?: "Unknown"
    val brandName = med.str("brand_name")
    val dosage = med.str("dosage") ?: ""
    val purpose = med.str("purpose")
    val foodInstruction = med.str("food_instruction")
    val category = med.str("category")
    val duration = med.str("duration")
    val instructions = med.str("instructions")

    val prescribedDate = med.str("prescribed_date")

    val textPrimary = if (expired) MedHistryColors.TextLight else MedHistryColors.TextPrimary
    val textSecondary = if (expired) MedHistryColors.TextLight.copy(alpha = 0.7f) else MedHistryColors.TextSecondary
    val purposeColor = if (expired) MedHistryColors.TextLight else MedHistryColors.Primary
    val dosageColor = if (expired) MedHistryColors.TextLight else accentColor

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Category icon
        Text(
            categoryEmoji(category),
            fontSize = 22.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            // Drug name + dosage
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary,
                )
                if (dosage.isNotEmpty() && dosage != "null") {
                    Text(
                        " $dosage",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = dosageColor,
                    )
                }
                if (expired) {
                    Text(
                        "  Completed",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MedHistryColors.TextLight,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFF3F4F6))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }

            // Brand name if different
            if (!brandName.isNullOrEmpty() && brandName != "null" && brandName.lowercase() != name.lowercase()) {
                Text(
                    brandName,
                    fontSize = 12.sp,
                    color = MedHistryColors.TextLight,
                )
            }

            // Purpose — the key patient-friendly info
            if (!purpose.isNullOrEmpty() && purpose != "null") {
                Spacer(Modifier.height(3.dp))
                Text(
                    purpose,
                    fontSize = 13.sp,
                    color = purposeColor,
                    fontWeight = FontWeight.Medium,
                )
            }

            // Food + timing + prescribed date
            val infoChips = buildList {
                foodInstruction?.let {
                    when (it) {
                        "before_food" -> add("\uD83C\uDF7D Before food")
                        "after_food" -> add("\uD83C\uDF7D After food")
                        "with_food" -> add("\uD83C\uDF7D With food")
                        "empty_stomach" -> add("\uD83C\uDF7D Empty stomach")
                    }
                }
                if (!duration.isNullOrEmpty() && duration != "null") {
                    add("\u23F0 $duration")
                }
                if (!instructions.isNullOrEmpty() && instructions != "null"
                    && instructions != foodInstruction
                ) {
                    add(instructions)
                }
                prescribedDate?.let { add("\uD83D\uDCC5 ${formatDateFriendly(it)}") }
            }
            if (infoChips.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    infoChips.joinToString("  •  "),
                    fontSize = 12.sp,
                    color = textSecondary,
                )
            }
        }
    }
}

/** Map drug category to a patient-friendly emoji */
private fun categoryEmoji(category: String?): String = when (category) {
    "antibiotic" -> "\uD83E\uDDA0"  // microbe
    "painkiller" -> "\uD83E\uDE79"  // adhesive bandage
    "antacid" -> "\uD83D\uDEE1\uFE0F"  // shield
    "vitamin_supplement" -> "\uD83D\uDCAA" // flexed bicep
    "blood_pressure" -> "\u2764\uFE0F" // heart
    "diabetes" -> "\uD83E\uDE78" // drop of blood
    "antihistamine" -> "\uD83E\uDDEA" // test tube
    "steroid" -> "\u26A1" // lightning
    "hormone" -> "\uD83E\uDDEC" // DNA
    else -> "\uD83D\uDC8A" // pill
}

/**
 * Determine if a medication course has expired based on prescribed_date + duration.
 * Returns false (active) if we can't determine — better to show than hide.
 * Medicines with duration "ongoing" or "continuous" are always active.
 */
private fun isMedicineExpired(med: Map<String, JsonElement>): Boolean {
    val prescribedDate = med.str("prescribed_date") ?: return false
    val duration = med.str("duration") ?: return false

    // "ongoing" or "continuous" or "lifelong" medicines never expire
    val durationLower = duration.lowercase()
    if ("ongoing" in durationLower || "continuous" in durationLower ||
        "lifelong" in durationLower || "indefinite" in durationLower
    ) return false

    return try {
        val startDate = LocalDate.parse(prescribedDate, DateTimeFormatter.ISO_LOCAL_DATE)
        val days = parseDurationToDays(durationLower)
        if (days <= 0) false  // Can't parse duration → assume active
        else LocalDate.now().isAfter(startDate.plusDays(days.toLong()))
    } catch (_: Exception) {
        false // On any error, assume active
    }
}

/** Parse a human-readable duration like "7 days", "2 weeks", "1 month" to approximate days. */
private fun parseDurationToDays(duration: String): Int {
    // Try to find a number in the string
    val num = Regex("(\\d+)").find(duration)?.groupValues?.get(1)?.toIntOrNull() ?: return 0
    return when {
        "day" in duration -> num
        "week" in duration -> num * 7
        "month" in duration -> num * 30
        "year" in duration -> num * 365
        else -> 0
    }
}

/** Format "2025-04-11" → "Apr 11, 2025" for patient-friendly display */
private fun formatDateFriendly(isoDate: String): String {
    return try {
        val date = LocalDate.parse(isoDate, DateTimeFormatter.ISO_LOCAL_DATE)
        date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    } catch (_: Exception) {
        isoDate
    }
}

/** Safe helper to pull a String from a Map<String, JsonElement> */
private fun Map<String, JsonElement>.str(key: String): String? {
    val value = this[key]?.jsonPrimitive?.content
    return if (value == "null" || value.isNullOrBlank()) null else value
}
