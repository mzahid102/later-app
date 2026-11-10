package app.later

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Home screen widget: shows what's next, mic button opens voice capture. */
class MicWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)

    companion object {
        fun refresh(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(context, MicWidget::class.java))
            if (ids.isEmpty()) return
            TaskStore.init(context)

            val now = System.currentTimeMillis()
            val open = TaskStore.tasks.value.filter { it.done == null }.sortedBy { it.due }
            val next = open.firstOrNull { it.due >= now } ?: open.firstOrNull()
            val dueToday = open.count { dayDiff(it.due, now) <= 0 }

            val views = RemoteViews(context.packageName, R.layout.widget_mic)
            views.setTextViewText(R.id.w_title, next?.title ?: "All clear")
            views.setTextViewText(
                R.id.w_meta,
                if (next == null) "Tap the mic to add a task"
                else fmtWhen(next.due, now) + (if (dueToday > 1) "  +${dueToday - 1} today" else "")
            )

            val capture = PendingIntent.getActivity(
                context, 1,
                Intent(context, CaptureActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                PendingIntent.FLAG_IMMUTABLE
            )
            val main = PendingIntent.getActivity(
                context, 2,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.w_mic, capture)
            views.setOnClickPendingIntent(R.id.w_body, main)
            mgr.updateAppWidget(ids, views)
        }
    }
}
