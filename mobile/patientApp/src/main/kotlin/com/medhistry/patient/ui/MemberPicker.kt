package com.medhistry.patient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medhistry.data.FamilyListResponse
import kotlinx.coroutines.launch

/**
 * Family-member selector used on Records, Medicines, and Lab Results.
 *
 * Rendering strategy (adapts to family size so it still works for joint
 * households):
 *
 *  - **No dependents** → nothing rendered. A single-member household has
 *    nothing to filter.
 *  - **1–4 dependents** → horizontal chip row [primary] [deps…]
 *    [+ Family member]. Fast and visible; the "+ Family member" chip
 *    at the end uses the same label and styling as the Home tab so the
 *    affordance is instantly recognizable across every screen.
 *  - **5+ dependents** → a single header pill "[initials] FirstName ▾"
 *    that opens a bottom sheet listing everyone vertically, with
 *    "+ Family member" pinned at the bottom of the sheet. Works at
 *    any family size and keeps the tab header clean.
 *
 * [selectedId] may be any id in the family (primary or a dependent).
 * Callers whose internal state uses null-for-primary should normalize
 * at the boundary: `selectedId = activePatientId ?: family.primary.id`.
 */
@Composable
fun MemberPicker(
    family: FamilyListResponse?,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onAddFamilyMember: () -> Unit,
) {
    val f = family ?: return
    if (f.dependents.isEmpty()) return

    // Threshold above which the chip row becomes awkward. Joint-family
    // India households can hit 8–10 members, so anything >=5 goes to the
    // vertical sheet instead of forcing horizontal-scroll discovery.
    val useSheet = f.dependents.size >= 5
    if (useSheet) {
        MemberPickerSheet(f, selectedId, onSelect, onAddFamilyMember)
    } else {
        MemberPickerChips(f, selectedId, onSelect, onAddFamilyMember)
    }
}

// --- Chip row variant (≤4 dependents) ---------------------------------------

@Composable
private fun MemberPickerChips(
    family: FamilyListResponse,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onAddFamilyMember: () -> Unit,
) {
    val primaryFirstName = firstName(family.primary.name, fallback = "Me")
    val resolvedSelected = selectedId ?: family.primary.id

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MemberChip(primaryFirstName, resolvedSelected == family.primary.id) {
            onSelect(family.primary.id)
        }
        family.dependents.forEach { dep ->
            MemberChip(firstName(dep.name, fallback = "—"), resolvedSelected == dep.id) {
                onSelect(dep.id)
            }
        }
        // Shared add-family chip — same composable used on Home so the
        // affordance looks identical across every screen.
        AddFamilyMemberChip(onClick = onAddFamilyMember)
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun MemberChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) MedHistryColors.Primary else MedHistryColors.Surface)
            .border(
                1.dp,
                if (selected) MedHistryColors.Primary else MedHistryColors.Border,
                RoundedCornerShape(20.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else MedHistryColors.TextPrimary,
        )
    }
}

/**
 * Shared "+ Family member" call-to-action chip.
 *
 * Used by every screen that lets the patient add a family member from
 * the chip row (Home, Records, Medicines, Lab Results). Lives here so
 * the affordance can't visually drift between screens — change the
 * styling or label once and it updates everywhere.
 *
 * Style: filled primary-light background with solid primary border and
 * bold primary text — matches the Home screen's "add family" CTA so
 * users see the same button everywhere.
 */
@Composable
fun AddFamilyMemberChip(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MedHistryColors.PrimaryLight)
            .border(
                1.dp,
                MedHistryColors.Primary,
                RoundedCornerShape(20.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            "+ Family member",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MedHistryColors.Primary,
        )
    }
}

// --- Bottom sheet variant (5+ dependents) -----------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberPickerSheet(
    family: FamilyListResponse,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onAddFamilyMember: () -> Unit,
) {
    val resolvedSelected = selectedId ?: family.primary.id
    val selectedName: String = when (resolvedSelected) {
        family.primary.id -> family.primary.name
        else -> family.dependents.find { it.id == resolvedSelected }?.name ?: family.primary.name
    }
    val selectedFirstName = firstName(selectedName, fallback = "Me")

    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // Header pill. Tappable row: "Showing records for · [avatar] FirstName ▾"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Showing records for",
            fontSize = 12.sp,
            color = MedHistryColors.TextLight,
        )
        Spacer(Modifier.width(8.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MedHistryColors.Surface)
                .border(1.dp, MedHistryColors.Border, RoundedCornerShape(20.dp))
                .clickable { showSheet = true }
                .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InitialsAvatar(selectedName, size = 22.dp, fontSize = 10.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                selectedFirstName,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MedHistryColors.TextPrimary,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = MedHistryColors.TextSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
    Spacer(Modifier.height(8.dp))

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = MedHistryColors.Surface,
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    "Select family member",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MedHistryColors.TextPrimary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )

                MemberSheetRow(
                    name = family.primary.name,
                    subtitle = "Me",
                    selected = resolvedSelected == family.primary.id,
                    onClick = {
                        onSelect(family.primary.id)
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) showSheet = false
                        }
                    },
                )
                family.dependents.forEach { dep ->
                    MemberSheetRow(
                        name = dep.name,
                        subtitle = dep.relationship?.replaceFirstChar { it.uppercase() },
                        selected = resolvedSelected == dep.id,
                        onClick = {
                            onSelect(dep.id)
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                if (!sheetState.isVisible) showSheet = false
                            }
                        },
                    )
                }

                // Divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .height(1.dp)
                        .background(MedHistryColors.Border),
                )

                // Pinned add-family row. Text + icon, same primary accent
                // as the other "add" affordances so it reads as an action.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                if (!sheetState.isVisible) {
                                    showSheet = false
                                    onAddFamilyMember()
                                }
                            }
                        }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MedHistryColors.Primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PersonAddAlt,
                            contentDescription = null,
                            tint = MedHistryColors.Primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "+ Family member",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MedHistryColors.Primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun MemberSheetRow(
    name: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MedHistryColors.Primary.copy(alpha = 0.08f) else Color.Transparent,
            )
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InitialsAvatar(name, size = 36.dp, fontSize = 14.sp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = MedHistryColors.TextPrimary,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = MedHistryColors.TextLight,
                )
            }
        }
        if (selected) {
            // Small filled dot, instead of a checkmark — less visual noise
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MedHistryColors.Primary),
            )
        }
    }
}

// --- Shared helpers ---------------------------------------------------------

@Composable
private fun InitialsAvatar(
    name: String,
    size: androidx.compose.ui.unit.Dp,
    fontSize: androidx.compose.ui.unit.TextUnit,
) {
    val initials = remember(name) {
        name.trim().split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "?" }
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MedHistryColors.Primary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = MedHistryColors.Primary,
        )
    }
}

private fun firstName(fullName: String, fallback: String): String =
    fullName.split(" ").firstOrNull()?.takeIf { it.isNotBlank() } ?: fallback
