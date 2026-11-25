package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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

@Composable
fun QuickNeondraContent(
    catalog: DropdownCatalog,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSaveNeondra: (
        name: String,
        fatherName: String,
        address: String,
        amount: Long,
        direction: ActivityDirection
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    var fatherName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
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
                        text = "Quick Neondra Entry",
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
                Icon(Icons.Rounded.Close, contentDescription = "Close Quick Neondra")
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

        if (name.isNotBlank() && fatherName.isNotBlank() && address.isNotBlank()) {
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
                            "Matched Existing Family • FN ${matchedFamily.familyId}"
                        } else {
                            "New Family • Will assign unique FN ${catalog.nextSuggestedFamilyId}"
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ActivityDirection.entries.forEach { dir ->
                val selected = direction == dir
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
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = dir.displayLabel,
                            style = MaterialTheme.typography.labelLarge,
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
                val amount = amountText.toLongOrNull() ?: 0L

                when {
                    cleanName.isEmpty() -> validationError = "Please enter Name."
                    cleanFather.isEmpty() -> validationError = "Please enter Father's Name."
                    cleanAddress.isEmpty() -> validationError = "Please enter Address."
                    amount <= 0L -> validationError = "Please enter a valid Neondra amount."
                    else -> {
                        validationError = null
                        onSaveNeondra(cleanName, cleanFather, cleanAddress, amount, direction)
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
