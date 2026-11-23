package com.example.ui.viewmodel

import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ActivityCategory
import com.example.data.ActivityDirection
import com.example.data.ActivityRecord
import com.example.data.DropdownCatalog
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
    ALL("سب"),
    IN("آمد (In)"),
    OUT("ادائیگی (Out)"),
    REMINDERS("یاد دہانیاں")
}

data class LedgerDashboardData(
    val activities: List<ActivityRecord> = emptyList(),
    val families: List<FamilyProfile> = emptyList(),
    val categories: List<ActivityCategory> = emptyList(),
    val catalog: DropdownCatalog = DropdownCatalog(),
    val summary: LedgerSummary = LedgerSummary()
)

class LedgerViewModel(
    private val repository: LedgerRepository,
    private val currentUserId: String,
    private val userEmail: String? = null
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

    private var pendingDriveActionAfterConsent: DrivePendingAction? = null

    private enum class DrivePendingAction {
        BACKUP,
        RESTORE
    }

    val dashboardState: StateFlow<UiState<LedgerDashboardData>> = combine(
        repository.observeActivities(currentUserId),
        repository.observeFamilies(currentUserId),
        repository.observeCategories(currentUserId)
    ) { activities, families, categories ->
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
                activities.map { it.eventType }
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
            totalCategoriesCount = categories.size
        )

        UiState.Success(
            LedgerDashboardData(
                activities = activities,
                families = families,
                categories = categories,
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
            emit(UiState.Error(rootError.localizedMessage ?: "ڈیٹا لوڈ کرنے میں مسئلہ پیش آیا۔"))
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
            _actionMessage.value = "Google Drive کی اجازت موصول نہیں ہوئی۔"
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
            activities = data.activities
        )
        result.onSuccess { syncedAt ->
            _driveSyncStatus.value = _driveSyncStatus.value.copy(
                isSyncing = false,
                lastSyncMillis = syncedAt,
                lastSyncedFileName = "Neondra_Roznamcha_Backup.json",
                statusMessage = "Google Drive پر محفوظ ہو گیا"
            )
            _actionMessage.value = "تمام ڈیٹا آپ کے Google Drive میں کامیابی سے محفوظ ہو گیا۔"
        }.onFailure { err ->
            _driveSyncStatus.value = _driveSyncStatus.value.copy(
                isSyncing = false,
                statusMessage = err.localizedMessage ?: "Drive بیک اپ ناکام"
            )
            _actionMessage.value = err.localizedMessage ?: "Google Drive اپلوڈ ناکام ہو گیا۔"
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
                existingActivities = data.activities
            )
            restoreRes.onSuccess { count ->
                _driveSyncStatus.value = _driveSyncStatus.value.copy(
                    isSyncing = false,
                    statusMessage = "$count ریکارڈز Google Drive سے بحال ہو گئے"
                )
                _actionMessage.value = if (count > 0) {
                    "Google Drive سے $count ریکارڈز کامیابی سے بحال کر دیے گئے۔"
                } else {
                    "آپ کا تمام ڈیٹا پہلے ہی اپ ٹو ڈیٹ ہے۔"
                }
            }.onFailure { err ->
                _driveSyncStatus.value = _driveSyncStatus.value.copy(isSyncing = false)
                _actionMessage.value = err.localizedMessage ?: "ریکارڈ بحال کرنے میں خرابی۔"
            }
        }.onFailure { err ->
            _driveSyncStatus.value = _driveSyncStatus.value.copy(isSyncing = false)
            _actionMessage.value = err.localizedMessage ?: "Google Drive سے فائل نہ مل سکی۔"
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
                activities = data.activities
            )
            result.onSuccess { syncedAt ->
                _driveSyncStatus.value = _driveSyncStatus.value.copy(
                    lastSyncMillis = syncedAt,
                    lastSyncedFileName = "Neondra_Roznamcha_Backup.json",
                    statusMessage = "Google Drive خودکار مطابقت مکمل"
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
                    _actionMessage.value = "بنیادی سرگرمی کیٹیگریز شامل کر دی گئیں۔"
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
                _actionMessage.value = "کیٹیگری محفوظ کر دی گئی۔"
                triggerAutoDriveBackupIfEnabled(context)
                onSaved(saved)
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "کیٹیگری محفوظ کرنے میں خرابی۔"
            }
        }
    }

    fun deleteCategory(context: Context, categoryId: String) {
        viewModelScope.launch {
            repository.deleteCategory(categoryId)
                .onSuccess {
                    _actionMessage.value = "کیٹیگری حذف کر دی گئی۔"
                    triggerAutoDriveBackupIfEnabled(context)
                }
                .onFailure { err ->
                    _actionMessage.value = err.localizedMessage ?: "کیٹیگری حذف کرنے میں خرابی۔"
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
                _actionMessage.value = "اندراج محفوظ اور فیملی لسٹ کے ساتھ مطابقت پذیر ہو گیا۔"
                triggerAutoDriveBackupIfEnabled(context)
                onSaved(saved)
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "محفوظ کرنے میں خرابی پیش آئی۔"
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
                _actionMessage.value = "فیملی پروفائل اور تمام متعلقہ اندراجات خودکار اپڈیٹ ہو گئے۔"
                triggerAutoDriveBackupIfEnabled(context)
                onSaved()
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "فیملی محفوظ کرنے میں خرابی پیش آئی۔"
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
                    _actionMessage.value = "یاد دہانی فعال کر دی گئی۔"
                } else {
                    ReminderScheduler.cancelReminder(context.applicationContext, record.id)
                    _actionMessage.value = "یاد دہانی بند کر دی گئی۔"
                }
                triggerAutoDriveBackupIfEnabled(context)
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "یاد دہانی اپڈیٹ نہ ہو سکی۔"
            }
        }
    }

    fun markCalendarSynced(context: Context, recordId: String) {
        viewModelScope.launch {
            repository.markCalendarSynced(recordId, true)
                .onSuccess {
                    _actionMessage.value = "کیلنڈر انضمام کے لیے ریکارڈ نشان زد ہو گیا۔"
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
                _actionMessage.value = "اندراج حذف کر دیا گیا۔"
                triggerAutoDriveBackupIfEnabled(context)
                onDeleted()
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "حذف کرنے میں خرابی پیش آئی۔"
            }
        }
    }

    fun deleteFamily(context: Context, familyDocId: String) {
        viewModelScope.launch {
            repository.deleteFamily(familyDocId)
                .onSuccess {
                    _actionMessage.value = "فیملی پروفائل حذف کر دیا گیا۔"
                    triggerAutoDriveBackupIfEnabled(context)
                }
                .onFailure { err ->
                    _actionMessage.value = err.localizedMessage ?: "حذف کرنے میں خرابی پیش آئی۔"
                }
        }
    }

    private companion object {
        const val TAG = "LedgerViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
