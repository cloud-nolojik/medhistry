package com.medhistry.patient.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medhistry.data.ChatMessage
import com.medhistry.data.DocumentOut
import com.medhistry.data.MedHistryApi
import kotlinx.coroutines.delay
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

// Top-level so it survives navigation (not reset by remember{} when screen recreates)
data class AiMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val isError: Boolean = false,
    val isDocCard: Boolean = false,
    val documentId: String? = null,
    val docLabel: String? = null,
    val docDate: String? = null,
)

// Cache survives within the same process — messages persist across navigation
private object AiMessageCache {
    var patientId: String? = "##unset##"
    var messages: List<AiMessage> = emptyList()

    fun cacheFor(pid: String?) = patientId == pid
    fun save(pid: String?, msgs: List<AiMessage>) { patientId = pid; messages = msgs }
    fun clear() { patientId = "##unset##"; messages = emptyList() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskAiScreen(
    api: MedHistryApi,
    personName: String,
    activePatientId: String?,
    onBack: () -> Unit,
    onUpload: () -> Unit = {},
    onViewDocument: (documentId: String, memberName: String) -> Unit = { _, _ -> },
    // When opened from DocumentDetail, inject that document's summary immediately
    injectDocumentId: String? = null,
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    // Restore from cache if we're returning to the same patient's chat; else start fresh
    var messages by remember {
        mutableStateOf(
            if (AiMessageCache.cacheFor(activePatientId)) AiMessageCache.messages
            else emptyList()
        )
    }

    // Keep cache in sync whenever messages change
    fun updateMessages(newMsgs: List<AiMessage>) {
        messages = newMsgs
        AiMessageCache.save(activePatientId, newMsgs)
    }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var historyLoading by remember { mutableStateOf(true) }
    var showPickerSheet by remember { mutableStateOf(false) }
    // Inline upload status — shown as a temporary bubble above the composer
    var uploadStatusText by remember { mutableStateOf<String?>(null) }

    val suggestedChips = listOf(
        "What's changed recently?",
        "Anything that needs attention?",
        "List all medicines",
        "Are my lab results normal?",
    )

    // Load persistent history on open — skip if cache already has messages for this patient
    LaunchedEffect(activePatientId) {
        if (AiMessageCache.cacheFor(activePatientId) && messages.isNotEmpty()) {
            // Returning to same patient's chat — restore scroll position, skip reload
            historyLoading = false
            listState.scrollToItem(Int.MAX_VALUE)
            return@LaunchedEffect
        }
        historyLoading = true
        runCatching { api.getPersonChatHistory(activePatientId) }.onSuccess { history ->
            val loaded = history.map { msg ->
                AiMessage(text = msg.content, isUser = msg.role == "user")
            }
            updateMessages(loaded)
            if (messages.isNotEmpty()) listState.scrollToItem(Int.MAX_VALUE)
        }
        historyLoading = false
    }

    // Inject a document attachment bubble (user side) + AI summary bubble below it
    // Declared before doUpload so doUpload can call it
    fun injectDocumentSummary(doc: DocumentOut) {
        val label = doc.docType?.replace("_", " ")?.replaceFirstChar { it.uppercase() } ?: "Report"
        val dateStr = (doc.documentDate ?: doc.createdAt).take(10).let { d ->
            runCatching {
                val parts = d.split("-")
                val months = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
                "${parts[2].trimStart('0')} ${months[parts[1].toInt()-1]} ${parts[0]}"
            }.getOrDefault(d)
        }
        val docBubble = AiMessage(
            text = label,
            isUser = true,
            documentId = doc.id,
            docLabel = label,
            docDate = dateStr,
        )
        val summary = doc.aiSummary
            ?: "No summary available yet — the record is still being processed."
        val summaryBubble = AiMessage(
            text = "Here's a summary of the **$label** from $dateStr:\n\n$summary",
            isUser = false,
            isDocCard = true,
        )
        updateMessages(messages + docBubble + summaryBubble)
        scope.launch {
            listState.animateScrollToItem(Int.MAX_VALUE)
            // Persist to backend so history survives logout/reinstall
            runCatching {
                api.recordPersonChatMessages(
                    patientId = activePatientId,
                    messages = listOf(
                        "user" to docBubble.text,
                        "assistant" to summaryBubble.text,
                    )
                )
            }
        }
    }

    // ── Inline upload logic ────────────────────────────────────────────────────
    fun doUpload(uri: android.net.Uri) {
        scope.launch {
            try {
                uploadStatusText = "Uploading…"
                listState.animateScrollToItem(Int.MAX_VALUE)
                // Read file bytes + metadata
                val cr = context.contentResolver
                val mimeType = cr.getType(uri) ?: "application/octet-stream"
                val ext = when {
                    mimeType.contains("pdf")  -> "pdf"
                    mimeType.contains("png")  -> "png"
                    else -> "jpg"
                }
                val bytes = cr.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw Exception("Could not read file")
                val filename = "record_${System.currentTimeMillis()}.$ext"

                // Request SAS upload URL from backend
                val uploadResp = api.requestUploadUrl(
                    com.medhistry.data.UploadUrlRequest(
                        filename = filename,
                        contentType = mimeType,
                        fileSizeBytes = bytes.size.toLong(),
                        patientId = activePatientId,
                    )
                )
                uploadStatusText = "Uploading… (${bytes.size / 1024} KB)"
                // PUT to Azure
                val status = api.uploadToAzure(uploadResp.uploadUrl, bytes, mimeType)
                if (status !in 200..299) throw Exception("Upload failed (HTTP $status)")

                // Confirm with backend → triggers AI processing
                uploadStatusText = "Organising your record…"
                val doc = api.confirmUpload(uploadResp.documentId)

                // Poll for AI processing
                var polls = 0
                var finalDoc = doc
                while (polls < 30) {
                    delay(2000)
                    val updated = runCatching { api.getDocument(doc.id) }.getOrNull()
                    if (updated != null && updated.processingStatus == "completed") {
                        finalDoc = updated
                        break
                    }
                    if (updated != null && updated.processingStatus == "failed") {
                        throw Exception("Processing failed. Please try again.")
                    }
                    polls++
                }
                uploadStatusText = null
                // Inject as inline chat message + AI summary
                injectDocumentSummary(finalDoc)
            } catch (e: Exception) {
                uploadStatusText = null
                updateMessages(messages + AiMessage(
                    text = "Upload failed: ${e.message ?: "unknown error"}",
                    isUser = false,
                    isError = true,
                ))
                listState.animateScrollToItem(Int.MAX_VALUE)
            }
        }
    }

    // Camera launcher
    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraUri != null) doUpload(cameraUri!!)
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val file = java.io.File(context.cacheDir, "cam_${System.currentTimeMillis()}.jpg")
            cameraUri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.provider", file
            )
            cameraLauncher.launch(cameraUri!!)
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { doUpload(it) }
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { doUpload(it) }
    }
    val notifPermLauncher = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    } else null

    suspend fun sendMessage(text: String) {
        if (text.isBlank() || sending) return
        val trimmed = text.trim()
        updateMessages(messages + AiMessage(text = trimmed, isUser = true))
        input = ""
        sending = true
        listState.animateScrollToItem(Int.MAX_VALUE)
        try {
            val reply = api.sendPersonChat(activePatientId, trimmed)
            updateMessages(messages + AiMessage(text = reply, isUser = false))
        } catch (e: Exception) {
            val errText = MedHistryApi.friendlyMessage(e)
            updateMessages(messages + AiMessage(text = errText, isUser = false, isError = true))
        }
        sending = false
        listState.animateScrollToItem(Int.MAX_VALUE)
    }

    // Auto-inject when opened from DocumentDetail
    LaunchedEffect(injectDocumentId) {
        if (injectDocumentId != null && messages.isEmpty()) {
            runCatching { api.getDocument(injectDocumentId) }.onSuccess { doc ->
                injectDocumentSummary(doc)
            }
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

            // Welcome hint — only when no messages yet
            if (!historyLoading && messages.isEmpty()) {
                item { WelcomeHint(personName) }
            }

            // Actual chat messages
            items(messages, key = { it.id }) { msg ->
                ChatBubble(
                    text = msg.text,
                    isUser = msg.isUser,
                    isError = msg.isError,
                    isDocCard = msg.isDocCard,
                    documentId = msg.documentId,
                    docLabel = msg.docLabel,
                    docDate = msg.docDate,
                    onViewDocument = { docId -> onViewDocument(docId, personName) },
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

            // Inline upload progress bubble
            uploadStatusText?.let { status ->
                item(key = "upload_status") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(MedHistryColors.PrimaryLight)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                color = MedHistryColors.Primary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(status, fontSize = 13.sp, color = MedHistryColors.Primary)
                        }
                    }
                }
            }

            // Suggestion chips — plain Row with horizontalScroll to avoid
            // nested LazyRow-in-LazyColumn gesture conflicts
            if (!historyLoading && messages.isEmpty()) {
                item {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Or ask something",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MedHistryColors.TextSecondary,
                        modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        suggestedChips.forEach { chip ->
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
                    .clickable { showPickerSheet = true },
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

    // ── File picker bottom sheet ───────────────────────────────────────────────
    if (showPickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPickerSheet = false },
            containerColor = MedHistryColors.Surface,
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
                Text(
                    "Add a record",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MedHistryColors.TextPrimary,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                PickerOption(
                    icon = Icons.Outlined.PhotoCamera,
                    label = "Take a photo",
                    sub = "Point your camera at the report",
                ) {
                    showPickerSheet = false
                    cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                }
                PickerOption(
                    icon = Icons.Outlined.PhotoLibrary,
                    label = "Choose from gallery",
                    sub = "Pick an existing photo",
                ) {
                    showPickerSheet = false
                    galleryLauncher.launch("image/*")
                }
                PickerOption(
                    icon = Icons.Outlined.PictureAsPdf,
                    label = "PDF file",
                    sub = "Choose from your files",
                ) {
                    showPickerSheet = false
                    pdfLauncher.launch("application/pdf")
                }
            }
        }
    }
}

@Composable
private fun PickerOption(
    icon: ImageVector,
    label: String,
    sub: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MedHistryColors.PrimaryLight),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MedHistryColors.Primary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MedHistryColors.TextPrimary)
            Text(sub, fontSize = 12.sp, color = MedHistryColors.TextSecondary)
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
        doc.aiSummary?.let { summary ->
            Spacer(Modifier.height(6.dp))
            Text(
                summary,
                fontSize = 11.sp,
                color = MedHistryColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp,
            )
        }
    }
}

