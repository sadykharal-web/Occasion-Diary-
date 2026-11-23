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
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.data.FamilyProfile
import com.example.reminders.CalendarIntentHelper
import com.example.ui.components.AutoCompleteDropdownField
import com.example.ui.components.DropdownOptionItem
import com.example.ui.components.formatDisplayDate
import com.example.ui.components.formatDisplayTime
import com.example.ui.components.formatReminderOffsetLabel
import java.util.Calendar

private val QUICK_HOME_CHIPS = listOf(
    "نقد",
    "ہار",
    "ایک سوٹ زنانہ",
    "ایک سوٹ مردانہ",
    "شوز پرس",
    "بنین رومال",
    "دو کلو مکس مٹھائی",
    "تین کلو سیب",
    "تین کلو مالٹے",
    "تین درجن کیلا"
)

private val REMINDER_OFFSETS = listOf(0L, 60L, 180L, 1440L, 2880L)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityEditorScreen(
    initialRecord: ActivityRecord?,
    prefilledFamily: FamilyProfile?,
    prefilledDateMillis: Long?,
    catalog: DropdownCatalog,
    isSaving: Boolean,
    onBack: () -> Unit,
    onQuickCreateCategory: (ActivityCategory) -> Unit,
    onSave: (ActivityRecord, openCalendarAfterSave: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
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
        mutableStateOf(initialRecord?.eventType ?: catalog.eventTypes.firstOrNull() ?: "شادی")
    }
    var eventDetail by remember {
        mutableStateOf(initialRecord?.eventDetail ?: "")
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
    var showTimePicker by remember { mutableStateOf(false) }
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

    val nameOptions = remember(catalog.families, catalog.names) {
        val fromFamilies = catalog.families.map { fam ->
            DropdownOptionItem(
                primaryValue = fam.name,
                subtitle = buildList {
                    if (fam.familyId.isNotBlank()) add("FN: ${fam.familyId}")
                    if (fam.fatherName.isNotBlank()) add("والد: ${fam.fatherName}")
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
                    if (fam.fatherName.isNotBlank()) add("والد: ${fam.fatherName}")
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
                subtitle = if (count > 0) "$count خاندان" else null
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
                    Text("منتخب کریں")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("منسوخ") }
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
                    Text("ٹھیک ہے")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("منسوخ") }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditing) "اندراج میں ترمیم" else "نیا اندراج شامل کریں",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "واپس"
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
                            text = "شخص اور فیملی کی معلومات (خودکار ڈراپ ڈاؤن مطابقت)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )

                        AutoCompleteDropdownField(
                            value = name,
                            onValueChange = {
                                name = it
                                selectedFamilyDocId = null
                            },
                            label = "نام (Name) *",
                            placeholder = "مثلاً: رجب علی",
                            supportingText = "پرانا نام منتخب کرنے پر FN، والد اور ایڈریس خودکار پُر ہوں گے",
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
                            leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            AutoCompleteDropdownField(
                                value = familyId,
                                onValueChange = { familyId = it },
                                label = "فیملی آئی ڈی (FN)",
                                placeholder = "مثلاً: 11",
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
                                modifier = Modifier.weight(1f)
                            )

                            AssistChip(
                                onClick = { familyId = catalog.nextSuggestedFamilyId },
                                label = { Text("نیا FN: ${catalog.nextSuggestedFamilyId}") },
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        AutoCompleteDropdownField(
                            value = fatherName,
                            onValueChange = { fatherName = it },
                            label = "والد (Father)",
                            placeholder = "مثلاً: رمضان",
                            options = fatherOptions,
                            onOptionSelected = { opt -> fatherName = opt.primaryValue }
                        )

                        AutoCompleteDropdownField(
                            value = address,
                            onValueChange = { address = it },
                            label = "ایڈریس / پتہ (Address)",
                            placeholder = "مثلاً: کوٹ رمضان",
                            options = addressOptions,
                            onOptionSelected = { opt -> address = opt.primaryValue },
                            leadingIcon = { Icon(Icons.Rounded.LocationOn, contentDescription = null) }
                        )
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
                                text = "کیٹیگری، تقریب اور لین دین",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = { showQuickCategoryDialog = true }) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("نئی کیٹیگری")
                            }
                        }

                        AutoCompleteDropdownField(
                            value = eventType,
                            onValueChange = { eventType = it },
                            label = "تقریب / کیٹیگری (Event) *",
                            placeholder = "مثلاً: شادی، ولیمہ، میٹنگ",
                            options = eventOptions,
                            onOptionSelected = { opt -> eventType = opt.primaryValue },
                            leadingIcon = {
                                val (catColor, catIcon) = catalog.resolveCategoryVisual(eventType)
                                Icon(catIcon, contentDescription = null, tint = catColor)
                            }
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            catalog.eventTypes.take(12).forEach { ev ->
                                val (catColor, catIcon) = catalog.resolveCategoryVisual(ev)
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
                                    label = { Text(ev) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = catColor.copy(alpha = 0.16f),
                                        selectedLabelColor = catColor
                                    )
                                )
                            }
                        }

                        OutlinedTextField(
                            value = eventDetail,
                            onValueChange = { eventDetail = it },
                            label = { Text("تفصیل (E-Detail)") },
                            placeholder = { Text("مثلاً: علی رضا بن رجب علی") },
                            singleLine = false,
                            maxLines = 2,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showDatePicker = true },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(52.dp)
                            ) {
                                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(formatDisplayDate(eventDateMillis))
                            }

                            OutlinedButton(
                                onClick = { showTimePicker = true },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(0.8f)
                                    .height(52.dp)
                            ) {
                                Icon(Icons.Rounded.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(formatDisplayTime(eventDateMillis))
                            }
                        }

                        Text(
                            text = "لین دین کی نوعیت (In / Out)",
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
                                            text = dir.urduLabel,
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
                            text = "گھر کی اشیاء اور نیوندرا",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = homeItems,
                            onValueChange = { homeItems = it },
                            label = { Text("گھر کی اشیاء / تحائف (Home)") },
                            placeholder = { Text("مثلاً: نقد 20000 ہار 3000 ایک سوٹ زنانہ...") },
                            leadingIcon = { Icon(Icons.Rounded.Home, contentDescription = null) },
                            minLines = 3,
                            maxLines = 5,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            QUICK_HOME_CHIPS.forEach { chipText ->
                                AssistChip(
                                    onClick = {
                                        homeItems = if (homeItems.isBlank()) {
                                            chipText
                                        } else {
                                            "${homeItems.trimEnd()} $chipText"
                                        }
                                    },
                                    label = { Text("+ $chipText") }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = neondraText,
                            onValueChange = { input ->
                                neondraText = input.filter { it.isDigit() }.take(9)
                            },
                            label = { Text("نیوندرا رقم (Neondra)") },
                            placeholder = { Text("مثلاً: 5000") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
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
                                        text = "یاد دہانی شامل کریں (Reminder)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "مقررہ وقت پر نوٹیفکیشن الرٹ",
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
                                text = "محفوظ کرنے کے بعد ڈیوائس / Google کیلنڈر میں بھی شامل کریں",
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
                            color = MaterialTheme.colorScheme.error,
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
                            validationError = "براہِ کرم نام درج کریں۔"
                            return@Button
                        }
                        if (trimmedEvent.isEmpty()) {
                            validationError = "براہِ کرم تقریب یا سرگرمی درج کریں۔"
                            return@Button
                        }
                        validationError = null
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
                            text = if (isEditing) "تبدیلیاں محفوظ کریں" else "اندراج محفوظ کریں",
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
