package com.medhistry.patient.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medhistry.patient.R

/**
 * Splash — big MedHistry wordmark on primary gradient with "Get Started".
 */
@Composable
fun SplashScreen(onGetStarted: () -> Unit, onLogin: () -> Unit) {
    // Match the status bar to the navy background so the top bar doesn't
    // show up as a light strip above the dark splash.
    StatusBarColor(color = MedHistryColors.NavyDark, darkIcons = false)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(MedHistryColors.NavyDark, MedHistryColors.NavyLight),
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = "MedHistry",
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(24.dp)),
            )
            Spacer(Modifier.height(24.dp))
            Text("MedHistry", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(
                "Your health story, in your hands",
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(60.dp))
            Box(
                modifier = Modifier
                    .width(280.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MedHistryColors.Primary)
                    .clickable { onGetStarted() }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Get Started", color = MedHistryColors.NavyDark, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(16.dp))
            Row {
                Text("Already have an account? ", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                Text(
                    "Log in",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { onLogin() },
                )
            }
        }
    }
}

@Composable
private fun OnboardingShell(
    icon: ImageVector,
    iconTint: Color,
    bubbleBg: Color,
    title: String,
    subtitle: String,
    step: Int, // 1-based
    ctaLabel: String,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MedHistryColors.Background)
            .padding(24.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopStart)
                .clickable { onBack() }
                .padding(4.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MedHistryColors.TextSecondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("Back", color = MedHistryColors.TextSecondary)
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(bubbleBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(64.dp),
                )
            }
            Spacer(Modifier.height(40.dp))
            Text(
                title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MedHistryColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                subtitle,
                fontSize = 15.sp,
                color = MedHistryColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 300.dp),
            )
            Spacer(Modifier.height(40.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { i ->
                    val active = i + 1 == step
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(if (active) 24.dp else 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (active) MedHistryColors.Primary else MedHistryColors.Border)
                    )
                }
            }
        }
        Button(
            onClick = onNext,
            colors = ButtonDefaults.buttonColors(containerColor = MedHistryColors.Primary),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(ctaLabel, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun Onboarding1(onBack: () -> Unit, onNext: () -> Unit) = OnboardingShell(
    icon = Icons.Outlined.Description,
    iconTint = MedHistryColors.Primary,
    bubbleBg = MedHistryColors.PrimaryLight,
    title = "All your records,\nalways with you",
    subtitle = "Scan a prescription or lab report with your phone. We'll read it, organise it, and flag any follow-up dates — so nothing slips through the cracks.",
    step = 1, ctaLabel = "Next", onBack = onBack, onNext = onNext,
)

@Composable
fun Onboarding2(onBack: () -> Unit, onNext: () -> Unit) = OnboardingShell(
    icon = Icons.Outlined.MedicalServices,
    iconTint = MedHistryColors.AccentDark,
    bubbleBg = Color(0xFFF0FDF4),
    title = "Share with your\ndoctor instantly",
    subtitle = "Show a QR code and your doctor sees your health summary instantly — no more carrying paper files or trying to remember everything.",
    step = 2, ctaLabel = "Next", onBack = onBack, onNext = onNext,
)

@Composable
fun Onboarding3(onBack: () -> Unit, onNext: () -> Unit) = OnboardingShell(
    icon = Icons.Outlined.Lock,
    iconTint = MedHistryColors.Danger,
    bubbleBg = Color(0xFFFEF2F2),
    title = "You're always\nin control",
    subtitle = "Your health data is encrypted and private. Only you decide which doctor sees it, and access expires automatically.",
    step = 3, ctaLabel = "Create Account", onBack = onBack, onNext = onNext,
)
