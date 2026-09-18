package com.dietaryops.manager.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dietaryops.manager.data.SettingsManager
import com.dietaryops.manager.data.local.AppDatabase
import com.dietaryops.manager.data.repository.DeliveryRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.TimeUnit

/**
 * Background sync worker that pushes locally-stored scan records to
 * Google Sheets and Firestore.
 *
 * Strategy:
 *  1. Fetch all records where syncedToSheets = 0 OR syncedToFirestore = 0
 *     and syncRetryCount < MAX_RETRIES.
 *  2. For each record, attempt Sheets and Firestore pushes in parallel.
 *  3. Mark individual sync flags on success.
 *  4. Increment the retry counter on failure.
 *  5. If any record failed, return [Result.retry()] so WorkManager re-schedules
 *     with exponential backoff (configured in [buildRequest]).
 *
 * Constraints: NETWORK_CONNECTED — so this never blocks the UI and naturally
 * queues while the tablet is offline.
 */
class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val WORK_NAME = "dops_background_sync"
        private const val MAX_RETRIES = 5

        fun buildRequest() = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, TimeUnit.SECONDS
            )
            .build()

        /** Enqueue a unique, keep-existing work item so rapid scans don't pile up. */
        fun enqueue(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, buildRequest())
        }

        /** Replace any queued work — use when settings (e.g. Sheets URL) have changed. */
        fun enqueueReplace(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, buildRequest())
        }
    }

    override suspend fun doWork(): Result = coroutineScope {
        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.scanRecordDao()
        val settingsManager = SettingsManager(applicationContext)
        val repository = DeliveryRepository(applicationContext)

        val webAppUrl = settingsManager.webAppUrl
        val pending = dao.getFailedSyncRecords(MAX_RETRIES)

        if (pending.isEmpty()) return@coroutineScope Result.success()

        var anyFailed = false

        for (record in pending) {
            var sheetsFailed = false
            var firestoreFailed = false

            // Push to Sheets if not yet synced
            if (!record.syncedToSheets && webAppUrl.isNotBlank()) {
                val sheetsJob = async {
                    try {
                        val result = repository.syncWithGoogleSheets(webAppUrl)
                        result.isSuccess
                    } catch (_: Exception) {
                        false
                    }
                }
                if (sheetsJob.await()) {
                    dao.markSheetsSynced(record.id)
                } else {
                    sheetsFailed = true
                }
            }

            // Push to Firestore if not yet synced
            if (!record.syncedToFirestore) {
                val firestoreJob = async {
                    try {
                        repository.pushScanRecordToFirestore(record)
                        true
                    } catch (_: Exception) {
                        false
                    }
                }
                if (firestoreJob.await()) {
                    dao.markFirestoreSynced(record.id)
                } else {
                    firestoreFailed = true
                }
            }

            if (sheetsFailed || firestoreFailed) {
                dao.incrementRetryCount(record.id)
                anyFailed = true
            }
        }

        if (anyFailed) Result.retry() else Result.success()
    }
}
