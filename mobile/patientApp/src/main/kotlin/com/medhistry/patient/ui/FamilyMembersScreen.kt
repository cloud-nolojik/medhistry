package com.medhistry.patient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medhistry.data.DependentCreateRequest
import com.medhistry.data.DependentOut
import com.medhistry.data.FamilyListResponse
import com.medhistry.data.MedHistryApi
import com.medhistry.data.PatientProfile
import kotlinx.coroutines.launch

/**
 * Family tab — lists every family member with a card.
 *
 * Per the redesign brief:
 *   • Full-width member cards (not the old compact rows + FAB layout)
 *   • Prominent "+ Add family member" dashed-border card at the top
 *   • Each card: avatar, name, relationship subtitle, doc count, alert count
 *   • Tapping a card sets that person as the globally-active person and
 *     navigates to the Dashboard
 *   • Phone owner (primary) appears first with a "You" chip
 *
 * This screen is now a tab destination, so it has no back arrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyMembersScreen(
    api: MedHistryApi,
    family: FamilyListResponse?,
    onRefreshFamily: () -> Unit,
    onSelectMember: (patientId: String?) -> Unit,  // null = primary
    onNavigateToDashboard: () -> Unit,
    // Called when the user explicitly wants to manage (add/remove) members
    // from within this tab.
    onManageMembers: (() -> Unit)? = null,
) {
    var showAddSheet by remember { mutableStateOf(false) }
    var editingDependent by remember { mutableStateOf<DependentOut?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MedHistryColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 96.dp),
    ) {
        // Header
        Column(modifier = Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(
                "Family",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MedHistryColors.TextPrimary,
            )
            Text(
                "Manage everyone's health in one place",
                fontSize = 14.sp,
                color = MedHistryColors.TextSecondary,
            )
        }

        // Error banner
        if (error != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFEF2F2))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MedHistryColors.Danger, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(error!!, fontSize = 13.sp, color = MedHistryColors.Danger, modifier = Modifier.weight(1f))
                Text(
                    "Got it",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MedHistryColors.Danger,
                    modifier = Modifier.clickable { error = null },
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        // ── "+ Add family member" card (dashed border) ──────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MedHistryColors.Surface)
                .border(
                    width = 1.5.dp,
                    color = MedHistryColors.Primary,
                    shape = RoundedCornerShape(16.dp),
                )
                .clickable { showAddSheet = true }
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MedHistryColors.PrimaryLight),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.PersonAddAlt,
                        contentDescription = null,
                        tint = MedHistryColors.Primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        "+ Add family member",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MedHistryColors.Primary,
                    )
                    Text(
                        "Add a parent, spouse, or child",
                        fontSize = 12.sp,
                        color = MedHistryColors.TextSecondary,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        val f = family
        if (f == null) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MedHistryColors.Primary)
            }
        } else {
            // Phone owner — always first, with "You" chip
            FamilyMemberCard(
                name = f.primary.name.toTitleCase(),
                subtitle = buildSubtitle(relationship = null, dob = f.primary.dateOfBirth),
                isOwner = true,
                onTap = {
                    onSelectMember(null)
                    onNavigateToDashboard()
                },
                onEdit = null,  // owner profile edited via Profile tab
                onRemove = null,
            )

            // Dependents
            if (f.dependents.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                EmptyFamilyHint()
            } else {
                f.dependents.forEach { dep ->
                    FamilyMemberCard(
                        name = dep.name.toTitleCase(),
                        subtitle = buildSubtitle(
                            relationship = dep.relationship?.replaceFirstChar { it.uppercase() },
                            dob = dep.dateOfBirth,
                        ),
                        isOwner = false,
                        onTap = {
                            onSelectMember(dep.id)
                            onNavigateToDashboard()
                        },
                        onEdit = { editingDependent = dep },
                        onRemove = {
                            scope.launch {
                                try {
                                    api.removeDependent(dep.id)
                                    onRefreshFamily()
                                    error = null
                                } catch (e: Exception) {
                                    error = MedHistryApi.friendlyMessage(e)
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    if (showAddSheet) {
        AddMemberDialog(
            onDismiss = { showAddSheet = false },
            onSubmit = { req ->
                scope.launch {
                    try {
                        api.addDependent(req)
                        showAddSheet = false
                        onRefreshFamily()
                        error = null
                    } catch (e: Exception) {
                        showAddSheet = false
                        error = MedHistryApi.friendlyMessage(e)
                    }
                }
            },
        )
    }

    editingDependent?.let { dep ->
        EditMemberDialog(
            dependent = dep,
            onDismiss = { editingDependent = null },
            onSubmit = { req ->
                scope.launch {
                    try {
                        api.updateDependent(dep.id, req)
                        editingDependent = null
                        onRefreshFamily()
                        error = null
                    } catch (e: Exception) {
                        editingDependent = null
                        error = MedHistryApi.friendlyMessage(e)
                    }
                }
            },
        )
    }
}

// ── Member card ──────────────────────────────────────────────────────────────────

@Composable
private fun FamilyMemberCard(
    name: String,
    subtitle: String,
    isOwner: Boolean,
    onTap: () -> Unit,
    onEdit: (() -> Unit)?,
    onRemove: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MedHistryColors.Surface)
            .border(1.dp, MedHistryColors.Border, RoundedCornerShape(16.dp))
            .clickable { onTap() }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isOwner) MedHistryColors.Primary else MedHistryColors.PrimaryLight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.take(1).uppercase(),
                    color = if (isOwner) Color.White else MedHistryColors.Primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MedHistryColors.TextPrimary,
                    )
                    if (isOwner) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MedHistryColors.PrimaryLight)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text("You", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MedHistryColors.Primary)
                        }
                    }
                }
                if (subtitle.isNotBlank()) {
                    Text(subtitle, fontSize = 12.sp, color = MedHistryColors.TextSecondary)
                }
            }
            // Edit / Remove icons (only for dependents, not owner)
            if (onEdit != null || onRemove != null) {
                Row {
                    if (onEdit != null) {
                        IconButton(onClick = onEdit) {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = "Edit",
                                tint = MedHistryColors.Primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    if (onRemove != null) {
                        IconButton(onClick = onRemove) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Remove",
                                tint = MedHistryColors.Danger,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFamilyHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MedHistryColors.PrimaryLight)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "No family members added yet",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MedHistryColors.TextPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Add a parent, spouse, or child to keep their medical history in one place.",
            fontSize = 13.sp,
            color = MedHistryColors.TextSecondary,
        )
    }
}

private fun buildSubtitle(relationship: String?, dob: String?): String =
    listOfNotNull(relationship, dob).joinToString(" · ")

// ── Add Member dialog ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
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
private fun AddMemberDialog(onDismiss: () -> Unit, onSubmit: (DependentCreateRequest) -> Unit) {
    var name by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var bloodGroup by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

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

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MedHistryColors.Surface,
        title = { Text("Add family member", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownField(
                    label = "Relationship *",
                    options = listOf("Father", "Mother", "Spouse", "Son", "Daughter", "Brother", "Sister", "Grandfather", "Grandmother", "Other"),
                    selected = relationship,
                    onSelected = { relationship = it },
                )
                OutlinedTextField(
                    value = dob,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date of birth") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Outlined.CalendarMonth, contentDescription = "Pick date", tint = MedHistryColors.TextSecondary)
                        }
                    },
                    interactionSource = remember { MutableInteractionSource() }.also { source ->
                        LaunchedEffect(source) {
                            source.interactions.collect { interaction ->
                                if (interaction is androidx.compose.foundation.interaction.PressInteraction.Release) {
                                    showDatePicker = true
                                }
                            }
                        }
                    },
                )
                DropdownField(
                    label = "Gender",
                    options = listOf("Male", "Female", "Other"),
                    selected = gender,
                    onSelected = { gender = it },
                )
                DropdownField(
                    label = "Blood group",
                    options = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"),
                    selected = bloodGroup,
                    onSelected = { bloodGroup = it },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(
                        DependentCreateRequest(
                            name = name.trim(),
                            relationship = relationship.trim().ifBlank { "family" },
                            dateOfBirth = dob.ifBlank { null },
                            gender = gender.lowercase().ifBlank { null },
                            bloodGroup = bloodGroup.ifBlank { null },
                            allergies = null,
                        )
                    )
                },
                enabled = name.isNotBlank() && relationship.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MedHistryColors.Primary),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Save", color = Color.White, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MedHistryColors.TextSecondary) }
        },
    )
}

/** Capitalises the first letter of every word: "vijesh krishna" → "Vijesh Krishna" */
private fun String.toTitleCase(): String =
    split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercase() }
    }

