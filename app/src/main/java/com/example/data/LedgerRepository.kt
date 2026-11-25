package com.example.data

import com.example.data.local.ActivityEntity
import com.example.data.local.CategoryEntity
import com.example.data.local.EventEntity
import com.example.data.local.FamilyEntity
import com.example.data.local.OccasionDiaryDao
import com.example.data.local.OccasionDiaryDatabase
import com.example.drive.LocalBackupPayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Repository routing all occasion, ledger, family, category, and backup operations
 * through the injected local RoomDatabase instance.
 */
class LedgerRepository(
    private val database: OccasionDiaryDatabase
) {
    private val dao: OccasionDiaryDao
        get() = database.dao()

    fun observeActivities(): Flow<List<ActivityRecord>> {
        return dao.observeActivities().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun observeFamilies(): Flow<List<FamilyProfile>> {
        return dao.observeFamilies().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun observeCategories(): Flow<List<ActivityCategory>> {
        return dao.observeCategories().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun observeEvents(): Flow<List<EventEntry>> {
        return dao.observeEvents().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun saveEvent(entry: EventEntry): Result<EventEntry> {
        return try {
            val cleanTitle = entry.title.trim().take(120)
            val cleanDescription = entry.description.trim().take(1000)
            val cleanCategory = entry.category.trim().take(80).ifEmpty { "Occasion" }
            val cleanLocation = entry.location.trim().take(200)
            val cleanFamilyDocId = entry.familyDocId?.trim()?.take(128)?.ifEmpty { null }
            val cleanFamilyName = entry.familyName.trim().take(100)
            val safeDateMillis = entry.eventDateMillis.coerceAtLeast(0L)

            require(cleanTitle.isNotEmpty()) { "Please enter an event title." }

            val now = System.currentTimeMillis()
            val resolvedId = entry.id.trim().ifEmpty { UUID.randomUUID().toString() }
            val createdAt = if (entry.id.isBlank()) now else entry.createdAtMillis

            val entity = EventEntity(
                id = resolvedId,
                title = cleanTitle,
                description = cleanDescription,
                eventDateMillis = safeDateMillis,
                category = cleanCategory,
                location = cleanLocation,
                familyDocId = cleanFamilyDocId,
                familyName = cleanFamilyName,
                createdAtMillis = createdAt,
                updatedAtMillis = now
            )
            dao.upsertEvent(entity)
            Result.success(entity.toDomain())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteEvent(eventId: String): Result<Unit> {
        return try {
            require(eventId.isNotBlank()) { "Event ID cannot be blank." }
            dao.deleteEvent(eventId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun seedDefaultCategoriesIfEmpty(existingCategories: List<ActivityCategory>): Result<Unit> {
        val legacySeedNames = setOf(
            "Wedding", "Walima", "Neondra", "Gathering", "Condolence", "Meeting", "Personal",
            "Aqiqah", "Birthday", "Shadi", "Nikah"
        )
        val hasLegacyDefaultSeeds = existingCategories.any {
            it.id.startsWith("default_") && it.name in legacySeedNames
        }
        if (existingCategories.isNotEmpty() && !hasLegacyDefaultSeeds) {
            return Result.success(Unit)
        }
        return try {
            val now = System.currentTimeMillis()
            if (hasLegacyDefaultSeeds) {
                existingCategories
                    .filter { it.id.startsWith("default_") }
                    .forEach { dao.deleteCategory(it.id) }
            }
            val entities = CategoryStyleCatalog.DEFAULT_SEEDS.mapIndexed { idx, seed ->
                CategoryEntity(
                    id = "default_${idx + 1}",
                    name = seed.name,
                    colorHex = seed.colorHex,
                    iconKey = seed.iconKey,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            }
            dao.upsertCategories(entities)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveCategoryAndSyncActivities(
        category: ActivityCategory,
        previousName: String?,
        @Suppress("UNUSED_PARAMETER") existingActivities: List<ActivityRecord>
    ): Result<ActivityCategory> {
        return try {
            val cleanName = category.name.trim().take(80)
            val cleanColor = category.colorHex.trim().take(9)
            val cleanIcon = category.iconKey.trim().take(40)
            require(cleanName.isNotEmpty()) { "Please enter an event type name." }
            require(cleanColor.matches(Regex("^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$"))) {
                "Invalid color code."
            }

            val now = System.currentTimeMillis()
            val resolvedId = category.id.trim().ifEmpty { UUID.randomUUID().toString() }
            val createdAt = if (category.id.isBlank()) now else category.createdAtMillis

            val entity = CategoryEntity(
                id = resolvedId,
                name = cleanName,
                colorHex = cleanColor,
                iconKey = cleanIcon,
                createdAtMillis = createdAt,
                updatedAtMillis = now
            )
            dao.upsertCategory(entity)

            if (!previousName.isNullOrBlank() && !previousName.equals(cleanName, ignoreCase = false)) {
                dao.renameActivityEventType(previousName, cleanName, now)
            }

            Result.success(entity.toDomain())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCategory(categoryId: String): Result<Unit> {
        return try {
            require(categoryId.isNotBlank()) { "Event Type ID cannot be blank." }
            dao.deleteCategory(categoryId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveActivityAndSyncFamily(
        record: ActivityRecord,
        existingFamilies: List<FamilyProfile>,
        existingActivities: List<ActivityRecord>
    ): Result<ActivityRecord> {
        return try {
            val cleanName = record.name.trim().take(100)
            val cleanFn = record.familyId.trim().take(40)
            val cleanFather = record.fatherName.trim().take(100)
            val cleanAddress = record.address.trim().take(200)
            val cleanEvent = record.eventType.trim().take(80)
            val cleanDetail = record.eventDetail.trim().take(250)
            val cleanHome = record.homeItems.trim().take(1000)

            require(cleanName.isNotEmpty()) { "Please enter a person or family name." }
            require(cleanEvent.isNotEmpty()) { "Please select an event type." }
            require(record.cashAmount in 0L..1_000_000_000L) { "Cash amount must be within valid limits." }
            require(record.haarAmount in 0L..1_000_000_000L) { "Haar amount must be within valid limits." }
            require(record.salamiAmount in 0L..1_000_000_000L) { "Salami amount must be within valid limits." }
            require(record.neondraAmount in 0L..1_000_000_000L) { "Neondra amount must be within valid limits." }

            val now = System.currentTimeMillis()
            val resolution = FamilyIdentityMatcher.resolveForActivity(
                name = cleanName,
                fatherName = cleanFather,
                address = cleanAddress,
                requestedFn = cleanFn,
                existingFamilies = existingFamilies,
                existingActivities = existingActivities,
                nowMillis = now
            )

            val resolvedFamily = resolution.familyProfile
            dao.upsertFamily(
                FamilyEntity(
                    id = resolvedFamily.id,
                    familyId = resolvedFamily.familyId,
                    name = resolvedFamily.name,
                    fatherName = resolvedFamily.fatherName,
                    address = resolvedFamily.address,
                    phone = resolvedFamily.phone,
                    createdAtMillis = resolvedFamily.createdAtMillis,
                    updatedAtMillis = now
                )
            )

            val isNew = record.id.isBlank()
            val resolvedActId = if (isNew) UUID.randomUUID().toString() else record.id
            val safeReminderMinutes = record.reminderMinutesBefore.coerceIn(0L, 10080L)
            val safeReminderAt = if (record.reminderEnabled) record.reminderAtMillis.coerceAtLeast(0L) else 0L
            val safeEventMillis = record.eventDateMillis.coerceAtLeast(0L)
            val resolvedDirection = ActivityDirection.fromWire(record.direction).wireValue

            val entity = ActivityEntity(
                id = resolvedActId,
                familyDocId = resolvedFamily.id,
                familyId = resolvedFamily.familyId,
                name = resolvedFamily.name,
                fatherName = resolvedFamily.fatherName,
                address = resolvedFamily.address,
                eventType = cleanEvent,
                eventDetail = cleanDetail,
                eventDateMillis = safeEventMillis,
                direction = resolvedDirection,
                homeItems = cleanHome,
                cashAmount = record.cashAmount,
                haarAmount = record.haarAmount,
                salamiAmount = record.salamiAmount,
                neondraAmount = record.neondraAmount,
                reminderEnabled = record.reminderEnabled,
                reminderAtMillis = safeReminderAt,
                reminderMinutesBefore = safeReminderMinutes,
                calendarSynced = record.calendarSynced,
                createdAtMillis = if (isNew) now else record.createdAtMillis,
                updatedAtMillis = now
            )
            dao.upsertActivity(entity)

            if (!isNew) {
                dao.syncFamilyFieldsToActivities(
                    familyDocId = resolvedFamily.id,
                    familyId = resolvedFamily.familyId,
                    name = resolvedFamily.name,
                    fatherName = resolvedFamily.fatherName,
                    address = resolvedFamily.address,
                    updatedAtMillis = now
                )
            }

            Result.success(entity.toDomain())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveFamilyAndSyncActivities(
        profile: FamilyProfile,
        existingFamilies: List<FamilyProfile>,
        existingActivities: List<ActivityRecord>
    ): Result<FamilyProfile> {
        return try {
            val cleanFn = profile.familyId.trim().take(40)
            val cleanName = profile.name.trim().take(100)
            val cleanFather = profile.fatherName.trim().take(100)
            val cleanAddress = profile.address.trim().take(200)
            val cleanPhone = profile.phone?.trim()?.take(30)?.ifEmpty { null }

            require(cleanName.isNotEmpty()) { "Please enter a family name." }

            val now = System.currentTimeMillis()
            val threeFieldMatch = FamilyIdentityMatcher.findMatchingFamily(
                families = existingFamilies,
                name = cleanName,
                fatherName = cleanFather,
                address = cleanAddress
            )

            val resolvedId = when {
                profile.id.isNotBlank() -> profile.id
                threeFieldMatch != null -> threeFieldMatch.id
                else -> UUID.randomUUID().toString()
            }

            val existingTarget = existingFamilies.firstOrNull { it.id == resolvedId }
            val resolvedFn = when {
                threeFieldMatch != null && profile.id.isBlank() && threeFieldMatch.familyId.isNotBlank() ->
                    threeFieldMatch.familyId
                cleanFn.isNotEmpty() && !FamilyIdentityMatcher.isFnUsedByDifferentFamily(cleanFn, resolvedId, existingFamilies) ->
                    cleanFn
                existingTarget != null && existingTarget.familyId.isNotBlank() &&
                    !FamilyIdentityMatcher.isFnUsedByDifferentFamily(existingTarget.familyId, resolvedId, existingFamilies) ->
                    existingTarget.familyId
                else -> FamilyIdentityMatcher.nextUniqueFamilyNumber(existingFamilies, existingActivities)
            }

            val entity = FamilyEntity(
                id = resolvedId,
                familyId = resolvedFn,
                name = cleanName,
                fatherName = cleanFather,
                address = cleanAddress,
                phone = cleanPhone ?: existingTarget?.phone,
                createdAtMillis = existingTarget?.createdAtMillis ?: if (profile.id.isBlank()) now else profile.createdAtMillis,
                updatedAtMillis = now
            )
            dao.upsertFamily(entity)

            if (existingTarget != null) {
                dao.syncFamilyFieldsToActivities(
                    familyDocId = resolvedId,
                    familyId = resolvedFn,
                    name = cleanName,
                    fatherName = cleanFather,
                    address = cleanAddress,
                    updatedAtMillis = now
                )
            }

            Result.success(entity.toDomain())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreFromLocalBackup(
        payload: LocalBackupPayload,
        existingCategories: List<ActivityCategory>,
        existingFamilies: List<FamilyProfile>,
        existingActivities: List<ActivityRecord>,
        existingEvents: List<EventEntry> = emptyList()
    ): Result<Int> {
        return try {
            val now = System.currentTimeMillis()
            var importedCount = 0

            val existingCatIds = existingCategories.map { it.id }.toSet()
            val newCats = payload.categories
                .filter { it.name.isNotBlank() && (it.id.isBlank() || it.id !in existingCatIds) }
                .map { cat ->
                    importedCount++
                    CategoryEntity(
                        id = cat.id.ifBlank { UUID.randomUUID().toString() },
                        name = cat.name.trim().take(80),
                        colorHex = cat.colorHex.takeIf { it.matches(Regex("^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$")) } ?: "#0F5C4D",
                        iconKey = cat.iconKey.trim().take(40).ifEmpty { "event" },
                        createdAtMillis = now,
                        updatedAtMillis = now
                    )
                }
            if (newCats.isNotEmpty()) dao.upsertCategories(newCats)

            val existingFamIds = existingFamilies.map { it.id }.toSet()
            val newFams = payload.families
                .filter { it.name.isNotBlank() && (it.id.isBlank() || it.id !in existingFamIds) }
                .map { fam ->
                    importedCount++
                    FamilyEntity(
                        id = fam.id.ifBlank { UUID.randomUUID().toString() },
                        familyId = fam.familyId.trim().take(40).ifEmpty { "1" },
                        name = fam.name.trim().take(100),
                        fatherName = fam.fatherName.trim().take(100),
                        address = fam.address.trim().take(200),
                        phone = fam.phone?.trim()?.take(30)?.ifEmpty { null },
                        createdAtMillis = now,
                        updatedAtMillis = now
                    )
                }
            if (newFams.isNotEmpty()) dao.upsertFamilies(newFams)

            val existingActIds = existingActivities.map { it.id }.toSet()
            val newActs = payload.activities
                .filter { it.name.isNotBlank() && (it.id.isBlank() || it.id !in existingActIds) }
                .map { act ->
                    importedCount++
                    ActivityEntity(
                        id = act.id.ifBlank { UUID.randomUUID().toString() },
                        familyDocId = act.familyDocId?.takeIf { it.isNotBlank() },
                        familyId = act.familyId.trim().take(40),
                        name = act.name.trim().take(100),
                        fatherName = act.fatherName.trim().take(100),
                        address = act.address.trim().take(200),
                        eventType = act.eventType.trim().take(80).ifEmpty { "شادی" },
                        eventDetail = act.eventDetail.trim().take(250),
                        eventDateMillis = act.eventDateMillis.coerceAtLeast(0L),
                        direction = ActivityDirection.fromWire(act.direction).wireValue,
                        homeItems = act.homeItems.trim().take(1000),
                        cashAmount = act.cashAmount.coerceIn(0L, 1_000_000_000L),
                        haarAmount = act.haarAmount.coerceIn(0L, 1_000_000_000L),
                        salamiAmount = act.salamiAmount.coerceIn(0L, 1_000_000_000L),
                        neondraAmount = act.neondraAmount.coerceIn(0L, 1_000_000_000L),
                        reminderEnabled = act.reminderEnabled,
                        reminderAtMillis = if (act.reminderEnabled) act.reminderAtMillis.coerceAtLeast(0L) else 0L,
                        reminderMinutesBefore = act.reminderMinutesBefore.coerceIn(0L, 10080L),
                        calendarSynced = act.calendarSynced,
                        createdAtMillis = now,
                        updatedAtMillis = now
                    )
                }
            if (newActs.isNotEmpty()) dao.upsertActivities(newActs)

            val existingEvtIds = existingEvents.map { it.id }.toSet()
            val newEvts = payload.events
                .filter { it.title.isNotBlank() && (it.id.isBlank() || it.id !in existingEvtIds) }
                .map { evt ->
                    importedCount++
                    EventEntity(
                        id = evt.id.ifBlank { UUID.randomUUID().toString() },
                        title = evt.title.trim().take(120),
                        description = evt.description.trim().take(1000),
                        eventDateMillis = evt.eventDateMillis.coerceAtLeast(0L),
                        category = evt.category.trim().take(80).ifEmpty { "شادی" },
                        location = evt.location.trim().take(200),
                        familyDocId = evt.familyDocId?.takeIf { it.isNotBlank() },
                        familyName = evt.familyName.trim().take(100),
                        createdAtMillis = now,
                        updatedAtMillis = now
                    )
                }
            if (newEvts.isNotEmpty()) dao.upsertEvents(newEvts)

            Result.success(importedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleReminder(
        activityId: String,
        enabled: Boolean,
        reminderAtMillis: Long,
        minutesBefore: Long
    ): Result<Unit> {
        return try {
            require(activityId.isNotBlank()) { "Entry ID cannot be blank." }
            dao.updateReminder(
                activityId = activityId,
                enabled = enabled,
                reminderAtMillis = if (enabled) reminderAtMillis.coerceAtLeast(0L) else 0L,
                minutesBefore = if (enabled) minutesBefore.coerceIn(0L, 10080L) else 0L,
                updatedAtMillis = System.currentTimeMillis()
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markCalendarSynced(activityId: String, synced: Boolean): Result<Unit> {
        return try {
            require(activityId.isNotBlank()) { "Entry ID cannot be blank." }
            dao.updateCalendarSynced(activityId, synced, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteActivity(activityId: String): Result<Unit> {
        return try {
            require(activityId.isNotBlank()) { "Entry ID cannot be blank." }
            dao.deleteActivity(activityId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFamily(familyDocId: String): Result<Unit> {
        return try {
            require(familyDocId.isNotBlank()) { "Family ID cannot be blank." }
            dao.deleteFamily(familyDocId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
