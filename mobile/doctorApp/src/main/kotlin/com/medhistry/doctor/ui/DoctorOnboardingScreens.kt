package com.medhistry.doctor.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.medhistry.doctor.R

/**
 * Doctor onboarding flow — dark gradient themed to separate from the patient app.
 *   Splash -> Onboarding1 (30-Second Briefing) -> Onboarding2 (Patient-Consented) -> Onboarding3 (Works with HMS)
 */

private val DoctorDarkGradient = Brush.linearGradient(
    listOf(DoctorColors.NavyDark, DoctorColors.NavyLight)
)

@Composable
fun DoctorSplashScreen(onContinue: () -> Unit) {
    // Auto-advance after 2 seconds; tap to skip
    LaunchedEffect(Unit) {
        delay(2000L)
        onContinue()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(DoctorColors.NavyDark, DoctorColors.NavyLight)))
            .clickable { onContinue() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = "MedHistry Pro",
                modifier = Modifier
                    .size(110.dp)
                    .clip(RoundedCornerShape(28.dp)),
            )
            Spacer(Modifier.height(20.dp))
            Text("MedHistry", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text("Pro", color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp)
        }
    }
}

@Composable
private fun DoctorOnboardingShell(
    icon: String,
    title: String,
    body: String,
    dotIndex: Int,
    ctaLabel: String,
    onSkip: (() -> Unit)?,
    onContinue: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DoctorDarkGradient),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                onSkip?.let {
                    Text(
                        "Skip",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        modifier = Modifier.clickable { it() },
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center,
            ) { Text(icon, fontSize = 68.sp) }
            Spacer(Modifier.height(36.dp))
            Text(
                title,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                body,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(3) { i ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (i == dotIndex) 24.dp else 8.dp, 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (i == dotIndex) Color.White
                                else Color.White.copy(alpha = 0.35f)
                            ),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .clickable { onContinue() }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    ctaLabel,
                    color = DoctorColors.Primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
fun DoctorOnboarding1(onSkip: () -> Unit, onContinue: () -> Unit) = DoctorOnboardingShell(
    icon = "\uD83E\uDE7A",
    title = "30-Second Briefing",
    body = "Get the clinical picture before your patient walks in. Allergies, active conditions, current meds — in seconds.",
    dotIndex = 0,
    ctaLabel = "Next",
    onSkip = onSkip,
    onContinue = onContinue,
)

@Composable
fun DoctorOnboarding2(onSkip: () -> Unit, onContinue: () -> Unit) = DoctorOnboardingShell(
    icon = "\uD83D\uDD10",
    title = "Patient-Consented",
    body = "Every briefing starts with the patient's QR code. No consent, no access. Every view is logged.",
    dotIndex = 1,
    ctaLabel = "Next",
    onSkip = onSkip,
    onContinue = onContinue,
)

@Composable
fun DoctorOnboarding3(onContinue: () -> Unit) = DoctorOnboardingShell(
    icon = "\uD83C\uDFE5",
    title = "Works With Your Hospital",
    body = "MedHistry integrates with your hospital's existing systems. Nothing new to learn.",
    dotIndex = 2,
    ctaLabel = "Get Started",
    onSkip = null,
    onContinue = onContinue,
)
