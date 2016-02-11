package com.basecamp.turbolinks;

import android.util.Log;

class TurbolinksLog {
    private static final String DEFAULT_TAG = "TurbolinksLog";
    private static boolean debugLoggingEnabled = false;

    /**
     * <p>Enables/disables debug logging.</p>
     *
     * @param enabled True, to enable.
     */
    static void setDebugLoggingEnabled(boolean enabled) {
        debugLoggingEnabled = enabled;
    }

    /**
     * <p>Send a DEBUG level log statement with the default tag</p>
     *
     * @param msg Debug message.
     */
    static void d(String msg) {
        log(Log.DEBUG, DEFAULT_TAG, msg);
    }

    /**
     * <p>Send a ERROR level log statement with the default tag</p>
     *
     * @param msg Error message.
     */
    static void e(String msg) {
        log(Log.ERROR, DEFAULT_TAG, msg);
    }

    /**
     * <p>Default log statement called by other convenience methods.</p>
     *
     * @param logLevel Log level of the statement.
     * @param tag Tag to identify the logging statement.
     * @param msg Message to log.
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