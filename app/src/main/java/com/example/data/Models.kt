package com.example.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class ActivityDirection(val wireValue: String, val displayLabel: String, val shortLabel: String) {
    IN("IN", "In (Received)", "In"),
    OUT("OUT", "Out (Given)", "Out");

    companion object {
        fun fromWire(raw: String?): ActivityDirection {
            return entries.firstOrNull { it.wireValue.equals(raw, ignoreCase = true) } ?: OUT
        }
    }
}

data class FamilyProfile(
    val id: String = "",
    val userId: String = "local",
    val familyId: String = "",
    val name: String = "",
    val fatherName: String = "",
    val address: String = "",
    val phone: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

data class ActivityCategory(
    val id: String = "",
    val userId: String = "local",
    val name: String = "",
    val colorHex: String = "#0F5C4D",
    val iconKey: String = "celebration",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    val parsedColor: Color
        get() = CategoryStyleCatalog.parseColorOrDefault(colorHex)

    val parsedIcon: ImageVector
        get() = CategoryStyleCatalog.iconForKey(iconKey)
}

data class ActivityRecord(
    val id: String = "",
    val userId: String = "local",
    val familyDocId: String? = null,
    val familyId: String = "",
    val name: String = "",
    val fatherName: String = "",
    val address: String = "",
    val eventType: String = "",
    val eventDetail: String = "",
    val eventDateMillis: Long = System.currentTimeMillis(),
    val direction: String = ActivityDirection.OUT.wireValue,
    val homeItems: String = "",
    val neondraAmount: Long = 0L,
    val reminderEnabled: Boolean = false,
    val reminderAtMillis: Long = 0L,
    val reminderMinutesBefore: Long = 0L,
    val calendarSynced: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    val parsedDirection: ActivityDirection
        get() = ActivityDirection.fromWire(direction)
}

data class EventEntry(
    val id: String = "",
    val userId: String = "local",
    val title: String = "",
    val description: String = "",
    val eventDateMillis: Long = System.currentTimeMillis(),
    val category: String = "Wedding",
    val location: String = "",
    val familyDocId: String? = null,
    val familyName: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

data class CategoryColorOption(val hex: String, val label: String)
data class CategoryIconOption(val key: String, val label: String, val icon: ImageVector)
data class DefaultCategorySeed(val name: String, val colorHex: String, val iconKey: String)

object CategoryStyleCatalog {
    val COLOR_PALETTE = listOf(
        CategoryColorOption("#0F5C4D", "Emerald"),
        CategoryColorOption("#B87314", "Amber Gold"),
        CategoryColorOption("#B83B2B", "Coral Red"),
        CategoryColorOption("#1976D2", "Royal Blue"),
        CategoryColorOption("#6A4C93", "Plum"),
        CategoryColorOption("#00897B", "Teal"),
        CategoryColorOption("#D81B60", "Rose"),
        CategoryColorOption("#5D4037", "Warm Brown"),
        CategoryColorOption("#2E7D32", "Forest Green"),
        CategoryColorOption("#EF6C00", "Tangerine"),
        CategoryColorOption("#28666E", "Deep Cyan"),
        CategoryColorOption("#455A64", "Slate")
    )

    val ICON_OPTIONS = listOf(
        CategoryIconOption("celebration", "Celebration", Icons.Rounded.Celebration),
        CategoryIconOption("favorite", "Joy & Gift", Icons.Rounded.Favorite),
        CategoryIconOption("groups", "Family", Icons.Rounded.Groups),
        CategoryIconOption("mosque", "Religious / Condolence", Icons.Rounded.Mosque),
        CategoryIconOption("work", "Work / Meeting", Icons.Rounded.Work),
        CategoryIconOption("payments", "Finance / Cash", Icons.Rounded.AccountBalanceWallet),
        CategoryIconOption("restaurant", "Dinner / Feast", Icons.Rounded.Restaurant),
        CategoryIconOption("shopping", "Shopping / Gift", Icons.Rounded.ShoppingBag),
        CategoryIconOption("home", "Home", Icons.Rounded.Home),
        CategoryIconOption("school", "Education", Icons.Rounded.School),
        CategoryIconOption("medical", "Health", Icons.Rounded.MedicalServices),
        CategoryIconOption("flight", "Travel", Icons.Rounded.Flight),
        CategoryIconOption("sparkles", "Special", Icons.Rounded.AutoAwesome),
        CategoryIconOption("event", "General Event", Icons.Rounded.Event)
    )

    val DEFAULT_SEEDS = listOf(
        DefaultCategorySeed("Wedding", "#D81B60", "celebration"),
        DefaultCategorySeed("Walima", "#0F5C4D", "restaurant"),
        DefaultCategorySeed("Aqiqah", "#00897B", "favorite"),
        DefaultCategorySeed("Gathering", "#B87314", "sparkles"),
        DefaultCategorySeed("Condolence", "#455A64", "mosque"),
        DefaultCategorySeed("Birthday", "#6A4C93", "celebration"),
        DefaultCategorySeed("Meeting", "#1976D2", "work"),
        DefaultCategorySeed("Personal", "#5D4037", "home")
    )

    fun parseColorOrDefault(hex: String?): Color {
        if (hex.isNullOrBlank()) return Color(0xFF0F5C4D)
        return try {
            Color(android.graphics.Color.parseColor(hex))
        } catch (_: Exception) {
            Color(0xFF0F5C4D)
        }
    }

    fun iconForKey(key: String?): ImageVector {
        return ICON_OPTIONS.firstOrNull { it.key == key }?.icon ?: Icons.Rounded.Event
    }
}

data class DropdownCatalog(
    val families: List<FamilyProfile> = emptyList(),
    val categories: List<ActivityCategory> = emptyList(),
    val names: List<String> = emptyList(),
    val familyIds: List<String> = emptyList(),
    val addresses: List<String> = emptyList(),
    val fatherNames: List<String> = emptyList(),
    val eventTypes: List<String> = DEFAULT_EVENT_TYPES,
    val nextSuggestedFamilyId: String = "1"
) {
    fun resolveCategoryVisual(eventName: String): Pair<Color, ImageVector> {
        val custom = categories.firstOrNull { it.name.equals(eventName.trim(), ignoreCase = true) }
        if (custom != null) {
            return custom.parsedColor to custom.parsedIcon
        }
        val seed = CategoryStyleCatalog.DEFAULT_SEEDS.firstOrNull {
            it.name.equals(eventName.trim(), ignoreCase = true)
        }
        if (seed != null) {
            return CategoryStyleCatalog.parseColorOrDefault(seed.colorHex) to
                CategoryStyleCatalog.iconForKey(seed.iconKey)
        }
        return Color(0xFF0F5C4D) to Icons.Rounded.Event
    }

    companion object {
        val DEFAULT_EVENT_TYPES = CategoryStyleCatalog.DEFAULT_SEEDS.map { it.name }
    }
}

data class LedgerSummary(
    val totalIn: Long = 0L,
    val totalOut: Long = 0L,
    val netBalance: Long = 0L,
    val totalEntries: Int = 0,
    val activeRemindersCount: Int = 0,
    val totalFamiliesCount: Int = 0,
    val totalCategoriesCount: Int = 0,
    val totalEventsCount: Int = 0
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
