package com.medhistry.patient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
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
 * Bottom nav — 3 tabs only: Family, Dashboard, Profile.
 *
 * Removed from nav per redesign brief:
 *   • Records  → now a section on Dashboard (drill-in list)
 *   • Medicines → now "Currently Taking" on Dashboard (drill-in list)
 *   • Lab Results → now "Latest Results" on Dashboard (drill-in list)
 *   • Share    → promoted to a prominent button on Dashboard, not a tab
 *
 * The Scan FAB replaces the old Upload tab entry point and is rendered
 * by PatientShell so it floats above the nav on all primary screens.
 */
enum class PatientTab(
    val label: String,
    val filledIcon: ImageVector,
    val outlinedIcon: ImageVector,
) {
    Family("Family", Icons.Filled.People, Icons.Outlined.People),
    Dashboard("Dashboard", Icons.Filled.Home, Icons.Outlined.Home),
    Profile("Profile", Icons.Filled.Person, Icons.Outlined.Person),
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
                    .padding(horizontal = 16.dp, vertical = 4.dp),
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
