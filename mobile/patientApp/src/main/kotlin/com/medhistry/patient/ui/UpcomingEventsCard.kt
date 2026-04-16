package com.medhistry.patient.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.medhistry.data.MedHistryApi
import com.medhistry.data.UpcomingEvent
import kotlinx.coroutines.launch
import java.io.File

/**
 * Home-screen card showing the next 3 upcoming follow-ups extracted from
 * uploaded documents. Each row gives the user three zero-friction actions:
 *  • Done  — marks the event completed (server-side) so it disappears.
 *  • Skip  — dismisses (user says "don't surface this again").
 *  • 📅     — adds the event to the phone's calendar via an ICS intent.
 *
 * The card re-fetches whenever [activePatientId] changes so "Sharing as"
 * chip switches refresh the list for the right person.
 *
 * When there are zero pending events the card renders a single compact
 * "All caught up" row — enough to teach the user the app tracks follow-ups
 * without eating significant scroll real estate.
 */
@Composable
fun UpcomingEventsCard(
    api: MedHistryApi,
    activePatientId: String?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var events by remember(activePatientId) { mutableStateOf<List<UpcomingEvent>?>(null) }
    var busyIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var error by remember { mutableStateOf<String?>(null) }

    // Trigger counter — bumping this forces the LaunchedEffect to re-fetch
    // after the user completes/dismisses an event, without needing a whole
    // state-machine rewrite.
    var reloadTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(activePatientId, reloadTrigger) {
        try {
            val resp = api.listUpcomingEvents(patientId = activePatientId)
            events = resp.events
            error = null
        } catch (e: Exception) {
            error = MedHistryApi.friendlyMessage(e)
            events = emptyList()
        }
    }

    val loaded = events
    when {
        loaded == null -> {
            // First-load shimmer — very subtle, just a centered spinner so
            // the card doesn't pop in jarringly.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = MedHistryColors.Primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        loaded.isEmpty() -> {
            // Compact zero-state. Keeps the "Upcoming" concept discoverable
            // so patients know the app will surface follow-ups once they
            // upload records — without stealing vertical space with a full
            // illustrated empty card.
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 10.dp),
                ) {
                    Text(
                        "Upcoming",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MedHistryColors.TextPrimary,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MedHistryColors.Surface)
                        .border(1.dp, MedHistryColors.Border, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MedHistryColors.Accent,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "You're all caught up",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MedHistryColors.TextPrimary,
                        )
                        Text(
                            "Follow-ups and repeat tests will show here.",
                            fontSize = 12.sp,
                            color = MedHistryColors.TextLight,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
        else -> {
            val visible = loaded.take(3)
            val extra = (loaded.size - visible.size).coerceAtLeast(0)

            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 10.dp),
                ) {
                    Text(
                        "Upcoming",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MedHistryColors.TextPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MedHistryColors.PrimaryLight)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "${loaded.size}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MedHistryColors.PrimaryDark,
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MedHistryColors.Surface)
                        .border(1.dp, MedHistryColors.Border, RoundedCornerShape(16.dp))
                        .padding(vertical = 4.dp),
                ) {
                    visible.forEachIndexed { i, ev ->
                        if (i > 0) Divider(color = MedHistryColors.Border)
                        EventRow(
                            event = ev,
                            busy = ev.id in busyIds,
                            onComplete = {
                                busyIds = busyIds + ev.id
                                scope.launch {
                                    runCatching { api.completeUpcomingEvent(ev.id) }
                                    busyIds = busyIds - ev.id
                                    reloadTrigger++
                                }
                            },
                            onDismiss = {
                                busyIds = busyIds + ev.id
                                scope.launch {
                                    runCatching { api.dismissUpcomingEvent(ev.id) }
                                    busyIds = busyIds - ev.id
                                    reloadTrigger++
                                }
                            },
                            onDismissSuggestion = {
                                busyIds = busyIds + ev.id
                                scope.launch {
                                    runCatching { api.dismissUpcomingEventSuggestion(ev.id) }
                                    busyIds = busyIds - ev.id
                                    reloadTrigger++
                                }
                            },
                            onAddToCalendar = {
                                busyIds = busyIds + ev.id
                                scope.launch {
                                    try {
                                        val ics = api.getUpcomingEventIcs(ev.id)
                                        // Drop the ICS into the cache under a FileProvider-exported
                                        // path so we can fire a content:// VIEW intent that the
                                        // OS calendar app can resolve.
                                        val cache = File(context.cacheDir, "calendar").apply { mkdirs() }
                                        val f = File(cache, "event-${ev.id}.ics").apply {
                                            writeText(ics)
                                        }
                                        val uri: Uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.provider",
                                            f,
                                        )
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, "text/calendar")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(
                                            Intent.createChooser(intent, "Add to calendar"),
                                        )
                                    } catch (_: Exception) {
                                        // Swallow — calendar intent is best-effort; the user
                                        // still has Done/Skip to clear the item.
                                    }
                                    busyIds = busyIds - ev.id
                                }
                            },
                        )
                    }

                    if (extra > 0) {
                        Text(
                            "+ $extra more",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MedHistryColors.TextLight,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EventRow(
    event: UpcomingEvent,
    busy: Boolean,
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
    onDismissSuggestion: () -> Unit,
    onAddToCalendar: () -> Unit,
) {
    // Colour the urgency dot: red (overdue/urgent), amber (soon), grey (routine).
    val (dotColor, urgencyLabel) = when {
        event.isOverdue -> MedHistryColors.Danger to "Overdue"
        event.urgency == "urgent" -> MedHistryColors.Danger to "Urgent"
        event.urgency == "soon" -> MedHistryColors.Warn to "Soon"
        else -> MedHistryColors.TextLight to "Routine"
    }

    // Human-friendly due summary. Copy nullable fields into locals so Kotlin
    // can smart-cast them — cross-module public properties otherwise refuse.
    val dueOn = event.dueOn
    val daysUntil = event.daysUntilDue
    val dueHint = event.dueHintText
    val dueText: String = when {
        dueOn != null && daysUntil != null -> when {
            daysUntil < 0 -> "${-daysUntil}d overdue · $dueOn"
            daysUntil == 0 -> "Today · $dueOn"
            daysUntil == 1 -> "Tomorrow · $dueOn"
            daysUntil <= 7 -> "In ${daysUntil}d · $dueOn"
            else -> dueOn
        }
        dueOn != null -> dueOn
        dueHint != null -> dueHint
        else -> "No date given"
    }

    // Smart-cast shields for cross-module suggestion fields.
    val suggestedDocId = event.suggestedCompleteByDocumentId
    val suggestedReason = event.suggestedCompleteReason
    val suggestedDocLabel = event.suggestedCompleteDocLabel
    val hasSuggestion = suggestedDocId != null

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Urgency dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    event.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MedHistryColors.TextPrimary,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        dueText,
                        fontSize = 12.sp,
                        color = if (event.isOverdue) MedHistryColors.Danger else MedHistryColors.TextSecondary,
                        fontWeight = if (event.isOverdue) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    if (event.urgency != "routine") {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "· $urgencyLabel",
                            fontSize = 12.sp,
                            color = MedHistryColors.TextLight,
                        )
                    }
                }
                event.withWhom?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        "with $it",
                        fontSize = 12.sp,
                        color = MedHistryColors.TextLight,
                    )
                }
            }

            if (busy) {
                CircularProgressIndicator(
                    color = MedHistryColors.Primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                // Compact icon-button row. Three Material icons fit neatly
                // next to the title without wrapping.
                SmallIconButton(icon = Icons.Outlined.CalendarMonth, contentDescription = "Add to calendar", onClick = onAddToCalendar)
                Spacer(Modifier.width(6.dp))
                SmallIconButton(icon = Icons.Outlined.Check, contentDescription = "Mark done", onClick = onComplete, tint = MedHistryColors.Accent)
                Spacer(Modifier.width(6.dp))
                SmallIconButton(icon = Icons.Outlined.Close, contentDescription = "Skip", onClick = onDismiss, tint = MedHistryColors.TextLight)
            }
        }

        // "Looks done?" safe-suggestion banner. Shown only when the backend's
        // fulfillment detector found overlap between a newly-uploaded document
        // and this event's expected tests. Never auto-completes — user must
        // tap ✓ to confirm or ✕ to dismiss the suggestion (keeping the event
        // pending so it stays on their radar).
        if (hasSuggestion) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MedHistryColors.PrimaryLight)
                    .border(1.dp, MedHistryColors.Primary, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MedHistryColors.PrimaryDark,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Looks done?",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MedHistryColors.PrimaryDark,
                    )
                    if (!suggestedReason.isNullOrBlank()) {
                        Text(
                            suggestedReason,
                            fontSize = 11.sp,
                            color = MedHistryColors.TextPrimary,
                        )
                    }
                    if (!suggestedDocLabel.isNullOrBlank()) {
                        Text(
                            suggestedDocLabel,
                            fontSize = 11.sp,
                            color = MedHistryColors.TextLight,
                        )
                    }
                }

                if (busy) {
                    CircularProgressIndicator(
                        color = MedHistryColors.Primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    // Check confirms — marks event complete (backend also clears suggestion).
                    SmallIconButton(
                        icon = Icons.Outlined.Check,
                        contentDescription = "Confirm",
                        onClick = onComplete,
                        tint = MedHistryColors.Accent,
                    )
                    Spacer(Modifier.width(6.dp))
                    // Close dismisses only the suggestion — event stays pending.
                    SmallIconButton(
                        icon = Icons.Outlined.Close,
                        contentDescription = "Dismiss suggestion",
                        onClick = onDismissSuggestion,
                        tint = MedHistryColors.TextLight,
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = MedHistryColors.TextPrimary,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MedHistryColors.Background)
            .border(1.dp, MedHistryColors.Border, RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
    }
}
