package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.DropdownCatalog
import com.example.data.EventEntry
import com.example.data.FamilyProfile
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewEventFormSheet(
    initialEvent: EventEntry? = null,
    initialDateMillis: Long? = null,
    catalog: DropdownCatalog,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSaveEvent: (EventEntry) -> Unit,
    onDeleteEvent: ((String) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        NewEventFormContent(
            initialEvent = initialEvent,
            initialDateMillis = initialDateMillis,
            catalog = catalog,
            isSaving = isSaving,
            onDismiss = onDismiss,
            onSaveEvent = onSaveEvent,
            onDeleteEvent = onDeleteEvent,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewEventFormContent(
    initialEvent: EventEntry? = null,
    initialDateMillis: Long? = null,
    catalog: DropdownCatalog,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSaveEvent: (EventEntry) -> Unit,
    onDeleteEvent: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isEditing = initialEvent != null && initialEvent.id.isNotBlank()

    var title by remember(initialEvent) { mutableStateOf(initialEvent?.title ?: "") }
    var description by remember(initialEvent) { mutableStateOf(initialEvent?.description ?: "") }
    var eventDateMillis by remember(initialEvent, initialDateMillis) {
        mutableLongStateOf(
            initialEvent?.eventDateMillis ?: initialDateMillis ?: System.currentTimeMillis()
        )
    }
    var category by remember(initialEvent) {
        mutableStateOf(
            initialEvent?.category?.takeIf { it.isNotBlank() }
                ?: catalog.eventTypes.firstOrNull()
                ?: "شادی"
        )
    }
    var location by remember(initialEvent) { mutableStateOf(initialEvent?.location ?: "") }
    var selectedFamilyDocId by remember(initialEvent) {
        mutableStateOf(initialEvent?.familyDocId)
    }
    var familyName by remember(initialEvent) { mutableStateOf(initialEvent?.familyName ?: "") }

    var validationError by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val familyOptions = remember(catalog.families) {
        catalog.families.map { fam ->
            DropdownOptionItem(
                primaryValue = fam.name,
                subtitle = buildList {
                    if (fam.familyId.isNotBlank()) add("FN ${fam.familyId}")
                    if (fam.address.isNotBlank()) add(fam.address)
                }.joinToString(" • "),
                payloadTag = fam.id
            )
        }
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
                    Text("منتخب کریں")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("منسوخ کریں") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val cal = remember(eventDateMillis) {
            Calendar.getInstance().apply { timeInMillis = eventDateMillis }
        }
        val timeState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE)
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("وقت منتخب کریں") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val updatedCal = Calendar.getInstance().apply {
                            timeInMillis = eventDateMillis
                            set(Calendar.HOUR_OF_DAY, timeState.hour)
                            set(Calendar.MINUTE, timeState.minute)
                            set(Calendar.SECOND, 0)
                        }
                        eventDateMillis = updatedCal.timeInMillis
                        showTimePicker = false
                    }
                ) {
                    Text("تصدیق کریں")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("منسوخ کریں") }
            }
        )
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
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Event,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = if (isEditing) "تقریب میں ترمیم" else "نئی تقریب",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isEditing) {
                            "اپنے موبائل پر تقریب کی تفصیل اپ ڈیٹ کریں"
                        } else {
                            "آنے والی تقریب کی تفصیل آف لائن موبائل میں محفوظ کریں"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_new_event_button")
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "فارم بند کریں")
            }
        }

        OutlinedTextField(
            value = title,
            onValueChange = {
                title = it.take(120)
                if (validationError != null) validationError = null
            },
            label = { Text("تقریب کا نام *") },
            placeholder = { Text("مثلاً: کزن کی شادی یا دعوت") },
            leadingIcon = { Icon(Icons.Rounded.Title, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("new_event_title_input")
        )

        Text(
            text = "تقریب کی تاریخ اور وقت",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { showDatePicker = true },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1.25f)
                    .height(50.dp)
                    .testTag("new_event_date_button")
            ) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(formatDisplayDate(eventDateMillis))
            }
            OutlinedButton(
                onClick = { showTimePicker = true },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(0.85f)
                    .height(50.dp)
                    .testTag("new_event_time_button")
            ) {
                Icon(Icons.Rounded.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(formatDisplayTime(eventDateMillis))
            }
        }

        OutlinedTextField(
            value = description,
            onValueChange = { description = it.take(1000) },
            label = { Text("تفصیل") },
            placeholder = { Text("میزبان کی تفصیل، تحائف یا اہم نوٹس...") },
            leadingIcon = { Icon(Icons.Rounded.Description, contentDescription = null) },
            minLines = 3,
            maxLines = 5,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("new_event_description_input")
        )

        if (catalog.eventTypes.isNotEmpty()) {
            Text(
                text = "قسم",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                catalog.eventTypes.take(12).forEach { catName ->
                    val (catColor, catIcon) = catalog.resolveCategoryVisual(catName)
                    FilterChip(
                        selected = category.equals(catName, ignoreCase = true),
                        onClick = { category = catName },
                        leadingIcon = {
                            Icon(
                                imageVector = catIcon,
                                contentDescription = null,
                                tint = catColor,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(catName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = catColor.copy(alpha = 0.16f),
                            selectedLabelColor = catColor
                        )
                    )
                }
            }
        }

        OutlinedTextField(
            value = location,
            onValueChange = { location = it.take(200) },
            label = { Text("مقام (اختیاری)") },
            placeholder = { Text("مثلاً: پرل ہال، مین بولیوارڈ") },
            leadingIcon = { Icon(Icons.Rounded.LocationOn, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("new_event_location_input")
        )

        AutoCompleteDropdownField(
            value = familyName,
            onValueChange = {
                familyName = it.take(100)
                selectedFamilyDocId = catalog.families
                    .firstOrNull { fam -> fam.name.equals(it.trim(), ignoreCase = true) }
                    ?.id
            },
            label = "منسلک خاندان (اختیاری)",
            placeholder = "خاندان منتخب کریں یا نام لکھیں",
            options = familyOptions,
            onOptionSelected = { opt ->
                familyName = opt.primaryValue
                val matched: FamilyProfile? = catalog.families.firstOrNull {
                    it.id == opt.payloadTag || it.name.equals(opt.primaryValue, ignoreCase = true)
                }
                selectedFamilyDocId = matched?.id
                val matchedAddress = matched?.address
                if (location.isBlank() && !matchedAddress.isNullOrBlank()) {
                    location = matchedAddress
                }
            },
            leadingIcon = { Icon(Icons.Rounded.Groups, contentDescription = null) }
        )

        if (validationError != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = validationError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = {
                val trimmedTitle = title.trim()
                if (trimmedTitle.isEmpty()) {
                    validationError = "براہ کرم تقریب کا نام درج کریں۔"
                    return@Button
                }
                validationError = null
                onSaveEvent(
                    EventEntry(
                        id = initialEvent?.id ?: "",
                        userId = initialEvent?.userId ?: "",
                        title = trimmedTitle,
                        description = description.trim(),
                        eventDateMillis = eventDateMillis,
                        category = category.trim().ifEmpty { "تقریب" },
                        location = location.trim(),
                        familyDocId = selectedFamilyDocId,
                        familyName = familyName.trim()
                    )
                )
            },
            enabled = !isSaving,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("save_new_event_button")
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = if (isEditing) "تقریب اپ ڈیٹ کریں" else "تقریب محفوظ کریں",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (initialEvent != null && onDeleteEvent != null) {
            OutlinedButton(
                onClick = { onDeleteEvent(initialEvent.id) },
                enabled = !isSaving,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("delete_event_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "تقریب حذف کریں",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
