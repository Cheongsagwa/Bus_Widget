package com.cheon.ccbuswidget.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** 위젯 갱신을 백그라운드에서 수행한다. */
class RefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ids = inputData.getIntArray(KEY_IDS)
            ?: WidgetRenderer.allWidgetIds(applicationContext)

        // 네트워크를 타지 않고 '오래됨' 표시만 띄우는 일
        if (inputData.getBoolean(KEY_STALE_ONLY, false)) {
            ids.forEach { id ->
                runCatching { WidgetRenderer.markStale(applicationContext, id) }
            }
            return Result.success()
        }

        // 위젯이 여러 개면 동시에 불러와 네트워크를 켜 두는 시간을 줄인다
        coroutineScope {
            ids.map { id -> async { runCatching { WidgetRenderer.refresh(applicationContext, id) } } }
                .forEach { it.await() }
        }
        // 5분 뒤 '오래됨' 표시 예약 — 위젯마다 따로 걸면 서로 덮어써서 마지막 위젯만 표시됐다.
        // 한 번만 걸고, 그때 모든 위젯의 마지막 갱신 시각을 각각 확인한다.
        scheduleStale(applicationContext)
        return Result.success()
    }

    companion object {
        private const val KEY_IDS = "widget_ids"
        private const val UNIQUE_PERIODIC = "cc_bus_widget_periodic"
        private const val UNIQUE_ONESHOT = "cc_bus_widget_now"
        private const val KEY_STALE_ONLY = "stale_only"
        private const val UNIQUE_STALE = "cc_bus_widget_stale"
        /** 새로고침 후 이만큼 지나면 '오래됨' 표시 */
        private const val STALE_DELAY_MIN = 5L

        fun enqueueNow(context: Context, ids: IntArray) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setInputData(Data.Builder().putIntArray(KEY_IDS, ids).build())
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_ONESHOT, ExistingWorkPolicy.REPLACE, request)
        }

        /**
         * 새로고침한 지 5분이 지나면 위젯에 '오래됨' 가림막을 띄우도록 예약한다. (모든 위젯 대상)
         * 네트워크를 쓰지 않으므로 제약 조건도 없다.
         */
        fun scheduleStale(context: Context) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setInputData(Data.Builder().putBoolean(KEY_STALE_ONLY, true).build())
                .setInitialDelay(STALE_DELAY_MIN, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_STALE, ExistingWorkPolicy.REPLACE, request)
        }

        /** WorkManager 주기 작업의 최소 간격은 15분 */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC)
        }
    }
}
