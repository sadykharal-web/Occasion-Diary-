package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiAssistantRepository
import com.example.ai.AiBalanceComparisonResult
import com.example.ai.AiSearchResult
import com.example.ai.SingleFamilyInOutComparison
import com.example.data.ActivityCategory
import com.example.data.ActivityDirection
import com.example.data.ActivityRecord
import com.example.data.DropdownCatalog
import com.example.data.EventEntry
import com.example.data.FamilyProfile
import com.example.data.LedgerRepository
import com.example.data.LedgerSummary
import com.example.data.UiState
import com.example.drive.LocalBackupManager
import com.example.drive.LocalBackupStatus
import com.example.reminders.ReminderScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LedgerFilter(val label: String) {
    ALL("All"),
    IN("In (Received)"),
    OUT("Out (Given)"),
    REMINDERS("Reminders")
}

data class DateRangeFilter(
    val startDateMillis: Long? = null,
    val endDateMillis: Long? = null,
    val presetLabel: String? = null
) {
    val isActive: Boolean
        get() = startDateMillis != null || endDateMillis != null
}

data class LedgerDashboardData(
    val activities: List<ActivityRecord> = emptyList(),
    val families: List<FamilyProfile> = emptyList(),
    val categories: List<ActivityCategory> = emptyList(),
    val events: List<EventEntry> = emptyList(),
    val catalog: DropdownCatalog = DropdownCatalog(),
    val summary: LedgerSummary = LedgerSummary()
)

