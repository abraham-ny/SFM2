package com.vitalsoft.sfm;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class StorageManager {

    private static final String TAG        = "SFM_Storage";
    private static final String PREFS_NAME = "sfm_prefs";
    private static final String KEY_RECENTS = "recents";
    private static final int    MAX_RECENTS = 50;

    private final SharedPreferences prefs;

    public StorageManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ─── Write ────────────────────────────────────────────────────────────────

    /**
     * Prepend a new JSON entry to the recents list, deduplicating by title.
     */
    public void saveResult(String jsonEntry) {
        try {
            JSONObject entry = new JSONObject(jsonEntry);
            if (!entry.has("timestamp")) {
                entry.put("timestamp", System.currentTimeMillis());
            }

            String newTitle = entry.optString("title", "").toLowerCase().trim();
            JSONArray current = getRecentsArray();
            JSONArray deduped = new JSONArray();

            for (int i = 0; i < current.length(); i++) {
                JSONObject item = current.optJSONObject(i);
                if (item == null) continue;
                String t = item.optString("title", "").toLowerCase().trim();
                if (!t.equals(newTitle)) {
                    deduped.put(item);
                }
            }

            // Build updated list: new entry first, then remainder up to MAX
            JSONArray updated = new JSONArray();
            updated.put(entry);
            for (int i = 0; i < deduped.length() && updated.length() < MAX_RECENTS; i++) {
                updated.put(deduped.get(i));
            }

            commit(updated);
            Log.d(TAG, "Saved '" + entry.optString("title") + "', total=" + updated.length());

        } catch (JSONException e) {
            Log.e(TAG, "saveResult parse error: " + e.getMessage());
        }
    }

    // ─── Read ─────────────────────────────────────────────────────────────────

    /** Returns the full recents list as a JSON array string. */
    public String getRecents() {
        return getRecentsArray().toString();
    }

    // ─── Delete ───────────────────────────────────────────────────────────────

    /** Remove the first entry whose title matches (case-insensitive). */
    public void deleteRecent(String title) {
        if (title == null || title.trim().isEmpty()) return;
        String target = title.toLowerCase().trim();
        JSONArray current = getRecentsArray();
        JSONArray updated = new JSONArray();

        boolean removed = false;
        for (int i = 0; i < current.length(); i++) {
            JSONObject item = current.optJSONObject(i);
            if (item == null) continue;
            String t = item.optString("title", "").toLowerCase().trim();
            if (!removed && t.equals(target)) {
                removed = true; // skip this entry
            } else {
                updated.put(item);
            }
        }

        if (removed) {
            commit(updated);
            Log.d(TAG, "Deleted recent: " + title);
        }
    }

    /** Remove all recents. */
    public void clearRecents() {
        prefs.edit().remove(KEY_RECENTS).apply();
        Log.d(TAG, "Recents cleared");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private JSONArray getRecentsArray() {
        String stored = prefs.getString(KEY_RECENTS, "[]");
        try {
            return new JSONArray(stored);
        } catch (JSONException e) {
            Log.e(TAG, "getRecentsArray parse error: " + e.getMessage());
            return new JSONArray();
        }
    }

    private void commit(JSONArray array) {
        prefs.edit().putString(KEY_RECENTS, array.toString()).apply();
    }
}
