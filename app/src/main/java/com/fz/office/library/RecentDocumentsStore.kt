package com.fz.office.library

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores only SAF document references opened by the user.
 *
 * The store never scans, edits, deletes, or rewrites the source documents.
 * @JvmStatic and @JvmField preserve the existing Java call sites while the
 * remaining application classes are migrated to Kotlin.
 */
object RecentDocumentsStore {
    private const val PREFS = "fz_document_library"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 60

    data class Entry(
        @JvmField val uri: String,
        @JvmField val name: String,
        @JvmField val mimeType: String,
        @JvmField val openedAt: Long,
        @JvmField val starred: Boolean
    )

    @JvmStatic
    @Synchronized
    fun getAll(context: Context): List<Entry> {
        val array = read(context)
        val entries = ArrayList<Entry>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val uri = item.optString("uri", "")
            if (uri.isEmpty()) continue

            entries += Entry(
                uri = uri,
                name = item.optString("name", "Document"),
                mimeType = item.optString("mime", ""),
                openedAt = item.optLong("openedAt", 0L),
                starred = item.optBoolean("starred", false)
            )
        }
        return entries.sortedByDescending { it.openedAt }
    }

    @JvmStatic
    @Synchronized
    fun record(context: Context, uri: Uri?, name: String?, mimeType: String?) {
        if (uri == null) return
        val key = uri.toString()
        if (key.isEmpty()) return

        val entries = getAll(context).toMutableList()
        val wasStarred = entries.firstOrNull { it.uri == key }?.starred ?: false
        entries.removeAll { it.uri == key }
        entries += Entry(
            uri = key,
            name = if (name.isNullOrBlank()) "Document" else name,
            mimeType = mimeType ?: "",
            openedAt = System.currentTimeMillis(),
            starred = wasStarred
        )
        trim(entries)
        save(context, entries)
    }

    @JvmStatic
    @Synchronized
    fun setStarred(context: Context, uri: String?, starred: Boolean) {
        if (uri == null) return
        val updated = getAll(context).map { entry ->
            if (uri == entry.uri) {
                Entry(entry.uri, entry.name, entry.mimeType, entry.openedAt, starred)
            } else {
                entry
            }
        }
        save(context, updated)
    }

    @JvmStatic
    @Synchronized
    fun remove(context: Context, uri: String?) {
        if (uri == null) return
        save(context, getAll(context).filterNot { it.uri == uri })
    }

    /** Clears history while keeping starred shortcuts. Source documents are never deleted. */
    @JvmStatic
    @Synchronized
    fun clearUnstarred(context: Context) {
        save(context, getAll(context).filter { it.starred })
    }

    private fun trim(entries: MutableList<Entry>) {
        entries.sortByDescending { it.openedAt }
        while (entries.size > MAX_ENTRIES) {
            var removeAt = entries.lastIndex
            for (index in entries.lastIndex downTo 0) {
                if (!entries[index].starred) {
                    removeAt = index
                    break
                }
            }
            entries.removeAt(removeAt)
        }
    }

    private fun read(context: Context): JSONArray {
        val raw = preferences(context).getString(KEY_ENTRIES, "[]") ?: "[]"
        return try {
            JSONArray(raw)
        } catch (_: Exception) {
            JSONArray()
        }
    }

    private fun save(context: Context, entries: List<Entry>) {
        val array = JSONArray()
        try {
            entries.forEach { entry ->
                val item = JSONObject()
                item.put("uri", entry.uri)
                item.put("name", entry.name)
                item.put("mime", entry.mimeType)
                item.put("openedAt", entry.openedAt)
                item.put("starred", entry.starred)
                array.put(item)
            }
        } catch (_: Exception) {
            // Keep the last valid preferences value if JSON serialization fails.
            return
        }
        preferences(context).edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    private fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
