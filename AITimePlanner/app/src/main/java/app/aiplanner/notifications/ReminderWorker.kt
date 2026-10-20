package app.aiplanner.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.aiplanner.data.LocalRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDateTime

/**
 * One-time worker: в момент старта блока показывает уведомление и помечает задачу IN_PROGRESS.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repo: LocalRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val blockId = inputData.getLong(KEY_BLOCK_ID, -1L)
        if (blockId <= 0) return Result.failure()
        val now = LocalDateTime.now()
        val blocks = repo.blocksInRange(now.minusMinutes(5), now.plusMinutes(60))
        val block = blocks.firstOrNull { it.id == blockId } ?: return Result.success()
        RemindersNotifier.notifyBlockStart(applicationContext, block.id, block.title, block.start)
        block.taskId?.let { runCatching { repo.setTaskStatus(it, app.aiplanner.domain.model.TaskStatus.IN_PROGRESS) } }
        return Result.success()
    }

    companion object {
        const val KEY_BLOCK_ID = "block_id"
    }
}
