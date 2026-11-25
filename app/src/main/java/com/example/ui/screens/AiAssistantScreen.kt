package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.Person
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ai.AiBalanceComparisonResult
import com.example.ai.AiFamilyBalanceInsight
import com.example.ai.AiFollowUpChatMessage
import com.example.ai.AiSearchResult
import com.example.ai.CategoryInOutBreakdown
import com.example.ai.SingleFamilyInOutComparison
import com.example.ai.getAppCheckDebugSecret
import com.example.ai.saveAppCheckDebugSecret
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
import com.example.ui.theme.NotoArabicFamily
import com.example.ui.theme.OutBadgeFgDark
import com.example.ui.theme.OutBadgeFgLight
import com.example.ui.viewmodel.LedgerDashboardData
import kotlin.math.max

private val AI_SEARCH_PROMPTS = listOf(
    "Who has the highest In balance?",
    "شادی entries and Neondra details",
    "Families with negative balance",
    "Recent نقد gifts & Neondra"
)

private val FOLLOW_UP_CHAT_SUGGESTIONS = listOf(
    "Who gave more In vs Out?",
    "کس تقریب میں سب سے زیادہ رقم آئی یا دی گئی؟",
    "آئندہ تقریب پر کتنا نیوندرا دینا مناسب رہے گا؟"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiAssistantScreen(
    dashboardState: UiState<LedgerDashboardData>,
    aiSearchState: UiState<AiSearchResult>?,
    aiComparisonState: UiState<AiBalanceComparisonResult>?,
    selectedCompareFamilyIds: Set<String>,
    selectedSingleFamilyId: String?,
    singleFamilyComparisonState: UiState<SingleFamilyInOutComparison>?,
    singleFamilyAiLoading: Boolean = false,
    singleFamilyAiError: String? = null,
    multiFamilyAiLoading: Boolean = false,
    multiFamilyAiError: String? = null,
    singleFamilyChatMessages: List<AiFollowUpChatMessage> = emptyList(),
    singleFamilyChatLoading: Boolean = false,
    singleFamilyChatError: String? = null,
    multiFamilyChatMessages: List<AiFollowUpChatMessage> = emptyList(),
    multiFamilyChatLoading: Boolean = false,
    multiFamilyChatError: String? = null,
    initialTabIndex: Int = 0,
    onRunAiSearch: (String, Boolean) -> Unit,
    onClearAiSearch: () -> Unit,
    onSelectSingleFamily: (String) -> Unit,
    onRunSingleFamilyAiAnalysis: () -> Unit,
    onSendSingleFamilyChat: (String, Boolean) -> Unit = { _, _ -> },
    onClearSingleFamilyChat: () -> Unit = {},
    onToggleFamilyCompare: (String) -> Unit,
    onSelectTopFamilies: () -> Unit,
    onRunAiComparison: () -> Unit,
    onRunMultiFamilyAiAnalysis: () -> Unit = {},
    onSendMultiFamilyChat: (String, Boolean) -> Unit = { _, _ -> },
    onClearMultiFamilyChat: () -> Unit = {},
    onClearAiComparison: () -> Unit,
    onActivityClick: (ActivityRecord) -> Unit,
    onCompareThisFamily: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable(initialTabIndex) { mutableIntStateOf(initialTabIndex) }
    var compareSubMode by rememberSaveable { mutableIntStateOf(0) } // 0 = Within 1 Family, 1 = 2+ Families
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
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                        Column {
                            Text(
                                text = "Life Line Search & Compare",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Search families, events, and ledger entries 100% offline on your device, or compare In vs Out balances.",
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
                        Text("Offline Search")
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
                        Text("Compare In / Out")
                    }
                }
            }

            if (selectedTab == 0) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = searchPrompt,
                            onValueChange = { searchPrompt = it },
                            label = { Text("Search offline families, events, FN, or entries...") },
                            placeholder = { Text("e.g. FN 1, شادی, نقد, or highest In balance") },
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
                                .height(50.dp)
                                .testTag("ai_search_offline_button")
                        ) {
                            if (aiSearchState is UiState.Loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Searching offline records...")
                            } else {
                                Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Offline Search", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                when (aiSearchState) {
                    null -> {
                        item {
                            AiEmptyPromptCard(
                                title = "Offline Search",
                                body = "Search your ${families.size} families and ${activities.size} ledger entries offline in English or Urdu."
                            )
                        }
                    }

                    is UiState.Loading -> {
                        item {
                            AiLoadingCard("Searching your offline families and ledger entries...")
                        }
                    }

                    is UiState.Error -> {
                        item {
                            AiErrorCard(
                                message = aiSearchState.message,
                                onRetry = {
                                    if (searchPrompt.isNotBlank()) onRunAiSearch(searchPrompt, false)
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
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Search,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "Offline Search Results",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        SourceModePill(isOnlineAi = false)
                                    }
                                    Text(
                                        text = result.answerSummary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = NotoArabicFamily
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
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .padding(top = 2.dp)
                                                )
                                                Text(
                                                    text = insight,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontFamily = NotoArabicFamily,
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
                                    text = "Matched Families (${matchedFamilies.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            items(matchedFamilies, key = { "fam_${it.id}" }) { fam ->
                                val famActs = activities.filter {
                                    FamilyIdentityMatcher.activityBelongsToFamily(it, fam)
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
                                        compareSubMode = 0
                                        selectedTab = 1
                                    }
                                )
                            }
                        }

                        val matchedEvents = events.filter { it.id in result.matchedEventIds }
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

                        val matchedActivities = activities.filter { it.id in result.matchedActivityIds }
                        if (matchedActivities.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Matched Ledger Entries (${matchedActivities.size})",
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
                // Compare In & Out Mode: Within 1 Family OR 2+ Families
                item {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = compareSubMode == 0,
                            onClick = { compareSubMode = 0 },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.testTag("compare_submode_single")
                        ) {
                            Text("Within 1 Family", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        SegmentedButton(
                            selected = compareSubMode == 1,
                            onClick = { compareSubMode = 1 },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.Groups,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.testTag("compare_submode_multi")
                        ) {
                            Text("2+ Families", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                if (compareSubMode == 0) {
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Pick One Family to Compare In vs Out Entries",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )

                                if (families.isEmpty()) {
                                    Text(
                                        text = "Add a family or ledger entry first to compare In vs Out records.",
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
                                            val isSelected = fam.id == selectedSingleFamilyId
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { onSelectSingleFamily(fam.id) },
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
                                                modifier = Modifier.testTag("single_family_chip_${fam.id}")
                                            )
                                        }
                                    }
                                }

                                Button(
                                    onClick = onRunSingleFamilyAiAnalysis,
                                    enabled = selectedSingleFamilyId != null && !singleFamilyAiLoading && singleFamilyComparisonState !is UiState.Loading,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("single_family_ai_button")
                                ) {
                                    if (singleFamilyAiLoading || singleFamilyComparisonState is UiState.Loading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Analyzing with Online AI...")
                                    } else {
                                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Analyze In & Out with AI (Urdu)", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }

                    if (singleFamilyAiLoading) {
                        item {
                            AiLoadingCard("Gemini AI is comparing this family's In vs Out entries in Urdu...")
                        }
                    }

                    if (!singleFamilyAiError.isNullOrBlank()) {
                        item {
                            AiErrorCard(
                                message = singleFamilyAiError,
                                onRetry = onRunSingleFamilyAiAnalysis
                            )
                        }
                    }

                    when (singleFamilyComparisonState) {
                        null -> {
                            item {
                                AiEmptyPromptCard(
                                    title = "Compare In vs Out Within One Family",
                                    body = "Tap any family above to view its instant offline In (Received) vs Out (Given) comparison, ask follow-up questions in AI Chat, or run Online AI Urdu analysis."
                                )
                            }
                        }

                        is UiState.Loading -> {
                            item {
                                AiLoadingCard("Gemini AI is comparing this family's In vs Out entries in Urdu...")
                            }
                        }

                        is UiState.Error -> {
                            item {
                                AiErrorCard(
                                    message = singleFamilyComparisonState.message,
                                    onRetry = onRunSingleFamilyAiAnalysis
                                )
                            }
                        }

                        is UiState.Success -> {
                            val comp = singleFamilyComparisonState.data
                            item {
                                SingleFamilySummaryCard(comp = comp)
                            }

                            item(key = "single_family_followup_chat") {
                                ComparisonFollowUpChatCard(
                                    contextLabel = "${comp.family.name} (FN ${comp.family.familyId})",
                                    messages = singleFamilyChatMessages,
                                    isLoading = singleFamilyChatLoading,
                                    errorMessage = singleFamilyChatError,
                                    onSendQuestion = onSendSingleFamilyChat,
                                    onClearChat = onClearSingleFamilyChat
                                )
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
                                    EmptyDirectionEntryCard("No In (Received) entries recorded for ${comp.family.name}.")
                                }
                            } else {
                                items(comp.inEntries, key = { "sf_in_${it.id}" }) { act ->
                                    MatchedActivityCard(
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
                                    EmptyDirectionEntryCard("No Out (Given) entries recorded for ${comp.family.name}.")
                                }
                            } else {
                                items(comp.outEntries, key = { "sf_out_${it.id}" }) { act ->
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
                    // Compare 2+ Families Mode
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
                                        text = "Add at least 2 families to compare In and Out balances.",
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
                                        Text("Comparing families...")
                                    } else {
                                        Icon(Icons.AutoMirrored.Rounded.CompareArrows, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Compare In & Out Offline", fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                OutlinedButton(
                                    onClick = onRunMultiFamilyAiAnalysis,
                                    enabled = selectedCompareFamilyIds.size >= 2 && !multiFamilyAiLoading,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("multi_family_online_ai_button")
                                ) {
                                    if (multiFamilyAiLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Online AI comparison running...")
                                    } else {
                                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Analyze with Online AI (Urdu)", fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }

                    if (multiFamilyAiLoading) {
                        item {
                            AiLoadingCard("Gemini AI is comparing selected families in Urdu...")
                        }
                    }

                    if (!multiFamilyAiError.isNullOrBlank()) {
                        item {
                            AiErrorCard(
                                message = multiFamilyAiError,
                                onRetry = onRunMultiFamilyAiAnalysis,
                                onOfflineFallback = onRunAiComparison
                            )
                        }
                    }

                    when (aiComparisonState) {
                        null -> {
                            item {
                                AiEmptyPromptCard(
                                    title = "Compare Balances Across Families",
                                    body = "Select 2 or more families above to compare In vs Out totals offline, run Online AI Urdu analysis, and ask follow-up questions in AI Chat."
                                )
                            }
                        }

                        is UiState.Loading -> {
                            item {
                                AiLoadingCard("Comparing In, Out, and net balances across families...")
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
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Balance,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = comp.headline,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontFamily = NotoArabicFamily,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            SourceModePill(isOnlineAi = comp.usedOnlineAi)
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
                                                        imageVector = Icons.AutoMirrored.Rounded.TrendingUp,
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
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontFamily = NotoArabicFamily
                                        )
                                    }
                                }
                            }

                            item(key = "multi_family_followup_chat") {
                                ComparisonFollowUpChatCard(
                                    contextLabel = "${comp.familyBreakdowns.size} Selected Families",
                                    messages = multiFamilyChatMessages,
                                    isLoading = multiFamilyChatLoading,
                                    errorMessage = multiFamilyChatError,
                                    onSendQuestion = onSendMultiFamilyChat,
                                    onClearChat = onClearMultiFamilyChat
                                )
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
                                                    text = "Balance Insights (Urdu)",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            comp.recommendations.forEach { rec ->
                                                Text(
                                                    text = "• $rec",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontFamily = NotoArabicFamily,
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
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ComparisonFollowUpChatCard(
    contextLabel: String,
    messages: List<AiFollowUpChatMessage>,
    isLoading: Boolean,
    errorMessage: String?,
    onSendQuestion: (question: String, useOnlineAi: Boolean) -> Unit,
    onClearChat: () -> Unit
) {
    var draftQuestion by rememberSaveable(contextLabel) { mutableStateOf("") }
    var lastAskedQuestion by rememberSaveable(contextLabel) { mutableStateOf("") }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("comparison_followup_chat_card")
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Chat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Follow-Up AI Chat (Replies in Urdu)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Ask further questions about $contextLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (messages.isNotEmpty()) {
                    TextButton(onClick = onClearChat) {
                        Text("Clear")
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FOLLOW_UP_CHAT_SUGGESTIONS.forEach { chipPrompt ->
                    AssistChip(
                        onClick = {
                            lastAskedQuestion = chipPrompt
                            draftQuestion = ""
                            onSendQuestion(chipPrompt, true)
                        },
                        label = { Text(chipPrompt, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }

            if (messages.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    messages.forEach { msg ->
                        val isUser = msg.isUser
                        Surface(
                            color = if (isUser) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isUser) "You" else "Life Line AI (Urdu)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isUser) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        }
                                    )
                                    if (!isUser) {
                                        Text(
                                            text = if (msg.isOnlineAi) "Online AI" else "Offline",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = if (isUser) JakartaSansFamily else NotoArabicFamily,
                                    color = if (isUser) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = "AI is preparing an Urdu reply...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                val fallbackQ = lastAskedQuestion.ifBlank { draftQuestion }
                AiErrorCard(
                    message = errorMessage,
                    onRetry = {
                        if (fallbackQ.isNotBlank()) onSendQuestion(fallbackQ, true)
                    },
                    onOfflineFallback = if (fallbackQ.isNotBlank()) {
                        { onSendQuestion(fallbackQ, false) }
                    } else null
                )
            }

            OutlinedTextField(
                value = draftQuestion,
                onValueChange = { draftQuestion = it },
                label = { Text("Ask a follow-up question...") },
                placeholder = { Text("e.g. Which occasion had the highest Neondra?") },
                maxLines = 3,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("comparison_chat_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val q = draftQuestion.trim()
                        if (q.isNotEmpty()) {
                            lastAskedQuestion = q
                            draftQuestion = ""
                            onSendQuestion(q, true)
                        }
                    },
                    enabled = draftQuestion.isNotBlank() && !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("comparison_chat_send_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ask AI (Urdu)", fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = {
                        val q = draftQuestion.trim()
                        if (q.isNotEmpty()) {
                            lastAskedQuestion = q
                            draftQuestion = ""
                            onSendQuestion(q, false)
                        }
                    },
                    enabled = draftQuestion.isNotBlank() && !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(46.dp)
                        .testTag("comparison_chat_offline_button")
                ) {
                    Icon(Icons.Rounded.OfflineBolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Offline")
                }
            }
        }
    }
}

@Composable
private fun SourceModePill(isOnlineAi: Boolean) {
    Surface(
        color = if (isOnlineAi) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isOnlineAi) Icons.Rounded.CloudDone else Icons.Rounded.OfflineBolt,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (isOnlineAi) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = if (isOnlineAi) "Online Gemini AI • Urdu" else "Offline • Urdu",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isOnlineAi) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun SingleFamilySummaryCard(comp: SingleFamilyInOutComparison) {
    val isDark = isSystemInDarkTheme()
    val inColor = if (isDark) InBadgeFgDark else InBadgeFgLight
    val outColor = if (isDark) OutBadgeFgDark else OutBadgeFgLight
    val maxVal = max(comp.totalIn, comp.totalOut).coerceAtLeast(1L)
    val inFraction = (comp.totalIn.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
    val outFraction = (comp.totalOut.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
    val netPrefix = if (comp.netBalance >= 0) "+" else ""

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
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
                Column(modifier = Modifier.weight(1f)) {
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
                        if (comp.family.fatherName.isNotBlank()) add("Father: ${comp.family.fatherName}")
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
                SourceModePill(isOnlineAi = comp.usedOnlineAi)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = inColor.copy(alpha = 0.12f),
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
                    color = outColor.copy(alpha = 0.12f),
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
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
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
            if (comp.insights.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    comp.insights.forEach { insight ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lightbulb,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(top = 2.dp)
                            )
                            Text(
                                text = insight,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = NotoArabicFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryInOutCard(
    breakdown: CategoryInOutBreakdown,
    catalog: DropdownCatalog
) {
    val isDark = isSystemInDarkTheme()
    val inColor = if (isDark) InBadgeFgDark else InBadgeFgLight
    val outColor = if (isDark) OutBadgeFgDark else OutBadgeFgLight
    val (catColor, catIcon) = catalog.resolveCategoryVisual(breakdown.categoryName)
    val netPrefix = if (breakdown.netBalance >= 0) "+" else ""

    Card(
        shape = RoundedCornerShape(16.dp),
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
    val isDark = isSystemInDarkTheme()
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
                        text = "${item.entriesCount} ledger entries • ${item.eventsCount} events",
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
                fontFamily = NotoArabicFamily,
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
                        val meta = buildList {
                            if (family.fatherName.isNotBlank()) add("Father: ${family.fatherName}")
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
                    text = "Home Gift / Items: ${record.homeItems}",
                    style = MaterialTheme.typography.bodySmall,
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
                imageVector = Icons.Rounded.Search,
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
private fun AiErrorCard(
    message: String,
    onRetry: () -> Unit,
    onOfflineFallback: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isAppCheckError = message.contains("App Check", ignoreCase = true) ||
        message.contains("403", ignoreCase = true)
    val debugToken = remember(message) {
        if (isAppCheckError) getAppCheckDebugSecret(context) else null
    }
    var customTokenInput by remember { mutableStateOf("") }
    var showTechnicalToken by remember { mutableStateOf(false) }
    var showTokenInput by remember { mutableStateOf(false) }
    var copiedNotice by remember { mutableStateOf(false) }
    var savedRestartNotice by remember { mutableStateOf(false) }

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
                    text = "AI Request Failed",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Medium
            )

            if (isAppCheckError && debugToken != null) {
                Text(
                    text = "Sideloaded debug APKs on a physical phone do not receive AI Studio's launch debug token automatically. Test Online AI in AI Studio's Streaming Emulator, paste an authorized debug token below, or use Offline Search and Offline Comparison.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )

                TextButton(
                    onClick = { showTechnicalToken = !showTechnicalToken },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = if (showTechnicalToken) "Hide Technical Details" else "Technical Details / Debug Token",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (showTechnicalToken) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Device App Check Debug Token",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = debugToken,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        cm?.setPrimaryClip(ClipData.newPlainText("App Check Debug Token", debugToken))
                                        copiedNotice = true
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (copiedNotice) "Copied!" else "Copy Token")
                                }
                                TextButton(onClick = { showTokenInput = !showTokenInput }) {
                                    Text(if (showTokenInput) "Close Input" else "Paste Token")
                                }
                            }
                            if (showTokenInput) {
                                OutlinedTextField(
                                    value = customTokenInput,
                                    onValueChange = { customTokenInput = it },
                                    label = { Text("Authorized App Check Debug Token") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    onClick = {
                                        if (saveAppCheckDebugSecret(context, customTokenInput)) {
                                            showTokenInput = false
                                            savedRestartNotice = true
                                        }
                                    },
                                    enabled = customTokenInput.isNotBlank(),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Save Token")
                                }
                            }
                            if (savedRestartNotice) {
                                Text(
                                    text = "Token saved. Force-close and reopen the app for the new token to take effect.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onOfflineFallback != null) {
                    Button(
                        onClick = onOfflineFallback,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Rounded.OfflineBolt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Use Offline Mode")
                    }
                }
                OutlinedButton(
                    onClick = onRetry,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.55f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Retry", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}
