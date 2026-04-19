package com.medhistry.patient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medhistry.data.AccessLogEntry
import com.medhistry.data.MedHistryApi
import kotlinx.coroutines.launch

/**
 * Access History — every doctor who's viewed the patient's summary.
 * Fetches from GET /patients/{patient_id}/access-log.
 */
@Composable
fun AccessHistoryScreen(
    api: MedHistryApi,
    patientId: String,
    onBack: () -> Unit,
) {
    var entries by remember { mutableStateOf<List<AccessLogEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            runCatching { api.getAccessLog(patientId) }
                .onSuccess { entries = it }
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MedHistryColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MedHistryColors.TextPrimary,
                modifier = Modifier.size(24.dp).clickable { onBack() },
            )
            Spacer(Modifier.width(16.dp))
            // Patient-friendly rename: "Access History" → "Who viewed my records".
            Text("Who has seen my records", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MedHistryColors.TextPrimary)
        }
        Text(
            "Every time a doctor opens your health summary, it's logged here — so you always know exactly who has seen what.",
            fontSize = 14.sp,
            color = MedHistryColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )

        Spacer(Modifier.height(16.dp))

        if (loading) {
            Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MedHistryColors.Primary)
            }
        } else if (entries.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MedHistryColors.Primary,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text("No one has looked yet", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MedHistryColors.TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    "The next time a doctor opens your health summary, it will appear here.",
                    fontSize = 14.sp,
                    color = MedHistryColors.TextSecondary,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MedHistryColors.Surface)
                    .border(1.dp, MedHistryColors.Border, RoundedCornerShape(16.dp))
                    .padding(vertical = 8.dp),
            ) {
                entries.forEachIndexed { i, entry ->
                    AccessRow(entry)
                    if (i < entries.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MedHistryColors.Border),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccessRow(entry: AccessLogEntry) {
    val methodLabel = when (entry.method) {
        "qr" -> "QR Scan"
        "share_code" -> "Share Code"
        else -> entry.method
    }
    val dateStr = entry.accessedAt.take(16).replace("T", " ") // YYYY-MM-DD HH:MM

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(MedHistryColors.Primary, MedHistryColors.Accent)
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                (entry.doctorName ?: "Dr").take(1).uppercase(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color.White,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.doctorName ?: "A doctor",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MedHistryColors.TextPrimary,
            )
            // If we don't have a hospital name, just show the date — avoids
            // defaulting to a flat "Hospital" label, which looked like a bug.
            val subtitle = entry.hospitalName?.takeIf { it.isNotBlank() }
                ?.let { "$it \u00B7 $dateStr" }
                ?: dateStr
            Text(
                subtitle,
                fontSize = 12.sp,
                color = MedHistryColors.TextSecondary,
            )
        }
        Text(
            methodLabel,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MedHistryColors.TextLight,
        )
    }
}
