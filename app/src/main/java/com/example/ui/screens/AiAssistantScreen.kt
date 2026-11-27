package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ai.AiSearchResult
import com.example.ai.CategoryInOutBreakdown
import com.example.ai.SingleFamilyInOutComparison
import com.example.data.ActivityDirection
import com.example.data.ActivityRecord
import com.example.data.DropdownCatalog
import com.example.data.EventEntry
import com.example.data.FamilyIdentityMatcher
import com.example.data.FamilyProfile
import com.example.data.UiState
import com.example.ui.components.CategoryBadge
import com.example.ui.components.DirectionPill
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
import kotlin.math.max

private val OFFLINE_SEARCH_PRESETS = listOf(
    "Who has the highest In balance?",
    "شادی",
    "نقد",
    "نیوندرا"
)

fun filterFamiliesForCompare(
    families: List<FamilyProfile>,
    query: String
): List<FamilyProfile> {
    val cleanQuery = query.trim().lowercase()
    if (cleanQuery.isBlank()) return emptyList()
    val numericToken = cleanQuery
        .removePrefix("fn")
        .trim()
        .takeIf { it.isNotBlank() && it.all { ch -> ch.isDigit() } }

    return families
        .filter { fam ->
            fam.name.lowercase().contains(cleanQuery) ||
                fam.fatherName.lowercase().contains(cleanQuery) ||
                fam.address.lowercase().contains(cleanQuery) ||
                fam.familyId.lowercase().contains(cleanQuery) ||
                (numericToken != null && fam.familyId.lowercase() == numericToken)
        }
        .sortedWith(
            compareBy<FamilyProfile>(
                { fam -> if (fam.name.lowercase().startsWith(cleanQuery)) 0 else 1 },
                { fam ->
                    if (numericToken != null && fam.familyId.lowercase() == numericToken) 0 else 1
                },
                { fam -> fam.name.lowercase() },
                { fam -> fam.familyId.toIntOrNull() ?: Int.MAX_VALUE }
            )
        )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiAssistantScreen(
    dashboardState: UiState<LedgerDashboardData>,
    aiSearchState: UiState<AiSearchResult>?,
    selectedSingleFamilyId: String?,
    singleFamilyComparisonState: UiState<SingleFamilyInOutComparison>?,
    initialTabIndex: Int = 0,
    onRunAiSearch: (String, Boolean) -> Unit,
    onClearAiSearch: () -> Unit,
    onSelectSingleFamily: (String) -> Unit,
    onActivityClick: (ActivityRecord) -> Unit,
    onCompareThisFamily: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalAppDarkTheme.current
    var selectedTab by rememberSaveable(initialTabIndex) { mutableIntStateOf(initialTabIndex) }
    var searchPrompt by rememberSaveable { mutableStateOf("") }
    var compareFamilyQuery by rememberSaveable { mutableStateOf("") }

    val dashboardData = (dashboardState as? UiState.Success)?.data
    val families = dashboardData?.families ?: emptyList()
    val activities = dashboardData?.activities ?: emptyList()
    val events = dashboardData?.events ?: emptyList()
    val catalog = dashboardData?.catalog ?: DropdownCatalog()

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("ai_mode_search")
                    ) {
                        Text("Offline Search", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    SegmentedButton(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.CompareArrows,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("ai_mode_compare")
                    ) {
                        Text("Compare", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            if (selectedTab == 0) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = searchPrompt,
                            onValueChange = { searchPrompt = it },
                            placeholder = { Text("Search...") },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchPrompt.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            searchPrompt = ""
                                            onClearAiSearch()
                                        }
                                    ) {
                                        Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_search_input")
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OFFLINE_SEARCH_PRESETS.forEach { preset ->
                                AssistChip(
                                    onClick = {
                                        searchPrompt = preset
                                        onRunAiSearch(preset, false)
                                    },
                                    label = { Text(preset) }
                                )
                            }
                        }

                        Button(
                            onClick = { onRunAiSearch(searchPrompt, false) },
                            enabled = searchPrompt.isNotBlank() && aiSearchState !is UiState.Loading,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("ai_search_offline_button")
                        ) {
                            if (aiSearchState is UiState.Loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Searching...")
                            } else {
                                Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Search", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                when (aiSearchState) {
                    null -> Unit

                    is UiState.Loading -> {
                        item {
                            SearchLoadingCard("Searching records...")
                        }
                    }

                    is UiState.Error -> {
                        item {
                            SearchErrorCard(
                                message = aiSearchState.message,
                                onRetry = {
                                    if (searchPrompt.isNotBlank()) onRunAiSearch(searchPrompt, false)
                                }
                            )
                        }
                    }

                    is UiState.Success -> {
                        val result = aiSearchState.data
                        val matchedFamilies = families.filter { it.id in result.matchedFamilyIds }
                        val matchedEvents = events.filter { it.id in result.matchedEventIds }
                        val matchedActivities = activities.filter { it.id in result.matchedActivityIds }

                        if (matchedFamilies.size == 1) {
                            val singleFam = matchedFamilies.first()
                            val famAllActs = activities.filter {
                                FamilyIdentityMatcher.activityBelongsToFamily(it, singleFam)
                            }
                            val fInAll = famAllActs.filter { it.parsedDirection == ActivityDirection.IN }.sumOf { it.totalAmount }
                            val fOutAll = famAllActs.filter { it.parsedDirection == ActivityDirection.OUT }.sumOf { it.totalAmount }

                            item(key = "single_search_family_header_${singleFam.id}") {
                                SingleFamilySearchHeaderCard(
                                    family = singleFam,
                                    totalIn = fInAll,
                                    totalOut = fOutAll,
                                    entriesCount = famAllActs.size,
                                    onCompareClick = {
                                        onCompareThisFamily(singleFam.id)
                                        selectedTab = 1
                                    }
                                )
                            }

                            val familyMatchedEntries = matchedActivities
                                .filter { FamilyIdentityMatcher.activityBelongsToFamily(it, singleFam) }
                                .ifEmpty { famAllActs.sortedByDescending { it.eventDateMillis } }

                            val inEntries = familyMatchedEntries.filter { it.parsedDirection == ActivityDirection.IN }
                            val outEntries = familyMatchedEntries.filter { it.parsedDirection == ActivityDirection.OUT }

                            if (inEntries.isNotEmpty()) {
                                item(key = "search_sf_in_header") {
                                    SectionDirectionHeader(
                                        title = "In Entries (Received)",
                                        count = inEntries.size,
                                        totalAmount = inEntries.sumOf { it.totalAmount },
                                        isIn = true
                                    )
                                }
                                items(inEntries, key = { "search_sf_in_${it.id}" }) { act ->
                                    SingleFamilyEntryCard(
                                        record = act,
                                        catalog = catalog,
                                        onClick = { onActivityClick(act) }
                                    )
                                }
                            }

                            if (outEntries.isNotEmpty()) {
                                item(key = "search_sf_out_header") {
                                    SectionDirectionHeader(
                                        title = "Out Entries (Given)",
                                        count = outEntries.size,
                                        totalAmount = outEntries.sumOf { it.totalAmount },
                                        isIn = false
                                    )
                                }
                                items(outEntries, key = { "search_sf_out_${it.id}" }) { act ->
                                    SingleFamilyEntryCard(
                                        record = act,
                                        catalog = catalog,
                                        onClick = { onActivityClick(act) }
                                    )
                                }
                            }

                            if (familyMatchedEntries.isEmpty()) {
                                item(key = "search_sf_empty_entries") {
                                    EmptyDirectionEntryCard("No In or Out entries recorded for ${singleFam.name}.")
                                }
                            }

                            val otherMatchedActivities = matchedActivities.filter {
                                !FamilyIdentityMatcher.activityBelongsToFamily(it, singleFam)
                            }
                            if (matchedEvents.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Matched Events (${matchedEvents.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                items(matchedEvents, key = { "evt_${it.id}" }) { evt ->
                                    MatchedEventCard(event = evt, catalog = catalog)
                                }
                            }
                            if (otherMatchedActivities.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Other Matched Entries (${otherMatchedActivities.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                items(otherMatchedActivities, key = { "other_act_${it.id}" }) { act ->
                                    MatchedActivityCard(
                                        record = act,
                                        catalog = catalog,
                                        onClick = { onActivityClick(act) }
                                    )
                                }
                            }
                        } else {
                            if (matchedFamilies.isEmpty() && matchedEvents.isEmpty() && matchedActivities.isEmpty()) {
                                item {
                                    EmptyDirectionEntryCard("No matching families or entries found.")
                                }
                            }

                            if (matchedFamilies.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Matched Families (${matchedFamilies.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                items(matchedFamilies, key = { "fam_${it.id}" }) { fam ->
                                    val famActs = activities.filter {
                                        FamilyIdentityMatcher.activityBelongsToFamily(it, fam)
                                    }
                                    val fIn = famActs.filter { it.parsedDirection == ActivityDirection.IN }.sumOf { it.totalAmount }
                                    val fOut = famActs.filter { it.parsedDirection == ActivityDirection.OUT }.sumOf { it.totalAmount }

                                    MatchedFamilyCard(
                                        family = fam,
                                        totalIn = fIn,
                                        totalOut = fOut,
                                        entriesCount = famActs.size,
                                        onCompareClick = {
                                            onCompareThisFamily(fam.id)
                                            selectedTab = 1
                                        }
                                    )
                                }
                            }

                            if (matchedEvents.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Matched Events (${matchedEvents.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                items(matchedEvents, key = { "evt_${it.id}" }) { evt ->
                                    MatchedEventCard(event = evt, catalog = catalog)
                                }
                            }

                            if (matchedActivities.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Matched Entries (${matchedActivities.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                items(matchedActivities, key = { "act_${it.id}" }) { act ->
                                    MatchedActivityCard(
                                        record = act,
                                        catalog = catalog,
                                        onClick = { onActivityClick(act) }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Compare In & Out (Search by Name / FN)
                val compareMatches = filterFamiliesForCompare(families, compareFamilyQuery)
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)) else null,
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 1.5.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Compare Family",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            OutlinedTextField(
                                value = compareFamilyQuery,
                                onValueChange = { compareFamilyQuery = it },
                                placeholder = { Text("Search name or FN...") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Search, contentDescription = null)
                                },
                                trailingIcon = {
                                    if (compareFamilyQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { compareFamilyQuery = "" }
                                        ) {
                                            Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                                        }
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        compareMatches.firstOrNull()?.let { matched ->
                                            onSelectSingleFamily(matched.id)
                                            compareFamilyQuery = ""
                                        }
                                    }
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("compare_family_search_input")
                            )

                            when {
                                families.isEmpty() -> {
                                    Text(
                                        text = "No families available yet.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                compareFamilyQuery.isBlank() && selectedSingleFamilyId == null -> {
                                    Text(
                                        text = "Search by name to select and compare FN In / Out entries.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                compareFamilyQuery.isNotBlank() && compareMatches.isEmpty() -> {
                                    Text(
                                        text = "No family found matching \"$compareFamilyQuery\".",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                compareMatches.isNotEmpty() -> {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        compareMatches.take(12).forEach { fam ->
                                            val isSelected = fam.id == selectedSingleFamilyId
                                            Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                                },
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isSelected) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                                    }
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .clickable {
                                                        onSelectSingleFamily(fam.id)
                                                        compareFamilyQuery = ""
                                                    }
                                                    .testTag("compare_family_result_${fam.id}")
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Groups,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = if (fam.familyId.isNotBlank()) "${fam.name} (FN ${fam.familyId})" else fam.name,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = FontWeight.SemiBold,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            val meta = buildList {
                                                                if (fam.fatherName.isNotBlank()) add("ولد: ${fam.fatherName}")
                                                                if (fam.address.isNotBlank()) add(fam.address)
                                                            }.joinToString(" • ")
                                                            if (meta.isNotEmpty()) {
                                                                Text(
                                                                    text = meta,
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                        }
                                                    }
                                                    if (isSelected) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Check,
                                                            contentDescription = "Selected",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                when (singleFamilyComparisonState) {
                    null -> Unit

                    is UiState.Loading -> {
                        item {
                            SearchLoadingCard("Loading comparison...")
                        }
                    }

                    is UiState.Error -> {
                        item {
                            SearchErrorCard(
                                message = singleFamilyComparisonState.message,
                                onRetry = {
                                    selectedSingleFamilyId?.let(onSelectSingleFamily)
                                }
                            )
                        }
                    }

                    is UiState.Success -> {
                        val comp = singleFamilyComparisonState.data
                        item {
                            SingleFamilySummaryCard(comp = comp)
                        }

                        if (comp.categoryBreakdowns.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Event Type Breakdown (${comp.categoryBreakdowns.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            items(comp.categoryBreakdowns, key = { "cat_cmp_${it.categoryName}" }) { cat ->
                                CategoryInOutCard(breakdown = cat, catalog = catalog)
                            }
                        }

                        item {
                            SectionDirectionHeader(
                                title = "In Entries (Received)",
                                count = comp.inEntries.size,
                                totalAmount = comp.totalIn,
                                isIn = true
                            )
                        }
                        if (comp.inEntries.isEmpty()) {
                            item {
                                EmptyDirectionEntryCard("No In entries for ${comp.family.name}.")
                            }
                        } else {
                            items(comp.inEntries, key = { "sf_in_${it.id}" }) { act ->
                                SingleFamilyEntryCard(
                                    record = act,
                                    catalog = catalog,
                                    onClick = { onActivityClick(act) }
                                )
                            }
                        }

                        item {
                            SectionDirectionHeader(
                                title = "Out Entries (Given)",
                                count = comp.outEntries.size,
                                totalAmount = comp.totalOut,
                                isIn = false
                            )
                        }
                        if (comp.outEntries.isEmpty()) {
                            item {
                                EmptyDirectionEntryCard("No Out entries for ${comp.family.name}.")
                            }
                        } else {
                            items(comp.outEntries, key = { "sf_out_${it.id}" }) { act ->
                                SingleFamilyEntryCard(
                                    record = act,
                                    catalog = catalog,
                                    onClick = { onActivityClick(act) }
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SingleFamilySummaryCard(comp: SingleFamilyInOutComparison) {
    val isDark = LocalAppDarkTheme.current
    val inColor = if (isDark) InBadgeFgDark else InBadgeFgLight
    val outColor = if (isDark) OutBadgeFgDark else OutBadgeFgLight
    val maxVal = max(comp.totalIn, comp.totalOut).coerceAtLeast(1L)
    val inFraction = (comp.totalIn.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
    val outFraction = (comp.totalOut.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
    val netPrefix = if (comp.netBalance >= 0) "+" else ""

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text(
                    text = if (comp.family.familyId.isNotBlank()) {
                        "${comp.family.name} (FN ${comp.family.familyId})"
                    } else {
                        comp.family.name
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                val famMeta = buildList {
                    if (comp.family.fatherName.isNotBlank()) add("ولد: ${comp.family.fatherName}")
                    if (comp.family.address.isNotBlank()) add(comp.family.address)
                }.joinToString(" • ")
                if (famMeta.isNotEmpty()) {
                    Text(
                        text = famMeta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = inColor.copy(alpha = if (isDark) 0.16f else 0.12f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("In (${comp.inEntries.size})", style = MaterialTheme.typography.labelSmall, color = inColor)
                        Text(
                            text = formatNeondraAmount(comp.totalIn),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JakartaSansFamily,
                            color = inColor
                        )
                    }
                }
                Surface(
                    color = outColor.copy(alpha = if (isDark) 0.16f else 0.12f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Out (${comp.outEntries.size})", style = MaterialTheme.typography.labelSmall, color = outColor)
                        Text(
                            text = formatNeondraAmount(comp.totalOut),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JakartaSansFamily,
                            color = outColor
                        )
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Net Balance",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "$netPrefix${formatNeondraAmount(comp.netBalance)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JakartaSansFamily,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(inFraction.coerceAtLeast(if (comp.totalIn > 0) 0.04f else 0f))
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(inColor)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(outFraction.coerceAtLeast(if (comp.totalOut > 0) 0.04f else 0f))
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(outColor)
                    )
                }
            }

            HorizontalDivider()

            Text(
                text = comp.headline,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = NotoArabicFamily,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = comp.summary,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = NotoArabicFamily
            )
        }
    }
}

@Composable
private fun CategoryInOutCard(
    breakdown: CategoryInOutBreakdown,
    catalog: DropdownCatalog
) {
    val isDark = LocalAppDarkTheme.current
    val inColor = if (isDark) InBadgeFgDark else InBadgeFgLight
    val outColor = if (isDark) OutBadgeFgDark else OutBadgeFgLight
    val (catColor, catIcon) = catalog.resolveCategoryVisual(breakdown.categoryName)
    val netPrefix = if (breakdown.netBalance >= 0) "+" else ""

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)) else null,
        modifier = Modifier.fillMaxWidth()
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
                CategoryBadge(label = breakdown.categoryName, color = catColor, icon = catIcon)
                Text(
                    text = "Net: $netPrefix${formatNeondraAmount(breakdown.netBalance)}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (breakdown.netBalance >= 0) inColor else outColor
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "In (${breakdown.inCount}): ${formatNeondraAmount(breakdown.totalIn)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = inColor,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Out (${breakdown.outCount}): ${formatNeondraAmount(breakdown.totalOut)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = outColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun SectionDirectionHeader(
    title: String,
    count: Int,
    totalAmount: Long,
    isIn: Boolean
) {
    val isDark = LocalAppDarkTheme.current
    val accent = if (isIn) {
        if (isDark) InBadgeFgDark else InBadgeFgLight
    } else {
        if (isDark) OutBadgeFgDark else OutBadgeFgLight
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (isIn) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "$title ($count)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = formatNeondraAmount(totalAmount),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            fontFamily = JakartaSansFamily,
            color = accent
        )
    }
}

@Composable
private fun EmptyDirectionEntryCard(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(14.dp)
        )
    }
}

@Composable
private fun MatchedFamilyCard(
    family: FamilyProfile,
    totalIn: Long,
    totalOut: Long,
    entriesCount: Int,
    onCompareClick: () -> Unit
) {
    val isDark = LocalAppDarkTheme.current
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)) else null,
        modifier = Modifier.fillMaxWidth()
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(
                            text = if (family.familyId.isNotBlank()) "${family.name} (FN ${family.familyId})" else family.name,
                            style = MaterialTheme.typography.titleSmall,
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
                OutlinedButton(
                    onClick = onCompareClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Compare", style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(
                text = "$entriesCount entries • In: ${formatNeondraAmount(totalIn)} • Out: ${formatNeondraAmount(totalOut)} • Net: ${formatNeondraAmount(totalIn - totalOut)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun MatchedEventCard(
    event: EventEntry,
    catalog: DropdownCatalog
) {
    val isDark = LocalAppDarkTheme.current
    val (catColor, catIcon) = catalog.resolveCategoryVisual(event.category)
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (event.category.isNotBlank()) {
                    CategoryBadge(label = event.category, color = catColor, icon = catIcon)
                }
            }
            if (event.description.isNotBlank()) {
                Text(
                    text = event.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = formatDisplayDate(event.eventDateMillis),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (event.location.isNotBlank()) {
                    Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = event.location,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SingleFamilySearchHeaderCard(
    family: FamilyProfile,
    totalIn: Long,
    totalOut: Long,
    entriesCount: Int,
    onCompareClick: () -> Unit
) {
    val isDark = LocalAppDarkTheme.current
    val inColor = if (isDark) InBadgeFgDark else InBadgeFgLight
    val outColor = if (isDark) OutBadgeFgDark else OutBadgeFgLight
    val netBalance = totalIn - totalOut
    val netPrefix = if (netBalance >= 0L) "+" else ""

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("single_family_search_header_${family.id}")
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
                    if (family.familyId.isNotBlank()) {
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
                        if (family.address.isNotBlank()) {
                            Text(
                                text = family.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = onCompareClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Compare", style = MaterialTheme.typography.labelMedium)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = inColor.copy(alpha = if (isDark) 0.16f else 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text("In", style = MaterialTheme.typography.labelSmall, color = inColor)
                        Text(
                            text = formatNeondraAmount(totalIn),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = inColor
                        )
                    }
                }
                Surface(
                    color = outColor.copy(alpha = if (isDark) 0.16f else 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text("Out", style = MaterialTheme.typography.labelSmall, color = outColor)
                        Text(
                            text = formatNeondraAmount(totalOut),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = outColor
                        )
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text(
                            text = "Net ($entriesCount)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "$netPrefix${formatNeondraAmount(netBalance)}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleFamilyEntryCard(
    record: ActivityRecord,
    catalog: DropdownCatalog,
    onClick: () -> Unit
) {
    val isDark = LocalAppDarkTheme.current
    val (catColor, catIcon) = catalog.resolveCategoryVisual(record.eventType)
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("single_family_entry_${record.id}")
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    DirectionPill(direction = record.parsedDirection)
                    CategoryBadge(label = record.eventType, color = catColor, icon = catIcon)
                }
                Text(
                    text = formatDisplayDate(record.eventDateMillis),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = JakartaSansFamily
                )
            }

            if (record.eventDetail.isNotBlank()) {
                Text(
                    text = record.eventDetail,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (record.homeItems.isNotBlank()) {
                Text(
                    text = "تحائف: ${record.homeItems}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = NotoArabicFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val hasExtraMoney = record.cashAmount > 0L || record.haarAmount > 0L || record.salamiAmount > 0L
            if (hasExtraMoney) {
                val breakdownText = buildList {
                    if (record.cashAmount > 0L) add("نقد ${formatNeondraAmount(record.cashAmount)}")
                    if (record.haarAmount > 0L) add("ہار ${formatNeondraAmount(record.haarAmount)}")
                    if (record.salamiAmount > 0L) add("سلامی ${formatNeondraAmount(record.salamiAmount)}")
                    if (record.neondraAmount > 0L) add("نیوندرا ${formatNeondraAmount(record.neondraAmount)}")
                }.joinToString(" • ")
                if (breakdownText.isNotEmpty()) {
                    Text(
                        text = breakdownText,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = NotoArabicFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Amount: ${formatNeondraAmount(record.totalAmount)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MatchedActivityCard(
    record: ActivityRecord,
    catalog: DropdownCatalog,
    onClick: () -> Unit
) {
    val isDark = LocalAppDarkTheme.current
    val (catColor, catIcon) = catalog.resolveCategoryVisual(record.eventType)
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (record.familyId.isNotBlank()) "${record.name} (FN ${record.familyId})" else record.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = buildString {
                            append(formatDisplayDate(record.eventDateMillis))
                            if (record.eventDetail.isNotBlank()) {
                                append(" • ")
                                append(record.eventDetail)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DirectionPill(direction = record.parsedDirection)
            }
            if (record.homeItems.isNotBlank()) {
                Text(
                    text = "تحائف: ${record.homeItems}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = NotoArabicFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryBadge(label = record.eventType, color = catColor, icon = catIcon)
                Text(
                    text = "Amount: ${formatNeondraAmount(record.totalAmount)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SearchLoadingCard(message: String) {
    val isDark = LocalAppDarkTheme.current
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SearchErrorCard(
    message: String,
    onRetry: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Search Error",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            OutlinedButton(
                onClick = onRetry,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Retry", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}
