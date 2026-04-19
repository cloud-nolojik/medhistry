package com.medhistry.patient.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import com.medhistry.data.*
import com.medhistry.patient.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Upload Records — camera/gallery/PDF picker with family member selector.
 * Uses Azure SAS direct upload: request URL → PUT to Azure → confirm with backend.
 *
 * Flow: user picks file → confirmation dialog with member dropdown → upload.
 */

sealed class UploadState {
    object Idle : UploadState()
    object RequestingUrl : UploadState()
    data class Uploading(val progress: Float) : UploadState()
    object Processing : UploadState()
    data class Done(val document: DocumentOut) : UploadState()
    data class Error(val message: String) : UploadState()
}

private const val NOTIF_CHANNEL_ID = "medhistry_reports"
private const val NOTIF_CHANNEL_NAME = "Report Ready"

private fun sendReportReadyNotification(
    context: Context,
    docType: String?,
    documentId: String,
    patientId: String?,
    memberName: String,
) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        nm.createNotificationChannel(
            NotificationChannel(NOTIF_CHANNEL_ID, NOTIF_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Notifies when a medical record is ready to view" }
        )
    }
    // Build an intent that opens PatientMainActivity and deep-links straight
    // to the document detail screen for this specific record + family member.
    val deepLinkIntent = context.packageManager
        .getLaunchIntentForPackage(context.packageName)
        ?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(com.medhistry.patient.PatientMainActivity.EXTRA_DOCUMENT_ID, documentId)
            putExtra(com.medhistry.patient.PatientMainActivity.EXTRA_PATIENT_ID, patientId)
            putExtra(com.medhistry.patient.PatientMainActivity.EXTRA_MEMBER_NAME, memberName)
        }
    val pendingIntent = PendingIntent.getActivity(
        context,
        documentId.hashCode(),
        deepLinkIntent ?: Intent(),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val label = docType?.replace("_", " ")?.replaceFirstChar { it.uppercase() } ?: "Your report"
    val body = if (memberName.isNotBlank()) "$memberName's record has been organised."
               else "Your record has been organised and is ready to view."
    val notif = NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("$label is ready")
        .setContentText(body)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()
    nm.notify(documentId.hashCode(), notif)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientUploadScreen(
    api: MedHistryApi,
    family: FamilyListResponse?,
    onBack: () -> Unit,
    onAddMember: (() -> Unit)? = null,
    initialActivePatientId: String? = null,
    onSetActivePerson: ((String?) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var uploadState by remember { mutableStateOf<UploadState>(UploadState.Idle) }
    var recentDocs by remember { mutableStateOf<List<DocumentOut>>(emptyList()) }
    // Which family member's reports to show in the recent list. Follows the global
    // active patient; user can also change it via the member picker on this screen.
    var filterPatientId by remember { mutableStateOf(initialActivePatientId) }

    // Pending URI waiting for user confirmation before upload
    var pendingUploadUri by remember { mutableStateOf<Uri?>(null) }
    // Document pending delete confirmation
    var pendingDeleteDoc by remember { mutableStateOf<DocumentOut?>(null) }

    // Primary account holder's first name — used in place of generic "Me" so
    // the member picker and document rows read like a person's name.
    val primaryFirstName = family?.primary?.name
        ?.split(" ")?.firstOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: "Me"

    // Build member list for dropdown: <primary first name> + dependents
    data class MemberOption(val id: String?, val label: String)
    val memberOptions = remember(family, primaryFirstName) {
        buildList {
            add(MemberOption(null, primaryFirstName))
            family?.dependents?.forEach { dep ->
                add(MemberOption(dep.id, dep.name))
            }
        }
    }

    // Helper to resolve member name from patient_id
    fun memberNameFor(patientId: String): String {
        if (patientId == family?.primary?.id) return primaryFirstName
        return family?.dependents?.find { it.id == patientId }?.name ?: primaryFirstName
    }

    // Load recent uploads
    LaunchedEffect(Unit) {
        runCatching { api.listDocuments(includeFamily = true) }
            .onSuccess { recentDocs = it.documents }
    }

    // Helper to perform the 3-step Azure upload
    fun doUpload(uri: Uri, targetPatientId: String?) {
        scope.launch {
            try {
                // 1. Read file info
                uploadState = UploadState.RequestingUrl
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                var fileName = "document"
                var fileSize = 0L
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = it.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx >= 0) fileName = it.getString(nameIdx) ?: "document"
                        if (sizeIdx >= 0) fileSize = it.getLong(sizeIdx)
                    }
                }
                val mimeType = context.contentResolver.getType(uri) ?: "application/pdf"
                val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
                    ?: throw Exception("Could not read file")
                if (fileSize == 0L) fileSize = bytes.size.toLong()

                // 2. Request SAS upload URL from backend
                val uploadResp = api.requestUploadUrl(
                    UploadUrlRequest(
                        filename = fileName,
                        contentType = mimeType,
                        fileSizeBytes = fileSize,
                        patientId = targetPatientId,
                    )
                )

                // 3. Upload to Azure
                uploadState = UploadState.Uploading(0.3f)
                val statusCode = api.uploadToAzure(uploadResp.uploadUrl, bytes, mimeType)
                if (statusCode !in 200..299) {
                    throw Exception("Azure upload failed (HTTP $statusCode)")
                }

                uploadState = UploadState.Uploading(0.7f)

                // 4. Confirm with backend → triggers AI processing
                uploadState = UploadState.Processing
                val doc = api.confirmUpload(uploadResp.documentId)

                // Show "organising" banner immediately — no AI summary yet.
                uploadState = UploadState.Processing

                // Refresh list so the new doc appears (status = processing/pending)
                delay(500)
                runCatching { api.listDocuments(includeFamily = true) }
                    .onSuccess { recentDocs = it.documents }

                // Poll for AI processing to finish — fire a notification when ready,
                // then update banner and list. User may have navigated away by now.
                var polls = 0
                while (polls < 30) { // max ~60 seconds
                    delay(2000)
                    val updated = runCatching { api.getDocument(doc.id) }.getOrNull()
                    if (updated != null && updated.processingStatus == "completed") {
                        // Notify the user and refresh the list.
                        val memberLabel = if (targetPatientId == null || targetPatientId == family?.primary?.id) ""
                                         else family?.dependents?.firstOrNull { it.id == targetPatientId }?.name?.split(" ")?.first() ?: ""
                        sendReportReadyNotification(
                            context = context,
                            docType = updated.docType,
                            documentId = updated.id,
                            patientId = targetPatientId,
                            memberName = memberLabel,
                        )
                        uploadState = UploadState.Done(updated)
                        runCatching { api.listDocuments(includeFamily = true) }
                            .onSuccess { recentDocs = it.documents }
                        break
                    }
                    if (updated != null && updated.processingStatus == "failed") {
                        uploadState = UploadState.Error("Processing failed. Please try again.")
                        break
                    }
                    polls++
                }
            } catch (e: Exception) {
                uploadState = UploadState.Error(e.message ?: "Upload failed")
            }
        }
    }

    // Camera launcher
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraUri != null) pendingUploadUri = cameraUri
    }

    // Camera permission launcher — requests permission, then opens camera if granted
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = java.io.File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
            cameraUri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.provider", file
            )
            cameraLauncher.launch(cameraUri!!)
        }
    }

    // Gallery launcher (images)
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { pendingUploadUri = it } }

    // PDF launcher
    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { pendingUploadUri = it } }

    // Notification permission (Android 13+) — requested on first upload attempt
    val notifPermissionLauncher = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op, best effort */ }
    } else null

    // Filtered recent docs — only show records belonging to the selected family member.
    val filteredRecentDocs = remember(recentDocs, filterPatientId, family) {
        val primaryId = family?.primary?.id
        if (filterPatientId == null) {
            // null = primary
            recentDocs.filter { it.patientId == primaryId }
        } else {
            recentDocs.filter { it.patientId == filterPatientId }
        }
    }

    // --- Upload confirmation dialog with member selector ---
    pendingUploadUri?.let { uri ->
        // Pre-select whoever is currently active (filterPatientId follows global active); fall back to primary.
        val defaultMember = remember(memberOptions, filterPatientId) {
            if (filterPatientId == null) memberOptions.first()
            else memberOptions.firstOrNull { it.id == filterPatientId } ?: memberOptions.first()
        }
        var selectedMember by remember { mutableStateOf(defaultMember) }
        // If family loaded after the dialog opened, correct the selection.
        LaunchedEffect(defaultMember) { selectedMember = defaultMember }
        var memberDropdownExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { pendingUploadUri = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = MedHistryColors.Surface,
            title = { Text("Add this report", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Whose report is this?", fontSize = 14.sp, color = MedHistryColors.TextSecondary)
                    Spacer(Modifier.height(12.dp))

                    // Member dropdown
                    ExposedDropdownMenuBox(
                        expanded = memberDropdownExpanded,
                        onExpandedChange = { memberDropdownExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = selectedMember.label,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = memberDropdownExpanded) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MedHistryColors.Primary,
                                unfocusedBorderColor = MedHistryColors.Border,
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                        )
                        ExposedDropdownMenu(
                            expanded = memberDropdownExpanded,
                            onDismissRequest = { memberDropdownExpanded = false },
                        ) {
                            memberOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        selectedMember = option
                                        filterPatientId = option.id
                                        // Sync back to global so the rest of the app
                                        // follows whoever was selected here.
                                        val normalized = if (option.id == family?.primary?.id) null else option.id
                                        onSetActivePerson?.invoke(normalized)
                                        memberDropdownExpanded = false
                                    },
                                )
                            }
                            // Add Member option
                            if (onAddMember != null) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "+ Add new member",
                                            color = MedHistryColors.Primary,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    },
                                    onClick = {
                                        memberDropdownExpanded = false
                                        pendingUploadUri = null
                                        onAddMember()
                                    },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Request notification permission so we can alert when AI finishes
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifPermissionLauncher?.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                        doUpload(uri, selectedMember.id)
                        pendingUploadUri = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MedHistryColors.Primary),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Add", color = Color.White, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingUploadUri = null }) {
                    Text("Cancel", color = MedHistryColors.TextSecondary)
                }
            },
        )
    }

    // --- Delete confirmation dialog ---
    pendingDeleteDoc?.let { doc ->
        AlertDialog(
            onDismissRequest = { pendingDeleteDoc = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = MedHistryColors.Surface,
            title = { Text("Remove this record?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This record will be removed from your account and your health summary will be updated. This can't be undone.",
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val docToDelete = doc
                        pendingDeleteDoc = null
                        scope.launch {
                            try {
                                api.deleteDocument(docToDelete.id)
                                // Refresh list
                                runCatching { api.listDocuments(includeFamily = true) }
                                    .onSuccess { recentDocs = it.documents }
                            } catch (e: Exception) {
                                uploadState = UploadState.Error("Couldn't remove this record — ${e.message}")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MedHistryColors.Danger),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Delete", color = Color.White, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteDoc = null }) {
                    Text("Cancel", color = MedHistryColors.TextSecondary)
                }
            },
        )
    }

    val isUploading = uploadState !is UploadState.Idle && uploadState !is UploadState.Done && uploadState !is UploadState.Error

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MedHistryColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MedHistryColors.TextPrimary,
                modifier = Modifier.size(24.dp).clickable { onBack() },
            )
            Spacer(Modifier.width(16.dp))
            Text("Scan a Record", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MedHistryColors.TextPrimary)
        }

        // Upload progress / status banner
        when (val state = uploadState) {
            is UploadState.Idle -> {}
            is UploadState.RequestingUrl -> UploadProgressBanner("Getting ready…", null)
            is UploadState.Uploading -> UploadProgressBanner("Uploading to secure storage…", state.progress)
            is UploadState.Processing -> UploadProgressBanner("Organizing your records…", null, pulsate = true)
            is UploadState.Done -> UploadSuccessBanner(state.document) { uploadState = UploadState.Idle }
            is UploadState.Error -> UploadErrorBanner(state.message) { uploadState = UploadState.Idle }
        }

        // Camera drop target
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isUploading) MedHistryColors.Surface else MedHistryColors.PrimaryLight)
                .border(2.dp, if (isUploading) MedHistryColors.Border else MedHistryColors.Primary, RoundedCornerShape(16.dp))
                .then(
                    if (!isUploading) Modifier.clickable {
                        if (androidx.core.content.ContextCompat.checkSelfPermission(
                                context, android.Manifest.permission.CAMERA
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            val file = java.io.File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
                            cameraUri = androidx.core.content.FileProvider.getUriForFile(
                                context, "${context.packageName}.provider", file
                            )
                            cameraLauncher.launch(cameraUri!!)
                        } else {
                            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                        }
                    } else Modifier
                )
                .padding(vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.PhotoCamera,
                contentDescription = null,
                tint = MedHistryColors.PrimaryDark,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text("Take a Photo", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MedHistryColors.PrimaryDark)
            Text("Point your camera at the report", fontSize = 13.sp, color = MedHistryColors.TextSecondary)
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UploadOption(
                icon = Icons.Outlined.PhotoLibrary,
                label = "Gallery",
                subtitle = "Pick an existing photo",
                modifier = Modifier.weight(1f),
                enabled = !isUploading,
            ) { galleryLauncher.launch("image/*") }
            UploadOption(
                icon = Icons.Outlined.PictureAsPdf,
                label = "PDF File",
                subtitle = "Choose from your files",
                modifier = Modifier.weight(1f),
                enabled = !isUploading,
            ) { pdfLauncher.launch("application/pdf") }
        }

        // Member picker + Recent Reports — only shown when there are uploads.
        if (recentDocs.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))

            // Family member filter — lets user switch whose reports they're seeing.
            // Follows the global active patient on entry; changes are local to this screen.
            MemberPicker(
                family = family,
                selectedId = filterPatientId ?: family?.primary?.id,
                onSelect = { id ->
                    filterPatientId = if (id == family?.primary?.id) null else id
                },
                onAddFamilyMember = onAddMember ?: {},
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Recent Reports",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MedHistryColors.TextPrimary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            )

            if (filteredRecentDocs.isEmpty()) {
                Text(
                    "No reports added yet for this person.",
                    fontSize = 14.sp,
                    color = MedHistryColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            } else {
                filteredRecentDocs.forEach { doc ->
                    DocumentRow(
                        doc = doc,
                        memberName = memberNameFor(doc.patientId),
                        onDelete = { pendingDeleteDoc = doc },
                    )
                }
            }
        }
    }
}

