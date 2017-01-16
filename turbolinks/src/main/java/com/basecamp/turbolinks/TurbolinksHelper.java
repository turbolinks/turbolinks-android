package com.basecamp.turbolinks;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.MutableContextWrapper;
import android.os.Handler;
import android.util.Base64;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

import android.widget.FrameLayout;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;

class TurbolinksHelper {
    private static String scriptInjectionFormat = "(function(){var parent = document.getElementsByTagName('head').item(0);var script = document.createElement('script');script.type = 'text/javascript';script.innerHTML = window.atob('%s');parent.appendChild(script);return true;})()";

    // ---------------------------------------------------
    // Package public
    // ---------------------------------------------------

    /**
     * <p>Creates the shared webView used throughout the lifetime of the TurbolinksSession.</p>
     *
     * @param applicationContext An application context.
     * @return The shared WebView.
     */
    static WebView createWebView(Context applicationContext) {
        MutableContextWrapper contextWrapper = new MutableContextWrapper(applicationContext);
        WebView webView = new WebView(contextWrapper);
        configureWebViewDefaults(webView);
        setWebViewLayoutParams(webView);

        return webView;
    }

    /**
     * <p>Encodes URLs so they are properly escaped, specifically for Javascript calls. Liberally
     * borrowed from: http://stackoverflow.com/questions/3286067/url-encoding-in-android/8962879#8962879</p>
     *
     * @param originalUrl Original URL string.
     * @return Encoded, escaped URL string.
     */
    static String encodeUrl(String originalUrl) {
        try {
            URL newUrl = new URL(originalUrl);
            URI uri = new URI(newUrl.getProtocol(), newUrl.getUserInfo(), newUrl.getHost(), newUrl.getPort(), newUrl.getPath(), newUrl.getQuery(), newUrl.getRef());
            return uri.toURL().toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * <p>Gets the base64-encoded string of a local asset file (typically a Javascript or HTML file)</p>
     *
     * @param context  An activity context.
     * @param filePath Local file path relative to the main/src directory.
     * @return A base-64 encoded string of the file contents.
     * @throws IOException Typically if a file cannot be found or read in.
     */
    static String getContentFromAssetFile(Context context, String filePath) throws IOException {
        InputStream inputStream = context.getAssets().open(filePath);
        byte[] buffer = new byte[inputStream.available()];
        inputStream.read(buffer);
        inputStream.close();
        return Base64.encodeToString(buffer, Base64.NO_WRAP);
    }

    /**
     * <p>Injects Javascript into the webView.</p>
     *
     * @param turbolinksSession The TurbolinksSession.
     * @param context           Any Android context.
     * @param webView           The shared webView.
     */
    static void injectTurbolinksBridge(final TurbolinksSession turbolinksSession, Context context, WebView webView) {
        try {
            String jsCall = String.format(scriptInjectionFormat, TurbolinksHelper.getContentFromAssetFile(context, "js/turbolinks_bridge.js"));
            runJavascriptRaw(context, webView, jsCall);
        } catch (IOException e) {
            TurbolinksLog.e("Error injecting script file into webview: " + e.toString());
        }
    }

    /**
     * <p>JSONifies any arbitrary number of params and runs the the Javascript function in the
     * webView.</p>
     *
     * @param context      An activity context.
     * @param webView      The shared webView.
     * @param functionName The Javascript function name only (no parenthesis or parameters).
     * @param params       A comma delimited list of parameter values.
     */
    static void runJavascript(Context context, final WebView webView, String functionName, Object... params) {
        final String fullJs;

        if (params != null) {
            Gson gson = new GsonBuilder().disableHtmlEscaping().create();
            for (int i = 0; i < params.length; i++) {
                params[i] = gson.toJson(params[i]);
            }

            fullJs = String.format("javascript: %s(%s);", functionName, StringUtils.join(params, ","));
        } else {
            fullJs = String.format("javascript: %s();", functionName);
        }

        runOnMainThread(context, new Runnable() {
            @Override
            public void run() {
                webView.loadUrl(fullJs);
            }
        });
    }

    /**
     * <p>Runs raw Javascript that's passed in. You are responsible for encoding/escaping the
     * function call.</p>
     *
     * @param context    An activity context.
     * @param webView    The shared webView.
     * @param javascript The raw Javascript to be executed, fully escaped/encoded in advance.
     */
    static void runJavascriptRaw(Context context, final WebView webView, final String javascript) {
        runOnMainThread(context, new Runnable() {
            @Override
            public void run() {
                webView.loadUrl("javascript:" + javascript);
            }
        });
    }

    /**
     * <p>Executes a given runnable on the main thread.</p>
     *
     * @param context  An activity context.
     * @param runnable A runnable to execute on the main thread.
     */
    static void runOnMainThread(Context context, Runnable runnable) {
        Handler handler = new Handler(context.getMainLooper());
        handler.post(runnable);
    }

    // ---------------------------------------------------
    // Private
    // ---------------------------------------------------

    /**
     * <p>Configures basic settings of the webView (Javascript enabled, DOM storage enabled,
     * database enabled).</p>
     *
     * @param webView The shared webView.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private static void configureWebViewDefaults(WebView webView) {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        webView.setWebChromeClient(new WebChromeClient());
    }

    /**
     * Sets the WebView's width/height layout params to MATCH_PARENT
     *
     * @param webView The shared webView.
     */
    private static void setWebViewLayoutParams(WebView webView) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        webView.setLayoutParams(params);
    }
}
