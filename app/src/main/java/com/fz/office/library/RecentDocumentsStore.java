package com.fz.office.library;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Stores only SAF document references opened by the user; never scans or modifies source files. */
public final class RecentDocumentsStore {
    private static final String PREFS = "fz_document_library";
    private static final String KEY_ENTRIES = "entries";
    private static final int MAX_ENTRIES = 60;

    private RecentDocumentsStore() { }

    public static final class Entry {
        public final String uri;
        public final String name;
        public final String mimeType;
        public final long openedAt;
        public final boolean starred;

        Entry(String uri, String name, String mimeType, long openedAt, boolean starred) {
            this.uri = uri;
            this.name = name;
            this.mimeType = mimeType;
            this.openedAt = openedAt;
            this.starred = starred;
        }
    }

    public static synchronized List<Entry> getAll(Context context) {
        JSONArray array = read(context);
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            String uri = item.optString("uri", "");
            if (uri.isEmpty()) continue;
            entries.add(new Entry(
                    uri,
                    item.optString("name", "Document"),
                    item.optString("mime", ""),
                    item.optLong("openedAt", 0L),
                    item.optBoolean("starred", false)));
        }
        Collections.sort(entries, (a, b) -> Long.compare(b.openedAt, a.openedAt));
        return entries;
    }

    public static synchronized void record(Context context, android.net.Uri uri,
                                            String name, String mimeType) {
        if (uri == null) return;
        String key = uri.toString();
        if (key.isEmpty()) return;
        List<Entry> entries = getAll(context);
        boolean wasStarred = false;
        for (Entry entry : entries) {
            if (key.equals(entry.uri)) {
                wasStarred = entry.starred;
                break;
            }
        }
        entries.removeIf(entry -> key.equals(entry.uri));
        entries.add(new Entry(key,
                name == null || name.trim().isEmpty() ? "Document" : name,
                mimeType == null ? "" : mimeType,
                System.currentTimeMillis(),
                wasStarred));
        trim(entries);
        save(context, entries);
    }

    public static synchronized void setStarred(Context context, String uri, boolean starred) {
        if (uri == null) return;
        List<Entry> entries = getAll(context);
        List<Entry> updated = new ArrayList<>();
        for (Entry entry : entries) {
            updated.add(uri.equals(entry.uri)
                    ? new Entry(entry.uri, entry.name, entry.mimeType, entry.openedAt, starred)
                    : entry);
        }
        save(context, updated);
    }

    public static synchronized void remove(Context context, String uri) {
        if (uri == null) return;
        List<Entry> entries = getAll(context);
        entries.removeIf(entry -> uri.equals(entry.uri));
        save(context, entries);
    }

    /** Clears history while keeping all starred shortcuts. Does not delete original documents. */
    public static synchronized void clearUnstarred(Context context) {
        List<Entry> entries = getAll(context);
        entries.removeIf(entry -> !entry.starred);
        save(context, entries);
    }

    private static void trim(List<Entry> entries) {
        Collections.sort(entries, (a, b) -> Long.compare(b.openedAt, a.openedAt));
        while (entries.size() > MAX_ENTRIES) {
            int removeAt = entries.size() - 1;
            for (int i = entries.size() - 1; i >= 0; i--) {
                if (!entries.get(i).starred) {
                    removeAt = i;
                    break;
                }
            }
            entries.remove(removeAt);
        }
    }

    private static JSONArray read(Context context) {
        String raw = preferences(context).getString(KEY_ENTRIES, "[]");
        try { return new JSONArray(raw); }
        catch (Exception ignored) { return new JSONArray(); }
    }

    private static void save(Context context, List<Entry> entries) {
        JSONArray array = new JSONArray();
        try {
            for (Entry entry : entries) {
                JSONObject item = new JSONObject();
                item.put("uri", entry.uri);
                item.put("name", entry.name);
                item.put("mime", entry.mimeType);
                item.put("openedAt", entry.openedAt);
                item.put("starred", entry.starred);
                array.put(item);
            }
        } catch (Exception ignored) { }
        preferences(context).edit().putString(KEY_ENTRIES, array.toString()).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
