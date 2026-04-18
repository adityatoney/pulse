package com.pulse

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.pulse.data.cloud.backup.BackupRestoreInitializer
import com.pulse.data.work.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PulseApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var backupRestoreInitializer: BackupRestoreInitializer

    override fun onCreate() {
        super.onCreate()
        syncScheduler.cancelLegacyWorker()
        syncScheduler.scheduleImmediateSync()
        syncScheduler.scheduleHistoryBackfill()
        syncScheduler.schedulePeriodic()
        backupRestoreInitializer.checkAndRestore()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
