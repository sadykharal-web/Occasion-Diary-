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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
            val dayFmt = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)
            var importedCount = 0

            val existingCatIds = existingCategories.map { it.id }.toSet()
            val existingCatNames = existingCategories
                .map { FamilyIdentityMatcher.normalizeField(it.name) }
                .filter { it.isNotEmpty() }
                .toMutableSet()
            val newCats = payload.categories
                .filter { cat ->
                    val normName = FamilyIdentityMatcher.normalizeField(cat.name)
                    normName.isNotEmpty() &&
                        (cat.id.isBlank() || cat.id !in existingCatIds) &&
                        normName !in existingCatNames
                }
                .map { cat ->
                    existingCatNames.add(FamilyIdentityMatcher.normalizeField(cat.name))
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
                .toMutableList()

            payload.activities.forEach { act ->
                val cleanEvent = act.eventType.trim().take(80)
                val normEvent = FamilyIdentityMatcher.normalizeField(cleanEvent)
                if (normEvent.isNotEmpty() && normEvent !in existingCatNames) {
                    existingCatNames.add(normEvent)
                    val seed = CategoryStyleCatalog.fallbackSeedFor(cleanEvent)
                    newCats.add(
                        CategoryEntity(
                            id = UUID.randomUUID().toString(),
                            name = cleanEvent,
                            colorHex = seed?.colorHex ?: "#0F5C4D",
                            iconKey = seed?.iconKey ?: "celebration",
                            createdAtMillis = now,
                            updatedAtMillis = now
                        )
                    )
                }
            }
            if (newCats.isNotEmpty()) dao.upsertCategories(newCats)

            val workingFamilies = existingFamilies.toMutableList()
            val workingActivities = existingActivities.toMutableList()
            val newFamEntities = mutableListOf<FamilyEntity>()

            payload.families.forEach { fam ->
                val cleanName = fam.name.trim().take(100)
                if (cleanName.isEmpty()) return@forEach
                val cleanFather = fam.fatherName.trim().take(100)
                val cleanAddress = fam.address.trim().take(200)
                val cleanFn = fam.familyId.trim().take(40)

                val existingMatch = workingFamilies.firstOrNull { existing ->
                    (fam.id.isNotBlank() && existing.id == fam.id) ||
                        FamilyIdentityMatcher.matchesThreeFields(existing, cleanName, cleanFather, cleanAddress) ||
                        (cleanFn.isNotEmpty() &&
                            existing.familyId.trim().equals(cleanFn, ignoreCase = true) &&
                            FamilyIdentityMatcher.normalizeField(existing.name) == FamilyIdentityMatcher.normalizeField(cleanName))
                }
                if (existingMatch == null) {
                    val assignedFn = if (cleanFn.isNotEmpty() &&
                        !FamilyIdentityMatcher.isFnUsedByDifferentFamily(cleanFn, null, workingFamilies)
                    ) {
                        cleanFn
                    } else {
                        FamilyIdentityMatcher.nextUniqueFamilyNumber(workingFamilies, workingActivities)
                    }
                    val entity = FamilyEntity(
                        id = fam.id.ifBlank { UUID.randomUUID().toString() },
                        familyId = assignedFn,
                        name = cleanName,
                        fatherName = cleanFather,
                        address = cleanAddress,
                        phone = fam.phone?.trim()?.take(30)?.ifEmpty { null },
                        createdAtMillis = now,
                        updatedAtMillis = now
                    )
                    newFamEntities.add(entity)
                    workingFamilies.add(entity.toDomain())
                    importedCount++
                }
            }

            fun activitySignature(act: ActivityRecord): String {
                return listOf(
                    FamilyIdentityMatcher.normalizeField(act.name),
                    FamilyIdentityMatcher.normalizeField(act.fatherName),
                    FamilyIdentityMatcher.normalizeField(act.address),
                    dayFmt.format(Date(act.eventDateMillis.coerceAtLeast(0L))),
                    FamilyIdentityMatcher.normalizeField(act.eventType.ifBlank { "شادی" }),
                    FamilyIdentityMatcher.normalizeField(act.eventDetail),
                    ActivityDirection.fromWire(act.direction).wireValue,
                    act.cashAmount.coerceIn(0L, 1_000_000_000L).toString(),
                    act.haarAmount.coerceIn(0L, 1_000_000_000L).toString(),
                    act.salamiAmount.coerceIn(0L, 1_000_000_000L).toString(),
                    act.neondraAmount.coerceIn(0L, 1_000_000_000L).toString(),
                    FamilyIdentityMatcher.normalizeField(act.homeItems)
                ).joinToString("|")
            }

            val existingActIds = existingActivities.map { it.id }.toSet()
            val existingActSigCounts = mutableMapOf<String, Int>()
            existingActivities.forEach { existing ->
                val sig = activitySignature(existing)
                existingActSigCounts[sig] = (existingActSigCounts[sig] ?: 0) + 1
            }

            val newActEntities = mutableListOf<ActivityEntity>()
            payload.activities.forEach { act ->
                val cleanName = act.name.trim().take(100)
                if (cleanName.isEmpty()) return@forEach
                if (act.id.isNotBlank() && act.id in existingActIds) return@forEach

                val sig = activitySignature(act)
                val remainingExisting = existingActSigCounts[sig] ?: 0
                if (remainingExisting > 0) {
                    existingActSigCounts[sig] = remainingExisting - 1
                    return@forEach
                }

                val cleanFather = act.fatherName.trim().take(100)
                val cleanAddress = act.address.trim().take(200)
                val cleanFn = act.familyId.trim().take(40)

                val explicitFamily = act.familyDocId?.takeIf { it.isNotBlank() }?.let { docId ->
                    workingFamilies.firstOrNull { it.id == docId }
                } ?: if (cleanFn.isNotEmpty()) {
                    workingFamilies.firstOrNull { fam ->
                        fam.familyId.trim().equals(cleanFn, ignoreCase = true) &&
                            FamilyIdentityMatcher.normalizeField(fam.name) == FamilyIdentityMatcher.normalizeField(cleanName) &&
                            (cleanFather.isEmpty() || fam.fatherName.isBlank() ||
                                FamilyIdentityMatcher.normalizeField(fam.fatherName) == FamilyIdentityMatcher.normalizeField(cleanFather))
                    }
                } else {
                    null
                }

                val resolvedFamily = explicitFamily ?: run {
                    val resolution = FamilyIdentityMatcher.resolveForActivity(
                        name = cleanName,
                        fatherName = cleanFather,
                        address = cleanAddress,
                        requestedFn = cleanFn,
                        existingFamilies = workingFamilies,
                        existingActivities = workingActivities,
                        nowMillis = now
                    )
                    if (!resolution.matchedExisting) {
                        val famProfile = resolution.familyProfile
                        val famEntity = FamilyEntity(
                            id = famProfile.id,
                            familyId = famProfile.familyId,
                            name = famProfile.name,
                            fatherName = famProfile.fatherName,
                            address = famProfile.address,
                            phone = famProfile.phone,
                            createdAtMillis = now,
                            updatedAtMillis = now
                        )
                        newFamEntities.add(famEntity)
                        workingFamilies.add(famProfile)
                    }
                    resolution.familyProfile
                }

                val entity = ActivityEntity(
                    id = act.id.ifBlank { UUID.randomUUID().toString() },
                    familyDocId = resolvedFamily.id,
                    familyId = resolvedFamily.familyId,
                    name = cleanName,
                    fatherName = cleanFather.ifEmpty { resolvedFamily.fatherName },
                    address = cleanAddress.ifEmpty { resolvedFamily.address },
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
                newActEntities.add(entity)
                workingActivities.add(entity.toDomain())
                importedCount++
            }

            if (newFamEntities.isNotEmpty()) dao.upsertFamilies(newFamEntities)
            if (newActEntities.isNotEmpty()) dao.upsertActivities(newActEntities)

            fun eventSignature(evt: EventEntry): String {
                return listOf(
                    FamilyIdentityMatcher.normalizeField(evt.title),
                    dayFmt.format(Date(evt.eventDateMillis.coerceAtLeast(0L))),
                    FamilyIdentityMatcher.normalizeField(evt.category.ifBlank { "شادی" }),
                    FamilyIdentityMatcher.normalizeField(evt.location)
                ).joinToString("|")
            }

            val existingEvtIds = existingEvents.map { it.id }.toSet()
            val existingEvtSigCounts = mutableMapOf<String, Int>()
            existingEvents.forEach { evt ->
                val sig = eventSignature(evt)
                existingEvtSigCounts[sig] = (existingEvtSigCounts[sig] ?: 0) + 1
            }

            val newEvts = payload.events
                .filter { evt ->
                    val cleanTitle = evt.title.trim()
                    if (cleanTitle.isEmpty()) return@filter false
                    if (evt.id.isNotBlank() && evt.id in existingEvtIds) return@filter false
                    val sig = eventSignature(evt)
                    val remaining = existingEvtSigCounts[sig] ?: 0
                    if (remaining > 0) {
                        existingEvtSigCounts[sig] = remaining - 1
                        false
                    } else {
                        true
                    }
                }
                .map { evt ->
                    val resolvedFamDocId = evt.familyDocId?.takeIf { it.isNotBlank() }
                        ?: evt.familyName.trim().takeIf { it.isNotEmpty() }?.let { fName ->
                            workingFamilies.firstOrNull {
                                FamilyIdentityMatcher.normalizeField(it.name) == FamilyIdentityMatcher.normalizeField(fName)
                            }?.id
                        }
                    importedCount++
                    EventEntity(
                        id = evt.id.ifBlank { UUID.randomUUID().toString() },
                        title = evt.title.trim().take(120),
                        description = evt.description.trim().take(1000),
                        eventDateMillis = evt.eventDateMillis.coerceAtLeast(0L),
                        category = evt.category.trim().take(80).ifEmpty { "شادی" },
                        location = evt.location.trim().take(200),
                        familyDocId = resolvedFamDocId,
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

    suspend fun renameEventGroup(
        activityIds: List<String>,
        eventIds: List<String>,
        newTitle: String
    ): Result<Unit> {
        return try {
            val cleanTitle = newTitle.trim().replace(Regex("\\s+"), " ").take(200)
            require(cleanTitle.isNotEmpty()) { "Event name cannot be blank." }
            val now = System.currentTimeMillis()
            val validActivityIds = activityIds.filter { it.isNotBlank() }
            val validEventIds = eventIds.filter { it.isNotBlank() }
            if (validActivityIds.isNotEmpty()) {
                dao.renameActivitiesEventDetail(validActivityIds, cleanTitle, now)
            }
            if (validEventIds.isNotEmpty()) {
                dao.renameEventsTitle(validEventIds, cleanTitle, now)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteEventGroup(
        activityIds: List<String>,
        eventIds: List<String>
    ): Result<Unit> {
        return try {
            val validActivityIds = activityIds.filter { it.isNotBlank() }
            val validEventIds = eventIds.filter { it.isNotBlank() }
            if (validActivityIds.isNotEmpty()) {
                dao.deleteActivitiesByIds(validActivityIds)
            }
            if (validEventIds.isNotEmpty()) {
                dao.deleteEventsByIds(validEventIds)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
