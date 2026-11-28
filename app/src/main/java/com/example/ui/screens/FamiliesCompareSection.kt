package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ai.CategoryInOutBreakdown
import com.example.ai.SingleFamilyInOutComparison
import com.example.data.ActivityDirection
import com.example.data.ActivityRecord
import com.example.data.DropdownCatalog
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
import kotlin.math.max

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

internal fun LazyListScope.familiesCompareContent(
    families: List<FamilyProfile>,
    catalog: DropdownCatalog,
    isDark: Boolean,
    compareFamilyQuery: String,
    onCompareFamilyQueryChange: (String) -> Unit,
    selectedSingleFamilyId: String?,
    singleFamilyComparisonState: UiState<SingleFamilyInOutComparison>?,
    onSelectSingleFamily: (String) -> Unit,
    onActivityClick: (ActivityRecord) -> Unit
) {
    val compareMatches = filterFamiliesForCompare(families, compareFamilyQuery)
    item(key = "compare_family_search_card") {
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
                    onValueChange = onCompareFamilyQueryChange,
                    placeholder = { Text("Search name or FN...") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (compareFamilyQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onCompareFamilyQueryChange("") }
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
                                onCompareFamilyQueryChange("")
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
                                            onCompareFamilyQueryChange("")
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
            item(key = "compare_loading") {
                CompareLoadingCard("Loading comparison...")
            }
        }

        is UiState.Error -> {
            item(key = "compare_error") {
                CompareErrorCard(
                    message = singleFamilyComparisonState.message,
                    onRetry = {
                        selectedSingleFamilyId?.let(onSelectSingleFamily)
                    }
                )
            }
        }

        is UiState.Success -> {
            val comp = singleFamilyComparisonState.data
            item(key = "compare_summary_${comp.family.id}") {
                SingleFamilySummaryCard(comp = comp)
            }

            if (comp.categoryBreakdowns.isNotEmpty()) {
                item(key = "compare_breakdown_header") {
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

            item(key = "compare_in_header") {
                SectionDirectionHeader(
                    title = "In Entries (Received)",
                    count = comp.inEntries.size,
                    totalAmount = comp.totalIn,
                    isIn = true
                )
            }
            if (comp.inEntries.isEmpty()) {
                item(key = "compare_in_empty") {
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

            item(key = "compare_out_header") {
                SectionDirectionHeader(
                    title = "Out Entries (Given)",
                    count = comp.outEntries.size,
                    totalAmount = comp.totalOut,
                    isIn = false
                )
            }
            if (comp.outEntries.isEmpty()) {
                item(key = "compare_out_empty") {
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
private fun CompareLoadingCard(message: String) {
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
private fun CompareErrorCard(
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
                    text = "Compare Error",
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
