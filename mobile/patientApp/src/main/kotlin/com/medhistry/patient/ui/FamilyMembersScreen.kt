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
import kotlinx.coroutines.launch

/**
 * Family Members screen — primary + dependents. A single account can manage
 * parents, spouse, and children. Each dependent is a Patient row with no
 * password, accessed only through the primary account.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyMembersScreen(
    api: MedHistryApi,
    onClose: () -> Unit,
) {
    var family by remember { mutableStateOf<FamilyListResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        try {
            family = api.listFamily()
            error = null
        } catch (e: Exception) {
            error = e.message ?: "Failed to load family"
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        containerColor = MedHistryColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("Family members", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    TextButton(onClick = onClose) {
                        Text("Back", color = MedHistryColors.Primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MedHistryColors.Background,
                    titleContentColor = MedHistryColors.TextPrimary,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = MedHistryColors.Primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("+  Add member", fontWeight = FontWeight.SemiBold)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                "Manage the people whose records you hold on this account.",
                fontSize = 13.sp,
                color = MedHistryColors.TextSecondary,
            )
            Spacer(Modifier.height(16.dp))

            when {
                isLoading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = MedHistryColors.Primary) }
                error != null -> Text(error!!, color = MedHistryColors.Danger)
                family != null -> {
                    val f = family!!
                    MemberRow(
                        initials = f.primary.name.take(1).uppercase(),
                        name = f.primary.name,
                        subtitle = "You (primary account)",
                        accent = true,
                        onRemove = null,
                    )
                    if (f.dependents.isEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        EmptyState()
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "DEPENDENTS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MedHistryColors.TextLight,
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                        f.dependents.forEach { dep ->
                            MemberRow(
                                initials = dep.name.take(1).uppercase(),
                                name = dep.name,
                                subtitle = listOfNotNull(
                                    dep.relationship?.replaceFirstChar { it.uppercase() },
                                    dep.dateOfBirth,
                                ).joinToString(" \u2022 "),
                                accent = false,
                                onRemove = {
                                    scope.launch {
                                        try {
                                            api.removeDependent(dep.id)
                                            reload()
                                        } catch (e: Exception) {
                                            error = e.message
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(96.dp)) // space for FAB
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
                        reload()
                    } catch (e: Exception) {
                        error = e.message
                    }
                }
            },
        )
    }
}

@Composable
private fun MemberRow(
    initials: String,
    name: String,
    subtitle: String,
    accent: Boolean,
    onRemove: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MedHistryColors.Surface)
            .border(1.dp, MedHistryColors.Border, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (accent) MedHistryColors.Primary else MedHistryColors.PrimaryLight),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initials,
                color = if (accent) Color.White else MedHistryColors.Primary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = MedHistryColors.TextPrimary,
            )
            if (subtitle.isNotBlank()) {
                Text(subtitle, fontSize = 12.sp, color = MedHistryColors.TextSecondary)
            }
        }
        if (onRemove != null) {
            TextButton(onClick = onRemove) {
                Text("Remove", color = MedHistryColors.Danger, fontSize = 13.sp)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MedHistryColors.PrimaryLight)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "No family members yet",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MedHistryColors.TextPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Add a parent, spouse or child to keep their medical history in one place.",
            fontSize = 13.sp,
            color = MedHistryColors.TextSecondary,
        )
    }
}

/**
 * Reusable dropdown field used for Relationship, Gender, and Blood Group.
 */
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

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMemberDialog(
    onDismiss: () -> Unit,
    onSubmit: (DependentCreateRequest) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var bloodGroup by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    val relationshipOptions = listOf("Father", "Mother", "Spouse", "Son", "Daughter", "Brother", "Sister", "Grandfather", "Grandmother", "Other")
    val genderOptions = listOf("Male", "Female", "Other")
    val bloodGroupOptions = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")

    // Date picker state — default to today
    val datePickerState = rememberDatePickerState()

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val instant = java.time.Instant.ofEpochMilli(millis)
                        val localDate = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        dob = localDate.toString() // YYYY-MM-DD
                    }
                    showDatePicker = false
                }) {
                    Text("OK", color = MedHistryColors.Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = MedHistryColors.TextSecondary)
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MedHistryColors.Surface,
        title = {
            Text("Add family member", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
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
                    options = relationshipOptions,
                    selected = relationship,
                    onSelected = { relationship = it },
                )
                // Date of birth with date picker
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
                            Text("\uD83D\uDCC5", fontSize = 18.sp) // calendar emoji
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
                    options = genderOptions,
                    selected = gender,
                    onSelected = { gender = it },
                )
                DropdownField(
                    label = "Blood group",
                    options = bloodGroupOptions,
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
            ) {
                Text("Add", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MedHistryColors.TextSecondary)
            }
        },
    )
}

// Unused import guard (kept to suppress compiler warnings for dep reference).
@Suppress("unused")
private fun depRef(d: DependentOut) = d.id
