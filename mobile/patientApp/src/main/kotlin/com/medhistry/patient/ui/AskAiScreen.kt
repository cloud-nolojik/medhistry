package com.medhistry.patient.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medhistry.data.MedHistryApi
import kotlinx.coroutines.launch

/**
 * Person-scoped AI chat — "Ask about [name]'s reports".
 *
 * Opened from the Ask AI CTA on Dashboard. Scope: all documents for the
 * currently-active family member.
 *
 * Per the brief:
 *   • Disclaimer banner (amber) at the top
 *   • Chat thread (WhatsApp-style alternating bubbles)
 *   • Suggested question chips above the composer (shown when chat is empty)
 *   • Composer: text input + send button (mic is stubbed — coming soon)
 *   • Session-local chat memory — wiped on screen leave (MVP)
 */
@Composable
fun AskAiScreen(
    api: MedHistryApi,
    personName: String,
    activePatientId: String?,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }

    data class Message(val text: String, val isUser: Boolean, val isError: Boolean = false)

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    val suggestedChips = listOf(
        "What's changed recently?",
        "What needs attention?",
        "Show medicines list",
        "Are the lab results normal?",
    )

    suspend fun sendMessage(text: String) {
        if (text.isBlank() || sending) return
        messages = messages + Message(text, isUser = true)
        input = ""
        sending = true
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
        try {
            val reply = api.sendPersonChat(activePatientId, text)
            messages = messages + Message(reply, isUser = false)
        } catch (e: Exception) {
            val errText = when {
                e.message?.contains("404") == true || e.message?.contains("Not Found") == true ->
                    "Having trouble connecting. The AI assistant isn't set up yet — try the per-report chat instead."
                else -> MedHistryApi.friendlyMessage(e)
            }
            messages = messages + Message(errText, isUser = false, isError = true)
        }
        sending = false
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MedHistryColors.Background)) {

        // ── Header ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MedHistryColors.Surface)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MedHistryColors.TextPrimary,
                modifier = Modifier.size(24.dp).clickable { onBack() },
            )
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier.size(34.dp).clip(CircleShape).background(MedHistryColors.Primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(personName.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Ask about $personName", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MedHistryColors.TextPrimary)
                Text("Powered by your records", fontSize = 12.sp, color = MedHistryColors.TextSecondary)
            }
        }
        Divider(color = MedHistryColors.Border)

        // ── Disclaimer banner ─────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFFFBEB))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "I can help you understand records, but I'm not a doctor. Always confirm with your physician.",
                fontSize = 12.sp,
                color = Color(0xFF92400E),
                lineHeight = 16.sp,
            )
        }
        Divider(color = Color(0xFFFDE68A))

        // ── Chat thread ───────────────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            if (messages.isEmpty()) {
                item { WelcomeHint(personName) }
            }
            items(messages) { msg ->
                ChatBubble(text = msg.text, isUser = msg.isUser, isError = msg.isError)
            }
            if (sending) {
                item {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TypingIndicator()
                    }
                }
            }
        }

        // ── Suggested chips ───────────────────────────────────────────────────
        if (messages.isEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(suggestedChips) { chip ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(MedHistryColors.Surface)
                            .border(1.dp, MedHistryColors.Border, RoundedCornerShape(20.dp))
                            .clickable { scope.launch { sendMessage(chip) } }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(chip, fontSize = 13.sp, color = MedHistryColors.TextPrimary)
                    }
                }
            }
        }

        Divider(color = MedHistryColors.Border)

        // ── Composer ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MedHistryColors.Surface)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MedHistryColors.Background)
                    .border(1.dp, MedHistryColors.Border, RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                textStyle = TextStyle(fontSize = 14.sp, color = MedHistryColors.TextPrimary),
                cursorBrush = SolidColor(MedHistryColors.Primary),
                decorationBox = { innerTextField ->
                    if (input.isEmpty()) {
                        Text(
                            "Ask anything about $personName's records…",
                            fontSize = 14.sp,
                            color = MedHistryColors.TextLight,
                        )
                    }
                    innerTextField()
                },
                maxLines = 4,
            )
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (input.isNotBlank() && !sending) MedHistryColors.Primary else MedHistryColors.Border)
                    .clickable(enabled = input.isNotBlank() && !sending) {
                        scope.launch { sendMessage(input) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (sending) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(text: String, isUser: Boolean, isError: Boolean) {
    val bgColor = when {
        isError -> Color(0xFFFEF2F2)
        isUser -> MedHistryColors.Primary
        else -> MedHistryColors.Surface
    }
    val textColor = when {
        isError -> MedHistryColors.Danger
        isUser -> Color.White
        else -> MedHistryColors.TextPrimary
    }
    val borderColor = when {
        isError -> MedHistryColors.Danger.copy(alpha = 0.3f)
        isUser -> Color.Transparent
        else -> MedHistryColors.Border
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp,
                    )
                )
                .background(bgColor)
                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(text, fontSize = 14.sp, color = textColor, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun WelcomeHint(personName: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MedHistryColors.PrimaryLight)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MedHistryColors.Primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Ask about $personName's records", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MedHistryColors.PrimaryDark)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "I can explain lab results, summarise medicines, and flag things to discuss with your doctor.",
            fontSize = 13.sp,
            color = MedHistryColors.TextSecondary,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun TypingIndicator() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MedHistryColors.Surface)
            .border(1.dp, MedHistryColors.Border, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        CircularProgressIndicator(color = MedHistryColors.Primary, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
    }
}
