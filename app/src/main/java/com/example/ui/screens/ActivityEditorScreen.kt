package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.ActivityCategory
import com.example.data.ActivityDirection
import com.example.data.ActivityRecord
import com.example.data.DropdownCatalog
import com.example.data.FamilyIdentityMatcher
import com.example.data.FamilyProfile
import com.example.reminders.CalendarIntentHelper
import com.example.ui.components.AutoCompleteDropdownField
import com.example.ui.components.DropdownOptionItem
import com.example.ui.components.adaptiveCategoryColor
import com.example.ui.components.formatDisplayDate
import com.example.ui.components.formatNeondraAmount
import com.example.ui.components.formatReminderOffsetLabel
import com.example.ui.theme.LocalAppDarkTheme
import com.example.ui.theme.NotoArabicFamily
import java.util.Calendar

private val SUIT_COUNT_OPTIONS = (1..20).toList()
private val KG_COUNT_OPTIONS = (1..25).toList()

fun appendHomeGiftItem(existing: String, newItem: String): String {
    val cleanItem = newItem.trim()
    if (cleanItem.isEmpty()) return existing
    val cleanExisting = existing.trimEnd().trimEnd(',').trimEnd()
    return if (cleanExisting.isEmpty()) cleanItem else "$cleanExisting, $cleanItem"
}

fun formatSuitGiftItem(label: String, count: Int): String {
    val safeCount = count.coerceAtLeast(1)
    return "$safeCount $label"
}

fun formatKgGiftItem(label: String, kg: Int): String {
    val safeKg = kg.coerceAtLeast(1)
    return "$safeKg کلوگرام $label"
}

