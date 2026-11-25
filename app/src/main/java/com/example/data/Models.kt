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
import java.util.UUID

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

data class FamilyIdentityResolution(
    val familyProfile: FamilyProfile,
    val matchedExisting: Boolean
)

/**
 * Enforces 3-field family identity:
 * A family is considered the same ONLY when normalized (Name, Father's Name, Address) match.
 * Every distinct (Name, Father's Name, Address) family receives a unique Family Number (FN).
 */
object FamilyIdentityMatcher {
    fun normalizeField(value: String): String {
        return value.trim().replace(Regex("\\s+"), " ").lowercase()
    }

    fun matchesThreeFields(
        family: FamilyProfile,
        name: String,
        fatherName: String,
        address: String
    ): Boolean {
        return normalizeField(family.name) == normalizeField(name) &&
            normalizeField(family.fatherName) == normalizeField(fatherName) &&
            normalizeField(family.address) == normalizeField(address)
    }

    fun findMatchingFamily(
        families: List<FamilyProfile>,
        name: String,
        fatherName: String,
        address: String
    ): FamilyProfile? {
        if (normalizeField(name).isEmpty()) return null
        return families.firstOrNull { matchesThreeFields(it, name, fatherName, address) }
    }

    fun nextUniqueFamilyNumber(
        existingFamilies: List<FamilyProfile>,
        existingActivities: List<ActivityRecord> = emptyList()
    ): String {
        val usedNumbers = (existingFamilies.mapNotNull { it.familyId.trim().toIntOrNull() } +
            existingActivities.mapNotNull { it.familyId.trim().toIntOrNull() }).toSet()
        var candidate = (usedNumbers.maxOrNull() ?: 0) + 1
        while (candidate in usedNumbers) {
            candidate++
        }
        return candidate.toString()
    }

    fun isFnUsedByDifferentFamily(
        candidateFn: String,
        targetFamilyDocId: String?,
        existingFamilies: List<FamilyProfile>
    ): Boolean {
        val cleanFn = candidateFn.trim()
        if (cleanFn.isEmpty()) return true
        return existingFamilies.any { fam ->
            fam.id != targetFamilyDocId && fam.familyId.trim().equals(cleanFn, ignoreCase = true)
        }
    }

    fun resolveForActivity(
        name: String,
        fatherName: String,
        address: String,
        requestedFn: String,
        existingFamilies: List<FamilyProfile>,
        existingActivities: List<ActivityRecord>,
        nowMillis: Long = System.currentTimeMillis()
    ): FamilyIdentityResolution {
        val cleanName = name.trim().take(100)
        val cleanFather = fatherName.trim().take(100)
        val cleanAddress = address.trim().take(200)
        val cleanFn = requestedFn.trim().take(40)

        val matched = findMatchingFamily(existingFamilies, cleanName, cleanFather, cleanAddress)
        if (matched != null) {
            val resolvedFn = matched.familyId.trim().takeIf { it.isNotEmpty() }
                ?: if (cleanFn.isNotEmpty() && !isFnUsedByDifferentFamily(cleanFn, matched.id, existingFamilies)) {
                    cleanFn
                } else {
                    nextUniqueFamilyNumber(existingFamilies, existingActivities)
                }

            val updatedProfile = matched.copy(
                familyId = resolvedFn,
                name = cleanName,
                fatherName = cleanFather.ifEmpty { matched.fatherName },
                address = cleanAddress.ifEmpty { matched.address },
                updatedAtMillis = nowMillis
            )
            return FamilyIdentityResolution(
                familyProfile = updatedProfile,
                matchedExisting = true
            )
        }

        val uniqueFn = if (cleanFn.isNotEmpty() && !isFnUsedByDifferentFamily(cleanFn, null, existingFamilies)) {
            cleanFn
        } else {
            nextUniqueFamilyNumber(existingFamilies, existingActivities)
        }
        val newProfile = FamilyProfile(
            id = UUID.randomUUID().toString(),
            userId = "local",
            familyId = uniqueFn,
            name = cleanName,
            fatherName = cleanFather,
            address = cleanAddress,
            phone = null,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis
        )
        return FamilyIdentityResolution(
            familyProfile = newProfile,
            matchedExisting = false
        )
    }

