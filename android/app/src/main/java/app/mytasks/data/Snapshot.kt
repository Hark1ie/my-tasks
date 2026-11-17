package app.mytasks.data

import android.content.Context
import app.mytasks.core.SubList
import app.mytasks.core.SubTask
import app.mytasks.core.Task
import app.mytasks.core.TaskList
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * A copy of the data saved on the device for the widgets, so they can be drawn
 * when the app is not running and before the database has loaded.
 */
data class Snapshot(
    val lists: List<TaskList>,
    val subLists: List<SubList>,
    val tasks: List<Task>,
    val subTasks: List<SubTask>,
) {
    fun toJson(): String = JSONObject()
        .put("lists", array(lists.map { it.id to it.toMap() }))
        .put("subLists", array(subLists.map { it.id to it.toMap() }))
        .put("tasks", array(tasks.map { it.id to it.toMap() }))
        .put("subTasks", array(subTasks.map { it.id to it.toMap() }))
        .toString()

    companion object {
        val EMPTY = Snapshot(emptyList(), emptyList(), emptyList(), emptyList())
        private const val FILE = "widget_snapshot.json"

        fun fromJson(text: String): Snapshot {
            val o = JSONObject(text)
            return Snapshot(
                lists = docs(o, "lists").map { (id, m) -> TaskList.fromMap(id, m) },
                subLists = docs(o, "subLists").map { (id, m) -> SubList.fromMap(id, m) },
                tasks = docs(o, "tasks").map { (id, m) -> Task.fromMap(id, m) },
                subTasks = docs(o, "subTasks").map { (id, m) -> SubTask.fromMap(id, m) },
            )
        }

        fun save(context: Context, snapshot: Snapshot) {
            val dir = context.filesDir
            val tmp = File(dir, "$FILE.tmp")
            tmp.writeText(snapshot.toJson())
            tmp.renameTo(File(dir, FILE))
        }

        fun load(context: Context): Snapshot? = try {
            File(context.filesDir, FILE).takeIf { it.exists() }?.readText()?.let { fromJson(it) }
        } catch (_: Exception) {
            null
        }

        fun clear(context: Context) {
            File(context.filesDir, FILE).delete()
        }

        private fun array(items: List<Pair<String, Map<String, Any?>>>): JSONArray {
            val a = JSONArray()
            for ((id, map) in items) {
                val o = JSONObject().put("id", id)
                for ((k, v) in map) o.put(k, toJsonValue(v))
                a.put(o)
            }
            return a
        }

        private fun toJsonValue(v: Any?): Any = when (v) {
            null -> JSONObject.NULL
            is List<*> -> JSONArray().also { arr -> v.forEach { arr.put(toJsonValue(it)) } }
            else -> v
        }

        private fun fromJsonValue(v: Any?): Any? = when (v) {
            null, JSONObject.NULL -> null
            is JSONArray -> (0 until v.length()).map { fromJsonValue(v.get(it)) }
            else -> v
        }

        private fun docs(o: JSONObject, key: String): List<Pair<String, Map<String, Any?>>> {
            val a = o.optJSONArray(key) ?: return emptyList()
            return (0 until a.length()).map { i ->
                val item = a.getJSONObject(i)
                val map = HashMap<String, Any?>()
                for (k in item.keys()) if (k != "id") map[k] = fromJsonValue(item.get(k))
                item.getString("id") to map
            }
        }
    }
}
