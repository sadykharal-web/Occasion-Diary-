package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ActivityDirection(val wireValue: String, val urduLabel: String, val shortLabel: String) {
    IN("IN", "آمد / وصولی (In)", "In"),
    OUT("OUT", "ادائیگی / خرچ (Out)", "Out");

    companion object {
        fun fromWire(raw: String?): ActivityDirection {
            return entries.firstOrNull { it.wireValue.equals(raw, ignoreCase = true) } ?: OUT
        }
    }
}

@Keep
@IgnoreExtraProperties
data class FamilyProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val familyId: String = "",
    val name: String = "",
    val fatherName: String = "",
    val address: String = "",
    val phone: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ActivityRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
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
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedDirection: ActivityDirection
        get() = ActivityDirection.fromWire(direction)
}

data class DropdownCatalog(
    val families: List<FamilyProfile> = emptyList(),
    val names: List<String> = emptyList(),
    val familyIds: List<String> = emptyList(),
    val addresses: List<String> = emptyList(),
    val fatherNames: List<String> = emptyList(),
    val eventTypes: List<String> = DEFAULT_EVENT_TYPES,
    val nextSuggestedFamilyId: String = "1"
) {
    companion object {
        val DEFAULT_EVENT_TYPES = listOf(
            "شادی",
            "ولیمہ",
            "عقیقہ",
            "ختنہ",
            "فوتگی / تعزیت",
            "سالگرہ",
            "میٹنگ",
            "ذاتی کام"
        )
    }
}

data class LedgerSummary(
    val totalIn: Long = 0L,
    val totalOut: Long = 0L,
    val netBalance: Long = 0L,
    val totalEntries: Int = 0,
    val activeRemindersCount: Int = 0,
    val totalFamiliesCount: Int = 0
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
