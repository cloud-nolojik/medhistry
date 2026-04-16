package com.medhistry.patient.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medhistry.data.ChatMessage
import com.medhistry.data.ChatStarter
import com.medhistry.data.MedHistryApi
import kotlinx.coroutines.launch

/**
 * Per-document chat screen.
 *
 * The backend grounds every assistant reply on the document's extracted
 * data + AI summary, enforces a 30-msg/day quota, and short-circuits
 * emergency keywords to a crisis-helpline reply before calling the LLM.
 * This UI mirrors that contract:
 *   • shows doc-type-aware starter prompts while history is empty
 *   • renders the emergency refusal message in a red-bordered card so
 *     users can't miss it
 *   • disables the send button during request, and shows an inline
 *     error when quota (429) hits
 */
@Composable
fun DocumentChatScreen(
    api: MedHistryApi,
    documentId: String,
    docTypeLabel: String?,        // e.g. "Prescription" — shown in the top bar
    onBack: () -> Unit,
) {
    BackHandler { onBack() }
    val scope = rememberCoroutineScope()

    var starters by remember { mutableStateOf<List<ChatStarter>>(emptyList()) }
    var disclaimer by remember { mutableStateOf<String?>(null) }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var remainingToday by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()

    // Initial load: starters + history in parallel.
    LaunchedEffect(documentId) {
        scope.launch {
            val s = runCatching { api.getChatStarters(documentId) }.getOrNull()
            val h = runCatching { api.getChatHistory(documentId) }.getOrNull()
            s?.let {
                starters = it.starters
                disclaimer = it.disclaimer
            }
            h?.let { messages = it.messages }
            loading = false
        }
    }

    // Scroll to newest whenever the list grows.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || sending) return
        sending = true
        errorText = null
        input = ""
        scope.launch {
            try {
                val resp = api.sendChatMessage(documentId, trimmed)
                messages = messages + resp.userMessage + resp.assistantMessage
                remainingToday = resp.remainingMessagesToday
            } catch (e: Exception) {
                errorText = MedHistryApi.friendlyMessage(e)
            }
            sending = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MedHistryColors.Background),
    ) {
        // Top bar — mirrors DocumentDetailScreen's chrome for visual continuity.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MedHistryColors.Surface)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "\u2039",
                fontSize = 28.sp,
                color = MedHistryColors.TextPrimary,
                modifier = Modifier.clickable { onBack() },
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Ask about this document",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MedHistryColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                docTypeLabel?.let {
                    Text(
                        it.replace("_", " ").replaceFirstChar { c -> c.uppercase() },
                        fontSize = 12.sp,
                        color = MedHistryColors.TextSecondary,
                    )
                }
            }
            remainingToday?.let { rem ->
                Text(
                    "$rem left today",
                    fontSize = 11.sp,
                    color = MedHistryColors.TextLight,
                )
            }
        }

        // Always-on disclaimer strip so the safety reminder is never far away.
        disclaimer?.let { text ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFFBEB))
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("\u2139\uFE0F", fontSize = 12.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text,
                    fontSize = 11.sp,
                    color = Color(0xFF92400E),
                    lineHeight = 16.sp,
                )
            }
        }

        // Content area
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MedHistryColors.Primary)
                    }
                }
                messages.isEmpty() -> {
                    EmptyChatState(
                        starters = starters,
                        onPick = { send(it) },
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(items = messages, key = { it.id }) { m ->
                            MessageBubble(m)
                        }
                        if (sending) {
                            item(key = "typing") { TypingBubble() }
                        }
                    }
                }
            }
        }

        // Inline error + composer
        errorText?.let {
            Text(
                it,
                fontSize = 12.sp,
                color = MedHistryColors.Danger,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        Divider(color = MedHistryColors.Border)
        Composer(
            value = input,
            onChange = { input = it },
            enabled = !sending,
            onSend = { send(input) },
        )
    }
}

@Composable
private fun EmptyChatState(
    starters: List<ChatStarter>,
    onPick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("\uD83D\uDCAC", fontSize = 36.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            "Ask anything about this document",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MedHistryColors.TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Tap a suggestion or type your own question.",
            fontSize = 13.sp,
            color = MedHistryColors.TextSecondary,
        )
        Spacer(Modifier.height(18.dp))

        // Starters — one per row so long labels don't get clipped.
        starters.forEach { s ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MedHistryColors.Surface)
                    .border(1.dp, MedHistryColors.Border, RoundedCornerShape(12.dp))
                    .clickable { onPick(s.prompt) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    s.label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MedHistryColors.TextPrimary,
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(m: ChatMessage) {
    val isUser = m.role == "user"
    val isRedFlag = m.refusalReason == "red_flag"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        val (bg, border, txt) = when {
            isRedFlag -> Triple(Color(0xFFFEF2F2), MedHistryColors.Danger, Color(0xFF991B1B))
            isUser -> Triple(MedHistryColors.Primary, MedHistryColors.Primary, Color.White)
            else -> Triple(MedHistryColors.Surface, MedHistryColors.Border, MedHistryColors.TextPrimary)
        }

        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bg)
                .border(1.dp, border, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (isRedFlag) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("\u26A0\uFE0F", fontSize = 13.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Please read",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = txt,
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
            Text(
                m.content,
                fontSize = 14.sp,
                color = txt,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun TypingBubble() {
    Row(horizontalArrangement = Arrangement.Start) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MedHistryColors.Surface)
                .border(1.dp, MedHistryColors.Border, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                "thinking…",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MedHistryColors.TextLight,
            )
        }
    }
}

@Composable
private fun Composer(
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MedHistryColors.Surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(MedHistryColors.Background)
                .border(1.dp, MedHistryColors.Border, RoundedCornerShape(22.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(
                    "Ask about this document…",
                    fontSize = 14.sp,
                    color = MedHistryColors.TextLight,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = TextStyle(
                    color = MedHistryColors.TextPrimary,
                    fontSize = 14.sp,
                ),
                cursorBrush = SolidColor(MedHistryColors.Primary),
                maxLines = 4,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(8.dp))
        val canSend = enabled && value.trim().isNotEmpty()
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(21.dp))
                .background(if (canSend) MedHistryColors.Primary else MedHistryColors.Border)
                .clickable(enabled = canSend) { onSend() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "\u2191",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}
