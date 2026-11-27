package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiAssistantRepository
import com.example.ai.AiBalanceComparisonResult
import com.example.ai.AiFollowUpChatMessage
import com.example.ai.AiSearchResult
import com.example.ai.SingleFamilyInOutComparison
import com.example.data.ActivityCategory
import com.example.data.ActivityDirection
import com.example.data.ActivityRecord
import com.example.data.DropdownCatalog
import com.example.data.EventEntry
import com.example.data.FamilyIdentityMatcher
import com.example.data.FamilyProfile
import com.example.data.LedgerRepository
import com.example.data.LedgerSummary
import com.example.data.UiState
import com.example.drive.LocalBackupManager
import com.example.drive.LocalBackupStatus
import com.example.reminders.ReminderScheduler
import com.example.ui.theme.ThemePreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

enum class LedgerSortOrder(val label: String, val shortLabel: String) {
    FN("Sort by: FN", "FN"),
    NAME_AZ("Sort by: Name (A-Z)", "Name (A-Z)"),
    EVENT_NAME("Sort by: Event Name", "Event Name"),
    EVENT_TYPE("Sort by: Event Type", "Event Type")
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

    private val _selectedEventTypes = MutableStateFlow<Set<String>>(emptySet())
    val selectedEventTypes: StateFlow<Set<String>> = _selectedEventTypes.asStateFlow()

    private val _selectedSortOrder = MutableStateFlow(LedgerSortOrder.FN)
    val selectedSortOrder: StateFlow<LedgerSortOrder> = _selectedSortOrder.asStateFlow()

    private val _dateRangeFilter = MutableStateFlow(DateRangeFilter())
    val dateRangeFilter: StateFlow<DateRangeFilter> = _dateRangeFilter.asStateFlow()

    private val _isDarkMode = MutableStateFlow<Boolean?>(null)
    val isDarkMode: StateFlow<Boolean?> = _isDarkMode.asStateFlow()

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

    private val _multiFamilyAiLoading = MutableStateFlow(false)
    val multiFamilyAiLoading: StateFlow<Boolean> = _multiFamilyAiLoading.asStateFlow()

    private val _multiFamilyAiError = MutableStateFlow<String?>(null)
    val multiFamilyAiError: StateFlow<String?> = _multiFamilyAiError.asStateFlow()

    private val _selectedCompareFamilyIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedCompareFamilyIds: StateFlow<Set<String>> = _selectedCompareFamilyIds.asStateFlow()

    private val _selectedSingleFamilyId = MutableStateFlow<String?>(null)
    val selectedSingleFamilyId: StateFlow<String?> = _selectedSingleFamilyId.asStateFlow()

    private val _singleFamilyComparisonState = MutableStateFlow<UiState<SingleFamilyInOutComparison>?>(null)
    val singleFamilyComparisonState: StateFlow<UiState<SingleFamilyInOutComparison>?> = _singleFamilyComparisonState.asStateFlow()

    private val _singleFamilyAiLoading = MutableStateFlow(false)
    val singleFamilyAiLoading: StateFlow<Boolean> = _singleFamilyAiLoading.asStateFlow()

    private val _singleFamilyAiError = MutableStateFlow<String?>(null)
    val singleFamilyAiError: StateFlow<String?> = _singleFamilyAiError.asStateFlow()

    private val _singleFamilyChatMessages = MutableStateFlow<List<AiFollowUpChatMessage>>(emptyList())
    val singleFamilyChatMessages: StateFlow<List<AiFollowUpChatMessage>> = _singleFamilyChatMessages.asStateFlow()

    private val _singleFamilyChatLoading = MutableStateFlow(false)
    val singleFamilyChatLoading: StateFlow<Boolean> = _singleFamilyChatLoading.asStateFlow()

    private val _singleFamilyChatError = MutableStateFlow<String?>(null)
    val singleFamilyChatError: StateFlow<String?> = _singleFamilyChatError.asStateFlow()

