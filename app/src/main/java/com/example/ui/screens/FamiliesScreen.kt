package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PostAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ActivityDirection
import com.example.data.ActivityRecord
import com.example.data.DropdownCatalog
import com.example.data.FamilyIdentityMatcher
import com.example.data.FamilyProfile
import com.example.data.UiState
import com.example.ui.components.CategoryBadge
import com.example.ui.components.DirectionPill
import com.example.ui.components.ReminderStatusBadge
import com.example.ui.components.formatDisplayDate
import com.example.ui.components.formatNeondraAmount
import com.example.ui.theme.InBadgeFgDark
import com.example.ui.theme.InBadgeFgLight
import com.example.ui.theme.JakartaSansFamily
import com.example.ui.theme.LocalAppDarkTheme
import com.example.ui.theme.NotoArabicFamily
import com.example.ui.theme.OutBadgeFgDark
import com.example.ui.theme.OutBadgeFgLight
import com.example.ui.viewmodel.LedgerDashboardData

private data class FamilyFinancialSummary(
    val totalIn: Long,
    val totalOut: Long,
    val cashIn: Long,
    val cashOut: Long,
    val haarIn: Long,
    val haarOut: Long,
    val salamiIn: Long,
    val salamiOut: Long,
    val neondraIn: Long,
    val neondraOut: Long,
    val homeGiftInCount: Int,
    val homeGiftOutCount: Int
)

