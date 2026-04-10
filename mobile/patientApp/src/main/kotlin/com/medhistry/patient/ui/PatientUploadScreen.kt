package com.medhistry.patient.ui

import android.net.Uri
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medhistry.data.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientUploadScreen(
    api: MedHistryApi,
    family: FamilyListResponse?,
    onBack: () -> Unit,
    onAddMember: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var uploadState by remember { mutableStateOf<UploadState>(UploadState.Idle) }
    var recentDocs by remember { mutableStateOf<List<DocumentOut>>(emptyList()) }

    // Pending URI waiting for user confirmation before upload
    var pendingUploadUri by remember { mutableStateOf<Uri?>(null) }
    // Document pending delete confirmation
    var pendingDeleteDoc by remember { mutableStateOf<DocumentOut?>(null) }

    // Build member list for dropdown: "Me (Self)" + dependents
    data class MemberOption(val id: String?, val label: String)
    val memberOptions = remember(family) {
        buildList {
            add(MemberOption(null, "Me (Self)"))
            family?.dependents?.forEach { dep ->
                add(MemberOption(dep.id, dep.name))
            }
        }
    }

    // Helper to resolve member name from patient_id
    fun memberNameFor(patientId: String): String {
        if (patientId == family?.primary?.id) return "Me (Self)"
        return family?.dependents?.find { it.id == patientId }?.name ?: "Unknown"
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

                uploadState = UploadState.Done(doc)

                // Refresh list
                delay(500)
                runCatching { api.listDocuments(includeFamily = true) }
                    .onSuccess { recentDocs = it.documents }

                // Poll for processing completion
                var polls = 0
                while (polls < 30) { // max ~60 seconds
                    delay(2000)
                    val updated = runCatching { api.getDocument(doc.id) }.getOrNull()
                    if (updated != null && updated.processingStatus == "completed") {
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

    // --- Upload confirmation dialog with member selector ---
    pendingUploadUri?.let { uri ->
        var selectedMember by remember { mutableStateOf(memberOptions.first()) }
        var memberDropdownExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { pendingUploadUri = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = MedHistryColors.Surface,
            title = { Text("Upload document", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Who is this document for?", fontSize = 14.sp, color = MedHistryColors.TextSecondary)
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
                    onClick = { doUpload(uri, selectedMember.id); pendingUploadUri = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MedHistryColors.Primary),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Upload", color = Color.White, fontWeight = FontWeight.SemiBold) }
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
            title = { Text("Delete document?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will permanently delete \"${doc.docType?.replace("_", " ")?.replaceFirstChar { it.uppercase() } ?: doc.filename}\" and update the health summary.",
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
                                uploadState = UploadState.Error("Delete failed: ${e.message}")
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
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("\u2039", fontSize = 28.sp, color = MedHistryColors.TextPrimary, modifier = Modifier.clickable { onBack() })
            Spacer(Modifier.width(16.dp))
            Text("Upload Records", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MedHistryColors.TextPrimary)
        }

        // Upload progress / status banner
        when (val state = uploadState) {
            is UploadState.Idle -> {}
            is UploadState.RequestingUrl -> UploadProgressBanner("Preparing upload...", null)
            is UploadState.Uploading -> UploadProgressBanner("Uploading to cloud...", state.progress)
            is UploadState.Processing -> UploadProgressBanner("AI is analyzing your document...", null, pulsate = true)
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
            Text("\uD83D\uDCF7", fontSize = 48.sp)
            Spacer(Modifier.height(16.dp))
            Text("Take a Photo", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MedHistryColors.PrimaryDark)
            Text("Point your camera at the document", fontSize = 13.sp, color = MedHistryColors.TextSecondary)
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UploadOption("\uD83D\uDDBC\uFE0F", "Gallery", Modifier.weight(1f), enabled = !isUploading) {
                galleryLauncher.launch("image/*")
            }
            UploadOption("\uD83D\uDCC4", "PDF File", Modifier.weight(1f), enabled = !isUploading) {
                pdfLauncher.launch("application/pdf")
            }
        }

        Spacer(Modifier.height(24.dp))

        // Recent Uploads
        Text(
            "Recent Uploads",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MedHistryColors.TextPrimary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
        )

        if (recentDocs.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No uploads yet", fontSize = 14.sp, color = MedHistryColors.TextSecondary)
            }
        } else {
            recentDocs.forEach { doc ->
                DocumentRow(
                    doc = doc,
                    memberName = memberNameFor(doc.patientId),
                    onDelete = { pendingDeleteDoc = doc },
                )
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
            Text(
                if (doc.processingStatus == "completed") "\u2705 Upload complete — AI analysis done!"
                else "\u2705 Uploaded — AI is analyzing...",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF166534),
            )
            Text(
                "\u2715",
                fontSize = 16.sp,
                color = Color(0xFF166534),
                modifier = Modifier.clickable { onDismiss() },
            )
        }
        doc.aiSummary?.let { summary ->
            Spacer(Modifier.height(8.dp))
            Text(
                summary,
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
        Text(
            "\u274C $message",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF991B1B),
            modifier = Modifier.weight(1f),
        )
        Text(
            "\u2715",
            fontSize = 16.sp,
            color = Color(0xFF991B1B),
            modifier = Modifier.clickable { onDismiss() },
        )
    }
}

@Composable
private fun DocumentRow(doc: DocumentOut, memberName: String, onDelete: () -> Unit) {
    val icon = when (doc.fileType) {
        "pdf" -> "\uD83D\uDCC4"
        "jpg", "jpeg", "png" -> "\uD83D\uDDBC\uFE0F"
        else -> "\uD83D\uDCC1"
    }
    val iconBg = when (doc.fileType) {
        "pdf" -> Color(0xFFEBF5FF)
        else -> Color(0xFFF0FDF4)
    }
    val statusText = when (doc.processingStatus) {
        "completed" -> "Analyzed \u2713"
        "processing", "pending" -> "Processing..."
        "pending_upload" -> "Uploading..."
        "failed" -> "Failed \u2717"
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
        ) { Text(icon, fontSize = 20.sp) }
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
        Text(
            "\uD83D\uDDD1\uFE0F",
            fontSize = 18.sp,
            modifier = Modifier.clickable { onDelete() },
        )
    }
}

@Composable
private fun UploadOption(
    icon: String,
    label: String,
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
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, fontSize = 28.sp)
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MedHistryColors.TextPrimary)
    }
}
