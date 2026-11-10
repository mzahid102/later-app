package app.later

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Tasks live in a small JSON file in app storage. Everything goes through here. */
object TaskStore {
    private var file: File? = null
    private var app: Context? = null
    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        val ctx = context.applicationContext
        app = ctx
        val f = File(ctx.filesDir, "tasks.json")
        file = f
        _tasks.value = if (f.exists()) runCatching { Task.listFromJson(f.readText()) }.getOrDefault(emptyList()) else emptyList()
    }

    fun get(id: String): Task? = _tasks.value.firstOrNull { it.id == id }

    fun add(new: List<Task>) {
        commit(_tasks.value + new)
        new.forEach { Reminders.schedule(ctx(), it) }
    }

    fun toggleDone(id: String) {
        val t = get(id) ?: return
        if (t.done == null) markDone(id) else {
            val u = t.copy(done = null)
            replace(u)
            Reminders.schedule(ctx(), u)
        }
    }

    fun markDone(id: String) {
        val t = get(id) ?: return
        if (t.done != null) return
        replace(t.copy(done = System.currentTimeMillis()))
        Reminders.cancel(ctx(), id)
    }

    fun reschedule(id: String, due: Long) {
        val t = get(id) ?: return
        val u = t.copy(due = due, done = null)
        replace(u)
        Reminders.cancel(ctx(), id)
        Reminders.schedule(ctx(), u)
    }

    fun delete(id: String) {
        Reminders.cancel(ctx(), id)
        commit(_tasks.value.filterNot { it.id == id })
    }

    private fun replace(u: Task) = commit(_tasks.value.map { if (it.id == u.id) u else it })

    @Synchronized
    private fun commit(list: List<Task>) {
        _tasks.value = list
        runCatching { file?.writeText(Task.listToJson(list)) }
        app?.let { MicWidget.refresh(it) }
    }

    private fun ctx(): Context = checkNotNull(app) { "TaskStore.init was not called" }
}
