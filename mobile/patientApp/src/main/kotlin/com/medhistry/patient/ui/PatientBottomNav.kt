package com.medhistry.patient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class PatientTab(val label: String, val glyph: String) {
    Home("Home", "\uD83C\uDFE0"),
    Timeline("Timeline", "\uD83D\uDDD3"),
    Medicines("Medicines", "\uD83D\uDC8A"),
    LabResults("Lab Results", "\uD83E\uDDEA"),
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
                Text(
                    tab.glyph,
                    fontSize = 20.sp,
                    color = if (active) MedHistryColors.Primary else MedHistryColors.TextLight,
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
