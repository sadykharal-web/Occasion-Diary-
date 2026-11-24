package com.example

import android.app.Application
import com.example.data.LedgerRepository
import com.example.data.local.OccasionDiaryDatabase

/**
 * Application entry point providing singleton local RoomDatabase and Repository instances
 * so all app data is persisted offline on the device.
 */
class OccasionDiaryApplication : Application() {

    val database: OccasionDiaryDatabase by lazy {
        OccasionDiaryDatabase.getInstance(this)
    }

    val repository: LedgerRepository by lazy {
        LedgerRepository(database)
    }
}
