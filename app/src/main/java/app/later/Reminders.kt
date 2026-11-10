package app.later

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build

object Reminders {
    const val CHANNEL = "reminders"
    const val ACTION_FIRE = "app.later.FIRE"
    const val ACTION_DONE = "app.later.DONE"
    const val ACTION_SNOOZE = "app.later.SNOOZE"
    const val EXTRA_ID = "id"
    const val EXTRA_MIN = "min"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Alerts when a task is due"
                    enableVibration(true)
                }
            )
        }
    }

    private fun broadcast(ctx: Context, action: String, id: String, minutes: Int = 0): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("later://task/$id/$minutes"))
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_MIN, minutes)
        return PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun schedule(ctx: Context, t: Task) {
        if (t.done != null || t.due <= System.currentTimeMillis()) return
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = broadcast(ctx, ACTION_FIRE, t.id)
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.due, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.due, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.due, pi)
        }
    }

    fun cancel(ctx: Context, id: String) {
        ctx.getSystemService(AlarmManager::class.java).cancel(broadcast(ctx, ACTION_FIRE, id))
        dismiss(ctx, id)
    }

    fun dismiss(ctx: Context, id: String) {
        ctx.getSystemService(NotificationManager::class.java).cancel(id.hashCode())
    }

    fun rescheduleAll(ctx: Context) {
        TaskStore.tasks.value.forEach { schedule(ctx, it) }
    }

    fun notify(ctx: Context, t: Task) {
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val icon = Icon.createWithResource(ctx, R.drawable.ic_notif)
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(t.title)
            .setContentText("Due " + fmtWhen(t.due, System.currentTimeMillis()).replaceFirstChar { it.lowercase() })
            .setCategory(Notification.CATEGORY_REMINDER)
            .setColor(0xFF2E5E4E.toInt())
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(icon, "Done", broadcast(ctx, ACTION_DONE, t.id)).build())
            .addAction(Notification.Action.Builder(icon, "In 15 min", broadcast(ctx, ACTION_SNOOZE, t.id, 15)).build())
            .addAction(Notification.Action.Builder(icon, "In 1 hour", broadcast(ctx, ACTION_SNOOZE, t.id, 60)).build())
            .build()
        try {
            ctx.getSystemService(NotificationManager::class.java).notify(t.id.hashCode(), n)
        } catch (_: SecurityException) { /* notifications turned off */ }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TaskStore.init(context)
        val id = intent.getStringExtra(Reminders.EXTRA_ID) ?: return
        val task = TaskStore.get(id) ?: return
        when (intent.action) {
            Reminders.ACTION_FIRE -> if (task.done == null) {
                Reminders.notify(context, task)
                MicWidget.refresh(context)
            }
            Reminders.ACTION_DONE -> TaskStore.markDone(id)
            Reminders.ACTION_SNOOZE -> {
                val min = intent.getIntExtra(Reminders.EXTRA_MIN, 15)
                TaskStore.reschedule(id, System.currentTimeMillis() + min * MINUTE)
            }
        }
    }
}

/** Alarms are wiped on reboot, so put them back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TaskStore.init(context)
        Reminders.rescheduleAll(context)
        MicWidget.refresh(context)
    }
}
