package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TrendingUp
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ai.AiBalanceComparisonResult
import com.example.ai.AiFamilyBalanceInsight
import com.example.ai.AiSearchResult
import com.example.data.ActivityDirection
import com.example.data.ActivityRecord
import com.example.data.DropdownCatalog
import com.example.data.EventEntry
import com.example.data.FamilyProfile
import com.example.data.UiState
import com.example.ui.components.CategoryBadge
import com.example.ui.components.DirectionPill
import com.example.ui.components.formatDisplayDate
import com.example.ui.components.formatNeondraAmount
import com.example.ui.theme.InBadgeFgDark
import com.example.ui.theme.InBadgeFgLight
import com.example.ui.theme.JakartaSansFamily
import com.example.ui.theme.OutBadgeFgDark
import com.example.ui.theme.OutBadgeFgLight
import com.example.ui.viewmodel.LedgerDashboardData
import kotlin.math.max

private val AI_SEARCH_PROMPTS = listOf(
    "Who has the highest In balance?",
    "Upcoming events and weddings",
    "Families with negative net balance",
    "Recent Out gifts and cash"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiAssistantScreen(
    dashboardState: UiState<LedgerDashboardData>,
    aiSearchState: UiState<AiSearchResult>?,
    aiComparisonState: UiState<AiBalanceComparisonResult>?,
    selectedCompareFamilyIds: Set<String>,
    initialTabIndex: Int = 0,
    onRunAiSearch: (String) -> Unit,
    onClearAiSearch: () -> Unit,
    onToggleFamilyCompare: (String) -> Unit,
    onSelectTopFamilies: () -> Unit,
    onRunAiComparison: () -> Unit,
    onClearAiComparison: () -> Unit,
    onActivityClick: (ActivityRecord) -> Unit,
    onCompareThisFamily: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable(initialTabIndex) { mutableIntStateOf(initialTabIndex) }
    var searchPrompt by rememberSaveable { mutableStateOf("") }

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
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                        Column {
                            Text(
                                text = "Smart Search & Balance Compare",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Instantly search families, ledger items, and occasions offline on your phone, or compare In vs Out balances side-by-side.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

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
                        Text("Smart Search")
                    }
                    SegmentedButton(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.CompareArrows,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("ai_mode_compare")
                    ) {
                        Text("Compare Balances")
                    }
                }
            }

            if (selectedTab == 0) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = searchPrompt,
                            onValueChange = { searchPrompt = it },
                            label = { Text("Search families, events, gifts, or amounts...") },
                            placeholder = { Text("e.g. Show all wedding events and high In families") },
                            leadingIcon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
                            trailingIcon = {
                                if (searchPrompt.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            searchPrompt = ""
                                            onClearAiSearch()
                                        }
                                    ) {
                                        Icon(Icons.Rounded.Clear, contentDescription = "Clear Search")
                                    }
                                }
                            },
                            maxLines = 3,
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
                            AI_SEARCH_PROMPTS.forEach { preset ->
                                AssistChip(
                                    onClick = {
                                        searchPrompt = preset
                                        onRunAiSearch(preset)
                                    },
                                    label = { Text(preset) }
                                )
                            }
                        }

                        Button(
                            onClick = { onRunAiSearch(searchPrompt) },
                            enabled = searchPrompt.isNotBlank() && aiSearchState !is UiState.Loading,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("ai_search_submit_button")
                        ) {
                            if (aiSearchState is UiState.Loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Searching with AI...")
                            } else {
                                Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Search Diary Offline", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                when (aiSearchState) {
                    null -> {
                        item {
                            AiEmptyPromptCard(
                                title = "Ask Anything About Your Diary",
                                body = "Smart search scans your ${families.size} families, ${events.size} saved occasions, and ${activities.size} ledger entries locally on your phone."
                            )
                        }
                    }

                    is UiState.Loading -> {
                        item {
                            AiLoadingCard("Scanning local families, occasions, and ledger records...")
                        }
                    }

                    is UiState.Error -> {
                        item {
                            AiErrorCard(
                                message = aiSearchState.message,
                                onRetry = {
                                    if (searchPrompt.isNotBlank()) onRunAiSearch(searchPrompt)
                                }
                            )
                        }
                    }

                    is UiState.Success -> {
                        val result = aiSearchState.data
                        item {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                                            imageVector = Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "AI Answer",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = result.answerSummary,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (result.keyInsights.isNotEmpty()) {
                                        HorizontalDivider()
                                        result.keyInsights.forEach { insight ->
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Lightbulb,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.size(16.dp).padding(top = 2.dp)
                                                )
                                                Text(
                                                    text = insight,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        val matchedFamilies = families.filter { it.id in result.matchedFamilyIds }
                        if (matchedFamilies.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Matching Families (${matchedFamilies.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            items(matchedFamilies, key = { "fam_${it.id}" }) { fam ->
                                val famActs = activities.filter {
                                    it.familyDocId == fam.id ||
                                        (it.familyId.isNotBlank() && it.familyId == fam.familyId &&
                                            it.name.equals(fam.name, ignoreCase = true))
                                }
                                val fIn = famActs.filter { it.parsedDirection == ActivityDirection.IN }.sumOf { it.neondraAmount }
                                val fOut = famActs.filter { it.parsedDirection == ActivityDirection.OUT }.sumOf { it.neondraAmount }

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

                        val matchedEvents = events.filter { it.id in result.matchedEventIds }
                        if (matchedEvents.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Matching Events (${matchedEvents.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            items(matchedEvents, key = { "evt_${it.id}" }) { evt ->
                                MatchedEventCard(event = evt, catalog = catalog)
                            }
                        }

                        val matchedActivities = activities.filter { it.id in result.matchedActivityIds }
                        if (matchedActivities.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Matching Ledger Entries (${matchedActivities.size})",
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
            } else {
                // Compare Family In & Out Balances Mode
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                                Text(
                                    text = "Select 2+ Families to Compare (${selectedCompareFamilyIds.size} selected)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = onSelectTopFamilies) {
                                    Text("Select Top 3")
                                }
                            }

                            if (families.isEmpty()) {
                                Text(
                                    text = "Add at least two families in the Families tab or via New Entry to compare their In and Out balances.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    families.forEach { fam ->
                                        val isSelected = fam.id in selectedCompareFamilyIds
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { onToggleFamilyCompare(fam.id) },
                                            leadingIcon = if (isSelected) {
                                                {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            } else null,
                                            label = {
                                                Text(
                                                    text = if (fam.familyId.isNotBlank()) "${fam.name} (FN ${fam.familyId})" else fam.name,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            ),
                                            modifier = Modifier.testTag("compare_family_chip_${fam.id}")
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = onRunAiComparison,
                                enabled = selectedCompareFamilyIds.size >= 2 && aiComparisonState !is UiState.Loading,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("ai_compare_button")
                            ) {
                                if (aiComparisonState is UiState.Loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Comparing Family Balances...")
                                } else {
                                    Icon(Icons.Rounded.CompareArrows, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Compare In & Out Balances", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                when (aiComparisonState) {
                    null -> {
                        item {
                            AiEmptyPromptCard(
                                title = "Compare Family In vs Out Balances",
                                body = "Pick 2 or more families above and tap Compare to get side-by-side In/Out charts, net differences, and balance insights offline."
                            )
                        }
                    }

                    is UiState.Loading -> {
                        item {
                            AiLoadingCard("Comparing In vs Out totals, net balances, and gift history...")
                        }
                    }

                    is UiState.Error -> {
                        item {
                            AiErrorCard(
                                message = aiComparisonState.message,
                                onRetry = onRunAiComparison
                            )
                        }
                    }

                    is UiState.Success -> {
                        val comp = aiComparisonState.data
                        item {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                                            imageVector = Icons.Rounded.Balance,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = comp.headline,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (comp.higherNetFamilyName.isNotBlank()) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.TrendingUp,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "Highest Net Balance: ${comp.higherNetFamilyName}",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = comp.summary,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }

                        val maxBarValue = comp.familyBreakdowns
                            .flatMap { listOf(it.totalIn, it.totalOut) }
                            .maxOrNull()
                            ?.coerceAtLeast(1L) ?: 1L

                        items(comp.familyBreakdowns, key = { "cmp_${it.familyDocId}" }) { item ->
                            FamilyBalanceCompareCard(item = item, maxScale = maxBarValue)
                        }

                        if (comp.recommendations.isNotEmpty()) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Lightbulb,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "AI Recommendations",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        comp.recommendations.forEach { rec ->
                                            Text(
                                                text = "• $rec",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
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
private fun FamilyBalanceCompareCard(
    item: AiFamilyBalanceInsight,
    maxScale: Long
) {
    val isDark = isSystemInDarkTheme()
    val inColor = if (isDark) InBadgeFgDark else InBadgeFgLight
    val outColor = if (isDark) OutBadgeFgDark else OutBadgeFgLight
    val inFraction = (item.totalIn.toFloat() / max(1L, maxScale).toFloat()).coerceIn(0f, 1f)
    val outFraction = (item.totalOut.toFloat() / max(1L, maxScale).toFloat()).coerceIn(0f, 1f)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = Modifier.fillMaxWidth()
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (item.familyId.isNotBlank()) "${item.familyName} (FN ${item.familyId})" else item.familyName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${item.entriesCount} ledger entries • ${item.eventsCount} linked events",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val netPrefix = if (item.netBalance >= 0) "+" else ""
                Surface(
                    color = if (item.netBalance >= 0) inColor.copy(alpha = 0.14f) else outColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Net: $netPrefix${formatNeondraAmount(item.netBalance)}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (item.netBalance >= 0) inColor else outColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            // IN bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("In (Received)", style = MaterialTheme.typography.labelMedium, color = inColor)
                    Text(
                        text = formatNeondraAmount(item.totalIn),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = JakartaSansFamily
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
                            .fillMaxWidth(inFraction.coerceAtLeast(if (item.totalIn > 0) 0.04f else 0f))
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(inColor)
                    )
                }
            }

            // OUT bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Out (Given)", style = MaterialTheme.typography.labelMedium, color = outColor)
                    Text(
                        text = formatNeondraAmount(item.totalOut),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = JakartaSansFamily
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
                            .fillMaxWidth(outFraction.coerceAtLeast(if (item.totalOut > 0) 0.04f else 0f))
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(outColor)
                    )
                }
            }

            Text(
                text = item.insight,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
    val (catColor, catIcon) = catalog.resolveCategoryVisual(event.category)
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
private fun MatchedActivityCard(
    record: ActivityRecord,
    catalog: DropdownCatalog,
    onClick: () -> Unit
) {
    val (catColor, catIcon) = catalog.resolveCategoryVisual(record.eventType)
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                        text = formatDisplayDate(record.eventDateMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DirectionPill(direction = record.parsedDirection)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryBadge(label = record.eventType, color = catColor, icon = catIcon)
                Text(
                    text = "Amount: ${formatNeondraAmount(record.neondraAmount)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AiEmptyPromptCard(title: String, body: String) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AiLoadingCard(message: String) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
private fun AiErrorCard(message: String, onRetry: () -> Unit) {
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
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(
                    text = "AI Request Failed",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(10.dp)) {
                Text("Retry")
            }
        }
    }
}