    fun activityBelongsToFamily(
        activity: ActivityRecord,
        family: FamilyProfile
    ): Boolean {
        if (!activity.familyDocId.isNullOrBlank()) {
            return activity.familyDocId == family.id
        }
        if (matchesThreeFields(family, activity.name, activity.fatherName, activity.address)) {
            return true
        }
        return activity.familyId.isNotBlank() &&
            family.familyId.isNotBlank() &&
            activity.familyId.trim().equals(family.familyId.trim(), ignoreCase = true) &&
            normalizeField(activity.name) == normalizeField(family.name)
    }
}

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
    val category: String = "Shadi",
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
        CategoryColorOption("#B87314", "Gold"),
        CategoryColorOption("#B83B2B", "Crimson"),
        CategoryColorOption("#1976D2", "Royal Blue"),
        CategoryColorOption("#6A4C93", "Plum"),
        CategoryColorOption("#00897B", "Teal"),
        CategoryColorOption("#D81B60", "Rose"),
        CategoryColorOption("#5D4037", "Umber"),
        CategoryColorOption("#2E7D32", "Forest"),
        CategoryColorOption("#EF6C00", "Amber"),
        CategoryColorOption("#28666E", "Deep Cyan"),
        CategoryColorOption("#455A64", "Slate")
    )

    val ICON_OPTIONS = listOf(
        CategoryIconOption("celebration", "Wedding / Party", Icons.Rounded.Celebration),
        CategoryIconOption("favorite", "Joy & Gift", Icons.Rounded.Favorite),
        CategoryIconOption("groups", "Family", Icons.Rounded.Groups),
        CategoryIconOption("mosque", "Religious / Condolence", Icons.Rounded.Mosque),
        CategoryIconOption("work", "Work / Meeting", Icons.Rounded.Work),
        CategoryIconOption("payments", "Neondra / Cash", Icons.Rounded.AccountBalanceWallet),
        CategoryIconOption("restaurant", "Dinner / Walima", Icons.Rounded.Restaurant),
        CategoryIconOption("shopping", "Gifts / Shopping", Icons.Rounded.ShoppingBag),
        CategoryIconOption("home", "Home", Icons.Rounded.Home),
        CategoryIconOption("school", "Education", Icons.Rounded.School),
        CategoryIconOption("medical", "Health", Icons.Rounded.MedicalServices),
        CategoryIconOption("flight", "Travel", Icons.Rounded.Flight),
        CategoryIconOption("sparkles", "Special Occasion", Icons.Rounded.AutoAwesome),
        CategoryIconOption("event", "General Event", Icons.Rounded.Event)
    )

    val DEFAULT_SEEDS = listOf(
        DefaultCategorySeed("Aqiqah", "#00897B", "favorite"),
        DefaultCategorySeed("Birthday", "#6A4C93", "celebration"),
        DefaultCategorySeed("Shadi", "#D81B60", "celebration"),
        DefaultCategorySeed("Nikah", "#0F5C4D", "mosque")
    )

    private val LEGACY_SEEDS = listOf(
        DefaultCategorySeed("Wedding", "#D81B60", "celebration"),
        DefaultCategorySeed("Walima", "#0F5C4D", "restaurant"),
        DefaultCategorySeed("Neondra", "#0F5C4D", "payments"),
        DefaultCategorySeed("Gathering", "#B87314", "sparkles"),
        DefaultCategorySeed("Condolence", "#455A64", "mosque"),
        DefaultCategorySeed("Meeting", "#1976D2", "work"),
        DefaultCategorySeed("Personal", "#5D4037", "home"),
        DefaultCategorySeed("شادی", "#D81B60", "celebration"),
        DefaultCategorySeed("ولیمہ", "#0F5C4D", "restaurant"),
        DefaultCategorySeed("نیوندرا", "#0F5C4D", "payments"),
        DefaultCategorySeed("عقیقہ", "#00897B", "favorite"),
        DefaultCategorySeed("دعوت / تقریب", "#B87314", "sparkles"),
        DefaultCategorySeed("تعزیت / فوتگی", "#455A64", "mosque"),
        DefaultCategorySeed("سالگرہ", "#6A4C93", "celebration"),
        DefaultCategorySeed("میٹنگ", "#1976D2", "work"),
        DefaultCategorySeed("ذاتی", "#5D4037", "home")
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

    fun fallbackSeedFor(eventName: String): DefaultCategorySeed? {
        val clean = eventName.trim()
        return DEFAULT_SEEDS.firstOrNull { it.name.equals(clean, ignoreCase = true) }
            ?: LEGACY_SEEDS.firstOrNull { it.name.equals(clean, ignoreCase = true) }
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
        val seed = CategoryStyleCatalog.fallbackSeedFor(eventName)
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
