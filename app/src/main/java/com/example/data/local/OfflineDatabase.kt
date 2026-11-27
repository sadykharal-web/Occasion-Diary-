package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.ActivityCategory
import com.example.data.ActivityRecord
import com.example.data.EventEntry
import com.example.data.FamilyProfile
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "families")
data class FamilyEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val name: String,
    val fatherName: String,
    val address: String,
    val phone: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
) {
    fun toDomain(): FamilyProfile = FamilyProfile(
        id = id,
        userId = "local",
        familyId = familyId,
        name = name,
        fatherName = fatherName,
        address = address,
        phone = phone,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis
    )
}

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorHex: String,
    val iconKey: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
) {
    fun toDomain(): ActivityCategory = ActivityCategory(
        id = id,
        userId = "local",
        name = name,
        colorHex = colorHex,
        iconKey = iconKey,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis
    )
}

@Entity(tableName = "activities")
data class ActivityEntity(
    @PrimaryKey val id: String,
    val familyDocId: String?,
    val familyId: String,
    val name: String,
    val fatherName: String,
    val address: String,
    val eventType: String,
    val eventDetail: String,
    val eventDateMillis: Long,
    val direction: String,
    val homeItems: String,
    val cashAmount: Long = 0L,
    val haarAmount: Long = 0L,
    val salamiAmount: Long = 0L,
    val neondraAmount: Long,
    val reminderEnabled: Boolean,
    val reminderAtMillis: Long,
    val reminderMinutesBefore: Long,
    val calendarSynced: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
) {
    fun toDomain(): ActivityRecord = ActivityRecord(
        id = id,
        userId = "local",
        familyDocId = familyDocId,
        familyId = familyId,
        name = name,
        fatherName = fatherName,
        address = address,
        eventType = eventType,
        eventDetail = eventDetail,
        eventDateMillis = eventDateMillis,
        direction = direction,
        homeItems = homeItems,
        cashAmount = cashAmount,
        haarAmount = haarAmount,
        salamiAmount = salamiAmount,
        neondraAmount = neondraAmount,
        reminderEnabled = reminderEnabled,
        reminderAtMillis = reminderAtMillis,
        reminderMinutesBefore = reminderMinutesBefore,
        calendarSynced = calendarSynced,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis
    )
}

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val eventDateMillis: Long,
    val category: String,
    val location: String,
    val familyDocId: String?,
    val familyName: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
) {
    fun toDomain(): EventEntry = EventEntry(
        id = id,
        userId = "local",
        title = title,
        description = description,
        eventDateMillis = eventDateMillis,
        category = category,
        location = location,
        familyDocId = familyDocId,
        familyName = familyName,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis
    )
}

@Dao
interface OccasionDiaryDao {
    @Query("SELECT * FROM activities ORDER BY eventDateMillis DESC")
    fun observeActivities(): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM families ORDER BY name ASC")
    fun observeFamilies(): Flow<List<FamilyEntity>>

    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM events ORDER BY eventDateMillis ASC")
    fun observeEvents(): Flow<List<EventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFamily(family: FamilyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFamilies(families: List<FamilyEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActivity(activity: ActivityEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActivities(activities: List<ActivityEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvent(event: EventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvents(events: List<EventEntity>)

    @Query("DELETE FROM families WHERE id = :familyDocId")
    suspend fun deleteFamily(familyDocId: String)

    @Query("DELETE FROM categories WHERE id = :categoryId")
    suspend fun deleteCategory(categoryId: String)

    @Query("DELETE FROM activities WHERE id = :activityId")
    suspend fun deleteActivity(activityId: String)

    @Query("DELETE FROM events WHERE id = :eventId")
    suspend fun deleteEvent(eventId: String)

    @Query("UPDATE activities SET eventType = :newName, updatedAtMillis = :updatedAtMillis WHERE LOWER(eventType) = LOWER(:oldName)")
    suspend fun renameActivityEventType(oldName: String, newName: String, updatedAtMillis: Long)

    @Query("UPDATE activities SET eventDetail = :newTitle, updatedAtMillis = :updatedAtMillis WHERE id IN (:activityIds)")
    suspend fun renameActivitiesEventDetail(activityIds: List<String>, newTitle: String, updatedAtMillis: Long)

    @Query("UPDATE events SET title = :newTitle, updatedAtMillis = :updatedAtMillis WHERE id IN (:eventIds)")
    suspend fun renameEventsTitle(eventIds: List<String>, newTitle: String, updatedAtMillis: Long)

    @Query("DELETE FROM activities WHERE id IN (:activityIds)")
    suspend fun deleteActivitiesByIds(activityIds: List<String>)

    @Query("DELETE FROM events WHERE id IN (:eventIds)")
    suspend fun deleteEventsByIds(eventIds: List<String>)

    @Query("UPDATE activities SET familyId = :familyId, name = :name, fatherName = :fatherName, address = :address, updatedAtMillis = :updatedAtMillis WHERE familyDocId = :familyDocId")
    suspend fun syncFamilyFieldsToActivities(
        familyDocId: String,
        familyId: String,
        name: String,
        fatherName: String,
        address: String,
        updatedAtMillis: Long
    )

    @Query("UPDATE activities SET reminderEnabled = :enabled, reminderAtMillis = :reminderAtMillis, reminderMinutesBefore = :minutesBefore, updatedAtMillis = :updatedAtMillis WHERE id = :activityId")
    suspend fun updateReminder(
        activityId: String,
        enabled: Boolean,
        reminderAtMillis: Long,
        minutesBefore: Long,
        updatedAtMillis: Long
    )

    @Query("UPDATE activities SET calendarSynced = :synced, updatedAtMillis = :updatedAtMillis WHERE id = :activityId")
    suspend fun updateCalendarSynced(activityId: String, synced: Boolean, updatedAtMillis: Long)
}

@Database(
    entities = [
        FamilyEntity::class,
        CategoryEntity::class,
        ActivityEntity::class,
        EventEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class OccasionDiaryDatabase : RoomDatabase() {
    abstract fun dao(): OccasionDiaryDao

    companion object {
        @Volatile
        private var INSTANCE: OccasionDiaryDatabase? = null

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE activities ADD COLUMN cashAmount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE activities ADD COLUMN haarAmount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE activities ADD COLUMN salamiAmount INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): OccasionDiaryDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    OccasionDiaryDatabase::class.java,
                    "occasion_diary_offline.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
