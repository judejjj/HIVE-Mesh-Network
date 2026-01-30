package com.example.hive;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.UUID;

public class MeshHelper {

    private static final String PREFS_NAME = "HivePrefs";
    public static final String KEY_NICKNAME = "KEY_NICKNAME";
    public static final String KEY_UUID = "KEY_MY_UUID"; // Must match what we use elsewhere

    public static String getBroadcastName(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String rawName = prefs.getString(KEY_NICKNAME, "Survivor");
        String uuid = prefs.getString(KEY_UUID, null);

        // Auto-generate if missing (Safety check)
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_UUID, uuid).apply();
        }

        String suffix = uuid.substring(0, 4).toUpperCase();
        return rawName + "#" + suffix;
    }
}
