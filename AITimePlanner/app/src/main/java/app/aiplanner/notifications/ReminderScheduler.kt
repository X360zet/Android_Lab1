package app.aiplanner.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Оффлайн-напоминания: ставим точные one-time work'и WorkManager на старт блоков.
 * Никакого интернета не требуется — всё планирование и уведомления живут на устройстве.
 */
object ReminderScheduler {

    fun scheduleBlock(context: Context, blockId: Long, title: String, start: LocalDateTime) {
        val delay = java.time.Duration.between(LocalDateTime.now(), start).toMillis()
        if (delay < 0) return
        val data = Data.Builder()
            .putLong(ReminderWorker.KEY_BLOCK_ID, blockId)
            .build()
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag("block_$blockId")
            .setInputData(data)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("block_work_$blockId", ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelBlock(context: Context, blockId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork("block_work_$blockId")
    }
}

/** После перезагрузки устройства переставляем напоминания для ближайших блоков дня. */
@AndroidEntryPoint
class RemindersBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // В оффлайн-режиме просто чистим устаревшие работы; новые поставятся при следующем открытии экрана «График».
            WorkManager.getInstance(context).pruneWork()
        }
    }
}
