package app.aiplanner.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Оффлайн-канал уведомлений о начале блоков расписания. */
object RemindersNotifier {

    private const val CHANNEL_ID = "ai_planner_reminders"
    private const val CHANNEL_NAME = "Напоминания ИИ-планировщика"

    /**
     * Android 13+ (API 33): POST_NOTIFICATIONS — runtime-разрешение.
     * На API 26–32 уведомления разрешены по умолчанию, константы Manifest.POST_NOTIFICATIONS
     * там нет в compileSdk, поэтому используем строку и проверку версии.
     */
    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            context, "android.permission.POST_NOTIFICATIONS"
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Сообщения о старте запланированных фокус-блоков"
        }
        manager.createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission") // проверяем вручную через hasNotificationPermission()
    fun notifyBlockStart(context: Context, blockId: Long, title: String, start: LocalDateTime) {
        ensureChannel(context)
        if (!hasNotificationPermission(context)) return

        val timeText = start.format(DateTimeFormatter.ofPattern("HH:mm"))
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Блок в $timeText")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title — начните сейчас или перестройте день в приложении."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(blockId.toInt(), notification)
    }
}
