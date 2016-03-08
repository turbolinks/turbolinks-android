package com.basecamp.turbolinks;

import android.content.Context;
import android.util.Base64;
import android.webkit.ValueCallback;
import android.webkit.WebView;

import java.io.IOException;
import java.io.InputStream;

public class TurbolinksJavascriptInjector {
    private static String scriptInjectionFormat = "(function(){var parent = document.getElementsByTagName('head').item(0);var script = document.createElement('script');script.type = 'text/javascript';script.innerHTML = window.atob('%s');parent.appendChild(script);return true;})()";

    // ---------------------------------------------------
    // Public
    // ---------------------------------------------------

    /**
     * <p>Injects Javascript into the webView.</p>
     *
     * @param turbolinksSession The TurbolinksSession.
     * @param context           Any Android context.
     * @param webView           The shared webView.
     * @param filePath          Local file path relative to the main/src directory.
     */
    public static void injectJavascript(final TurbolinksSession turbolinksSession, Context context, WebView webView, String filePath) {
        try {
            String script = TurbolinksJavascriptInjector.getContentFromAssetFile(context, filePath);
            String jsCall = String.format(scriptInjectionFormat, script);

            webView.evaluateJavascript(jsCall, new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String s) {
                    if (turbolinksSession != null) {
                        turbolinksSession.turbolinksBridgeInjected = Boolean.parseBoolean(s);
                    }
                }
            });
        } catch (IOException e) {
            TurbolinksLog.e("Error injecting script file into webview: " + e.toString());
        }
    }

    /**
     * <p>Injects the Turbolinks bridge Javascript into the webView.</p>
     *
     * @param turbolinksSession The TurbolinksSession.
     * @param context           Any Android context.
     * @param webView           The shared webView.
     */
    public static void injectTurbolinksBridge(final TurbolinksSession turbolinksSession, Context context, WebView webView) {
        TurbolinksJavascriptInjector.injectJavascript(turbolinksSession, context, webView, "js/turbolinks_bridge.js");
    }

    // ---------------------------------------------------
    // Private
    // ---------------------------------------------------

    /**
     * <p>Gets the base64-encoded string of a local asset file (typically a Javascript or HTML file)</p>
     *
     * @param context  An activity context.
     * @param filePath Local file path relative to the main/src directory.
     * @return A base-64 encoded string of the file contents.
     * @throws IOException Typically if a file cannot be found or read in.
     */
    private static String getContentFromAssetFile(Context context, String filePath) throws IOException {
        InputStream inputStream = context.getAssets().open(filePath);
        byte[] buffer = new byte[inputStream.available()];
        inputStream.read(buffer);
        inputStream.close();
        return Base64.encodeToString(buffer, Base64.NO_WRAP);
    }
}
