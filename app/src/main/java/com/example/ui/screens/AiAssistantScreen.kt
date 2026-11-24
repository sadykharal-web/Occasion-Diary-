package com.example.ui.screens

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
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudDone
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
import androidx.compose.runtime.saveable.rememberSaveable
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.remember
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
import androidx.compose.material.icons.rounded.ContentCopy
import com.example.ai.AiBalanceComparisonResult
import com.example.ai.AiFamilyBalanceInsight
import com.example.ai.AiSearchResult
import com.example.ai.CategoryInOutBreakdown
import com.example.ai.SingleFamilyInOutComparison
import com.example.ai.getAppCheckDebugSecret
import com.example.ai.saveAppCheckDebugSecret
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
    "سب سے زیادہ آمدن کس خاندان کی ہے؟",
    "آنے والی تقریبات اور شادیاں",
    "منفی بیلنس والے خاندان",
    "حالیہ خرچ اور نقد تحائف"
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
    initialTabIndex: Int = 0,
    onRunAiSearch: (String, Boolean) -> Unit,
    onClearAiSearch: () -> Unit,
    onSelectSingleFamily: (String) -> Unit,
    onRunSingleFamilyAiAnalysis: () -> Unit,
    onToggleFamilyCompare: (String) -> Unit,
    onSelectTopFamilies: () -> Unit,
    onRunAiComparison: () -> Unit,
    onRunMultiFamilyAiAnalysis: () -> Unit = {},
    onClearAiComparison: () -> Unit,
    onActivityClick: (ActivityRecord) -> Unit,
    onCompareThisFamily: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable(initialTabIndex) { mutableIntStateOf(initialTabIndex) }
    var compareSubMode by rememberSaveable { mutableIntStateOf(0) } // 0 = ایک خاندان کے اندر، 1 = 2 یا زیادہ خاندان
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
                                text = "Life Line AI تلاش اور موازنہ",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "فون کے آف لائن ریکارڈ میں آن لائن Gemini AI سے تلاش کریں یا ایک یا زیادہ خاندانوں کی آمدن اور خرچ کا موازنہ کریں۔",
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
                        Text("AI تلاش")
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
                        Text("آمدن / خرچ موازنہ")
                    }
                }
            }

            if (selectedTab == 0) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = searchPrompt,
                            onValueChange = { searchPrompt = it },
                            label = { Text("اپنے آف لائن کھاتے کے بارے میں AI سے پوچھیں...") },
                            placeholder = { Text("مثلاً شادی کی تمام تقریبات اور زیادہ آمدن والے خاندان دکھائیں") },
                            leadingIcon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
                            trailingIcon = {
                                if (searchPrompt.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            searchPrompt = ""
                                            onClearAiSearch()
                                        }
                                    ) {
                                        Icon(Icons.Rounded.Clear, contentDescription = "تلاش صاف کریں")
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
                                        onRunAiSearch(preset, true)
                                    },
                                    label = { Text(preset) }
                                )
                            }
                        }

                        Button(
                            onClick = { onRunAiSearch(searchPrompt, true) },
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
                                Text("AI کے ذریعے آف لائن ریکارڈ تلاش کیا جا رہا ہے...")
                            } else {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("آن لائن AI سے تلاش کریں", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        OutlinedButton(
                            onClick = { onRunAiSearch(searchPrompt, false) },
                            enabled = searchPrompt.isNotBlank() && aiSearchState !is UiState.Loading,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("ai_search_offline_button")
                        ) {
                            Icon(Icons.Rounded.OfflineBolt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("آف لائن فوری تلاش", fontWeight = FontWeight.Medium)
                        }
                    }
                }

                when (aiSearchState) {
                    null -> {
                        item {
                            AiEmptyPromptCard(
                                title = "آف لائن ڈیٹا میں Gemini AI سے تلاش کریں",
                                body = "آن لائن AI آپ کے فون میں محفوظ ${families.size} خاندانوں، ${events.size} تقریبات اور ${activities.size} کھاتہ اندراجات کا تجزیہ کر کے اردو میں جواب دیتا ہے۔"
                            )
                        }
                    }

                    is UiState.Loading -> {
                        item {
                            AiLoadingCard("AI آپ کے آف لائن خاندانوں، تقریبات اور کھاتے کے اندراجات کا تجزیہ کر رہا ہے...")
                        }
                    }

                    is UiState.Error -> {
                        item {
                            AiErrorCard(
                                message = aiSearchState.message,
                                onRetry = {
                                    if (searchPrompt.isNotBlank()) onRunAiSearch(searchPrompt, true)
                                },
                                onOfflineFallback = {
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
                                                imageVector = Icons.Rounded.AutoAwesome,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "AI کا جواب",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        SourceModePill(isOnlineAi = result.isOnlineAi)
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
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .padding(top = 2.dp)
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
                                    text = "ملتے جلتے خاندان (${matchedFamilies.size})",
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
                                    text = "متعلقہ تقریبات (${matchedEvents.size})",
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
                                    text = "متعلقہ کھاتہ اندراجات (${matchedActivities.size})",
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
                // Compare In & Out Mode: Single Family (In vs Out) OR Multi-Family
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
                            Text("ایک خاندان کے اندر", maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                            Text("2+ خاندان", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                if (compareSubMode == 0) {
                    // Single-Family In vs Out Entries Comparison
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
                                    text = "ایک خاندان کے آمدن اور خرچ کا موازنہ کریں",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )

                                if (families.isEmpty()) {
                                    Text(
                                        text = "آمدن اور خرچ کا موازنہ کرنے کے لیے خاندان ٹیب یا نیا اندراج سے خاندان شامل کریں۔",
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
                                        Text("AI کے ذریعے اردو موازنہ ہو رہا ہے...")
                                    } else {
                                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("آن لائن AI سے اردو موازنہ کریں", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }

                    if (singleFamilyAiLoading) {
                        item {
                            AiLoadingCard("Gemini AI اس خاندان کے آمدن اور خرچ کا اردو میں موازنہ کر رہا ہے...")
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
                                    title = "ایک خاندان کے اندر آمدن بمقابلہ خرچ",
                                    body = "اوپر کسی بھی خاندان پر ٹیپ کریں تاکہ اس کی آمدن (وصول) اور خرچ (دیے گئے) کا فوری آف لائن موازنہ دیکھیں، یا آن لائن AI سے اردو تجزیہ کروائیں۔"
                                )
                            }
                        }

                        is UiState.Loading -> {
                            item {
                                AiLoadingCard("Gemini AI اس خاندان کے آمدن اور خرچ کا اردو میں موازنہ کر رہا ہے...")
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

                            if (comp.categoryBreakdowns.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "اقسام کے لحاظ سے تفصیل (${comp.categoryBreakdowns.size})",
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
                                    title = "آمدن کے اندراجات (وصول)",
                                    count = comp.inEntries.size,
                                    totalAmount = comp.totalIn,
                                    isIn = true
                                )
                            }
                            if (comp.inEntries.isEmpty()) {
                                item {
                                    EmptyDirectionEntryCard("${comp.family.name} کے لیے کوئی آمدن (وصول) اندراج موجود نہیں ہے۔")
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
                                    title = "خرچ کے اندراجات (دیے گئے)",
                                    count = comp.outEntries.size,
                                    totalAmount = comp.totalOut,
                                    isIn = false
                                )
                            }
                            if (comp.outEntries.isEmpty()) {
                                item {
                                    EmptyDirectionEntryCard("${comp.family.name} کے لیے کوئی خرچ (دیے گئے) اندراج موجود نہیں ہے۔")
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
                                        text = "موازنے کے لیے 2+ خاندان منتخب کریں (${selectedCompareFamilyIds.size} منتخب)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = onSelectTopFamilies) {
                                        Text("سرفہرست 3 منتخب کریں")
                                    }
                                }

                                if (families.isEmpty()) {
                                    Text(
                                        text = "آمدن اور خرچ کا موازنہ کرنے کے لیے کم از کم دو خاندان شامل کریں۔",
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
                                        Text("خاندانوں کا موازنہ کیا جا رہا ہے...")
                                    } else {
                                        Icon(Icons.AutoMirrored.Rounded.CompareArrows, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("فوری آف لائن موازنہ دیکھیں", fontWeight = FontWeight.SemiBold)
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
                                        Text("آن لائن AI اردو موازنہ جاری ہے...")
                                    } else {
                                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("آن لائن AI سے اردو موازنہ", fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }

                    if (multiFamilyAiLoading) {
                        item {
                            AiLoadingCard("Gemini AI منتخب خاندانوں کے بیلنس کا اردو میں موازنہ کر رہا ہے...")
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
                                    title = "متعدد خاندانوں کے بیلنس کا موازنہ",
                                    body = "اوپر 2 یا زیادہ خاندان منتخب کریں اور فوری آف لائن موازنہ یا آن لائن AI اردو تجزیہ حاصل کریں۔"
                                )
                            }
                        }

                        is UiState.Loading -> {
                            item {
                                AiLoadingCard("خاندانوں کے آمدن، خرچ اور خالص بیلنس کا موازنہ ہو رہا ہے...")
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
                                                        text = "سب سے زیادہ خالص بیلنس: ${comp.higherNetFamilyName}",
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
                                                    text = "بیلنس کے اہم نکات",
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
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
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
                text = if (isOnlineAi) "آن لائن Gemini AI" else "آف لائن ریکارڈ",
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
                    if (comp.family.address.isNotBlank()) {
                        Text(
                            text = comp.family.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                SourceModePill(isOnlineAi = comp.usedOnlineAi)
            }

            // Side-by-side summary tiles: IN | OUT | NET
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
                        Text("آمدن (${comp.inEntries.size})", style = MaterialTheme.typography.labelSmall, color = inColor)
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
                        Text("خرچ (${comp.outEntries.size})", style = MaterialTheme.typography.labelSmall, color = outColor)
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
                            text = "خالص بیلنس",
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

            // Visual Comparison Bars
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
                fontWeight = FontWeight.Bold
            )
            Text(
                text = comp.summary,
                style = MaterialTheme.typography.bodyMedium
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
                    text = "خالص: $netPrefix${formatNeondraAmount(breakdown.netBalance)}",
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
                    text = "آمدن (${breakdown.inCount}): ${formatNeondraAmount(breakdown.totalIn)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = inColor,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "خرچ (${breakdown.outCount}): ${formatNeondraAmount(breakdown.totalOut)}",
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
                        text = "${item.entriesCount} کھاتہ اندراجات • ${item.eventsCount} تقریبات",
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
                        text = "خالص: $netPrefix${formatNeondraAmount(item.netBalance)}",
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
                    Text("آمدن (وصول)", style = MaterialTheme.typography.labelMedium, color = inColor)
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
                    Text("خرچ (دیے گئے)", style = MaterialTheme.typography.labelMedium, color = outColor)
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
                    Text("موازنہ دیکھیں", style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(
                text = "$entriesCount اندراجات • آمدن: ${formatNeondraAmount(totalIn)} • خرچ: ${formatNeondraAmount(totalOut)} • خالص: ${formatNeondraAmount(totalIn - totalOut)}",
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
                    text = "تحائف / اشیاء: ${record.homeItems}",
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
                    text = "رقم: ${formatNeondraAmount(record.neondraAmount)}",
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
                    text = "AI درخواست ناکام ہو گئی",
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
                    text = "فون پر براہ راست انسٹال کی گئی ڈیبگ APK کو لانچ کے وقت App Check کا مجاز ٹوکن خودکار نہیں ملتا۔ آپ ابھی \"آف لائن فوری تلاش\" اور \"فوری آف لائن موازنہ\" بغیر کسی رکاوٹ کے استعمال کر سکتے ہیں۔",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )

                TextButton(
                    onClick = { showTechnicalToken = !showTechnicalToken },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = if (showTechnicalToken) "تکنیکی تفصیل چھپائیں" else "تکنیکی تفصیل / ڈی بگ ٹوکن",
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
                                text = "ڈیوائس App Check ڈی بگ ٹوکن",
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
                                    Text(if (copiedNotice) "کاپی ہو گیا!" else "ٹوکن کاپی کریں")
                                }
                                TextButton(onClick = { showTokenInput = !showTokenInput }) {
                                    Text(if (showTokenInput) "ان پٹ بند کریں" else "ٹوکن درج کریں")
                                }
                            }
                            if (showTokenInput) {
                                OutlinedTextField(
                                    value = customTokenInput,
                                    onValueChange = { customTokenInput = it },
                                    label = { Text("مجاز App Check ڈی بگ ٹوکن") },
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
                                    Text("ٹوکن محفوظ کریں")
                                }
                            }
                            if (savedRestartNotice) {
                                Text(
                                    text = "ٹوکن محفوظ ہو گیا ہے۔ تبدیلی لاگو ہونے کے لیے براہ کرم ایپ کو مکمل بند کر کے دوبارہ کھولیں۔",
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
                        Text("آف لائن تلاش استعمال کریں")
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
                    Text("دوبارہ کوشش کریں", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

