package com.ksy.recordlib.service.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * Created by eflakemac on 15/6/24.
 */
public class PrefUtil {

    public static void saveMp4Config(Context context, MP4Config config) {
        SharedPreferences preferences = context.getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(Constants.PREFERENCE_KEY_MP4CONFIG_PROFILE_LEVEL, config.getProfileLevel());
        editor.putString(Constants.PREFERENCE_KEY_MP4CONFIG_B64PPS, config.getB64PPS());
        editor.putString(Constants.PREFERENCE_KEY_MP4CONFIG_B64SPS, config.getB64SPS());
        editor.commit();
    }

    public static String getMp4ConfigProfileLevel(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE);
        return preferences.getString(Constants.PREFERENCE_KEY_MP4CONFIG_PROFILE_LEVEL, null);
    }

    public static String getMp4ConfigPps(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE);
        return preferences.getString(Constants.PREFERENCE_KEY_MP4CONFIG_B64PPS, null);
    }

    public static String getMp4ConfigSps(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE);
        return preferences.getString(Constants.PREFERENCE_KEY_MP4CONFIG_B64SPS, null);
    }
}
