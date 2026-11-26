package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ActivityDirection
import com.example.data.LedgerSummary
import com.example.ui.theme.HeroDarkEnd
import com.example.ui.theme.HeroDarkMid
import com.example.ui.theme.HeroDarkStart
import com.example.ui.theme.HeroLightEnd
import com.example.ui.theme.HeroLightMid
import com.example.ui.theme.HeroLightStart
import com.example.ui.theme.InBadgeBgDark
import com.example.ui.theme.InBadgeBgLight
import com.example.ui.theme.InBadgeFgDark
import com.example.ui.theme.InBadgeFgLight
import com.example.ui.theme.JakartaSansFamily
import com.example.ui.theme.LocalAppDarkTheme
import com.example.ui.theme.OutBadgeBgDark
import com.example.ui.theme.OutBadgeBgLight
import com.example.ui.theme.OutBadgeFgDark
import com.example.ui.theme.OutBadgeFgLight
import com.example.ui.theme.ReminderAmberBgDark
import com.example.ui.theme.ReminderAmberBgLight
import com.example.ui.theme.ReminderAmberFgDark
import com.example.ui.theme.ReminderAmberFgLight
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatNeondraAmount(amount: Long): String {
    return NumberFormat.getNumberInstance(Locale.US).format(amount)
}

fun formatDisplayDate(millis: Long): String {
    return SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH).format(Date(millis))
}

fun formatDisplayTime(millis: Long): String {
    return SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date(millis))
}

fun formatReminderOffsetLabel(minutesBefore: Long): String {
    return when (minutesBefore) {
        0L -> "At event time"
        60L -> "1 hour before"
        180L -> "3 hours before"
        1440L -> "1 day before"
        2880L -> "2 days before"
        else -> "$minutesBefore min before"
    }
}

fun adaptiveCategoryColor(baseColor: Color, isDark: Boolean): Color {
    if (!isDark) return baseColor
    return if (baseColor.luminance() < 0.45f) {
        lerp(baseColor, Color.White, 0.48f)
    } else {
        baseColor
    }
}

@Composable
fun CategoryBadge(
    label: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val isDark = LocalAppDarkTheme.current
    val resolvedColor = adaptiveCategoryColor(color, isDark)
    Surface(
        color = resolvedColor.copy(alpha = if (isDark) 0.22f else 0.14f),
        border = if (isDark) BorderStroke(1.dp, resolvedColor.copy(alpha = 0.42f)) else null,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = resolvedColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = resolvedColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun DirectionPill(
    direction: ActivityDirection,
    modifier: Modifier = Modifier
) {
    val isDark = LocalAppDarkTheme.current
    val bgColor = when (direction) {
        ActivityDirection.IN -> if (isDark) InBadgeBgDark else InBadgeBgLight
        ActivityDirection.OUT -> if (isDark) OutBadgeBgDark else OutBadgeBgLight
    }
    val fgColor = when (direction) {
        ActivityDirection.IN -> if (isDark) InBadgeFgDark else InBadgeFgLight
        ActivityDirection.OUT -> if (isDark) OutBadgeFgDark else OutBadgeFgLight
    }
    val icon = when (direction) {
        ActivityDirection.IN -> Icons.Rounded.ArrowDownward
        ActivityDirection.OUT -> Icons.Rounded.ArrowUpward
    }

    Surface(
        color = bgColor,
        border = if (isDark) BorderStroke(1.dp, fgColor.copy(alpha = 0.35f)) else null,
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = direction.shortLabel,
                tint = fgColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = direction.shortLabel,
                style = MaterialTheme.typography.labelMedium,
                color = fgColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ReminderStatusBadge(
    label: String = "Reminder On",
    modifier: Modifier = Modifier
) {
    val isDark = LocalAppDarkTheme.current
    val bg = if (isDark) ReminderAmberBgDark else ReminderAmberBgLight
    val fg = if (isDark) ReminderAmberFgDark else ReminderAmberFgLight
    Surface(
        color = bg,
        border = if (isDark) BorderStroke(1.dp, fg.copy(alpha = 0.35f)) else null,
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.NotificationsActive,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = fg
            )
        }
    }
}

@Composable
fun HeroLedgerSummaryCard(
    summary: LedgerSummary,
    modifier: Modifier = Modifier
) {
    val isDark = LocalAppDarkTheme.current
    val gradient = Brush.linearGradient(
        colors = if (isDark) {
            listOf(HeroDarkStart, HeroDarkMid, HeroDarkEnd)
        } else {
            listOf(HeroLightStart, HeroLightMid, HeroLightEnd)
        }
    )
    val inValueColor = if (isDark) InBadgeFgDark else Color.White
    val outValueColor = if (isDark) OutBadgeFgDark else Color.White
    val familyValueColor = if (isDark) Color(0xFFFFD166) else Color.White
    val reminderValueColor = if (isDark) Color(0xFFFF9ECE) else Color.White

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(gradient)
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Net Balance (In - Out)",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.88f)
                    )
                    val prefix = if (summary.netBalance >= 0) "+" else ""
                    Text(
                        text = "$prefix${formatNeondraAmount(summary.netBalance)}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = Color.White.copy(alpha = if (isDark) 0.14f else 0.18f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${summary.totalEntries}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontFamily = JakartaSansFamily
                        )
                        Text(
                            text = "Entries",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.88f)
                        )
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.24f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryStatColumn(
                    label = "Total In",
                    value = formatNeondraAmount(summary.totalIn),
                    valueColor = inValueColor,
                    modifier = Modifier.weight(1f)
                )
                SummaryStatColumn(
                    label = "Total Out",
                    value = formatNeondraAmount(summary.totalOut),
                    valueColor = outValueColor,
                    modifier = Modifier.weight(1f)
                )
                SummaryStatColumn(
                    label = "Families",
                    value = "${summary.totalFamiliesCount}",
                    valueColor = familyValueColor,
                    modifier = Modifier.weight(0.7f)
                )
                SummaryStatColumn(
                    label = "Reminders",
                    value = "${summary.activeRemindersCount}",
                    valueColor = reminderValueColor,
                    modifier = Modifier.weight(0.75f)
                )
            }
        }
    }
}

@Composable
private fun SummaryStatColumn(
    label: String,
    value: String,
    valueColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.82f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor,
            fontWeight = FontWeight.Bold
        )
    }
}

data class DropdownOptionItem(
    val primaryValue: String,
    val subtitle: String? = null,
    val payloadTag: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoCompleteDropdownField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    options: List<DropdownOptionItem>,
    onOptionSelected: (DropdownOptionItem) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val filteredOptions = remember(value, options) {
        val q = value.trim()
        if (q.isEmpty()) options
        else options.filter {
            it.primaryValue.contains(q, ignoreCase = true) ||
                (it.subtitle?.contains(q, ignoreCase = true) == true)
        }.ifEmpty { options }
    }

    ExposedDropdownMenuBox(
        expanded = expanded && filteredOptions.isNotEmpty(),
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(label) },
            placeholder = if (placeholder.isNotEmpty()) {
                { Text(placeholder) }
            } else null,
            supportingText = if (supportingText != null) {
                { Text(supportingText) }
            } else null,
            leadingIcon = leadingIcon,
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (value.isNotEmpty()) {
                        IconButton(onClick = { onValueChange("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = "Clear",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (options.isNotEmpty()) {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true)
                .fillMaxWidth()
        )

        if (filteredOptions.isNotEmpty()) {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                filteredOptions.take(15).forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = option.primaryValue,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (!option.subtitle.isNullOrBlank()) {
                                    Text(
                                        text = option.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
