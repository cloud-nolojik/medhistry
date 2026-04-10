package com.medhistry.doctor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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

enum class DoctorTab(val label: String, val glyph: String) {
    Home("Home", "\uD83C\uDFE0"),
    Scan("Scan", "\uD83D\uDCF7"),
    Profile("Profile", "\uD83D\uDC64"),
}

@Composable
fun DoctorBottomNav(
    current: DoctorTab,
    onSelect: (DoctorTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DoctorColors.Surface)
            .border(1.dp, DoctorColors.Border)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DoctorTab.values().forEach { tab ->
            val selected = tab == current
            if (tab == DoctorTab.Scan) {
                // Floating accent scan button
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(DoctorColors.NavyDark, DoctorColors.NavyLight)
                            )
                        )
                        .clickable { onSelect(tab) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(tab.glyph, fontSize = 24.sp)
                }
            } else {
                Column(
                    modifier = Modifier
                        .clickable { onSelect(tab) }
                        .padding(horizontal = 18.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        tab.glyph,
                        fontSize = 20.sp,
                        color = if (selected) DoctorColors.Primary else DoctorColors.TextLight,
                    )
                    Text(
                        tab.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) DoctorColors.Primary else DoctorColors.TextLight,
                    )
                }
            }
        }
    }
}
