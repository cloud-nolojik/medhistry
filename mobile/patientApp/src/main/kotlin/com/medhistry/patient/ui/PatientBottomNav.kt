package com.medhistry.patient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Bottom nav tabs.
 *
 * Renamed "Timeline" → "Records" because patients think in "reports and
 * records", not "timelines". Behaviour is identical — the screen still
 * shows a chronological list of uploaded documents.
 *
 * Each tab carries both a filled and outlined icon variant so the active
 * tab can render filled (stronger presence) and inactive tabs outlined
 * (lighter weight), matching Material's bottom-nav conventions.
 */
enum class PatientTab(
    val label: String,
    val filledIcon: ImageVector,
    val outlinedIcon: ImageVector,
) {
    Home("Home", Icons.Filled.Home, Icons.Outlined.Home),
    Timeline("Records", Icons.Filled.Description, Icons.Outlined.Description),
    Medicines("Medicines", Icons.Filled.Medication, Icons.Outlined.Medication),
    LabResults("Lab Results", Icons.Filled.Science, Icons.Outlined.Science),
}

@Composable
fun PatientBottomNav(
    current: PatientTab,
    onSelect: (PatientTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MedHistryColors.Surface)
            .padding(top = 10.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        for (tab in PatientTab.values()) {
            val active = tab == current
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = if (active) tab.filledIcon else tab.outlinedIcon,
                    contentDescription = tab.label,
                    tint = if (active) MedHistryColors.Primary else MedHistryColors.TextLight,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    tab.label,
                    fontSize = 11.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active) MedHistryColors.Primary else MedHistryColors.TextLight,
                )
            }
        }
    }
}
