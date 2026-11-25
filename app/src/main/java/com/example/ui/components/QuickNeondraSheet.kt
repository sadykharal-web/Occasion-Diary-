package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.ActivityDirection
import com.example.data.DropdownCatalog
import com.example.data.FamilyIdentityMatcher

private val QUICK_NEONDRA_EVENT_TYPES = listOf("شادی", "نکاح", "عقیقہ", "سالگرہ")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickNeondraSheet(
    catalog: DropdownCatalog,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSaveNeondra: (
        name: String,
        fatherName: String,
        address: String,
        eventType: String,
        eventDetail: String,
        amount: Long,
        direction: ActivityDirection
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        QuickNeondraContent(
            catalog = catalog,
            isSaving = isSaving,
            onDismiss = onDismiss,
            onSaveNeondra = onSaveNeondra,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickNeondraContent(
    catalog: DropdownCatalog,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSaveNeondra: (
        name: String,
        fatherName: String,
        address: String,
        eventType: String,
        eventDetail: String,
        amount: Long,
        direction: ActivityDirection
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    var fatherName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var eventType by remember { mutableStateOf(QUICK_NEONDRA_EVENT_TYPES.first()) }
    var eventDetail by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf(ActivityDirection.OUT) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val nameOptions = remember(catalog.families, catalog.names) {
        val fromFamilies = catalog.families.map { fam ->
            DropdownOptionItem(
                primaryValue = fam.name,
                subtitle = buildList {
                    if (fam.familyId.isNotBlank()) add("FN ${fam.familyId}")
                    if (fam.fatherName.isNotBlank()) add("Father: ${fam.fatherName}")
                    if (fam.address.isNotBlank()) add(fam.address)
                }.joinToString(" • "),
                payloadTag = fam.id
            )
        }
        val extraNames = catalog.names
            .filter { n -> catalog.families.none { it.name.equals(n, ignoreCase = true) } }
            .map { DropdownOptionItem(primaryValue = it) }
        fromFamilies + extraNames
    }

    val fatherOptions = remember(catalog.fatherNames) {
        catalog.fatherNames.map { DropdownOptionItem(primaryValue = it) }
    }

    val addressOptions = remember(catalog.addresses) {
        catalog.addresses.map { DropdownOptionItem(primaryValue = it) }
    }

    val matchedFamily = remember(catalog.families, name, fatherName, address) {
        if (name.isBlank() || fatherName.isBlank() || address.isBlank()) {
            null
        } else {
            FamilyIdentityMatcher.findMatchingFamily(
                families = catalog.families,
                name = name,
                fatherName = fatherName,
                address = address
            )
        }
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AccountBalanceWallet,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = "Neondra Entry",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Matching Name, Father's Name & Address automatically links to that family.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_quick_neondra_button")
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close Neondra")
            }
        }

        Surface(
            color = if (matchedFamily != null) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("quick_neondra_fn_preview")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Badge,
                    contentDescription = null,
                    tint = if (matchedFamily != null) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (matchedFamily != null) {
                        "FN: ${matchedFamily.familyId} • Matched Existing Family"
                    } else {
                        "FN: ${catalog.nextSuggestedFamilyId} • Auto-assigned on save"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (matchedFamily != null) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
            }
        }

        AutoCompleteDropdownField(
            value = name,
            onValueChange = {
                name = it.take(100)
                if (validationError != null) validationError = null
            },
            label = "Name *",
            placeholder = "e.g. Rajab Ali",
            options = nameOptions,
            onOptionSelected = { opt ->
                name = opt.primaryValue
                val fam = catalog.families.firstOrNull {
                    it.id == opt.payloadTag || it.name.equals(opt.primaryValue, ignoreCase = true)
                }
                if (fam != null) {
                    if (fam.fatherName.isNotBlank()) fatherName = fam.fatherName
                    if (fam.address.isNotBlank()) address = fam.address
                }
                if (validationError != null) validationError = null
            },
            leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
            modifier = Modifier.testTag("quick_neondra_name_input")
        )

        AutoCompleteDropdownField(
            value = fatherName,
            onValueChange = {
                fatherName = it.take(100)
                if (validationError != null) validationError = null
            },
            label = "Father's Name *",
            placeholder = "e.g. Muhammad Ramzan",
            options = fatherOptions,
            onOptionSelected = { opt ->
                fatherName = opt.primaryValue
                if (validationError != null) validationError = null
            },
            modifier = Modifier.testTag("quick_neondra_father_input")
        )

        AutoCompleteDropdownField(
            value = address,
            onValueChange = {
                address = it.take(200)
                if (validationError != null) validationError = null
            },
            label = "Address *",
            placeholder = "e.g. Kot Ramzan",
            options = addressOptions,
            onOptionSelected = { opt ->
                address = opt.primaryValue
                if (validationError != null) validationError = null
            },
            leadingIcon = { Icon(Icons.Rounded.LocationOn, contentDescription = null) },
            modifier = Modifier.testTag("quick_neondra_address_input")
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Event Type *",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                QUICK_NEONDRA_EVENT_TYPES.forEach { typeName ->
                    val isSelected = eventType == typeName
                    val (catColor, catIcon) = catalog.resolveCategoryVisual(typeName)
                    FilterChip(
                        selected = isSelected,
                        onClick = { eventType = typeName },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isSelected) Icons.Rounded.Check else catIcon,
                                contentDescription = null,
                                tint = catColor,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = typeName,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = catColor.copy(alpha = 0.16f),
                            selectedLabelColor = catColor
                        ),
                        modifier = Modifier.testTag("quick_neondra_event_type_${typeName.lowercase()}")
                    )
                }
            }
        }

        OutlinedTextField(
            value = eventDetail,
            onValueChange = {
                eventDetail = it.take(200)
                if (validationError != null) validationError = null
            },
            label = { Text("Event Name") },
            placeholder = { Text("e.g. Ali Raza Wedding") },
            leadingIcon = { Icon(Icons.Rounded.Description, contentDescription = null) },
            minLines = 1,
            maxLines = 2,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("quick_neondra_event_detail_input")
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Transaction Type (In / Out) *",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ActivityDirection.entries.forEach { dir ->
                    val selected = direction == dir
                    val dirIcon = if (dir == ActivityDirection.IN) {
                        Icons.Rounded.ArrowDownward
                    } else {
                        Icons.Rounded.ArrowUpward
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        },
                        border = if (selected) {
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        } else null,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { direction = dir }
                            .testTag(
                                if (dir == ActivityDirection.IN) "quick_neondra_dir_in"
                                else "quick_neondra_dir_out"
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (selected) Icons.Rounded.Check else dirIcon,
                                contentDescription = null,
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = dir.displayLabel,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = amountText,
            onValueChange = { input ->
                amountText = input.filter { it.isDigit() }.take(9)
                if (validationError != null) validationError = null
            },
            label = { Text("Neondra Amount *") },
            placeholder = { Text("e.g. 5000") },
            leadingIcon = { Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("quick_neondra_amount_input")
        )

        if (validationError != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = validationError!!,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Button(
            onClick = {
                val cleanName = name.trim()
                val cleanFather = fatherName.trim()
                val cleanAddress = address.trim()
                val cleanDetail = eventDetail.trim()
                val amount = amountText.toLongOrNull() ?: 0L

                when {
                    cleanName.isEmpty() -> validationError = "Please enter Name."
                    cleanFather.isEmpty() -> validationError = "Please enter Father's Name."
                    cleanAddress.isEmpty() -> validationError = "Please enter Address."
                    amount <= 0L -> validationError = "Please enter a valid Neondra amount."
                    else -> {
                        validationError = null
                        onSaveNeondra(
                            cleanName,
                            cleanFather,
                            cleanAddress,
                            eventType,
                            cleanDetail,
                            amount,
                            direction
                        )
                    }
                }
            },
            enabled = !isSaving,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("save_quick_neondra_button")
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = "Save Neondra",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