class LedgerViewModel(
    private val repository: LedgerRepository,
    private val aiRepository: AiAssistantRepository = AiAssistantRepository()
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(LedgerFilter.ALL)
    val selectedFilter: StateFlow<LedgerFilter> = _selectedFilter.asStateFlow()

    private val _selectedAddress = MutableStateFlow<String?>(null)
    val selectedAddress: StateFlow<String?> = _selectedAddress.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow<String?>(null)
    val selectedCategoryFilter: StateFlow<String?> = _selectedCategoryFilter.asStateFlow()

    private val _dateRangeFilter = MutableStateFlow(DateRangeFilter())
    val dateRangeFilter: StateFlow<DateRangeFilter> = _dateRangeFilter.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _localBackupStatus = MutableStateFlow(LocalBackupStatus())
    val localBackupStatus: StateFlow<LocalBackupStatus> = _localBackupStatus.asStateFlow()

    private val _aiSearchState = MutableStateFlow<UiState<AiSearchResult>?>(null)
    val aiSearchState: StateFlow<UiState<AiSearchResult>?> = _aiSearchState.asStateFlow()

    private val _aiComparisonState = MutableStateFlow<UiState<AiBalanceComparisonResult>?>(null)
    val aiComparisonState: StateFlow<UiState<AiBalanceComparisonResult>?> = _aiComparisonState.asStateFlow()

    private val _selectedCompareFamilyIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedCompareFamilyIds: StateFlow<Set<String>> = _selectedCompareFamilyIds.asStateFlow()

    private val _selectedSingleFamilyId = MutableStateFlow<String?>(null)
    val selectedSingleFamilyId: StateFlow<String?> = _selectedSingleFamilyId.asStateFlow()

    private val _singleFamilyComparisonState = MutableStateFlow<UiState<SingleFamilyInOutComparison>?>(null)
    val singleFamilyComparisonState: StateFlow<UiState<SingleFamilyInOutComparison>?> = _singleFamilyComparisonState.asStateFlow()

    val dashboardState: StateFlow<UiState<LedgerDashboardData>> = combine(
        repository.observeActivities(),
        repository.observeFamilies(),
        repository.observeCategories(),
        repository.observeEvents()
    ) { activities, families, categories, events ->
        val mergedNames = (families.map { it.name } + activities.map { it.name })
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()

        val mergedFns = (families.map { it.familyId } + activities.map { it.familyId })
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedWith(compareBy<String> { it.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it })

        val mergedAddresses = (families.map { it.address } + activities.map { it.address })
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()

        val mergedFathers = (families.map { it.fatherName } + activities.map { it.fatherName })
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()

        val mergedEvents = (
            categories.map { it.name } +
                DropdownCatalog.DEFAULT_EVENT_TYPES +
                activities.map { it.eventType } +
                events.map { it.category }
            )
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        val maxNumericFn = mergedFns.mapNotNull { it.toIntOrNull() }.maxOrNull() ?: 0

        val totalIn = activities
            .filter { it.parsedDirection == ActivityDirection.IN }
            .sumOf { it.neondraAmount }
        val totalOut = activities
            .filter { it.parsedDirection == ActivityDirection.OUT }
            .sumOf { it.neondraAmount }

        val activeRemindersCount = activities.count {
            it.reminderEnabled && it.reminderAtMillis > System.currentTimeMillis()
        }

        val catalog = DropdownCatalog(
            families = families,
            categories = categories,
            names = mergedNames,
            familyIds = mergedFns,
            addresses = mergedAddresses,
            fatherNames = mergedFathers,
            eventTypes = mergedEvents,
            nextSuggestedFamilyId = (maxNumericFn + 1).toString()
        )
        val summary = LedgerSummary(
            totalIn = totalIn,
            totalOut = totalOut,
            netBalance = totalIn - totalOut,
            totalEntries = activities.size,
            activeRemindersCount = activeRemindersCount,
            totalFamiliesCount = families.size,
            totalCategoriesCount = categories.size,
            totalEventsCount = events.size
        )

        UiState.Success(
            LedgerDashboardData(
                activities = activities,
                families = families,
                categories = categories,
                events = events,
                catalog = catalog,
                summary = summary
            )
        ) as UiState<LedgerDashboardData>
    }
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Error observing offline ledger dashboard", error)
            emit(UiState.Error(error.localizedMessage ?: "Failed to load offline diary data."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    fun initBackupPrefs(context: Context) {
        _localBackupStatus.value = _localBackupStatus.value.copy(
            lastBackupMillis = LocalBackupManager.getLastBackupMillis(context)
        )
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: LedgerFilter) {
        _selectedFilter.value = filter
    }

    fun setAddressFilter(address: String?) {
        _selectedAddress.value = address
    }

    fun setCategoryFilter(categoryName: String?) {
        _selectedCategoryFilter.value = categoryName
    }

    fun setDateRangeFilter(startMillis: Long?, endMillis: Long?, presetLabel: String? = null) {
        _dateRangeFilter.value = DateRangeFilter(
            startDateMillis = startMillis,
            endDateMillis = endMillis,
            presetLabel = presetLabel
        )
    }

    fun clearDateRangeFilter() {
        _dateRangeFilter.value = DateRangeFilter()
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    // --- EventEntry Operations ---

    fun saveEvent(
        @Suppress("UNUSED_PARAMETER") context: Context,
        entry: EventEntry,
        onSaved: (EventEntry) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.saveEvent(entry)
            _isSaving.value = false
            result.onSuccess { saved ->
                _actionMessage.value = if (entry.id.isBlank()) {
                    "Occasion \"${saved.title}\" saved offline on device."
                } else {
                    "Occasion \"${saved.title}\" updated offline."
                }
                onSaved(saved)
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Failed to save occasion."
            }
        }
    }

    fun deleteEvent(@Suppress("UNUSED_PARAMETER") context: Context, eventId: String) {
        viewModelScope.launch {
            repository.deleteEvent(eventId)
                .onSuccess {
                    _actionMessage.value = "Occasion deleted."
                }
                .onFailure { err ->
                    _actionMessage.value = err.localizedMessage ?: "Failed to delete occasion."
                }
        }
    }

    // --- Online AI Search (over Offline Room Data) & Offline Quick Filter ---

    fun runAiSearch(query: String, useOnlineAi: Boolean = true) {
        val data = (dashboardState.value as? UiState.Success)?.data
        if (data == null) {
            _aiSearchState.value = UiState.Error("Your offline records are still loading. Please try again in a moment.")
            return
        }
        viewModelScope.launch {
            _aiSearchState.value = UiState.Loading
            val result = if (useOnlineAi) {
                aiRepository.searchFamiliesAndEventsOnline(
                    query = query,
                    families = data.families,
                    activities = data.activities,
                    events = data.events
                )
            } else {
                aiRepository.searchFamiliesAndEvents(
                    query = query,
                    families = data.families,
                    activities = data.activities,
                    events = data.events
                )
            }
            _aiSearchState.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.localizedMessage ?: "Search failed.") }
            )
        }
    }

    fun clearAiSearch() {
        _aiSearchState.value = null
    }

    // --- Single-Family In vs Out Entries Comparison ---

    fun selectSingleFamilyForCompare(familyDocId: String) {
        _selectedSingleFamilyId.value = familyDocId
        val data = (dashboardState.value as? UiState.Success)?.data ?: return
        val family = data.families.firstOrNull { it.id == familyDocId } ?: return
        val localResult = aiRepository.compareWithinSingleFamilyLocal(
            family = family,
            activities = data.activities,
            events = data.events
        )
        _singleFamilyComparisonState.value = UiState.Success(localResult)
    }

    fun runSingleFamilyOnlineAiAnalysis() {
        val data = (dashboardState.value as? UiState.Success)?.data
        if (data == null) {
            _singleFamilyComparisonState.value = UiState.Error("Your offline records are still loading. Please try again.")
            return
        }
        val targetId = _selectedSingleFamilyId.value ?: data.families.firstOrNull()?.id
        val family = data.families.firstOrNull { it.id == targetId }
        if (family == null) {
            _singleFamilyComparisonState.value = UiState.Error("Please select a family to compare its In and Out entries.")
            return
        }
        _selectedSingleFamilyId.value = family.id
        viewModelScope.launch {
            _singleFamilyComparisonState.value = UiState.Loading
            val result = aiRepository.analyzeWithinSingleFamilyOnline(
                family = family,
                activities = data.activities,
                events = data.events
            )
            _singleFamilyComparisonState.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.localizedMessage ?: "Online AI analysis failed.") }
            )
        }
    }

    fun clearSingleFamilyComparison() {
        _singleFamilyComparisonState.value = null
    }

    fun toggleFamilyForComparison(familyDocId: String) {
        val current = _selectedCompareFamilyIds.value
        _selectedCompareFamilyIds.value = if (familyDocId in current) {
            current - familyDocId
        } else {
            current + familyDocId
        }
    }

    fun selectTopFamiliesForComparison(limit: Int = 3) {
        val data = (dashboardState.value as? UiState.Success)?.data ?: return
        val topIds = data.families
            .sortedByDescending { fam ->
                data.activities.count {
                    it.familyDocId == fam.id ||
                        (it.familyId.isNotBlank() && it.familyId == fam.familyId && it.name.equals(fam.name, ignoreCase = true))
                }
            }
            .take(limit)
            .map { it.id }
            .toSet()
        _selectedCompareFamilyIds.value = topIds
    }

    fun runAiBalanceComparison(familyIdsOverride: Set<String>? = null) {
        val data = (dashboardState.value as? UiState.Success)?.data
        if (data == null) {
            _aiComparisonState.value = UiState.Error("Your records are still loading. Please try again.")
            return
        }
        val targetIds = familyIdsOverride ?: _selectedCompareFamilyIds.value
        if (familyIdsOverride != null) {
            _selectedCompareFamilyIds.value = familyIdsOverride
        }
        val selected = data.families.filter { it.id in targetIds }
        if (selected.size < 2) {
            _aiComparisonState.value = UiState.Error("Please select at least 2 families to compare their In and Out balances.")
            return
        }
        viewModelScope.launch {
            _aiComparisonState.value = UiState.Loading
            val result = aiRepository.compareFamilyBalances(
                selectedFamilies = selected,
                activities = data.activities,
                events = data.events
            )
            _aiComparisonState.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.localizedMessage ?: "Balance comparison failed.") }
            )
        }
    }

    fun clearAiComparison() {
        _aiComparisonState.value = null
    }

    // --- Offline Local File Backup & Restore ---

    fun exportOfflineBackupToUri(context: Context, uri: Uri) {
        val currentData = (dashboardState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _localBackupStatus.value = _localBackupStatus.value.copy(isBusy = true, statusMessage = null)
            val result = LocalBackupManager.exportBackupToUri(
                context = context,
                uri = uri,
                categories = currentData.categories,
                families = currentData.families,
                activities = currentData.activities,
                events = currentData.events
            )
            result.onSuccess { exportedAt ->
                _localBackupStatus.value = _localBackupStatus.value.copy(
                    isBusy = false,
                    lastBackupMillis = exportedAt,
                    lastFileName = LocalBackupManager.DEFAULT_BACKUP_FILE_NAME,
                    statusMessage = "Offline backup file exported to phone storage"
                )
                _actionMessage.value = "Backup saved successfully to your phone."
            }.onFailure { err ->
                _localBackupStatus.value = _localBackupStatus.value.copy(
                    isBusy = false,
                    statusMessage = err.localizedMessage ?: "Backup export failed"
                )
                _actionMessage.value = err.localizedMessage ?: "Could not save backup file."
            }
        }
    }

    fun importOfflineBackupFromUri(context: Context, uri: Uri) {
        val currentData = (dashboardState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _localBackupStatus.value = _localBackupStatus.value.copy(isBusy = true, statusMessage = null)
            val readRes = LocalBackupManager.importBackupFromUri(context, uri)
            readRes.onSuccess { payload ->
                val restoreRes = repository.restoreFromLocalBackup(
                    payload = payload,
                    existingCategories = currentData.categories,
                    existingFamilies = currentData.families,
                    existingActivities = currentData.activities,
                    existingEvents = currentData.events
                )
                restoreRes.onSuccess { count ->
                    _localBackupStatus.value = _localBackupStatus.value.copy(
                        isBusy = false,
                        statusMessage = "Restored $count offline records"
                    )
                    _actionMessage.value = if (count > 0) {
                        "Successfully imported $count records to your offline database."
                    } else {
                        "All records in that backup are already saved on this device."
                    }
                }.onFailure { err ->
                    _localBackupStatus.value = _localBackupStatus.value.copy(isBusy = false)
                    _actionMessage.value = err.localizedMessage ?: "Failed to import backup records."
                }
            }.onFailure { err ->
                _localBackupStatus.value = _localBackupStatus.value.copy(isBusy = false)
                _actionMessage.value = err.localizedMessage ?: "Invalid backup JSON file."
            }
        }
    }

    // --- Categories, Activities & Families ---

    fun seedDefaultCategoriesIfEmpty() {
        val currentCats = (dashboardState.value as? UiState.Success)?.data?.categories ?: return
        if (currentCats.isNotEmpty()) return
        viewModelScope.launch {
            repository.seedDefaultCategoriesIfEmpty(currentCats)
        }
    }

    fun saveCategory(
        @Suppress("UNUSED_PARAMETER") context: Context,
        category: ActivityCategory,
        previousName: String?,
        onSaved: (ActivityCategory) -> Unit = {}
    ) {
        val currentActivities = (dashboardState.value as? UiState.Success)?.data?.activities ?: emptyList()
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.saveCategoryAndSyncActivities(
                category = category,
                previousName = previousName,
                existingActivities = currentActivities
            )
            _isSaving.value = false
            result.onSuccess { saved ->
                _actionMessage.value = "Category saved offline."
                onSaved(saved)
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Failed to save category."
            }
        }
    }

    fun deleteCategory(@Suppress("UNUSED_PARAMETER") context: Context, categoryId: String) {
        viewModelScope.launch {
            repository.deleteCategory(categoryId)
                .onSuccess {
                    _actionMessage.value = "Category deleted."
                }
                .onFailure { err ->
                    _actionMessage.value = err.localizedMessage ?: "Failed to delete category."
                }
        }
    }

    fun saveActivity(
        context: Context,
        record: ActivityRecord,
        onSaved: (ActivityRecord) -> Unit = {}
    ) {
        val currentData = (dashboardState.value as? UiState.Success)?.data
        val existingFamilies = currentData?.families ?: emptyList()
        val existingActivities = currentData?.activities ?: emptyList()

        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.saveActivityAndSyncFamily(
                record = record,
                existingFamilies = existingFamilies,
                existingActivities = existingActivities
            )
            _isSaving.value = false
            result.onSuccess { saved ->
                if (saved.reminderEnabled) {
                    ReminderScheduler.scheduleReminder(context.applicationContext, saved)
                } else {
                    ReminderScheduler.cancelReminder(context.applicationContext, saved.id)
                }
                _actionMessage.value = "Entry saved offline to your mobile database."
                onSaved(saved)
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Failed to save entry."
            }
        }
    }

    fun saveFamily(
        @Suppress("UNUSED_PARAMETER") context: Context,
        profile: FamilyProfile,
        onSaved: () -> Unit = {}
    ) {
        val currentActivities = (dashboardState.value as? UiState.Success)?.data?.activities ?: emptyList()
        val linked = currentActivities.filter { act ->
            (profile.id.isNotBlank() && act.familyDocId == profile.id) ||
                (act.familyId.isNotBlank() && act.familyId == profile.familyId &&
                    act.name.equals(profile.name, ignoreCase = true))
        }

        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.saveFamilyAndSyncActivities(profile, linked)
            _isSaving.value = false
            result.onSuccess {
                _actionMessage.value = "Family profile and linked entries updated offline."
                onSaved()
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Failed to save family profile."
            }
        }
    }

    fun toggleReminder(
        context: Context,
        record: ActivityRecord,
        enabled: Boolean,
        minutesBefore: Long = record.reminderMinutesBefore.takeIf { it > 0 } ?: 1440L
    ) {
        val computedReminderAt = if (enabled) {
            val offsetMillis = minutesBefore * 60_000L
            (record.eventDateMillis - offsetMillis).coerceAtLeast(System.currentTimeMillis() + 60_000L)
        } else {
            0L
        }
        viewModelScope.launch {
            val result = repository.toggleReminder(
                activityId = record.id,
                enabled = enabled,
                reminderAtMillis = computedReminderAt,
                minutesBefore = if (enabled) minutesBefore else 0L
            )
            result.onSuccess {
                val updated = record.copy(
                    reminderEnabled = enabled,
                    reminderAtMillis = computedReminderAt,
                    reminderMinutesBefore = if (enabled) minutesBefore else 0L
                )
                if (enabled) {
                    ReminderScheduler.scheduleReminder(context.applicationContext, updated)
                    _actionMessage.value = "Reminder enabled."
                } else {
                    ReminderScheduler.cancelReminder(context.applicationContext, record.id)
                    _actionMessage.value = "Reminder turned off."
                }
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Failed to update reminder."
            }
        }
    }

    fun markCalendarSynced(@Suppress("UNUSED_PARAMETER") context: Context, recordId: String) {
        viewModelScope.launch {
            repository.markCalendarSynced(recordId, true)
                .onSuccess {
                    _actionMessage.value = "Marked entry as synced to device calendar."
                }
        }
    }

    fun deleteActivity(context: Context, activityId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.deleteActivity(activityId)
            _isSaving.value = false
            result.onSuccess {
                ReminderScheduler.cancelReminder(context.applicationContext, activityId)
                _actionMessage.value = "Entry deleted."
                onDeleted()
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Failed to delete entry."
            }
        }
    }

    fun deleteFamily(@Suppress("UNUSED_PARAMETER") context: Context, familyDocId: String) {
        viewModelScope.launch {
            repository.deleteFamily(familyDocId)
                .onSuccess {
                    _actionMessage.value = "Family profile deleted."
                }
                .onFailure { err ->
                    _actionMessage.value = err.localizedMessage ?: "Failed to delete family profile."
                }
        }
    }

    private companion object {
        const val TAG = "LedgerViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
