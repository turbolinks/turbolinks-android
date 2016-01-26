package com.basecamp.turbolinks;

import android.util.Log;

public class TurbolinksLog {
    private static final String DEFAULT_TAG = "Turbolinks";
    private static boolean debugLoggingEnabled = false;

    static void setDebugLoggingEnabled(boolean enabled) {
        debugLoggingEnabled = enabled;
    }

    /**
     * Send a DEBUG level log statement with the default tag
     */
    static void d(String msg) {
        log(Log.DEBUG, DEFAULT_TAG, msg);
    }

    /**
     * Send a ERROR level log statement with the default tag
     */
    static void e(String msg) {
        log(Log.ERROR, DEFAULT_TAG, msg);
    }

    /**
     * Log a statement
     */
    private static void log(int logLevel, String tag, String msg) {
        switch (logLevel) {
            case Log.DEBUG:
                if (debugLoggingEnabled) {
                    Log.d(tag, msg);
                }
                break;
            case Log.ERROR:
                Log.e(tag, msg);
                break;
            default:
                break;
        }
    }
}