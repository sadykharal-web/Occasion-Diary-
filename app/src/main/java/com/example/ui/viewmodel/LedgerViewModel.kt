package com.example.ui.viewmodel

import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiAssistantRepository
import com.example.ai.AiBalanceComparisonResult
import com.example.ai.AiSearchResult
import com.example.data.ActivityCategory
import com.example.data.ActivityDirection
import com.example.data.ActivityRecord
import com.example.data.DropdownCatalog
import com.example.data.EventEntry
import com.example.data.FamilyProfile
import com.example.data.LedgerRepository
import com.example.data.LedgerSummary
import com.example.data.UiState
import com.example.drive.DriveAuthState
import com.example.drive.DriveSyncStatus
import com.example.drive.GoogleDriveSyncManager
import com.example.reminders.ReminderScheduler
import com.google.firebase.firestore.FirebaseFirestoreException
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
    private val currentUserId: String,
    private val userEmail: String? = null,
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

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _driveSyncStatus = MutableStateFlow(DriveSyncStatus())
    val driveSyncStatus: StateFlow<DriveSyncStatus> = _driveSyncStatus.asStateFlow()

    private val _pendingDriveConsent = MutableStateFlow<PendingIntent?>(null)
    val pendingDriveConsent: StateFlow<PendingIntent?> = _pendingDriveConsent.asStateFlow()

    private val _aiSearchState = MutableStateFlow<UiState<AiSearchResult>?>(null)
    val aiSearchState: StateFlow<UiState<AiSearchResult>?> = _aiSearchState.asStateFlow()

    private val _aiComparisonState = MutableStateFlow<UiState<AiBalanceComparisonResult>?>(null)
    val aiComparisonState: StateFlow<UiState<AiBalanceComparisonResult>?> = _aiComparisonState.asStateFlow()

    private val _selectedCompareFamilyIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedCompareFamilyIds: StateFlow<Set<String>> = _selectedCompareFamilyIds.asStateFlow()

    private var pendingDriveActionAfterConsent: DrivePendingAction? = null

    private enum class DrivePendingAction {
        BACKUP,
        RESTORE
    }

    val dashboardState: StateFlow<UiState<LedgerDashboardData>> = combine(
        repository.observeActivities(currentUserId),
        repository.observeFamilies(currentUserId),
        repository.observeCategories(currentUserId),
        repository.observeEvents(currentUserId)
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
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing ledger dashboard", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Failed to load dashboard data."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    fun initDrivePrefs(context: Context) {
        _driveSyncStatus.value = _driveSyncStatus.value.copy(
            autoSyncEnabled = GoogleDriveSyncManager.isAutoSyncEnabled(context, currentUserId),
            lastSyncMillis = GoogleDriveSyncManager.getLastSyncMillis(context, currentUserId)
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

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun consumePendingDriveConsent() {
        _pendingDriveConsent.value = null
    }

    // --- EventEntry Operations ---

    fun saveEvent(
        context: Context,
        entry: EventEntry,
        onSaved: (EventEntry) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.saveEvent(entry)
            _isSaving.value = false
            result.onSuccess { saved ->
                _actionMessage.value = if (entry.id.isBlank()) {
                    "Event \"${saved.title}\" saved to Firestore."
                } else {
                    "Event \"${saved.title}\" updated."
                }
                triggerAutoDriveBackupIfEnabled(context)
                onSaved(saved)
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Failed to save event."
            }
        }
    }

    fun deleteEvent(context: Context, eventId: String) {
        viewModelScope.launch {
            repository.deleteEvent(eventId)
                .onSuccess {
                    _actionMessage.value = "Event deleted."
                    triggerAutoDriveBackupIfEnabled(context)
                }
                .onFailure { err ->
                    _actionMessage.value = err.localizedMessage ?: "Failed to delete event."
                }
        }
    }

    // --- Firebase AI Logic Search & Family Balance Comparison ---

    fun runAiSearch(query: String) {
        val data = (dashboardState.value as? UiState.Success)?.data
        if (data == null) {
            _aiSearchState.value = UiState.Error("Your records are still loading. Please try again in a moment.")
            return
        }
        viewModelScope.launch {
            _aiSearchState.value = UiState.Loading
            val result = aiRepository.searchFamiliesAndEvents(
                query = query,
                families = data.families,
                activities = data.activities,
                events = data.events
            )
            _aiSearchState.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.localizedMessage ?: "AI search failed.") }
            )
        }
    }

    fun clearAiSearch() {
        _aiSearchState.value = null
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
                onFailure = { UiState.Error(it.localizedMessage ?: "AI balance comparison failed.") }
            )
        }
    }

    fun clearAiComparison() {
        _aiComparisonState.value = null
    }

    // --- Google Drive Sync ---

    fun setAutoDriveSync(context: Context, enabled: Boolean) {
        GoogleDriveSyncManager.setAutoSyncEnabled(context, currentUserId, enabled)
        _driveSyncStatus.value = _driveSyncStatus.value.copy(autoSyncEnabled = enabled)
        if (enabled) {
            syncToGoogleDrive(context, interactive = false)
        }
    }

    fun syncToGoogleDrive(context: Context, interactive: Boolean = true) {
        val currentData = (dashboardState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _driveSyncStatus.value = _driveSyncStatus.value.copy(
                isSyncing = true,
                statusMessage = null
            )
            when (val authState = GoogleDriveSyncManager.requestDriveAuthorization(context)) {
                is DriveAuthState.Authorized -> {
                    executeDriveUpload(context, authState.accessToken, currentData)
                }

                is DriveAuthState.NeedsUserConsent -> {
                    _driveSyncStatus.value = _driveSyncStatus.value.copy(isSyncing = false)
                    if (interactive) {
                        pendingDriveActionAfterConsent = DrivePendingAction.BACKUP
                        _pendingDriveConsent.value = authState.pendingIntent
                    }
                }

                is DriveAuthState.Error -> {
                    _driveSyncStatus.value = _driveSyncStatus.value.copy(
                        isSyncing = false,
                        statusMessage = authState.message
                    )
                    if (interactive) {
                        _actionMessage.value = authState.message
                    }
                }
            }
        }
    }

    fun restoreFromGoogleDrive(context: Context) {
        val currentData = (dashboardState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _driveSyncStatus.value = _driveSyncStatus.value.copy(
                isSyncing = true,
                statusMessage = null
            )
            when (val authState = GoogleDriveSyncManager.requestDriveAuthorization(context)) {
                is DriveAuthState.Authorized -> {
                    executeDriveRestore(authState.accessToken, currentData)
                }

                is DriveAuthState.NeedsUserConsent -> {
                    _driveSyncStatus.value = _driveSyncStatus.value.copy(isSyncing = false)
                    pendingDriveActionAfterConsent = DrivePendingAction.RESTORE
                    _pendingDriveConsent.value = authState.pendingIntent
                }

                is DriveAuthState.Error -> {
                    _driveSyncStatus.value = _driveSyncStatus.value.copy(
                        isSyncing = false,
                        statusMessage = authState.message
                    )
                    _actionMessage.value = authState.message
                }
            }
        }
    }

    fun onDriveConsentGranted(context: Context, accessToken: String?) {
        if (accessToken.isNullOrBlank()) {
            _actionMessage.value = "Google Drive permission was not granted."
            pendingDriveActionAfterConsent = null
            return
        }
        val currentData = (dashboardState.value as? UiState.Success)?.data ?: return
        val action = pendingDriveActionAfterConsent ?: DrivePendingAction.BACKUP
        pendingDriveActionAfterConsent = null

        viewModelScope.launch {
            _driveSyncStatus.value = _driveSyncStatus.value.copy(isSyncing = true)
            when (action) {
                DrivePendingAction.BACKUP -> executeDriveUpload(context, accessToken, currentData)
                DrivePendingAction.RESTORE -> executeDriveRestore(accessToken, currentData)
            }
        }
    }

    private suspend fun executeDriveUpload(
        context: Context,
        accessToken: String,
        data: LedgerDashboardData
    ) {
        val result = GoogleDriveSyncManager.uploadBackupToDrive(
            context = context.applicationContext,
            userId = currentUserId,
            userEmail = userEmail,
            accessToken = accessToken,
            categories = data.categories,
            families = data.families,
            activities = data.activities,
            events = data.events
        )
        result.onSuccess { syncedAt ->
            _driveSyncStatus.value = _driveSyncStatus.value.copy(
                isSyncing = false,
                lastSyncMillis = syncedAt,
                lastSyncedFileName = GoogleDriveSyncManager.BACKUP_FILE_NAME,
                statusMessage = "Backed up to Google Drive"
            )
            _actionMessage.value = "All data backed up to Google Drive successfully."
        }.onFailure { err ->
            _driveSyncStatus.value = _driveSyncStatus.value.copy(
                isSyncing = false,
                statusMessage = err.localizedMessage ?: "Drive backup failed"
            )
            _actionMessage.value = err.localizedMessage ?: "Google Drive upload failed."
        }
    }

    private suspend fun executeDriveRestore(
        accessToken: String,
        data: LedgerDashboardData
    ) {
        val dlResult = GoogleDriveSyncManager.downloadBackupFromDrive(accessToken)
        dlResult.onSuccess { backup ->
            val restoreRes = repository.restoreFromDriveBackup(
                payload = backup,
                existingCategories = data.categories,
                existingFamilies = data.families,
                existingActivities = data.activities,
                existingEvents = data.events
            )
            restoreRes.onSuccess { count ->
                _driveSyncStatus.value = _driveSyncStatus.value.copy(
                    isSyncing = false,
                    statusMessage = "Restored $count records from Google Drive"
                )
                _actionMessage.value = if (count > 0) {
                    "Successfully restored $count records from Google Drive."
                } else {
                    "All of your data is already up to date."
                }
            }.onFailure { err ->
                _driveSyncStatus.value = _driveSyncStatus.value.copy(isSyncing = false)
                _actionMessage.value = err.localizedMessage ?: "Failed to restore records."
            }
        }.onFailure { err ->
            _driveSyncStatus.value = _driveSyncStatus.value.copy(isSyncing = false)
            _actionMessage.value = err.localizedMessage ?: "Could not find backup file on Google Drive."
        }
    }

    private fun triggerAutoDriveBackupIfEnabled(context: Context) {
        if (!_driveSyncStatus.value.autoSyncEnabled) return
        val data = (dashboardState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            val token = GoogleDriveSyncManager.tryGetSilentToken(context) ?: return@launch
            val result = GoogleDriveSyncManager.uploadBackupToDrive(
                context = context.applicationContext,
                userId = currentUserId,
                userEmail = userEmail,
                accessToken = token,
                categories = data.categories,
                families = data.families,
                activities = data.activities,
                events = data.events
            )
            result.onSuccess { syncedAt ->
                _driveSyncStatus.value = _driveSyncStatus.value.copy(
                    lastSyncMillis = syncedAt,
                    lastSyncedFileName = GoogleDriveSyncManager.BACKUP_FILE_NAME,
                    statusMessage = "Google Drive auto-sync complete"
                )
            }
        }
    }

    fun seedDefaultCategoriesIfEmpty() {
        val currentCats = (dashboardState.value as? UiState.Success)?.data?.categories ?: return
        if (currentCats.isNotEmpty()) return
        viewModelScope.launch {
            repository.seedDefaultCategoriesIfEmpty(currentCats)
                .onSuccess {
                    _actionMessage.value = "Default occasion categories added."
                }
        }
    }

    fun saveCategory(
        context: Context,
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
                _actionMessage.value = "Category saved."
                triggerAutoDriveBackupIfEnabled(context)
                onSaved(saved)
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Failed to save category."
            }
        }
    }

    fun deleteCategory(context: Context, categoryId: String) {
        viewModelScope.launch {
            repository.deleteCategory(categoryId)
                .onSuccess {
                    _actionMessage.value = "Category deleted."
                    triggerAutoDriveBackupIfEnabled(context)
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
                _actionMessage.value = "Entry saved and synced with your Family Directory."
                triggerAutoDriveBackupIfEnabled(context)
                onSaved(saved)
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Failed to save entry."
            }
        }
    }

    fun saveFamily(
        context: Context,
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
                _actionMessage.value = "Family profile and linked entries updated."
                triggerAutoDriveBackupIfEnabled(context)
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
                triggerAutoDriveBackupIfEnabled(context)
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Failed to update reminder."
            }
        }
    }

    fun markCalendarSynced(context: Context, recordId: String) {
        viewModelScope.launch {
            repository.markCalendarSynced(recordId, true)
                .onSuccess {
                    _actionMessage.value = "Marked entry as synced to device calendar."
                    triggerAutoDriveBackupIfEnabled(context)
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
                triggerAutoDriveBackupIfEnabled(context)
                onDeleted()
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Failed to delete entry."
            }
        }
    }

    fun deleteFamily(context: Context, familyDocId: String) {
        viewModelScope.launch {
            repository.deleteFamily(familyDocId)
                .onSuccess {
                    _actionMessage.value = "Family profile deleted."
                    triggerAutoDriveBackupIfEnabled(context)
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
