package com.medhistry.doctor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medhistry.data.DoctorDashboardBriefing
import com.medhistry.data.MedHistryApi
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Full paginated list of the doctor's past briefings, grouped into a timeline
 * by day ("Today", "Yesterday", "Earlier this week", "Older"). Supports:
 *   - Date-range filter chips (Today / 7 days / 30 days / All)
 *   - Method filter (QR / Code / All)
 *   - Name search
 *
 * Backed by GET /doctors/me/briefings.
 */
@Composable
fun AllPatientsScreen(
    api: MedHistryApi,
    onBack: () -> Unit,
    onPatientTap: (DoctorDashboardBriefing) -> Unit,
) {
    val scope = rememberCoroutineScope()

    var rangeFilter by remember { mutableStateOf(RangeFilter.WEEK) }
    var methodFilter by remember { mutableStateOf(MethodFilter.ALL) }
    var searchText by remember { mutableStateOf(TextFieldValue("")) }

    var briefings by remember { mutableStateOf<List<DoctorDashboardBriefing>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Reload whenever a filter changes or the search text settles
    LaunchedEffect(rangeFilter, methodFilter, searchText.text) {
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                val result = api.listDoctorBriefings(
                    days = rangeFilter.days,
                    method = methodFilter.apiValue,
                    search = searchText.text.trim().ifEmpty { null },
                    limit = 100,
                    offset = 0,
                )
                briefings = result.briefings
                total = result.total
            } catch (e: Exception) {
                errorMessage = MedHistryApi.friendlyMessage(e)
                briefings = emptyList()
                total = 0
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DoctorColors.Background),
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "\u2190",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = DoctorColors.Primary,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(8.dp),
            )
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "All Patients",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = DoctorColors.TextPrimary,
                )
                Text(
                    if (isLoading) "Loading\u2026" else "$total briefing${if (total == 1) "" else "s"}",
                    fontSize = 12.sp,
                    color = DoctorColors.TextLight,
                )
            }
        }

        // Search box
        SearchBox(
            value = searchText,
            onValueChange = { searchText = it },
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(12.dp))

        // Range filter chips
        ChipRow(
            selected = rangeFilter.name,
            chips = RangeFilter.values().map { it.name to it.label },
            onSelect = { name -> rangeFilter = RangeFilter.valueOf(name) },
        )

        // Method filter chips
        ChipRow(
            selected = methodFilter.name,
            chips = MethodFilter.values().map { it.name to it.label },
            onSelect = { name -> methodFilter = MethodFilter.valueOf(name) },
        )

        Spacer(Modifier.height(6.dp))

        // Timeline list
        if (errorMessage != null) {
            EmptyStateMessage(errorMessage!!, "Pull down to retry")
        } else if (briefings.isEmpty() && !isLoading) {
            EmptyStateMessage(
                "No patients match these filters",
                "Try widening the date range or clearing the search."
            )
        } else {
            val groups = remember(briefings) { groupByTimeline(briefings) }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                groups.forEach { group ->
                    item(key = "hdr-${group.label}") {
                        Text(
                            group.label.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DoctorColors.TextLight,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                    items(group.items, key = { it.id }) { briefing ->
                        TimelineRow(
                            briefing = briefing,
                            onClick = { onPatientTap(briefing) },
                        )
                    }
                }
            }
        }
    }
}

// -- Filter enums ------------------------------------------------------------

private enum class RangeFilter(val label: String, val days: Int?) {
    TODAY("Today", 1),
    WEEK("7 days", 7),
    MONTH("30 days", 30),
    ALL("All time", null),
}

private enum class MethodFilter(val label: String, val apiValue: String?) {
    ALL("All methods", null),
    QR("QR scan", "qr_scan"),
    CODE("Share code", "share_code"),
}

// -- Small UI bits ----------------------------------------------------------

@Composable
private fun SearchBox(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DoctorColors.Surface)
            .border(1.dp, DoctorColors.Border, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("\uD83D\uDD0D", fontSize = 14.sp)
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (value.text.isEmpty()) {
                Text(
                    "Search patient name",
                    fontSize = 14.sp,
                    color = DoctorColors.TextLight,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    color = DoctorColors.TextPrimary,
                ),
                cursorBrush = SolidColor(DoctorColors.Primary),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        if (value.text.isNotEmpty()) {
            Text(
                "\u00D7",
                fontSize = 18.sp,
                color = DoctorColors.TextLight,
                modifier = Modifier
                    .clickable { onValueChange(TextFieldValue("")) }
                    .padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun ChipRow(
    selected: String,
    chips: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { (key, label) ->
            val isSelected = key == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) DoctorColors.Primary else DoctorColors.Surface)
                    .border(
                        1.dp,
                        if (isSelected) DoctorColors.Primary else DoctorColors.Border,
                        RoundedCornerShape(16.dp),
                    )
                    .clickable { onSelect(key) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) Color.White else DoctorColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun TimelineRow(briefing: DoctorDashboardBriefing, onClick: () -> Unit) {
    val initials = briefing.patientName.split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercase() }
        .joinToString("")
        .ifEmpty { "?" }
    val time = runCatching {
        Instant.parse(briefing.accessedAt)
            .atZone(ZoneId.systemDefault())
            .format(ROW_TIME_FORMATTER)
    }.getOrDefault(briefing.accessedAt.takeLast(8))
    val methodLabel = when (briefing.method) {
        "qr_scan" -> "QR scan"
        "share_code" -> "Share code"
        else -> briefing.method
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(DoctorColors.PrimaryLight),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initials,
                fontWeight = FontWeight.Bold,
                color = DoctorColors.PrimaryDark,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                briefing.patientName,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = DoctorColors.TextPrimary,
            )
            Text(methodLabel, fontSize = 12.sp, color = DoctorColors.TextSecondary)
        }
        Text(time, fontSize = 12.sp, color = DoctorColors.TextLight, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EmptyStateMessage(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, fontSize = 15.sp, color = DoctorColors.TextSecondary, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, fontSize = 13.sp, color = DoctorColors.TextLight)
    }
}

// -- Timeline grouping -------------------------------------------------------

private val ROW_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val SECTION_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

private data class TimelineGroup(
    val label: String,
    val items: List<DoctorDashboardBriefing>,
)

private fun groupByTimeline(
    briefings: List<DoctorDashboardBriefing>,
): List<TimelineGroup> {
    if (briefings.isEmpty()) return emptyList()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val yesterday = today.minusDays(1)
    val sevenDaysAgo = today.minusDays(7)

    // Each briefing → local date (best-effort; falls back to oldest bucket)
    val withDate = briefings.map { b ->
        val date = runCatching {
            Instant.parse(b.accessedAt).atZone(zone).toLocalDate()
        }.getOrDefault(LocalDate.MIN)
        b to date
    }

    // Bucket in chronological input order; items already sorted desc by backend
    val buckets = linkedMapOf<String, MutableList<DoctorDashboardBriefing>>()
    withDate.forEach { (b, date) ->
        val bucket = when {
            date == today -> "Today"
            date == yesterday -> "Yesterday"
            date.isAfter(sevenDaysAgo) && date.isBefore(today) -> "Earlier this week"
            ChronoUnit.DAYS.between(date, today) in 8..30 -> "Last 30 days"
            else -> "Older \u00B7 " + date.format(SECTION_DATE_FORMATTER).takeIf {
                date != LocalDate.MIN
            }.orEmpty().ifEmpty { "Older" }
        }
        buckets.getOrPut(bucket) { mutableListOf() }.add(b)
    }
    return buckets.map { (label, items) -> TimelineGroup(label, items) }
}