@Composable
private fun UploadProgressBanner(text: String, progress: Float?, pulsate: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFEBF5FF))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MedHistryColors.Primary,
            )
            Spacer(Modifier.width(12.dp))
            Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MedHistryColors.PrimaryDark)
        }
        if (progress != null) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = MedHistryColors.Primary,
                trackColor = MedHistryColors.Primary.copy(alpha = 0.2f),
            )
        }
    }
}

@Composable
private fun UploadSuccessBanner(doc: DocumentOut, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF0FDF4))
            .border(1.dp, Color(0xFF86EFAC), RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF166534),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (doc.processingStatus == "completed") "Report ready! Tap the record to read it."
                    else "Report uploaded — we're organising it now.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF166534),
                )
            }
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Dismiss",
                tint = Color(0xFF166534),
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onDismiss() },
            )
        }
        if (doc.processingStatus != "completed") {
            Spacer(Modifier.height(6.dp))
            Text(
                "We'll send you a notification when it's ready to view.",
                fontSize = 13.sp,
                color = Color(0xFF166534),
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun UploadErrorBanner(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFEE2E2))
            .border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = Color(0xFF991B1B),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                message,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF991B1B),
            )
        }
        Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = "Dismiss",
            tint = Color(0xFF991B1B),
            modifier = Modifier
                .size(18.dp)
                .clickable { onDismiss() },
        )
    }
}