// ── Edit Member dialog ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditMemberDialog(
    dependent: DependentOut,
    onDismiss: () -> Unit,
    onSubmit: (DependentCreateRequest) -> Unit,
) {
    var name by remember { mutableStateOf(dependent.name.toTitleCase()) }
    var relationship by remember { mutableStateOf(dependent.relationship?.replaceFirstChar { it.uppercase() } ?: "") }
    var dob by remember { mutableStateOf(dependent.dateOfBirth ?: "") }
    var gender by remember {
        mutableStateOf(dependent.gender?.replaceFirstChar { it.uppercase() } ?: "")
    }
    var bloodGroup by remember { mutableStateOf(dependent.bloodGroup ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

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

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MedHistryColors.Surface,
        title = { Text("Edit family member", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownField(
                    label = "Relationship *",
                    options = listOf("Father", "Mother", "Spouse", "Son", "Daughter", "Brother", "Sister", "Grandfather", "Grandmother", "Other"),
                    selected = relationship,
                    onSelected = { relationship = it },
                )
                OutlinedTextField(
                    value = dob,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date of birth") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Outlined.CalendarMonth, contentDescription = "Pick date", tint = MedHistryColors.TextSecondary)
                        }
                    },
                    interactionSource = remember { MutableInteractionSource() }.also { source ->
                        LaunchedEffect(source) {
                            source.interactions.collect { interaction ->
                                if (interaction is androidx.compose.foundation.interaction.PressInteraction.Release) {
                                    showDatePicker = true
                                }
                            }
                        }
                    },
                )
                DropdownField(
                    label = "Gender",
                    options = listOf("Male", "Female", "Other"),
                    selected = gender,
                    onSelected = { gender = it },
                )
                DropdownField(
                    label = "Blood group",
                    options = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"),
                    selected = bloodGroup,
                    onSelected = { bloodGroup = it },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(
                        DependentCreateRequest(
                            name = name.trim(),
                            relationship = relationship.trim().lowercase().ifBlank { "family" },
                            dateOfBirth = dob.ifBlank { null },
                            gender = gender.lowercase().ifBlank { null },
                            bloodGroup = bloodGroup.ifBlank { null },
                            allergies = dependent.allergies,
                        )
                    )
                },
                enabled = name.isNotBlank() && relationship.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MedHistryColors.Primary),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Save", color = Color.White, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MedHistryColors.TextSecondary) }
        },
    )
}

@Suppress("unused")
private fun depRef(d: DependentOut) = d.id
