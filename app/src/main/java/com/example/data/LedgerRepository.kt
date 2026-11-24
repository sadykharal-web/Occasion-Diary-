package com.example.data

import com.example.data.local.ActivityEntity
import com.example.data.local.CategoryEntity
import com.example.data.local.EventEntity
import com.example.data.local.FamilyEntity
import com.example.data.local.OccasionDiaryDao
import com.example.drive.LocalBackupPayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class LedgerRepository(
    private val dao: OccasionDiaryDao
) {

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
            val cleanCategory = entry.category.trim().take(80).ifEmpty { "Event" }
            val cleanLocation = entry.location.trim().take(200)
            val cleanFamilyDocId = entry.familyDocId?.trim()?.take(128)?.ifEmpty { null }
            val cleanFamilyName = entry.familyName.trim().take(100)
            val safeDateMillis = entry.eventDateMillis.coerceAtLeast(0L)

            require(cleanTitle.isNotEmpty()) { "Please enter an occasion name." }

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
            require(eventId.isNotBlank()) { "Event ID must not be blank." }
            dao.deleteEvent(eventId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun seedDefaultCategoriesIfEmpty(existingCategories: List<ActivityCategory>): Result<Unit> {
        if (existingCategories.isNotEmpty()) return Result.success(Unit)
        return try {
            val now = System.currentTimeMillis()
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
            require(cleanName.isNotEmpty()) { "Please enter a category name." }
            require(cleanColor.matches(Regex("^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$"))) {
                "Invalid color hex code."
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
            require(categoryId.isNotBlank()) { "Category ID must not be blank." }
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
        @Suppress("UNUSED_PARAMETER") existingActivities: List<ActivityRecord>
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
            require(cleanEvent.isNotEmpty()) { "Please select an event or activity category." }
            require(record.neondraAmount in 0L..1_000_000_000L) { "Amount must be within valid bounds." }

            val now = System.currentTimeMillis()
            var resolvedFamilyDocId: String? = record.familyDocId?.takeIf { it.isNotBlank() }

            if (cleanFn.isNotEmpty() || cleanName.isNotEmpty()) {
                val effectiveFn = cleanFn.ifEmpty {
                    val maxNum = existingFamilies.mapNotNull { it.familyId.toIntOrNull() }.maxOrNull() ?: 0
                    (maxNum + 1).toString()
                }
                val matchedFamily = existingFamilies.firstOrNull { fam ->
                    (resolvedFamilyDocId != null && fam.id == resolvedFamilyDocId) ||
                        (fam.name.equals(cleanName, ignoreCase = true) &&
                            (fam.familyId == effectiveFn || fam.fatherName.equals(cleanFather, ignoreCase = true))) ||
                        (cleanFn.isNotEmpty() && fam.familyId == cleanFn && fam.name.equals(cleanName, ignoreCase = true))
                }

                if (matchedFamily != null) {
                    resolvedFamilyDocId = matchedFamily.id
                    dao.upsertFamily(
                        FamilyEntity(
                            id = matchedFamily.id,
                            familyId = effectiveFn,
                            name = cleanName,
                            fatherName = cleanFather.ifEmpty { matchedFamily.fatherName },
                            address = cleanAddress.ifEmpty { matchedFamily.address },
                            phone = matchedFamily.phone,
                            createdAtMillis = matchedFamily.createdAtMillis,
                            updatedAtMillis = now
                        )
                    )
                } else {
                    val newFamId = UUID.randomUUID().toString()
                    resolvedFamilyDocId = newFamId
                    dao.upsertFamily(
                        FamilyEntity(
                            id = newFamId,
                            familyId = effectiveFn,
                            name = cleanName,
                            fatherName = cleanFather,
                            address = cleanAddress,
                            phone = null,
                            createdAtMillis = now,
                            updatedAtMillis = now
                        )
                    )
                }
            }

            val isNew = record.id.isBlank()
            val resolvedActId = if (isNew) UUID.randomUUID().toString() else record.id
            val safeReminderMinutes = record.reminderMinutesBefore.coerceIn(0L, 10080L)
            val safeReminderAt = if (record.reminderEnabled) record.reminderAtMillis.coerceAtLeast(0L) else 0L
            val safeEventMillis = record.eventDateMillis.coerceAtLeast(0L)
            val resolvedDirection = ActivityDirection.fromWire(record.direction).wireValue

            val entity = ActivityEntity(
                id = resolvedActId,
                familyDocId = resolvedFamilyDocId,
                familyId = cleanFn,
                name = cleanName,
                fatherName = cleanFather,
                address = cleanAddress,
                eventType = cleanEvent,
                eventDetail = cleanDetail,
                eventDateMillis = safeEventMillis,
                direction = resolvedDirection,
                homeItems = cleanHome,
                neondraAmount = record.neondraAmount,
                reminderEnabled = record.reminderEnabled,
                reminderAtMillis = safeReminderAt,
                reminderMinutesBefore = safeReminderMinutes,
                calendarSynced = record.calendarSynced,
                createdAtMillis = if (isNew) now else record.createdAtMillis,
                updatedAtMillis = now
            )
            dao.upsertActivity(entity)

            if (!isNew && resolvedFamilyDocId != null) {
                dao.syncFamilyFieldsToActivities(
                    familyDocId = resolvedFamilyDocId,
                    familyId = cleanFn,
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

    suspend fun saveFamilyAndSyncActivities(
        profile: FamilyProfile,
        @Suppress("UNUSED_PARAMETER") linkedActivities: List<ActivityRecord>
    ): Result<FamilyProfile> {
        return try {
            val cleanFn = profile.familyId.trim().take(40)
            val cleanName = profile.name.trim().take(100)
            val cleanFather = profile.fatherName.trim().take(100)
            val cleanAddress = profile.address.trim().take(200)
            val cleanPhone = profile.phone?.trim()?.take(30)?.ifEmpty { null }

            require(cleanFn.isNotEmpty()) { "Please enter a Family ID (FN)." }
            require(cleanName.isNotEmpty()) { "Please enter a family name." }

            val now = System.currentTimeMillis()
            val isNew = profile.id.isBlank()
            val resolvedId = if (isNew) UUID.randomUUID().toString() else profile.id

            val entity = FamilyEntity(
                id = resolvedId,
                familyId = cleanFn,
                name = cleanName,
                fatherName = cleanFather,
                address = cleanAddress,
                phone = cleanPhone,
                createdAtMillis = if (isNew) now else profile.createdAtMillis,
                updatedAtMillis = now
            )
            dao.upsertFamily(entity)

            if (!isNew) {
                dao.syncFamilyFieldsToActivities(
                    familyDocId = resolvedId,
                    familyId = cleanFn,
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
                        eventType = act.eventType.trim().take(80).ifEmpty { "Wedding" },
                        eventDetail = act.eventDetail.trim().take(250),
                        eventDateMillis = act.eventDateMillis.coerceAtLeast(0L),
                        direction = ActivityDirection.fromWire(act.direction).wireValue,
                        homeItems = act.homeItems.trim().take(1000),
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
                        category = evt.category.trim().take(80).ifEmpty { "Event" },
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
            require(activityId.isNotBlank()) { "Activity ID must not be blank." }
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
            require(activityId.isNotBlank()) { "Activity ID must not be blank." }
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
            require(activityId.isNotBlank()) { "Activity ID must not be blank." }
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
            require(familyDocId.isNotBlank()) { "Family ID must not be blank." }
            dao.deleteFamily(familyDocId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
