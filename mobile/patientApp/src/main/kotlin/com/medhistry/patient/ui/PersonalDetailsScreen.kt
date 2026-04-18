package com.medhistry.patient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medhistry.data.MedHistryApi
import com.medhistry.data.ProfileUpdateRequest
import kotlinx.coroutines.launch

/**
 * Personal Details editor — lets the patient update their name, date of birth,
 * gender, blood group, and allergies.
 *
 * Loads the current profile from the API on first composition, then saves via PATCH /patients/me.
 * [onSaved] is called with the updated name so the caller can refresh the session.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalDetailsScreen(
    api: MedHistryApi,
    onBack: () -> Unit,
    onSaved: (newName: String) -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }

    // Editable fields
    var name by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var bloodGroup by remember { mutableStateOf("") }
    var allergies by remember { mutableStateOf("") }

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    val scope = rememberCoroutineScope()

    // Load current profile
    LaunchedEffect(Unit) {
        try {
            val profile = api.getMyProfile()
            name = profile.name.toTitleCase()
            dob = profile.dateOfBirth ?: ""
            gender = profile.gender?.replaceFirstChar { it.uppercase() } ?: ""
            bloodGroup = profile.bloodGroup ?: ""
            allergies = profile.allergies ?: ""
        } catch (e: Exception) {
            error = MedHistryApi.friendlyMessage(e)
        } finally {
            loading = false
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val instant = java.time.Instant.ofEpochMilli(millis)
                        val localDate = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        dob = localDate.toString()
                    }
                    showDatePicker = false
                }) { Text("OK", color = MedHistryColors.Primary) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = MedHistryColors.TextSecondary)
                }
            },
        ) { DatePicker(state = datePickerState) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MedHistryColors.Background),
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MedHistryColors.TextPrimary,
                modifier = Modifier.size(24.dp).clickable { onBack() },
            )
            Spacer(Modifier.width(16.dp))
            Text(
                "Personal Details",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MedHistryColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
        }

        if (loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MedHistryColors.Primary)
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Avatar preview
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MedHistryColors.Primary)
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.take(1).uppercase().ifBlank { "?" },
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Error / success banners
            if (error != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFEF2F2))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.ErrorOutline, null, tint = MedHistryColors.Danger, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(error!!, fontSize = 13.sp, color = MedHistryColors.Danger, modifier = Modifier.weight(1f))
                    Text("Got it", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MedHistryColors.Danger,
                        modifier = Modifier.clickable { error = null })
                }
            }
            if (success) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFF0FDF4))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Saved successfully", fontSize = 13.sp, color = Color(0xFF16A34A))
                }
            }

            // ── Form fields ──────────────────────────────────────────────────
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = outlinedTextFieldColors(),
            )

            // Date of birth — read-only, opens date picker
            OutlinedTextField(
                value = dob,
                onValueChange = {},
                readOnly = true,
                label = { Text("Date of birth") },
                placeholder = { Text("YYYY-MM-DD") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Outlined.CalendarMonth, "Pick date", tint = MedHistryColors.TextSecondary)
                    }
                },
                interactionSource = remember { MutableInteractionSource() }.also { src ->
                    LaunchedEffect(src) {
                        src.interactions.collect { interaction ->
                            if (interaction is androidx.compose.foundation.interaction.PressInteraction.Release) {
                                showDatePicker = true
                            }
                        }
                    }
                },
                colors = outlinedTextFieldColors(),
            )

            DropdownFormField(
                label = "Gender",
                options = listOf("Male", "Female", "Other"),
                selected = gender,
                onSelected = { gender = it },
            )

            DropdownFormField(
                label = "Blood group",
                options = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"),
                selected = bloodGroup,
                onSelected = { bloodGroup = it },
            )

            OutlinedTextField(
                value = allergies,
                onValueChange = { allergies = it },
                label = { Text("Allergies") },
                placeholder = { Text("e.g. Penicillin, Peanuts") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                colors = outlinedTextFieldColors(),
            )

            Spacer(Modifier.height(8.dp))
        }

        // ── Save button ──────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MedHistryColors.Background)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Button(
                onClick = {
                    scope.launch {
                        saving = true
                        error = null
                        success = false
                        try {
                            val updated = api.updateMyProfile(
                                ProfileUpdateRequest(
                                    name = name.trim().ifBlank { null },
                                    dateOfBirth = dob.ifBlank { null },
                                    gender = gender.lowercase().ifBlank { null },
                                    bloodGroup = bloodGroup.ifBlank { null },
                                    allergies = allergies.trim().ifBlank { null },
                                )
                            )
                            success = true
                            onSaved(updated.name)
                        } catch (e: Exception) {
                            error = MedHistryApi.friendlyMessage(e)
                        } finally {
                            saving = false
                        }
                    }
                },
                enabled = !saving && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MedHistryColors.Primary),
                shape = RoundedCornerShape(14.dp),
            ) {
                if (saving) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save changes", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownFormField(
    label: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            colors = outlinedTextFieldColors(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelected(option); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun outlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MedHistryColors.Primary,
    focusedLabelColor = MedHistryColors.Primary,
    cursorColor = MedHistryColors.Primary,
)

private fun String.toTitleCase(): String =
    split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
