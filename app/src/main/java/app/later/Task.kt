package app.later

import org.json.JSONArray
import org.json.JSONObject

data class Task(
    val id: String,
    val title: String,
    val due: Long,
    val created: Long,
    val done: Long? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("title", title).put("due", due).put("created", created)
        .apply { if (done != null) put("done", done) }

    companion object {
        fun fromJson(o: JSONObject) = Task(
            id = o.getString("id"),
            title = o.getString("title"),
            due = o.getLong("due"),
            created = o.optLong("created"),
            done = if (o.has("done")) o.getLong("done") else null,
        )

        fun listToJson(list: List<Task>): String =
            JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()

        fun listFromJson(s: String): List<Task> {
            val a = JSONArray(s)
            return (0 until a.length()).map { fromJson(a.getJSONObject(it)) }
        }
    }
}
