package com.example.data

import android.content.Context
import com.example.R
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
            ?: throw IllegalStateException("Google سائن ان درکار ہے۔")
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

    /**
     * Atomically saves or updates an ActivityRecord AND automatically builds/synchronizes
     * the linked FamilyProfile in /users/{uid}/families so Name, FN, Father, and Address
     * dropdowns stay automatically synchronized.
     */
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

            require(cleanName.isNotEmpty()) { "براہِ کرم نام درج کریں۔" }
            require(cleanEvent.isNotEmpty()) { "براہِ کرم تقریب یا سرگرمی منتخب کریں۔" }
            require(record.neondraAmount in 0L..1_000_000_000L) { "نیوندرا رقم درست حد میں ہونی چاہیے۔" }

            val userDoc = db.collection("users").document(uid)
            val familiesCol = userDoc.collection("families")
            val activitiesCol = userDoc.collection("activities")

            val batch = db.batch()
            var resolvedFamilyDocId: String? = record.familyDocId?.takeIf { it.isNotBlank() }

            // Auto-link or create/update FamilyProfile whenever FN or Name is supplied
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

                // Keep sibling records pointing to the same familyDocId synchronized if core identity changed
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

    /**
     * Saves or updates a FamilyProfile and automatically synchronizes linked ActivityRecords.
     */
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

            require(cleanFn.isNotEmpty()) { "فیملی آئی ڈی (FN) درج کریں۔" }
            require(cleanName.isNotEmpty()) { "نام درج کریں۔" }

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

                // Sync all matching activities automatically
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