// ── Timeline doc card (vertical, full-width) ─────────────────────────────────

@Composable
private fun TimelineDocCard(doc: DocumentOut, onClick: () -> Unit) {
    val (icon, iconTint, iconBg) = when (doc.docType) {
        "lab_report"   -> Triple(Icons.Outlined.Science,     Color(0xFF0EA5E9), Color(0xFFE0F2FE))
        "prescription" -> Triple(Icons.Outlined.Description, Color(0xFF7C3AED), Color(0xFFEDE9FE))
        else -> when (doc.fileType) {
            "pdf"  -> Triple(Icons.Outlined.PictureAsPdf, MedHistryColors.Primary, Color(0xFFEBF5FF))
            else   -> Triple(Icons.Outlined.Image,        MedHistryColors.Accent,  Color(0xFFF0FDF4))
        }
    }
    val label = doc.docType?.replace("_", " ")?.replaceFirstChar { it.uppercase() }
        ?: doc.filename.take(28)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MedHistryColors.Surface)
            .border(1.dp, MedHistryColors.Border, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MedHistryColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(doc.hospitalName, doc.doctorName).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    fontSize = 12.sp,
                    color = MedHistryColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            doc.aiSummary?.let { summary ->
                Text(
                    summary,
                    fontSize = 12.sp,
                    color = MedHistryColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MedHistryColors.TextLight,
            modifier = Modifier.size(20.dp),
        )
    }
}

// ── Chat bubble ──────────────────────────────────────────────────────────────

@Composable
private fun ChatBubble(
    text: String,
    isUser: Boolean,
    isError: Boolean = false,
    isDocCard: Boolean = false,
    // Document attachment fields — non-null when this bubble is a tapped report card
    documentId: String? = null,
    docLabel: String? = null,
    docDate: String? = null,
    onViewDocument: (String) -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isUser) 48.dp else 0.dp,
                end   = if (isUser) 0.dp else 48.dp,
            ),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        // ── Document attachment card ──────────────────────────────────────────
        if (documentId != null && docLabel != null) {
            val shape = RoundedCornerShape(
                topStart = 18.dp, topEnd = 18.dp,
                bottomStart = 18.dp, bottomEnd = 4.dp,
            )
            Column(
                modifier = Modifier
                    .clip(shape)
                    .background(MedHistryColors.Primary)
                    .clickable { onViewDocument(documentId) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Description,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            docLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                        if (docDate != null) {
                            Text(
                                docDate,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.75f),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "View original report",
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("→", fontSize = 12.sp, color = Color.White)
                }
            }
            return
        }

        // ── Normal text bubble ────────────────────────────────────────────────
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
