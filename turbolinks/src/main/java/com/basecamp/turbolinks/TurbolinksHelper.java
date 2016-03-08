package com.basecamp.turbolinks;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.MutableContextWrapper;
import android.os.Handler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.net.URL;

class TurbolinksHelper {
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
}