private val REMINDER_OFFSETS = listOf(0L, 60L, 180L, 1440L, 2880L)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityEditorScreen(
    initialRecord: ActivityRecord?,
    prefilledFamily: FamilyProfile?,
    prefilledDateMillis: Long?,
    prefilledEventName: String? = null,
    prefilledEventType: String? = null,
    catalog: DropdownCatalog,
    isSaving: Boolean,
    onBack: () -> Unit,
    onQuickCreateCategory: (ActivityCategory) -> Unit,
    onSave: (ActivityRecord, openCalendarAfterSave: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val isDark = LocalAppDarkTheme.current
    val isEditing = initialRecord != null && initialRecord.id.isNotBlank()

    var selectedFamilyDocId by remember {
        mutableStateOf(initialRecord?.familyDocId ?: prefilledFamily?.id)
    }
    var name by remember {
        mutableStateOf(initialRecord?.name ?: prefilledFamily?.name ?: "")
    }
    var familyId by remember {
        mutableStateOf(initialRecord?.familyId ?: prefilledFamily?.familyId ?: "")
    }
    var fatherName by remember {
        mutableStateOf(initialRecord?.fatherName ?: prefilledFamily?.fatherName ?: "")
    }
    var address by remember {
        mutableStateOf(initialRecord?.address ?: prefilledFamily?.address ?: "")
    }
    var eventType by remember {
        mutableStateOf(
            initialRecord?.eventType
                ?: prefilledEventType?.takeIf { it.isNotBlank() }
                ?: catalog.eventTypes.firstOrNull()
                ?: "شادی"
        )
    }
    var eventDetail by remember {
        mutableStateOf(initialRecord?.eventDetail ?: prefilledEventName ?: "")
    }
    var eventDateMillis by remember {
        mutableLongStateOf(
            initialRecord?.eventDateMillis
                ?: prefilledDateMillis
                ?: System.currentTimeMillis()
        )
    }
    var direction by remember {
        mutableStateOf(initialRecord?.parsedDirection ?: ActivityDirection.OUT)
    }
    var homeItems by remember {
        mutableStateOf(initialRecord?.homeItems ?: "")
    }
    var selectedLadySuitCount by remember { mutableIntStateOf(1) }
    var selectedGentsSuitCount by remember { mutableIntStateOf(1) }
    var selectedFruitsKg by remember { mutableIntStateOf(1) }
    var selectedSweetsKg by remember { mutableIntStateOf(1) }
    var cashAmountText by remember {
        mutableStateOf(initialRecord?.cashAmount?.takeIf { it > 0L }?.toString() ?: "")
    }
    var haarAmountText by remember {
        mutableStateOf(initialRecord?.haarAmount?.takeIf { it > 0L }?.toString() ?: "")
    }
    var salamiAmountText by remember {
        mutableStateOf(initialRecord?.salamiAmount?.takeIf { it > 0L }?.toString() ?: "")
    }
    var neondraText by remember {
        mutableStateOf(
            initialRecord?.neondraAmount?.takeIf { it > 0L }?.toString() ?: ""
        )
    }
    var reminderEnabled by remember {
        mutableStateOf(initialRecord?.reminderEnabled ?: false)
    }
    var reminderMinutesBefore by remember {
        mutableLongStateOf(
            initialRecord?.reminderMinutesBefore?.takeIf { it > 0L } ?: 1440L
        )
    }
    var alsoOpenDeviceCalendar by remember {
        mutableStateOf(false)
    }
    var validationError by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showQuickCategoryDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        reminderEnabled = granted
    }

    if (showQuickCategoryDialog) {
        CategoryEditorDialog(
            initial = null,
            onDismiss = { showQuickCategoryDialog = false },
            onConfirm = { newCat ->
                showQuickCategoryDialog = false
                eventType = newCat.name
                onQuickCreateCategory(newCat)
            }
        )
    }

    val liveMatchedFamily = remember(catalog.families, name, fatherName, address) {
        FamilyIdentityMatcher.findMatchingFamily(
            families = catalog.families,
            name = name,
            fatherName = fatherName,
            address = address
        )
    }

    val nameOptions = remember(catalog.families, catalog.names) {
        val fromFamilies = catalog.families.map { fam ->
            DropdownOptionItem(
                primaryValue = fam.name,
                subtitle = buildList {
                    if (fam.familyId.isNotBlank()) add("FN: ${fam.familyId}")
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

    val fnOptions = remember(catalog.families, catalog.familyIds) {
        val fromFamilies = catalog.families.map { fam ->
            DropdownOptionItem(
                primaryValue = fam.familyId,
                subtitle = buildList {
                    add(fam.name)
                    if (fam.fatherName.isNotBlank()) add("Father: ${fam.fatherName}")
                    if (fam.address.isNotBlank()) add(fam.address)
                }.joinToString(" • "),
                payloadTag = fam.id
            )
        }
        val extraFns = catalog.familyIds
            .filter { fn -> catalog.families.none { it.familyId == fn } }
            .map { DropdownOptionItem(primaryValue = it) }
        fromFamilies + extraFns
    }

    val fatherOptions = remember(catalog.fatherNames) {
        catalog.fatherNames.map { DropdownOptionItem(primaryValue = it) }
    }

    val addressOptions = remember(catalog.addresses, catalog.families) {
        catalog.addresses.map { addr ->
            val count = catalog.families.count { it.address.equals(addr, ignoreCase = true) }
            DropdownOptionItem(
                primaryValue = addr,
                subtitle = if (count > 0) "$count families" else null
            )
        }
    }

    val eventOptions = remember(catalog.eventTypes) {
        catalog.eventTypes.map { DropdownOptionItem(primaryValue = it) }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = eventDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val picked = datePickerState.selectedDateMillis
                        if (picked != null) {
                            val oldCal = Calendar.getInstance().apply { timeInMillis = eventDateMillis }
                            val newCal = Calendar.getInstance().apply {
                                timeInMillis = picked
                                set(Calendar.HOUR_OF_DAY, oldCal.get(Calendar.HOUR_OF_DAY))
                                set(Calendar.MINUTE, oldCal.get(Calendar.MINUTE))
                                set(Calendar.SECOND, 0)
                            }
                            eventDateMillis = newCal.timeInMillis
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Select")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditing) "Edit Ledger Entry" else "New Ledger Entry",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("editor_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Person & Family Details",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            AutoCompleteDropdownField(
                                value = familyId,
                                onValueChange = { familyId = it },
                                label = "FN",
                                placeholder = "e.g. ${catalog.nextSuggestedFamilyId}",
                                options = fnOptions,
                                onOptionSelected = { opt ->
                                    familyId = opt.primaryValue
                                    val matched = catalog.families.firstOrNull {
                                        it.id == opt.payloadTag || it.familyId == opt.primaryValue
                                    }
                                    if (matched != null) {
                                        selectedFamilyDocId = matched.id
                                        name = matched.name
                                        if (matched.fatherName.isNotBlank()) fatherName = matched.fatherName
                                        if (matched.address.isNotBlank()) address = matched.address
                                    }
                                },
                                leadingIcon = { Icon(Icons.Rounded.Badge, contentDescription = null) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("editor_fn_input")
                            )

                            AssistChip(
                                onClick = { familyId = catalog.nextSuggestedFamilyId },
                                label = { Text("Next FN: ${catalog.nextSuggestedFamilyId}") },
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        AutoCompleteDropdownField(
                            value = name,
                            onValueChange = {
                                name = it
                                selectedFamilyDocId = null
                            },
                            label = "Name *",
                            placeholder = "e.g. Rajab Ali",
                            supportingText = "Same Name + Father's Name + Address automatically share one FN",
                            options = nameOptions,
                            onOptionSelected = { opt ->
                                name = opt.primaryValue
                                val matched = catalog.families.firstOrNull {
                                    it.id == opt.payloadTag || it.name.equals(opt.primaryValue, ignoreCase = true)
                                }
                                if (matched != null) {
                                    selectedFamilyDocId = matched.id
                                    if (matched.familyId.isNotBlank()) familyId = matched.familyId
                                    if (matched.fatherName.isNotBlank()) fatherName = matched.fatherName
                                    if (matched.address.isNotBlank()) address = matched.address
                                }
                            },
                            leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                            modifier = Modifier.testTag("editor_name_input")
                        )

                        AutoCompleteDropdownField(
                            value = fatherName,
                            onValueChange = {
                                fatherName = it
                                selectedFamilyDocId = null
                            },
                            label = "Father's Name",
                            placeholder = "e.g. Muhammad Ramzan",
                            options = fatherOptions,
                            onOptionSelected = { opt ->
                                fatherName = opt.primaryValue
                                selectedFamilyDocId = null
                            }
                        )

                        AutoCompleteDropdownField(
                            value = address,
                            onValueChange = {
                                address = it
                                selectedFamilyDocId = null
                            },
                            label = "Address",
                            placeholder = "e.g. Kot Ramzan",
                            options = addressOptions,
                            onOptionSelected = { opt ->
                                address = opt.primaryValue
                                selectedFamilyDocId = null
                            },
                            leadingIcon = { Icon(Icons.Rounded.LocationOn, contentDescription = null) },
                            modifier = Modifier.testTag("editor_address_input")
                        )

                        if (name.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (liveMatchedFamily != null) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (liveMatchedFamily != null) {
                                            Icons.Rounded.CheckCircle
                                        } else {
                                            Icons.Rounded.PersonAdd
                                        },
                                        contentDescription = null,
                                        tint = if (liveMatchedFamily != null) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.secondary
                                        },
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = if (liveMatchedFamily != null) {
                                            "Matched existing 3-field family • FN ${liveMatchedFamily.familyId}"
                                        } else {
                                            "New family (Name + Father + Address) • Will use unique FN ${familyId.trim().ifBlank { catalog.nextSuggestedFamilyId }}"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Event Type",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = { showQuickCategoryDialog = true }) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New Event Type")
                            }
                        }

                        AutoCompleteDropdownField(
                            value = eventType,
                            onValueChange = { eventType = it },
                            label = "Event Type *",
                            placeholder = "e.g. شادی، نکاح، عقیقہ، سالگرہ",
                            options = eventOptions,
                            onOptionSelected = { opt -> eventType = opt.primaryValue },
                            leadingIcon = {
                                val (rawCatColor, catIcon) = catalog.resolveCategoryVisual(eventType)
                                Icon(catIcon, contentDescription = null, tint = adaptiveCategoryColor(rawCatColor, isDark))
                            },
                            modifier = Modifier.testTag("editor_event_type_input")
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            catalog.eventTypes.take(12).forEach { ev ->
                                val (rawCatColor, catIcon) = catalog.resolveCategoryVisual(ev)
                                val catColor = adaptiveCategoryColor(rawCatColor, isDark)
                                FilterChip(
                                    selected = eventType == ev,
                                    onClick = { eventType = ev },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = catIcon,
                                            contentDescription = null,
                                            tint = catColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text(ev, fontFamily = NotoArabicFamily) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = catColor.copy(alpha = if (isDark) 0.24f else 0.16f),
                                        selectedLabelColor = catColor
                                    )
                                )
                            }
                        }

                        OutlinedTextField(
                            value = eventDetail,
                            onValueChange = { eventDetail = it },
                            label = { Text("Event Name") },
                            placeholder = { Text("e.g. Ali Raza Wedding") },
                            singleLine = false,
                            maxLines = 2,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_event_name_input")
                        )

                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("editor_event_date_button")
                        ) {
                            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Event Date: ${formatDisplayDate(eventDateMillis)}")
                        }

                        Text(
                            text = "Transaction Direction (In / Out)",
                            style = MaterialTheme.typography.labelLarge
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
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        if (selected) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
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
                    }
                }

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Home Gift / Items & Amounts",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = homeItems,
                            onValueChange = { homeItems = it },
                            label = { Text("Home Gift / Items") },
                            placeholder = { Text("e.g. 2 زنانہ سوٹ, 1 مردانہ سوٹ, 3 کلوگرام پھل, 2 کلوگرام مٹھائی...") },
                            leadingIcon = { Icon(Icons.Rounded.Home, contentDescription = null) },
                            minLines = 2,
                            maxLines = 5,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_home_items_input")
                        )

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Gift / Item Categories",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )

                                // زنانہ سوٹ (Lady Suit) + No. dropdown
                                GiftCategoryQuantityRow(
                                    label = "زنانہ سوٹ",
                                    options = SUIT_COUNT_OPTIONS,
                                    selectedValue = selectedLadySuitCount,
                                    unitSuffix = "",
                                    onValueSelected = { selectedLadySuitCount = it },
                                    onAddClick = {
                                        homeItems = appendHomeGiftItem(
                                            homeItems,
                                            formatSuitGiftItem("زنانہ سوٹ", selectedLadySuitCount)
                                        )
                                    },
                                    dropdownTag = "home_gift_lady_suit_dropdown",
                                    addButtonTag = "home_gift_lady_suit_add_button"
                                )

                                // مردانہ سوٹ (Gents Suit) + No. dropdown
                                GiftCategoryQuantityRow(
                                    label = "مردانہ سوٹ",
                                    options = SUIT_COUNT_OPTIONS,
                                    selectedValue = selectedGentsSuitCount,
                                    unitSuffix = "",
                                    onValueSelected = { selectedGentsSuitCount = it },
                                    onAddClick = {
                                        homeItems = appendHomeGiftItem(
                                            homeItems,
                                            formatSuitGiftItem("مردانہ سوٹ", selectedGentsSuitCount)
                                        )
                                    },
                                    dropdownTag = "home_gift_gents_suit_dropdown",
                                    addButtonTag = "home_gift_gents_suit_add_button"
                                )

                                // پھل (Fruits) + No. کلوگرام dropdown
                                GiftCategoryQuantityRow(
                                    label = "پھل",
                                    options = KG_COUNT_OPTIONS,
                                    selectedValue = selectedFruitsKg,
                                    unitSuffix = " کلوگرام",
                                    onValueSelected = { selectedFruitsKg = it },
                                    onAddClick = {
                                        homeItems = appendHomeGiftItem(
                                            homeItems,
                                            formatKgGiftItem("پھل", selectedFruitsKg)
                                        )
                                    },
                                    dropdownTag = "home_gift_fruits_dropdown",
                                    addButtonTag = "home_gift_fruits_add_button"
                                )

                                // مٹھائی (Sweets) + No. کلوگرام dropdown
                                GiftCategoryQuantityRow(
                                    label = "مٹھائی",
                                    options = KG_COUNT_OPTIONS,
                                    selectedValue = selectedSweetsKg,
                                    unitSuffix = " کلوگرام",
                                    onValueSelected = { selectedSweetsKg = it },
                                    onAddClick = {
                                        homeItems = appendHomeGiftItem(
                                            homeItems,
                                            formatKgGiftItem("مٹھائی", selectedSweetsKg)
                                        )
                                    },
                                    dropdownTag = "home_gift_sweets_dropdown",
                                    addButtonTag = "home_gift_sweets_add_button"
                                )
                            }
                        }

                        // 3 Entries Above Neondra: نقد (Cash), ہار (Haar), سلامی (Salami)
                        OutlinedTextField(
                            value = cashAmountText,
                            onValueChange = { input ->
                                cashAmountText = input.filter { it.isDigit() }.take(9)
                            },
                            label = {
                                Text(
                                    text = "نقد",
                                    fontFamily = NotoArabicFamily,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            placeholder = { Text("e.g. 5000") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_cash_amount_input")
                        )

                        OutlinedTextField(
                            value = haarAmountText,
                            onValueChange = { input ->
                                haarAmountText = input.filter { it.isDigit() }.take(9)
                            },
                            label = {
                                Text(
                                    text = "ہار",
                                    fontFamily = NotoArabicFamily,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            placeholder = { Text("e.g. 2000") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_haar_amount_input")
                        )

                        OutlinedTextField(
                            value = salamiAmountText,
                            onValueChange = { input ->
                                salamiAmountText = input.filter { it.isDigit() }.take(9)
                            },
                            label = {
                                Text(
                                    text = "سلامی",
                                    fontFamily = NotoArabicFamily,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            placeholder = { Text("e.g. 1000") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_salami_amount_input")
                        )

                        OutlinedTextField(
                            value = neondraText,
                            onValueChange = { input ->
                                neondraText = input.filter { it.isDigit() }.take(9)
                            },
                            label = {
                                Text(
                                    text = "نیوندرا",
                                    fontFamily = NotoArabicFamily,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            placeholder = { Text("e.g. 5000") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_neondra_amount_input")
                        )

                        val liveTotal = (cashAmountText.toLongOrNull() ?: 0L) +
                            (haarAmountText.toLongOrNull() ?: 0L) +
                            (salamiAmountText.toLongOrNull() ?: 0L) +
                            (neondraText.toLongOrNull() ?: 0L)
                        if (liveTotal > 0L) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Total Amount",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = formatNeondraAmount(liveTotal),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
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
                                    imageVector = Icons.Rounded.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = "Add Reminder",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Schedule an alert notification before the event",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = reminderEnabled,
                                onCheckedChange = { want ->
                                    if (want && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) {
                                            reminderEnabled = true
                                        } else {
                                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    } else {
                                        reminderEnabled = want
                                    }
                                }
                            )
                        }

                        if (reminderEnabled) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                REMINDER_OFFSETS.forEach { offsetMin ->
                                    FilterChip(
                                        selected = reminderMinutesBefore == offsetMin,
                                        onClick = { reminderMinutesBefore = offsetMin },
                                        label = { Text(formatReminderOffsetLabel(offsetMin)) }
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { alsoOpenDeviceCalendar = !alsoOpenDeviceCalendar },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = alsoOpenDeviceCalendar,
                                onCheckedChange = { alsoOpenDeviceCalendar = it }
                            )
                            Text(
                                text = "Also add to Device / Google Calendar after saving",
                                style = MaterialTheme.typography.bodyMedium
                            )
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

                Button(
                    onClick = {
                        val trimmedName = name.trim()
                        val trimmedEvent = eventType.trim()
                        if (trimmedName.isEmpty()) {
                            validationError = "Please enter a name."
                            return@Button
                        }
                        if (trimmedEvent.isEmpty()) {
                            validationError = "Please select an event type."
                            return@Button
                        }
                        validationError = null
                        val cashAmount = cashAmountText.toLongOrNull() ?: 0L
                        val haarAmount = haarAmountText.toLongOrNull() ?: 0L
                        val salamiAmount = salamiAmountText.toLongOrNull() ?: 0L
                        val neondraAmount = neondraText.toLongOrNull() ?: 0L
                        val reminderAt = if (reminderEnabled) {
                            (eventDateMillis - reminderMinutesBefore * 60_000L)
                                .coerceAtLeast(System.currentTimeMillis() + 60_000L)
                        } else {
                            0L
                        }
                        val recordToSave = ActivityRecord(
                            id = initialRecord?.id ?: "",
                            userId = initialRecord?.userId ?: "",
                            familyDocId = selectedFamilyDocId,
                            familyId = familyId.trim(),
                            name = trimmedName,
                            fatherName = fatherName.trim(),
                            address = address.trim(),
                            eventType = trimmedEvent,
                            eventDetail = eventDetail.trim(),
                            eventDateMillis = eventDateMillis,
                            direction = direction.wireValue,
                            homeItems = homeItems.trim(),
                            cashAmount = cashAmount,
                            haarAmount = haarAmount,
                            salamiAmount = salamiAmount,
                            neondraAmount = neondraAmount,
                            reminderEnabled = reminderEnabled,
                            reminderAtMillis = reminderAt,
                            reminderMinutesBefore = if (reminderEnabled) reminderMinutesBefore else 0L,
                            calendarSynced = (initialRecord?.calendarSynced ?: false) || alsoOpenDeviceCalendar
                        )
                        if (alsoOpenDeviceCalendar) {
                            runCatching {
                                context.startActivity(CalendarIntentHelper.buildInsertCalendarIntent(recordToSave))
                            }
                        }
                        onSave(recordToSave, alsoOpenDeviceCalendar)
                    },
                    enabled = !isSaving,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("save_activity_button")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (isEditing) "Save Changes" else "Save Entry",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GiftCategoryQuantityRow(
    label: String,
    options: List<Int>,
    selectedValue: Int,
    unitSuffix: String,
    onValueSelected: (Int) -> Unit,
    onAddClick: () -> Unit,
    dropdownTag: String,
    addButtonTag: String
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = NotoArabicFamily,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier
                .width(116.dp)
                .testTag(dropdownTag)
        ) {
            OutlinedTextField(
                value = "$selectedValue$unitSuffix",
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = NotoArabicFamily),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { count ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "$count$unitSuffix",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = NotoArabicFamily,
                                fontWeight = if (count == selectedValue) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onValueSelected(count)
                            expanded = false
                        }
                    )
                }
            }
        }

        FilledTonalButton(
            onClick = onAddClick,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .height(48.dp)
                .testTag(addButtonTag)
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add")
        }
    }
}
