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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medhistry.data.ChatMessage
import com.medhistry.data.DocumentOut
import com.medhistry.data.MedHistryApi
import kotlinx.coroutines.launch

/**
 * Patient-level AI chat — "Ask about [name]'s records".
 *
 * The backend has context of ALL uploaded documents for this patient.
 * History is persisted server-side and loaded on open.
 *
 * Features:
 *  - Loads persistent history on open so conversation survives app restarts
 *  - Document cards in empty state — tap to inject that report's summary
 *  - Suggested quick-chips when thread is empty
 *  - + button to upload a new record from within chat
 *  - imePadding() so the keyboard never covers the composer
 */
@Composable
fun AskAiScreen(
    api: MedHistryApi,
    personName: String,
    activePatientId: String?,
    onBack: () -> Unit,
    onUpload: () -> Unit = {},
) {
    BackHandler { onBack() }

    data class Message(
        val text: String,
        val isUser: Boolean,
        val isError: Boolean = false,
        val isDocCard: Boolean = false,   // AI-injected document summary bubble
    )

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var historyLoading by remember { mutableStateOf(true) }

    // Documents belonging to this patient — shown as context cards before first message
    var documents by remember { mutableStateOf<List<DocumentOut>>(emptyList()) }

    val suggestedChips = listOf(
        "What's changed recently?",
        "Anything that needs attention?",
        "List all medicines",
        "Are my lab results normal?",
    )

    // Load persistent history + documents on open
    LaunchedEffect(activePatientId) {
        historyLoading = true
        runCatching { api.getPersonChatHistory(activePatientId) }.onSuccess { history ->
            messages = history.map { msg ->
                Message(text = msg.content, isUser = msg.role == "user")
            }
            if (messages.isNotEmpty()) {
                listState.scrollToItem(messages.size - 1)
            }
        }
        runCatching { api.listDocuments(includeFamily = true) }.onSuccess { result ->
            documents = result.documents
                .filter { doc ->
                    doc.processingStatus == "completed" &&
                    (activePatientId == null || doc.patientId == activePatientId)
                }
                .sortedByDescending { it.documentDate ?: it.createdAt }
        }
        historyLoading = false
    }

    suspend fun sendMessage(text: String) {
        if (text.isBlank() || sending) return
        val trimmed = text.trim()
        messages = messages + Message(trimmed, isUser = true)
        input = ""
        sending = true
        listState.animateScrollToItem(messages.size - 1)
        try {
            val reply = api.sendPersonChat(activePatientId, trimmed)
            messages = messages + Message(reply, isUser = false)
        } catch (e: Exception) {
            val errText = MedHistryApi.friendlyMessage(e)
            messages = messages + Message(errText, isUser = false, isError = true)
        }
        sending = false
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // Inject a document's AI summary as an AI bubble, then follow with chips
    fun injectDocumentSummary(doc: DocumentOut) {
        val label = doc.docType?.replace("_", " ")?.replaceFirstChar { it.uppercase() } ?: "Report"
        val dateStr = (doc.documentDate ?: doc.createdAt).take(10).let { d ->
            runCatching {
                val parts = d.split("-")
                val months = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
                "${parts[2].trimStart('0')} ${months[parts[1].toInt()-1]} ${parts[0]}"
            }.getOrDefault(d)
        }
        val intro = "Here's a summary of the **$label** from $dateStr:"
        val summary = doc.aiSummary ?: "No summary available yet — the record is still being processed."
        messages = messages + Message("$intro\n\n$summary", isUser = false, isDocCard = true)
        scope.launch {
            if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .imePadding(),   // keyboard pushes composer up — never covers text input
    ) {

        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MedHistryColors.Surface)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                modifier = Modifier.size(36.dp).clip(CircleShape).background(MedHistryColors.Primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    personName.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    personName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MedHistryColors.TextPrimary,
                )
                Text(
                    "AI health assistant",
                    fontSize = 12.sp,
                    color = MedHistryColors.TextSecondary,
                )
            }
        }
        HorizontalDivider(color = MedHistryColors.Border)

        // ── Disclaimer ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFFFBEB))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = Color(0xFFD97706),
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "For understanding your records only — not a substitute for medical advice.",
                fontSize = 11.sp,
                color = Color(0xFF92400E),
                lineHeight = 15.sp,
            )
        }
        HorizontalDivider(color = Color(0xFFFDE68A))

        // ── Chat thread ───────────────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Loading skeleton
            if (historyLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MedHistryColors.Primary, modifier = Modifier.size(28.dp))
                    }
                }
            }

            // Empty state — show document cards + welcome
            if (!historyLoading && messages.isEmpty()) {
                item { WelcomeHint(personName) }

                if (documents.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tap a record to discuss it",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MedHistryColors.TextSecondary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                        )
                    }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(end = 8.dp),
                        ) {
                            items(documents) { doc ->
                                DocumentContextCard(
                                    doc = doc,
                                    onClick = { injectDocumentSummary(doc) },
                                )
                            }
                        }
                    }
                }
            }

            // Actual chat messages
            items(messages, key = { it.hashCode() }) { msg ->
                ChatBubble(
                    text = msg.text,
                    isUser = msg.isUser,
                    isError = msg.isError,
                    isDocCard = msg.isDocCard,
                )
            }

            // Typing indicator
            if (sending) {
                item {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TypingIndicator()
                    }
                }
            }

            // Suggestion chips — shown below the first AI reply if thread is short
            if (!historyLoading && messages.isEmpty()) {
                item {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Or ask something",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MedHistryColors.TextSecondary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                    )
                    LazyRow(
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
            }
        }

        // ── Composer ──────────────────────────────────────────────────────────
        HorizontalDivider(color = MedHistryColors.Border)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MedHistryColors.Surface)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // Upload / attach button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MedHistryColors.PrimaryLight)
                    .clickable { onUpload() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = "Add a record",
                    tint = MedHistryColors.Primary,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.width(8.dp))

            // Text input
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFFF0F0F0))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    color = MedHistryColors.TextPrimary,
                    lineHeight = 20.sp,
                ),
                cursorBrush = SolidColor(MedHistryColors.Primary),
                decorationBox = { innerTextField ->
                    if (input.isEmpty()) {
                        Text(
                            "Message",
                            fontSize = 14.sp,
                            color = MedHistryColors.TextLight,
                        )
                    }
                    innerTextField()
                },
                maxLines = 5,
            )

            Spacer(Modifier.width(8.dp))

            // Send button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (input.isNotBlank() && !sending) MedHistryColors.Primary
                        else Color(0xFFD0D0D0)
                    )
                    .clickable(enabled = input.isNotBlank() && !sending) {
                        scope.launch { sendMessage(input) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (sending) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

// ── Document context card ────────────────────────────────────────────────────

@Composable
private fun DocumentContextCard(doc: DocumentOut, onClick: () -> Unit) {
    val (icon, iconTint, iconBg) = when (doc.docType) {
        "lab_report" -> Triple(Icons.Outlined.Science, Color(0xFF0EA5E9), Color(0xFFE0F2FE))
        "prescription" -> Triple(Icons.Outlined.Description, Color(0xFF7C3AED), Color(0xFFEDE9FE))
        else -> when (doc.fileType) {
            "pdf" -> Triple(Icons.Outlined.PictureAsPdf, MedHistryColors.Primary, Color(0xFFEBF5FF))
            else -> Triple(Icons.Outlined.Image, MedHistryColors.Accent, Color(0xFFF0FDF4))
        }
    }
    val label = doc.docType?.replace("_", " ")?.replaceFirstChar { it.uppercase() }
        ?: doc.filename.take(20)
    val dateStr = (doc.documentDate ?: doc.createdAt).take(10).let { d ->
        runCatching {
            val parts = d.split("-")
            val months = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
            "${parts[2].trimStart('0')} ${months[parts[1].toInt()-1]}"
        }.getOrDefault(d)
    }

    Column(
        modifier = Modifier
            .width(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MedHistryColors.Surface)
            .border(1.dp, MedHistryColors.Border, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MedHistryColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            dateStr,
            fontSize = 11.sp,
            color = MedHistryColors.TextSecondary,
        )
        if (doc.aiSummary != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                doc.aiSummary,
                fontSize = 11.sp,
                color = MedHistryColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp,
            )
        }
    }
}

// ── Chat bubble ──────────────────────────────────────────────────────────────

@Composable
private fun ChatBubble(
    text: String,
    isUser: Boolean,
    isError: Boolean = false,
    isDocCard: Boolean = false,
) {
    val bgColor = when {
        isError   -> Color(0xFFFEF2F2)
        isUser    -> MedHistryColors.Primary
        isDocCard -> Color(0xFFF0F9FF)
        else      -> MedHistryColors.Surface
    }
    val textColor = when {
        isError -> MedHistryColors.Danger
        isUser  -> Color.White
        else    -> MedHistryColors.TextPrimary
    }
    val borderColor = when {
        isError   -> MedHistryColors.Danger.copy(alpha = 0.3f)
        isDocCard -> Color(0xFFBAE6FD)
        isUser    -> Color.Transparent
        else      -> MedHistryColors.Border
    }
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isUser) 18.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 18.dp,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isUser) 48.dp else 0.dp,
                end   = if (isUser) 0.dp else 48.dp,
            ),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .clip(shape)
                .background(bgColor)
                .border(1.dp, borderColor, shape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text,
                fontSize = 14.sp,
                color = textColor,
                lineHeight = 20.sp,
            )
        }
    }
}

// ── Welcome hint ─────────────────────────────────────────────────────────────

@Composable
private fun WelcomeHint(personName: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MedHistryColors.PrimaryLight)
            .padding(14.dp),
    ) {
        Text(
            "👋 Hi! I know ${personName}'s records.",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MedHistryColors.PrimaryDark,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Ask me to explain any result, compare lab values over time, list medicines, or flag what needs attention.",
            fontSize = 13.sp,
            color = MedHistryColors.TextSecondary,
            lineHeight = 18.sp,
        )
    }
}

// ── Typing indicator ─────────────────────────────────────────────────────────

@Composable
private fun TypingIndicator() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MedHistryColors.Surface)
            .border(1.dp, MedHistryColors.Border, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        CircularProgressIndicator(
            color = MedHistryColors.Primary,
            strokeWidth = 2.dp,
            modifier = Modifier.size(14.dp),
        )
    }
}