@Composable
private fun DocumentRow(doc: DocumentOut, memberName: String, onDelete: () -> Unit) {
    val icon: ImageVector = when (doc.fileType) {
        "pdf" -> Icons.Outlined.PictureAsPdf
        "jpg", "jpeg", "png" -> Icons.Outlined.Image
        else -> Icons.Outlined.Description
    }
    val iconTint = when (doc.fileType) {
        "pdf" -> MedHistryColors.Primary
        else -> MedHistryColors.Accent
    }
    val iconBg = when (doc.fileType) {
        "pdf" -> Color(0xFFEBF5FF)
        else -> Color(0xFFF0FDF4)
    }
    val statusText = when (doc.processingStatus) {
        "completed" -> "Ready"
        "processing", "pending" -> "Organizing…"
        "pending_upload" -> "Uploading…"
        "failed" -> "Upload failed"
        else -> doc.processingStatus
    }
    val statusColor = when (doc.processingStatus) {
        "completed" -> MedHistryColors.Accent
        "failed" -> MedHistryColors.Danger
        else -> MedHistryColors.TextLight
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MedHistryColors.Surface)
            .border(1.dp, MedHistryColors.Border, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                doc.docType?.replace("_", " ")?.replaceFirstChar { it.uppercase() } ?: doc.filename,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MedHistryColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    statusText,
                    fontSize = 12.sp,
                    color = statusColor,
                )
                Text(
                    "  \u2022  ",
                    fontSize = 12.sp,
                    color = MedHistryColors.TextLight,
                )
                Text(
                    memberName,
                    fontSize = 12.sp,
                    color = MedHistryColors.TextSecondary,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Outlined.DeleteOutline,
            contentDescription = "Delete",
            tint = MedHistryColors.TextLight,
            modifier = Modifier.size(22.dp).clickable { onDelete() },
        )
    }
}

@Composable
private fun UploadOption(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) MedHistryColors.Surface else MedHistryColors.Surface.copy(alpha = 0.5f))
            .border(1.dp, MedHistryColors.Border, RoundedCornerShape(14.dp))
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 20.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MedHistryColors.Primary,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MedHistryColors.TextPrimary)
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 11.sp, color = MedHistryColors.TextLight)
        }
    }
}
