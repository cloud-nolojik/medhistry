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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Coronavirus
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
@Composable
fun MedicinesScreen(
    api: MedHistryApi,
    onScanReport: () -> Unit = {},
    onManageFamily: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    initialActivePatientId: String? = null,
) {
    var family by remember { mutableStateOf<FamilyListResponse?>(null) }
    // Pre-scoped to the active person from the Dashboard if provided.
    var activePatientId by remember { mutableStateOf(initialActivePatientId) }
    var healthSummary by remember { mutableStateOf<PatientHealthSummary?>(null) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { api.listFamily() }.onSuccess { family = it }
    }

    // Reload when family member changes
    LaunchedEffect(activePatientId, family) {
        loading = true
        runCatching { api.getHealthSummary(activePatientId) }.onSuccess { healthSummary = it }
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MedHistryColors.Background),
    ) {
        // Header — with optional back arrow when used as a sub-screen
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
                Text("Prescriptions", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MedHistryColors.TextPrimary)
                Text("Currently taking and past medicines", fontSize = 13.sp, color = MedHistryColors.TextSecondary)
            }
        }

        // Family member picker. Shown only when the user has dependents;
        // adapts to family size (chips for up to 4, bottom-sheet for 5+).
        // Null activePatientId means "me" — normalize at the boundary.
        MemberPicker(
            family = family,
            selectedId = activePatientId ?: family?.primary?.id,
            onSelect = { id ->
                activePatientId = if (id == family?.primary?.id) null else id
            },
            onAddFamilyMember = onManageFamily,
        )

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
                EmptyMedicinesState(onScanReport = onScanReport)
            } else {
                val (activeMeds, expiredMeds) = meds.partition { !isMedicineExpired(it) }
                MedicinesByTimeOfDay(activeMeds, expiredMeds)
            }
        }
    }
}

@Composable
private fun EmptyMedicinesState(onScanReport: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Medication,
            contentDescription = null,
            tint = MedHistryColors.Primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "No medicines yet",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = MedHistryColors.TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Scan a prescription and we'll organize your medicines into a daily schedule.",
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
            Text("Scan a prescription", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
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
            TimeSection(Icons.Outlined.LightMode, "Morning", "Take with breakfast", it, Color(0xFFFFA726), Color(0xFFFFF8E1))
        }
        activeGrouped["afternoon"]?.takeIf { it.isNotEmpty() }?.let {
            TimeSection(Icons.Outlined.WbCloudy, "Afternoon", "Take with lunch", it, Color(0xFF42A5F5), Color(0xFFE3F2FD))
        }
        activeGrouped["night"]?.takeIf { it.isNotEmpty() }?.let {
            TimeSection(Icons.Outlined.Bedtime, "Night", "Take after dinner", it, Color(0xFF7E57C2), Color(0xFFF3E5F5))
        }
        activeGrouped["as_needed"]?.takeIf { it.isNotEmpty() }?.let {
            // "Take only for symptoms" reads clearer than "Take when required"
            // or the clinical "SOS" label, especially for first-time users.
            TimeSection(Icons.Outlined.Bolt, "As Needed", "Take only for symptoms", it, MedHistryColors.TextSecondary, Color(0xFFF5F5F5))
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
                Icon(
                    imageVector = if (showExpired) Icons.Filled.ArrowDropDown else Icons.Filled.ArrowRight,
                    contentDescription = null,
                    tint = MedHistryColors.TextLight,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(4.dp))
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
    icon: ImageVector,
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
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp),
                )
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
        Icon(
            imageVector = categoryIcon(category),
            contentDescription = null,
            tint = if (expired) MedHistryColors.TextLight else MedHistryColors.Primary,
            modifier = Modifier
                .padding(top = 3.dp)
                .size(20.dp),
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

            // Food + timing + prescribed date (shown as inline chips)
            val infoChips = buildList<Pair<ImageVector?, String>> {
                foodInstruction?.let {
                    val label = when (it) {
                        "before_food" -> "Before food"
                        "after_food" -> "After food"
                        "with_food" -> "With food"
                        "empty_stomach" -> "Empty stomach"
                        else -> null
                    }
                    if (label != null) add(Icons.Outlined.Restaurant to label)
                }
                if (!duration.isNullOrEmpty() && duration != "null") {
                    add(Icons.Outlined.Schedule to duration)
                }
                if (!instructions.isNullOrEmpty() && instructions != "null"
                    && instructions != foodInstruction
                ) {
                    add(null to instructions)
                }
                prescribedDate?.let { add(Icons.Outlined.CalendarMonth to formatDateFriendly(it)) }
            }
            if (infoChips.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    infoChips.forEach { (icon, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (icon != null) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = textSecondary,
                                    modifier = Modifier.size(12.dp),
                                )
                                Spacer(Modifier.width(3.dp))
                            }
                            Text(label, fontSize = 12.sp, color = textSecondary)
                        }
                    }
                }
            }
        }
    }
}

/** Map drug category to a patient-friendly Material icon */
private fun categoryIcon(category: String?): ImageVector = when (category) {
    "antibiotic" -> Icons.Outlined.Coronavirus
    "painkiller" -> Icons.Outlined.Healing
    "antacid" -> Icons.Outlined.Shield
    "vitamin_supplement" -> Icons.Outlined.FitnessCenter
    "blood_pressure" -> Icons.Outlined.Favorite
    "diabetes" -> Icons.Outlined.Bloodtype
    "antihistamine" -> Icons.Outlined.Science
    "steroid" -> Icons.Outlined.Bolt
    "hormone" -> Icons.Outlined.Biotech
    else -> Icons.Outlined.Medication
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
