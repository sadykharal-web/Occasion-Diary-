package com.example.data

import android.content.Context
import com.example.R
import com.example.drive.DriveBackupPayload
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class LedgerRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Google Sign-In is required.")
    }

    fun observeFamilies(userId: String): Flow<List<FamilyProfile>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/families"
        emitAll(
            db.collection("users").document(userId).collection("families")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FamilyProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareBy<FamilyProfile> { it.familyId.toIntOrNull() ?: Int.MAX_VALUE }
                            .thenBy { it.familyId }
                            .thenBy { it.name }
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeCategories(userId: String): Flow<List<ActivityCategory>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/categories"
        emitAll(
            db.collection("users").document(userId).collection("categories")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ActivityCategory::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.name }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeActivities(userId: String): Flow<List<ActivityRecord>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/activities"
        emitAll(
            db.collection("users").document(userId).collection("activities")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ActivityRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.eventDateMillis }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeEvents(userId: String): Flow<List<EventEntry>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/events"
        emitAll(
            db.collection("users").document(userId).collection("events")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        EventEntry::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.eventDateMillis }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveEvent(entry: EventEntry): Result<EventEntry> {
        var logPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/events"
        return try {
            val uid = requireUserId()
            val cleanTitle = entry.title.trim().take(120)
            val cleanDescription = entry.description.trim().take(1000)
            val cleanCategory = entry.category.trim().take(80)
            val cleanLocation = entry.location.trim().take(200)
            val cleanFamilyDocId = entry.familyDocId?.trim()?.take(128)?.ifEmpty { null }
            val cleanFamilyName = entry.familyName.trim().take(100)
            val safeDateMillis = entry.eventDateMillis.coerceAtLeast(0L)

            require(cleanTitle.isNotEmpty()) { "Please enter an event title." }

            val col = db.collection("users").document(uid).collection("events")
            val isNew = entry.id.isBlank()
            val ref = if (isNew) col.document() else col.document(entry.id)
            logPath = ref.path

            if (isNew) {
                val createPayload = buildMap<String, Any> {
                    put("userId", uid)
                    put("title", cleanTitle)
                    put("description", cleanDescription)
                    put("eventDateMillis", safeDateMillis)
                    put("category", cleanCategory)
                    put("location", cleanLocation)
                    if (cleanFamilyDocId != null) put("familyDocId", cleanFamilyDocId)
                    put("familyName", cleanFamilyName)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                ref.set(createPayload).await()
            } else {
                val updatePayload = buildMap<String, Any> {
                    put("title", cleanTitle)
                    put("description", cleanDescription)
                    put("eventDateMillis", safeDateMillis)
                    put("category", cleanCategory)
                    put("location", cleanLocation)
                    if (cleanFamilyDocId != null) put("familyDocId", cleanFamilyDocId)
                    put("familyName", cleanFamilyName)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                ref.update(updatePayload).await()
            }

            Result.success(
                entry.copy(
                    id = ref.id,
                    userId = uid,
                    title = cleanTitle,
                    description = cleanDescription,
                    eventDateMillis = safeDateMillis,
                    category = cleanCategory,
                    location = cleanLocation,
                    familyDocId = cleanFamilyDocId,
                    familyName = cleanFamilyName
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, logPath)
            Result.failure(e)
        }
    }

    suspend fun deleteEvent(eventId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/events/$eventId"
        return try {
            val uid = requireUserId()
            require(eventId.isNotBlank()) { "Event ID must not be blank." }
            val ref = db.collection("users").document(uid).collection("events").document(eventId)
            docPath = ref.path
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun seedDefaultCategoriesIfEmpty(existingCategories: List<ActivityCategory>): Result<Unit> {
        if (existingCategories.isNotEmpty()) return Result.success(Unit)
        var logPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/categories"
        return try {
            val uid = requireUserId()
            val col = db.collection("users").document(uid).collection("categories")
            logPath = col.path
            val batch = db.batch()
            CategoryStyleCatalog.DEFAULT_SEEDS.forEachIndexed { idx, seed ->
                val ref = col.document("default_${idx + 1}")
                batch.set(
                    ref,
                    mapOf(
                        "userId" to uid,
                        "name" to seed.name,
                        "colorHex" to seed.colorHex,
                        "iconKey" to seed.iconKey,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, logPath)
            Result.failure(e)
        }
    }

    suspend fun saveCategoryAndSyncActivities(
        category: ActivityCategory,
        previousName: String?,
        existingActivities: List<ActivityRecord>
    ): Result<ActivityCategory> {
        var logPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/categories"
        return try {
            val uid = requireUserId()
            val cleanName = category.name.trim().take(80)
            val cleanColor = category.colorHex.trim().take(9)
            val cleanIcon = category.iconKey.trim().take(40)
            require(cleanName.isNotEmpty()) { "Please enter a category name." }
            require(cleanColor.matches(Regex("^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$"))) {
                "Invalid color hex code."
            }

            val userDoc = db.collection("users").document(uid)
            val catCol = userDoc.collection("categories")
            val actCol = userDoc.collection("activities")

            val isNew = category.id.isBlank()
            val catRef = if (isNew) catCol.document() else catCol.document(category.id)
            logPath = catRef.path

            val batch = db.batch()
            if (isNew) {
                batch.set(
                    catRef,
                    mapOf(
                        "userId" to uid,
                        "name" to cleanName,
                        "colorHex" to cleanColor,
                        "iconKey" to cleanIcon,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.update(
                    catRef,
                    mapOf(
                        "name" to cleanName,
                        "colorHex" to cleanColor,
                        "iconKey" to cleanIcon,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                if (!previousName.isNullOrBlank() && !previousName.equals(cleanName, ignoreCase = false)) {
                    existingActivities
                        .filter { it.eventType.equals(previousName, ignoreCase = true) }
                        .forEach { act ->
                            batch.update(
                                actCol.document(act.id),
                                mapOf(
                                    "eventType" to cleanName,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        }
                }
            }
            batch.commit().await()
            Result.success(
                category.copy(
                    id = catRef.id,
                    userId = uid,
                    name = cleanName,
                    colorHex = cleanColor,
                    iconKey = cleanIcon
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, logPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCategory(categoryId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/categories/$categoryId"
        return try {
            val uid = requireUserId()
            require(categoryId.isNotBlank()) { "Category ID must not be blank." }
            val ref = db.collection("users").document(uid).collection("categories").document(categoryId)
            docPath = ref.path
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveActivityAndSyncFamily(
        record: ActivityRecord,
        existingFamilies: List<FamilyProfile>,
        existingActivities: List<ActivityRecord>
    ): Result<ActivityRecord> {
        var logPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/activities"
        return try {
            val uid = requireUserId()
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

            val userDoc = db.collection("users").document(uid)
            val familiesCol = userDoc.collection("families")
            val activitiesCol = userDoc.collection("activities")

            val batch = db.batch()
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
                    val famRef = familiesCol.document(matchedFamily.id)
                    val famUpdate = buildMap<String, Any> {
                        put("familyId", effectiveFn)
                        put("name", cleanName)
                        put("fatherName", cleanFather.ifEmpty { matchedFamily.fatherName })
                        put("address", cleanAddress.ifEmpty { matchedFamily.address })
                        put("updatedAt", FieldValue.serverTimestamp())
                    }
                    batch.update(famRef, famUpdate)
                } else {
                    val famRef = familiesCol.document()
                    resolvedFamilyDocId = famRef.id
                    val famCreate = buildMap<String, Any> {
                        put("userId", uid)
                        put("familyId", effectiveFn)
                        put("name", cleanName)
                        put("fatherName", cleanFather)
                        put("address", cleanAddress)
                        put("createdAt", FieldValue.serverTimestamp())
                        put("updatedAt", FieldValue.serverTimestamp())
                    }
                    batch.set(famRef, famCreate)
                }
            }

            val isNew = record.id.isBlank()
            val actRef = if (isNew) activitiesCol.document() else activitiesCol.document(record.id)
            logPath = actRef.path

            val safeReminderMinutes = record.reminderMinutesBefore.coerceIn(0L, 10080L)
            val safeReminderAt = if (record.reminderEnabled) record.reminderAtMillis.coerceAtLeast(0L) else 0L
            val safeEventMillis = record.eventDateMillis.coerceAtLeast(0L)

            if (isNew) {
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    if (resolvedFamilyDocId != null) put("familyDocId", resolvedFamilyDocId)
                    put("familyId", cleanFn)
                    put("name", cleanName)
                    put("fatherName", cleanFather)
                    put("address", cleanAddress)
                    put("eventType", cleanEvent)
                    put("eventDetail", cleanDetail)
                    put("eventDateMillis", safeEventMillis)
                    put("direction", ActivityDirection.fromWire(record.direction).wireValue)
                    put("homeItems", cleanHome)
                    put("neondraAmount", record.neondraAmount)
                    put("reminderEnabled", record.reminderEnabled)
                    put("reminderAtMillis", safeReminderAt)
                    put("reminderMinutesBefore", safeReminderMinutes)
                    put("calendarSynced", record.calendarSynced)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                batch.set(actRef, payload)
            } else {
                val updatePayload = buildMap<String, Any> {
                    if (resolvedFamilyDocId != null) put("familyDocId", resolvedFamilyDocId)
                    put("familyId", cleanFn)
                    put("name", cleanName)
                    put("fatherName", cleanFather)
                    put("address", cleanAddress)
                    put("eventType", cleanEvent)
                    put("eventDetail", cleanDetail)
                    put("eventDateMillis", safeEventMillis)
                    put("direction", ActivityDirection.fromWire(record.direction).wireValue)
                    put("homeItems", cleanHome)
                    put("neondraAmount", record.neondraAmount)
                    put("reminderEnabled", record.reminderEnabled)
                    put("reminderAtMillis", safeReminderAt)
                    put("reminderMinutesBefore", safeReminderMinutes)
                    put("calendarSynced", record.calendarSynced)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                batch.update(actRef, updatePayload)

                if (resolvedFamilyDocId != null) {
                    existingActivities
                        .filter { it.id != record.id && it.familyDocId == resolvedFamilyDocId }
                        .forEach { sibling ->
                            val siblingRef = activitiesCol.document(sibling.id)
                            batch.update(
                                siblingRef,
                                mapOf(
                                    "familyId" to cleanFn,
                                    "name" to cleanName,
                                    "fatherName" to cleanFather,
                                    "address" to cleanAddress,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        }
                }
            }

            batch.commit().await()
            val saved = record.copy(
                id = actRef.id,
                userId = uid,
                familyDocId = resolvedFamilyDocId,
                familyId = cleanFn,
                name = cleanName,
                fatherName = cleanFather,
                address = cleanAddress,
                eventType = cleanEvent,
                eventDetail = cleanDetail,
                eventDateMillis = safeEventMillis,
                direction = ActivityDirection.fromWire(record.direction).wireValue,
                homeItems = cleanHome,
                reminderAtMillis = safeReminderAt,
                reminderMinutesBefore = safeReminderMinutes
            )
            Result.success(saved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, logPath)
            Result.failure(e)
        }
    }

    suspend fun saveFamilyAndSyncActivities(
        profile: FamilyProfile,
        linkedActivities: List<ActivityRecord>
    ): Result<FamilyProfile> {
        var logPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/families"
        return try {
            val uid = requireUserId()
            val cleanFn = profile.familyId.trim().take(40)
            val cleanName = profile.name.trim().take(100)
            val cleanFather = profile.fatherName.trim().take(100)
            val cleanAddress = profile.address.trim().take(200)
            val cleanPhone = profile.phone?.trim()?.take(30)?.ifEmpty { null }

            require(cleanFn.isNotEmpty()) { "Please enter a Family ID (FN)." }
            require(cleanName.isNotEmpty()) { "Please enter a family name." }

            val userDoc = db.collection("users").document(uid)
            val familiesCol = userDoc.collection("families")
            val activitiesCol = userDoc.collection("activities")

            val isNew = profile.id.isBlank()
            val famRef = if (isNew) familiesCol.document() else familiesCol.document(profile.id)
            logPath = famRef.path

            val batch = db.batch()
            if (isNew) {
                val createMap = buildMap<String, Any> {
                    put("userId", uid)
                    put("familyId", cleanFn)
                    put("name", cleanName)
                    put("fatherName", cleanFather)
                    put("address", cleanAddress)
                    if (cleanPhone != null) put("phone", cleanPhone)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                batch.set(famRef, createMap)
            } else {
                val updateMap = buildMap<String, Any> {
                    put("familyId", cleanFn)
                    put("name", cleanName)
                    put("fatherName", cleanFather)
                    put("address", cleanAddress)
                    if (cleanPhone != null) put("phone", cleanPhone)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                batch.update(famRef, updateMap)

                linkedActivities.forEach { act ->
                    val actRef = activitiesCol.document(act.id)
                    batch.update(
                        actRef,
                        mapOf(
                            "familyId" to cleanFn,
                            "name" to cleanName,
                            "fatherName" to cleanFather,
                            "address" to cleanAddress,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }

            batch.commit().await()
            Result.success(
                profile.copy(
                    id = famRef.id,
                    userId = uid,
                    familyId = cleanFn,
                    name = cleanName,
                    fatherName = cleanFather,
                    address = cleanAddress,
                    phone = cleanPhone
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, logPath)
            Result.failure(e)
        }
    }

    suspend fun restoreFromDriveBackup(
        payload: DriveBackupPayload,
        existingCategories: List<ActivityCategory>,
        existingFamilies: List<FamilyProfile>,
        existingActivities: List<ActivityRecord>,
        existingEvents: List<EventEntry> = emptyList()
    ): Result<Int> {
        var logPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val userDoc = db.collection("users").document(uid)
            logPath = userDoc.path
            val batch = db.batch()
            var importedCount = 0

            val existingCatIds = existingCategories.map { it.id }.toSet()
            payload.categories.forEach { cat ->
                val validId = cat.id.takeIf { it.matches(Regex("^[a-zA-Z0-9_\\-]+$")) }
                if (validId != null && validId !in existingCatIds && cat.name.isNotBlank()) {
                    val ref = userDoc.collection("categories").document(validId)
                    batch.set(
                        ref,
                        mapOf(
                            "userId" to uid,
                            "name" to cat.name.trim().take(80),
                            "colorHex" to cat.colorHex,
                            "iconKey" to cat.iconKey,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    importedCount++
                }
            }

            val existingFamIds = existingFamilies.map { it.id }.toSet()
            payload.families.forEach { fam ->
                val validId = fam.id.takeIf { it.matches(Regex("^[a-zA-Z0-9_\\-]+$")) }
                if (validId != null && validId !in existingFamIds && fam.name.isNotBlank() && fam.familyId.isNotBlank()) {
                    val ref = userDoc.collection("families").document(validId)
                    val map = buildMap<String, Any> {
                        put("userId", uid)
                        put("familyId", fam.familyId.trim().take(40))
                        put("name", fam.name.trim().take(100))
                        put("fatherName", fam.fatherName.trim().take(100))
                        put("address", fam.address.trim().take(200))
                        if (!fam.phone.isNullOrBlank()) put("phone", fam.phone.trim().take(30))
                        put("createdAt", FieldValue.serverTimestamp())
                        put("updatedAt", FieldValue.serverTimestamp())
                    }
                    batch.set(ref, map)
                    importedCount++
                }
            }

            val existingActIds = existingActivities.map { it.id }.toSet()
            payload.activities.forEach { act ->
                val validId = act.id.takeIf { it.matches(Regex("^[a-zA-Z0-9_\\-]+$")) }
                if (validId != null && validId !in existingActIds && act.name.isNotBlank() && act.eventType.isNotBlank()) {
                    val ref = userDoc.collection("activities").document(validId)
                    val map = buildMap<String, Any> {
                        put("userId", uid)
                        if (!act.familyDocId.isNullOrBlank()) put("familyDocId", act.familyDocId)
                        put("familyId", act.familyId.trim().take(40))
                        put("name", act.name.trim().take(100))
                        put("fatherName", act.fatherName.trim().take(100))
                        put("address", act.address.trim().take(200))
                        put("eventType", act.eventType.trim().take(80))
                        put("eventDetail", act.eventDetail.trim().take(250))
                        put("eventDateMillis", act.eventDateMillis.coerceAtLeast(0L))
                        put("direction", ActivityDirection.fromWire(act.direction).wireValue)
                        put("homeItems", act.homeItems.trim().take(1000))
                        put("neondraAmount", act.neondraAmount.coerceIn(0L, 1_000_000_000L))
                        put("reminderEnabled", act.reminderEnabled)
                        put("reminderAtMillis", act.reminderAtMillis.coerceAtLeast(0L))
                        put("reminderMinutesBefore", act.reminderMinutesBefore.coerceIn(0L, 10080L))
                        put("calendarSynced", act.calendarSynced)
                        put("createdAt", FieldValue.serverTimestamp())
                        put("updatedAt", FieldValue.serverTimestamp())
                    }
                    batch.set(ref, map)
                    importedCount++
                }
            }

            val existingEventIds = existingEvents.map { it.id }.toSet()
            payload.events.forEach { evt ->
                val validId = evt.id.takeIf { it.matches(Regex("^[a-zA-Z0-9_\\-]+$")) }
                if (validId != null && validId !in existingEventIds && evt.title.isNotBlank()) {
                    val ref = userDoc.collection("events").document(validId)
                    val map = buildMap<String, Any> {
                        put("userId", uid)
                        put("title", evt.title.trim().take(120))
                        put("description", evt.description.trim().take(1000))
                        put("eventDateMillis", evt.eventDateMillis.coerceAtLeast(0L))
                        put("category", evt.category.trim().take(80))
                        put("location", evt.location.trim().take(200))
                        if (!evt.familyDocId.isNullOrBlank()) put("familyDocId", evt.familyDocId.trim().take(128))
                        put("familyName", evt.familyName.trim().take(100))
                        put("createdAt", FieldValue.serverTimestamp())
                        put("updatedAt", FieldValue.serverTimestamp())
                    }
                    batch.set(ref, map)
                    importedCount++
                }
            }

            if (importedCount > 0) {
                batch.commit().await()
            }
            Result.success(importedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, logPath)
            Result.failure(e)
        }
    }

    suspend fun toggleReminder(
        activityId: String,
        enabled: Boolean,
        reminderAtMillis: Long,
        minutesBefore: Long
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/activities/$activityId"
        return try {
            val uid = requireUserId()
            require(activityId.isNotBlank()) { "Activity ID must not be blank" }
            val ref = db.collection("users").document(uid).collection("activities").document(activityId)
            docPath = ref.path
            ref.update(
                mapOf(
                    "reminderEnabled" to enabled,
                    "reminderAtMillis" to if (enabled) reminderAtMillis.coerceAtLeast(0L) else 0L,
                    "reminderMinutesBefore" to minutesBefore.coerceIn(0L, 10080L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun markCalendarSynced(activityId: String, synced: Boolean = true): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/activities/$activityId"
        return try {
            val uid = requireUserId()
            require(activityId.isNotBlank()) { "Activity ID must not be blank" }
            val ref = db.collection("users").document(uid).collection("activities").document(activityId)
            docPath = ref.path
            ref.update(
                mapOf(
                    "calendarSynced" to synced,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteActivity(activityId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/activities/$activityId"
        return try {
            val uid = requireUserId()
            require(activityId.isNotBlank()) { "Activity ID must not be blank" }
            val ref = db.collection("users").document(uid).collection("activities").document(activityId)
            docPath = ref.path
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteFamily(familyDocId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/families/$familyDocId"
        return try {
            val uid = requireUserId()
            require(familyDocId.isNotBlank()) { "Family Doc ID must not be blank" }
            val ref = db.collection("users").document(uid).collection("families").document(familyDocId)
            docPath = ref.path
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