    private val _multiFamilyChatMessages = MutableStateFlow<List<AiFollowUpChatMessage>>(emptyList())
    val multiFamilyChatMessages: StateFlow<List<AiFollowUpChatMessage>> = _multiFamilyChatMessages.asStateFlow()

    private val _multiFamilyChatLoading = MutableStateFlow(false)
    val multiFamilyChatLoading: StateFlow<Boolean> = _multiFamilyChatLoading.asStateFlow()

    private val _multiFamilyChatError = MutableStateFlow<String?>(null)
    val multiFamilyChatError: StateFlow<String?> = _multiFamilyChatError.asStateFlow()

    private var aiSearchJob: Job? = null
    private var singleFamilyAiJob: Job? = null
    private var multiFamilyAiJob: Job? = null
    private var singleFamilyChatJob: Job? = null
    private var multiFamilyChatJob: Job? = null

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
            DropdownCatalog.DEFAULT_EVENT_TYPES +
                categories.map { it.name }
            )
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        val nextFn = FamilyIdentityMatcher.nextUniqueFamilyNumber(families, activities)

        val totalIn = activities
            .filter { it.parsedDirection == ActivityDirection.IN }
            .sumOf { it.totalAmount }
        val totalOut = activities
            .filter { it.parsedDirection == ActivityDirection.OUT }
            .sumOf { it.totalAmount }

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
            nextSuggestedFamilyId = nextFn
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
            emit(UiState.Error(error.localizedMessage ?: "Failed to load offline ledger data."))
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

    fun initThemePrefs(context: Context, systemDark: Boolean) {
        if (_isDarkMode.value == null) {
            _isDarkMode.value = ThemePreferences.isDarkMode(context, systemDark)
        }
    }

    fun setDarkMode(context: Context, enabled: Boolean) {
        ThemePreferences.setDarkMode(context, enabled)
        _isDarkMode.value = enabled
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

    fun toggleEventTypeFilter(eventType: String) {
        val clean = eventType.trim()
        if (clean.isEmpty()) return
        val current = _selectedEventTypes.value
        _selectedEventTypes.value = if (clean in current) {
            current - clean
        } else {
            current + clean
        }
    }

    fun clearEventTypeFilters() {
        _selectedEventTypes.value = emptySet()
    }

    fun setSortOrder(order: LedgerSortOrder) {
        _selectedSortOrder.value = order
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

    fun clearAllLedgerFilters() {
        _searchQuery.value = ""
        _selectedFilter.value = LedgerFilter.ALL
        _selectedAddress.value = null
        _selectedEventTypes.value = emptySet()
        _dateRangeFilter.value = DateRangeFilter()
        _selectedSortOrder.value = LedgerSortOrder.FN
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
                    "Event \"${saved.title}\" saved offline on your device."
                } else {
                    "Event \"${saved.title}\" updated offline."
                }
                onSaved(saved)
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Failed to save event."
            }
        }
    }

    fun deleteEvent(@Suppress("UNUSED_PARAMETER") context: Context, eventId: String) {
        viewModelScope.launch {
            repository.deleteEvent(eventId)
                .onSuccess {
                    _actionMessage.value = "Event deleted."
                }
                .onFailure { err ->
                    _actionMessage.value = err.localizedMessage ?: "Failed to delete event."
                }
        }
    }

    fun renameEventGroup(
        activityIds: List<String>,
        eventIds: List<String>,
        newTitle: String
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.renameEventGroup(
                activityIds = activityIds,
                eventIds = eventIds,
                newTitle = newTitle
            )
            _isSaving.value = false
            result.onSuccess {
                _actionMessage.value = "Event updated to \"${newTitle.trim()}\"."
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Failed to rename event."
            }
        }
    }

    fun deleteEventGroup(
        context: Context,
        activityIds: List<String>,
        eventIds: List<String>
    ) {
        viewModelScope.launch {
            repository.deleteEventGroup(activityIds = activityIds, eventIds = eventIds)
                .onSuccess {
                    activityIds.forEach { actId ->
                        ReminderScheduler.cancelReminder(context.applicationContext, actId)
                    }
                    _actionMessage.value = "Event deleted."
                }
                .onFailure { err ->
                    _actionMessage.value = err.localizedMessage ?: "Failed to delete event."
                }
        }
    }

    // --- Offline Search (over Offline Room Data) ---

    fun runAiSearch(query: String, @Suppress("UNUSED_PARAMETER") useOnlineAi: Boolean = false) {
        val data = (dashboardState.value as? UiState.Success)?.data
        if (data == null) {
            _aiSearchState.value = UiState.Error("Your offline ledger data is still loading. Please try again.")
            return
        }
        aiSearchJob?.cancel()
        aiSearchJob = viewModelScope.launch {
            _aiSearchState.value = UiState.Loading
            val result = aiRepository.searchFamiliesAndEvents(
                query = query,
                families = data.families,
                activities = data.activities,
                events = data.events
            )
            _aiSearchState.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.localizedMessage ?: "Search failed.") }
            )
        }
    }

    fun clearAiSearch() {
        aiSearchJob?.cancel()
        _aiSearchState.value = null
    }

    // --- Single-Family In vs Out Entries Comparison ---

    fun selectSingleFamilyForCompare(familyDocId: String) {
        val switchedFamily = _selectedSingleFamilyId.value != familyDocId
        _selectedSingleFamilyId.value = familyDocId
        if (switchedFamily) {
            singleFamilyAiJob?.cancel()
            singleFamilyChatJob?.cancel()
            _singleFamilyAiError.value = null
            _singleFamilyAiLoading.value = false
            _singleFamilyChatMessages.value = emptyList()
            _singleFamilyChatError.value = null
            _singleFamilyChatLoading.value = false
        }
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
            _singleFamilyAiError.value = "Your offline ledger data is still loading. Please try again."
            return
        }
        val targetId = _selectedSingleFamilyId.value ?: data.families.firstOrNull()?.id
        val family = data.families.firstOrNull { it.id == targetId }
        if (family == null) {
            _singleFamilyAiError.value = "Please select a family to compare In and Out entries."
            return
        }
        _selectedSingleFamilyId.value = family.id
        if (_singleFamilyComparisonState.value !is UiState.Success) {
            _singleFamilyComparisonState.value = UiState.Success(
                aiRepository.compareWithinSingleFamilyLocal(
                    family = family,
                    activities = data.activities,
                    events = data.events
                )
            )
        }
        singleFamilyAiJob?.cancel()
        singleFamilyAiJob = viewModelScope.launch {
            _singleFamilyAiLoading.value = true
            _singleFamilyAiError.value = null
            try {
                val result = aiRepository.analyzeWithinSingleFamilyOnline(
                    family = family,
                    activities = data.activities,
                    events = data.events
                )
                if (_selectedSingleFamilyId.value == family.id) {
                    result.onSuccess { aiComparison ->
                        _singleFamilyComparisonState.value = UiState.Success(aiComparison)
                    }.onFailure { err ->
                        _singleFamilyAiError.value = err.localizedMessage ?: "Online AI comparison failed."
                    }
                }
            } finally {
                _singleFamilyAiLoading.value = false
            }
        }
    }

    fun clearSingleFamilyComparison() {
        singleFamilyAiJob?.cancel()
        singleFamilyChatJob?.cancel()
        _singleFamilyComparisonState.value = null
        _singleFamilyAiLoading.value = false
        _singleFamilyAiError.value = null
        _singleFamilyChatMessages.value = emptyList()
        _singleFamilyChatLoading.value = false
        _singleFamilyChatError.value = null
    }

    fun sendSingleFamilyChatQuestion(question: String, useOnlineAi: Boolean = true) {
        val cleanQuestion = question.trim()
        if (cleanQuestion.isEmpty()) return
        val comparison = (_singleFamilyComparisonState.value as? UiState.Success)?.data ?: return

        val previousHistory = _singleFamilyChatMessages.value
        val alreadyRecordedUser = previousHistory.lastOrNull()?.let {
            it.isUser && it.text == cleanQuestion
        } == true

        val updatedWithUser = if (alreadyRecordedUser) {
            previousHistory
        } else {
            previousHistory + AiFollowUpChatMessage(isUser = true, text = cleanQuestion, isOnlineAi = useOnlineAi)
        }
        _singleFamilyChatMessages.value = updatedWithUser
        _singleFamilyChatError.value = null

        if (!useOnlineAi) {
            val offlineReply = aiRepository.answerSingleFamilyFollowUpOffline(cleanQuestion, comparison)
            _singleFamilyChatMessages.value = updatedWithUser + AiFollowUpChatMessage(
                isUser = false,
                text = offlineReply,
                isOnlineAi = false
            )
            return
        }

        val contextSnapshot = aiRepository.buildSingleFamilyChatContext(comparison)
        singleFamilyChatJob?.cancel()
        singleFamilyChatJob = viewModelScope.launch {
            _singleFamilyChatLoading.value = true
            try {
                val result = aiRepository.askComparisonFollowUpOnline(
                    question = cleanQuestion,
                    comparisonContext = contextSnapshot,
                    history = previousHistory
                )
                result.onSuccess { reply ->
                    _singleFamilyChatMessages.value = _singleFamilyChatMessages.value + AiFollowUpChatMessage(
                        isUser = false,
                        text = reply,
                        isOnlineAi = true
                    )
                }.onFailure { err ->
                    _singleFamilyChatError.value = err.localizedMessage ?: "Follow-up AI chat failed."
                }
            } finally {
                _singleFamilyChatLoading.value = false
            }
        }
    }

    fun clearSingleFamilyChat() {
        singleFamilyChatJob?.cancel()
        _singleFamilyChatMessages.value = emptyList()
        _singleFamilyChatLoading.value = false
        _singleFamilyChatError.value = null
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
                data.activities.count { FamilyIdentityMatcher.activityBelongsToFamily(it, fam) }
            }
            .take(limit)
            .map { it.id }
            .toSet()
        _selectedCompareFamilyIds.value = topIds
    }

    fun runAiBalanceComparison(familyIdsOverride: Set<String>? = null) {
        val data = (dashboardState.value as? UiState.Success)?.data
        if (data == null) {
            _aiComparisonState.value = UiState.Error("Your offline ledger data is still loading. Please try again.")
            return
        }
        val targetIds = familyIdsOverride ?: _selectedCompareFamilyIds.value
        if (familyIdsOverride != null) {
            _selectedCompareFamilyIds.value = familyIdsOverride
        }
        val selected = data.families.filter { it.id in targetIds }
        if (selected.size < 2) {
            _aiComparisonState.value = UiState.Error("Please select at least 2 families to compare In and Out balances.")
            return
        }
        _multiFamilyAiError.value = null
        _multiFamilyAiLoading.value = false
        _multiFamilyChatMessages.value = emptyList()
        _multiFamilyChatError.value = null
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

    fun runMultiFamilyOnlineAiAnalysis() {
        val data = (dashboardState.value as? UiState.Success)?.data
        if (data == null) {
            _multiFamilyAiError.value = "Your offline ledger data is still loading. Please try again."
            return
        }
        val selected = data.families.filter { it.id in _selectedCompareFamilyIds.value }
        if (selected.size < 2) {
            _multiFamilyAiError.value = "Please select at least 2 families to compare In and Out balances."
            return
        }
        if (_aiComparisonState.value !is UiState.Success) {
            val local = aiRepository.compareFamilyBalances(selected, data.activities, data.events)
            local.onSuccess { _aiComparisonState.value = UiState.Success(it) }
        }
        multiFamilyAiJob?.cancel()
        multiFamilyAiJob = viewModelScope.launch {
            _multiFamilyAiLoading.value = true
            _multiFamilyAiError.value = null
            try {
                val result = aiRepository.analyzeMultiFamilyOnline(
                    selectedFamilies = selected,
                    activities = data.activities,
                    events = data.events
                )
                result.onSuccess { aiComp ->
                    _aiComparisonState.value = UiState.Success(aiComp)
                }.onFailure { err ->
                    _multiFamilyAiError.value = err.localizedMessage ?: "Online AI comparison failed."
                }
            } finally {
                _multiFamilyAiLoading.value = false
            }
        }
    }

    fun clearAiComparison() {
        multiFamilyAiJob?.cancel()
        multiFamilyChatJob?.cancel()
        _aiComparisonState.value = null
        _multiFamilyAiLoading.value = false
        _multiFamilyAiError.value = null
        _multiFamilyChatMessages.value = emptyList()
        _multiFamilyChatLoading.value = false
        _multiFamilyChatError.value = null
    }

    fun sendMultiFamilyChatQuestion(question: String, useOnlineAi: Boolean = true) {
        val cleanQuestion = question.trim()
        if (cleanQuestion.isEmpty()) return
        val comparison = (_aiComparisonState.value as? UiState.Success)?.data ?: return
        val activities = (dashboardState.value as? UiState.Success)?.data?.activities ?: emptyList()

        val previousHistory = _multiFamilyChatMessages.value
        val alreadyRecordedUser = previousHistory.lastOrNull()?.let {
            it.isUser && it.text == cleanQuestion
        } == true

        val updatedWithUser = if (alreadyRecordedUser) {
            previousHistory
        } else {
            previousHistory + AiFollowUpChatMessage(isUser = true, text = cleanQuestion, isOnlineAi = useOnlineAi)
        }
        _multiFamilyChatMessages.value = updatedWithUser
        _multiFamilyChatError.value = null

        if (!useOnlineAi) {
            val offlineReply = aiRepository.answerMultiFamilyFollowUpOffline(cleanQuestion, comparison)
            _multiFamilyChatMessages.value = updatedWithUser + AiFollowUpChatMessage(
                isUser = false,
                text = offlineReply,
                isOnlineAi = false
            )
            return
        }

        val contextSnapshot = aiRepository.buildMultiFamilyChatContext(comparison, activities)
        multiFamilyChatJob?.cancel()
        multiFamilyChatJob = viewModelScope.launch {
            _multiFamilyChatLoading.value = true
            try {
                val result = aiRepository.askComparisonFollowUpOnline(
                    question = cleanQuestion,
                    comparisonContext = contextSnapshot,
                    history = previousHistory
                )
                result.onSuccess { reply ->
                    _multiFamilyChatMessages.value = _multiFamilyChatMessages.value + AiFollowUpChatMessage(
                        isUser = false,
                        text = reply,
                        isOnlineAi = true
                    )
                }.onFailure { err ->
                    _multiFamilyChatError.value = err.localizedMessage ?: "Follow-up AI chat failed."
                }
            } finally {
                _multiFamilyChatLoading.value = false
            }
        }
    }

    fun clearMultiFamilyChat() {
        multiFamilyChatJob?.cancel()
        _multiFamilyChatMessages.value = emptyList()
        _multiFamilyChatLoading.value = false
        _multiFamilyChatError.value = null
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
                    statusMessage = "Excel backup (.xlsx) saved to device"
                )
                _actionMessage.value = "Excel backup saved successfully to your device."
            }.onFailure { err ->
                _localBackupStatus.value = _localBackupStatus.value.copy(
                    isBusy = false,
                    statusMessage = err.localizedMessage ?: "Failed to export Excel backup"
                )
                _actionMessage.value = err.localizedMessage ?: "Failed to save Excel backup file."
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
                        statusMessage = "$count offline records restored"
                    )
                    _actionMessage.value = if (count > 0) {
                        "$count records imported into your offline database."
                    } else {
                        "All records from this backup are already stored on your device."
                    }
                }.onFailure { err ->
                    _localBackupStatus.value = _localBackupStatus.value.copy(isBusy = false)
                    _actionMessage.value = err.localizedMessage ?: "Failed to restore backup records."
                }
            }.onFailure { err ->
                _localBackupStatus.value = _localBackupStatus.value.copy(isBusy = false)
                _actionMessage.value = err.localizedMessage ?: "Invalid backup file."
            }
        }
    }

    // --- Categories, Activities, Quick Neondra & Families ---

    fun seedDefaultCategoriesIfEmpty() {
        val currentCats = (dashboardState.value as? UiState.Success)?.data?.categories ?: return
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
                _actionMessage.value = "Event type saved offline."
                onSaved(saved)
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Failed to save event type."
            }
        }
    }

    fun deleteCategory(@Suppress("UNUSED_PARAMETER") context: Context, categoryId: String) {
        viewModelScope.launch {
            repository.deleteCategory(categoryId)
                .onSuccess {
                    _actionMessage.value = "Event type deleted."
                }
                .onFailure { err ->
                    _actionMessage.value = err.localizedMessage ?: "Failed to delete event type."
                }
        }
    }

    fun saveQuickNeondra(
        @Suppress("UNUSED_PARAMETER") context: Context,
        name: String,
        fatherName: String,
        address: String,
        eventType: String,
        eventDetail: String,
        amount: Long,
        direction: ActivityDirection,
        eventDateMillis: Long = System.currentTimeMillis(),
        onSaved: (ActivityRecord) -> Unit = {}
    ) {
        val currentData = (dashboardState.value as? UiState.Success)?.data
        val existingFamilies = currentData?.families ?: emptyList()
        val existingActivities = currentData?.activities ?: emptyList()
        val matchedBeforeSave = FamilyIdentityMatcher.findMatchingFamily(
            families = existingFamilies,
            name = name,
            fatherName = fatherName,
            address = address
        )

        val record = ActivityRecord(
            name = name.trim(),
            fatherName = fatherName.trim(),
            address = address.trim(),
            eventType = eventType.trim().ifEmpty { "شادی" },
            eventDetail = eventDetail.trim(),
            eventDateMillis = eventDateMillis,
            direction = direction.wireValue,
            neondraAmount = amount
        )

        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.saveActivityAndSyncFamily(
                record = record,
                existingFamilies = existingFamilies,
                existingActivities = existingActivities
            )
            _isSaving.value = false
            result.onSuccess { saved ->
                _actionMessage.value = if (matchedBeforeSave != null) {
                    "نیوندرا saved & linked to Family FN ${saved.familyId}."
                } else {
                    "نیوندرا saved & created new Family FN ${saved.familyId}."
                }
                onSaved(saved)
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Failed to save نیوندرا entry."
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
                _actionMessage.value = "Entry saved offline to Family FN ${saved.familyId}."
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
        val currentData = (dashboardState.value as? UiState.Success)?.data
        val existingFamilies = currentData?.families ?: emptyList()
        val existingActivities = currentData?.activities ?: emptyList()

        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.saveFamilyAndSyncActivities(
                profile = profile,
                existingFamilies = existingFamilies,
                existingActivities = existingActivities
            )
            _isSaving.value = false
            result.onSuccess { saved ->
                _actionMessage.value = "Family FN ${saved.familyId} and linked entries updated offline."
                onSaved()
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Failed to save family."
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
                    _actionMessage.value = "Entry synced with device calendar."
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
                    _actionMessage.value = "Family record deleted."
                }
                .onFailure { err ->
                    _actionMessage.value = err.localizedMessage ?: "Failed to delete family."
                }
        }
    }

    private companion object {
        const val TAG = "LedgerViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