private fun computeFamilyFinancialSummary(entries: List<ActivityRecord>): FamilyFinancialSummary {
    val inList = entries.filter { it.parsedDirection == ActivityDirection.IN }
    val outList = entries.filter { it.parsedDirection == ActivityDirection.OUT }
    return FamilyFinancialSummary(
        totalIn = inList.sumOf { it.totalAmount },
        totalOut = outList.sumOf { it.totalAmount },
        cashIn = inList.sumOf { it.cashAmount },
        cashOut = outList.sumOf { it.cashAmount },
        haarIn = inList.sumOf { it.haarAmount },
        haarOut = outList.sumOf { it.haarAmount },
        salamiIn = inList.sumOf { it.salamiAmount },
        salamiOut = outList.sumOf { it.salamiAmount },
        neondraIn = inList.sumOf { it.neondraAmount },
        neondraOut = outList.sumOf { it.neondraAmount },
        homeGiftInCount = inList.count { it.homeItems.isNotBlank() },
        homeGiftOutCount = outList.count { it.homeItems.isNotBlank() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamiliesScreen(
    state: UiState<LedgerDashboardData>,
    onSaveFamily: (FamilyProfile) -> Unit,
    onDeleteFamily: (String) -> Unit,
    onNewActivityForFamily: (FamilyProfile) -> Unit,
    onActivityClick: (ActivityRecord) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = LocalAppDarkTheme.current
    var search by remember { mutableStateOf("") }
    var editingFamily by remember { mutableStateOf<FamilyProfile?>(null) }
    var showFamilyDialog by remember { mutableStateOf(false) }
    var deletingFamily by remember { mutableStateOf<FamilyProfile?>(null) }
    var inspectingFamily by remember { mutableStateOf<FamilyProfile?>(null) }

    val data = (state as? UiState.Success)?.data
    val families = data?.families ?: emptyList()
    val activities = data?.activities ?: emptyList()
    val catalog = data?.catalog ?: DropdownCatalog()

    val filteredFamilies = remember(families, search) {
        val q = search.trim()
        if (q.isEmpty()) families
        else families.filter { fam ->
            fam.name.contains(q, ignoreCase = true) ||
                fam.familyId.contains(q, ignoreCase = true) ||
                fam.fatherName.contains(q, ignoreCase = true) ||
                fam.address.contains(q, ignoreCase = true)
        }
    }

    if (showFamilyDialog) {
        FamilyEditorDialog(
            initial = editingFamily,
            suggestedFn = data?.catalog?.nextSuggestedFamilyId ?: "1",
            onDismiss = { showFamilyDialog = false },
            onConfirm = { profile ->
                showFamilyDialog = false
                onSaveFamily(profile)
            }
        )
    }

    deletingFamily?.let { famToDelete ->
        val linkedCount = activities.count { FamilyIdentityMatcher.activityBelongsToFamily(it, famToDelete) }
        AlertDialog(
            onDismissRequest = { deletingFamily = null },
            title = { Text("Delete Family (FN ${famToDelete.familyId})?") },
            text = {
                Text(
                    buildString {
                        append("Are you sure you want to delete \"${famToDelete.name}\" (FN ${famToDelete.familyId})?")
                        if (linkedCount > 0) {
                            append("\n\nThis family currently has $linkedCount ledger ")
                            append(if (linkedCount == 1) "entry." else "entries.")
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletingFamily = null
                        onDeleteFamily(famToDelete.id)
                    },
                    modifier = Modifier.testTag("confirm_delete_family_button")
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingFamily = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    inspectingFamily?.let { fam ->
        val famActs = activities
            .filter { FamilyIdentityMatcher.activityBelongsToFamily(it, fam) }
            .sortedByDescending { it.eventDateMillis }
        FamilyEntriesBottomSheet(
            family = fam,
            entries = famActs,
            catalog = catalog,
            onDismiss = { inspectingFamily = null },
            onNewEntry = {
                inspectingFamily = null
                onNewActivityForFamily(fam)
            },
            onEntryClick = { record ->
                inspectingFamily = null
                onActivityClick(record)
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingFamily = null
                    showFamilyDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_family_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add New Family")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.42f else 0.55f)
                        ),
                        border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Groups,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(30.dp)
                            )
                            Text(
                                text = "Families Directory (${families.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text("Search...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("families_search_input")
                    )
                }

                if (filteredFamilies.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Groups,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = "No families saved yet",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(filteredFamilies, key = { it.id }) { family ->
                        val famActs = activities.filter { act ->
                            FamilyIdentityMatcher.activityBelongsToFamily(act, family)
                        }
                        val summary = computeFamilyFinancialSummary(famActs)

                        FamilyDirectoryCard(
                            family = family,
                            entriesCount = famActs.size,
                            summary = summary,
                            isDark = isDark,
                            onCardClick = { inspectingFamily = family },
                            onEditClick = {
                                editingFamily = family
                                showFamilyDialog = true
                            },
                            onDeleteClick = { deletingFamily = family },
                            onNewEntryClick = { onNewActivityForFamily(family) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FamilyDirectoryCard(
    family: FamilyProfile,
    entriesCount: Int,
    summary: FamilyFinancialSummary,
    isDark: Boolean,
    onCardClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onNewEntryClick: () -> Unit
) {
    val inColor = if (isDark) InBadgeFgDark else InBadgeFgLight
    val outColor = if (isDark) OutBadgeFgDark else OutBadgeFgLight
    val cashAccent = if (isDark) Color(0xFF34D399) else Color(0xFF047857)
    val haarAccent = if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309)
    val salamiAccent = if (isDark) Color(0xFFC084FC) else Color(0xFF7E22CE)
    val neondraAccent = if (isDark) Color(0xFF38BDF8) else Color(0xFF0369A1)
    val homeGiftAccent = if (isDark) Color(0xFFFB7185) else Color(0xFFBE123C)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 1.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick)
            .testTag("family_card_${family.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "FN ${family.familyId}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontFamily = JakartaSansFamily,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    Column {
                        Text(
                            text = family.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (family.fatherName.isNotBlank()) {
                            Text(
                                text = "ولد: ${family.fatherName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Row {
                    IconButton(onClick = onEditClick) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit Family")
                    }
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.testTag("delete_family_${family.id}")
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete Family",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (family.address.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = family.address,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Overall In / Out Summary Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = inColor.copy(alpha = if (isDark) 0.16f else 0.11f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Text("Total In", style = MaterialTheme.typography.labelSmall, color = inColor)
                        Text(
                            text = formatNeondraAmount(summary.totalIn),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JakartaSansFamily,
                            color = inColor
                        )
                    }
                }
                Surface(
                    color = outColor.copy(alpha = if (isDark) 0.16f else 0.11f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Text("Total Out", style = MaterialTheme.typography.labelSmall, color = outColor)
                        Text(
                            text = formatNeondraAmount(summary.totalOut),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JakartaSansFamily,
                            color = outColor
                        )
                    }
                }
            }

            // Category Breakdown (نقد, ہار, سلامی, نیوندرا, ہوم گفٹ) In & Out
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FamilyCategoryInOutChip(
                    label = "نقد",
                    inText = formatNeondraAmount(summary.cashIn),
                    outText = formatNeondraAmount(summary.cashOut),
                    accent = cashAccent,
                    isDark = isDark
                )
                FamilyCategoryInOutChip(
                    label = "ہار",
                    inText = formatNeondraAmount(summary.haarIn),
                    outText = formatNeondraAmount(summary.haarOut),
                    accent = haarAccent,
                    isDark = isDark
                )
                FamilyCategoryInOutChip(
                    label = "سلامی",
                    inText = formatNeondraAmount(summary.salamiIn),
                    outText = formatNeondraAmount(summary.salamiOut),
                    accent = salamiAccent,
                    isDark = isDark
                )
                FamilyCategoryInOutChip(
                    label = "نیوندرا",
                    inText = formatNeondraAmount(summary.neondraIn),
                    outText = formatNeondraAmount(summary.neondraOut),
                    accent = neondraAccent,
                    isDark = isDark
                )
                FamilyCategoryInOutChip(
                    label = "ہوم گفٹ",
                    inText = "${summary.homeGiftInCount}",
                    outText = "${summary.homeGiftOutCount}",
                    accent = homeGiftAccent,
                    isDark = isDark
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (isDark) 0.55f else 0.72f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .clickable(onClick = onCardClick)
                        .testTag("family_entries_pill_${family.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "$entriesCount ${if (entriesCount == 1) "entry" else "entries"}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "View Entries",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                OutlinedButton(
                    onClick = onNewEntryClick,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Rounded.PostAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Entry")
                }
            }
        }
    }
}

@Composable
private fun FamilyCategoryInOutChip(
    label: String,
    inText: String,
    outText: String,
    accent: Color,
    isDark: Boolean
) {
    val inColor = if (isDark) InBadgeFgDark else InBadgeFgLight
    val outColor = if (isDark) OutBadgeFgDark else OutBadgeFgLight

    Surface(
        color = accent.copy(alpha = if (isDark) 0.14f else 0.08f),
        border = BorderStroke(1.dp, accent.copy(alpha = if (isDark) 0.42f else 0.25f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = NotoArabicFamily,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "In: $inText",
                    style = MaterialTheme.typography.labelSmall,
                    color = inColor,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Out: $outText",
                    style = MaterialTheme.typography.labelSmall,
                    color = outColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FamilyEntriesBottomSheet(
    family: FamilyProfile,
    entries: List<ActivityRecord>,
    catalog: DropdownCatalog,
    onDismiss: () -> Unit,
    onNewEntry: () -> Unit,
    onEntryClick: (ActivityRecord) -> Unit
) {
    val isDark = LocalAppDarkTheme.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val inColor = if (isDark) InBadgeFgDark else InBadgeFgLight
    val outColor = if (isDark) OutBadgeFgDark else OutBadgeFgLight
    val summary = remember(entries) { computeFamilyFinancialSummary(entries) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
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
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "FN ${family.familyId}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontFamily = JakartaSansFamily,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    Column {
                        Text(
                            text = family.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        val meta = buildList {
                            if (family.fatherName.isNotBlank()) add("ولد: ${family.fatherName}")
                            if (family.address.isNotBlank()) add(family.address)
                        }.joinToString(" • ")
                        if (meta.isNotEmpty()) {
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = inColor.copy(alpha = if (isDark) 0.16f else 0.11f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Total In", style = MaterialTheme.typography.labelSmall, color = inColor)
                        Text(
                            text = formatNeondraAmount(summary.totalIn),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = inColor
                        )
                    }
                }
                Surface(
                    color = outColor.copy(alpha = if (isDark) 0.16f else 0.11f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Total Out", style = MaterialTheme.typography.labelSmall, color = outColor)
                        Text(
                            text = formatNeondraAmount(summary.totalOut),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = outColor
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Entries (${entries.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(
                    onClick = onNewEntry,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Rounded.PostAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Entry")
                }
            }

            if (entries.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No entries recorded for this family yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(entries, key = { it.id }) { record ->
                        val (catColor, catIcon) = catalog.resolveCategoryVisual(record.eventType)
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.42f else 0.45f)
                            ),
                            border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEntryClick(record) }
                                .testTag("family_sheet_entry_${record.id}")
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        DirectionPill(direction = record.parsedDirection)
                                        CategoryBadge(label = record.eventType, color = catColor, icon = catIcon)
                                    }
                                    Text(
                                        text = formatDisplayDate(record.eventDateMillis),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (record.eventDetail.isNotBlank()) {
                                    Text(
                                        text = "Event: ${record.eventDetail}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (record.homeItems.isNotBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CardGiftcard,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "تحائف: ${record.homeItems}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = NotoArabicFamily,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                val moneyParts = buildList {
                                    if (record.cashAmount > 0L) add("نقد: ${formatNeondraAmount(record.cashAmount)}")
                                    if (record.haarAmount > 0L) add("ہار: ${formatNeondraAmount(record.haarAmount)}")
                                    if (record.salamiAmount > 0L) add("سلامی: ${formatNeondraAmount(record.salamiAmount)}")
                                    if (record.neondraAmount > 0L) add("نیوندرا: ${formatNeondraAmount(record.neondraAmount)}")
                                }
                                if (moneyParts.isNotEmpty()) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        moneyParts.forEach { part ->
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = part,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontFamily = NotoArabicFamily,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (record.reminderEnabled) {
                                        ReminderStatusBadge()
                                    } else {
                                        Spacer(modifier = Modifier.width(1.dp))
                                    }
                                    Text(
                                        text = "Total: ${formatNeondraAmount(record.totalAmount)}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = JakartaSansFamily,
                                        color = if (record.parsedDirection == ActivityDirection.IN) inColor else outColor
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun FamilyEditorDialog(
    initial: FamilyProfile?,
    suggestedFn: String,
    onDismiss: () -> Unit,
    onConfirm: (FamilyProfile) -> Unit
) {
    var fn by remember { mutableStateOf(initial?.familyId ?: suggestedFn) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var father by remember { mutableStateOf(initial?.fatherName ?: "") }
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initial == null) "Add New Family" else "Edit Family")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = fn,
                    onValueChange = { fn = it },
                    label = { Text("FN *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = father,
                    onValueChange = { father = it },
                    label = { Text("Father's Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (fn.trim().isEmpty() || name.trim().isEmpty()) {
                        error = "FN and Name are required."
                        return@TextButton
                    }
                    onConfirm(
                        FamilyProfile(
                            id = initial?.id ?: "",
                            userId = initial?.userId ?: "",
                            familyId = fn.trim(),
                            name = name.trim(),
                            fatherName = father.trim(),
                            address = address.trim()
                        )
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
