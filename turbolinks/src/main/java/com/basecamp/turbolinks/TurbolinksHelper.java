package com.basecamp.turbolinks;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.MutableContextWrapper;
import android.os.Handler;
import android.util.Base64;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;

public class TurbolinksHelper {
    private static String scriptInjectionFormat = "(function(){var parent = document.getElementsByTagName('head').item(0);var script = document.createElement('script');script.type = 'text/javascript';script.innerHTML = window.atob('%s');parent.appendChild(script);return true;})()";

    static WebView createWebView(Context context) {
        MutableContextWrapper contextWrapper = new MutableContextWrapper(context.getApplicationContext());
        WebView webView = new WebView(contextWrapper);
        configureWebViewDefaults(webView);

        return webView;
    }

    static void injectTurbolinksBridge(final Turbolinks singleton, Context context, WebView webView) {
        try {
            String script = TurbolinksHelper.getContentFromAssetFile(context, "js/turbolinks.js");
            String jsCall = String.format(scriptInjectionFormat, script);

            webView.evaluateJavascript(jsCall, new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String s) {
                    if (singleton != null) {
                        singleton.turbolinksInjected = Boolean.parseBoolean(s);
                    }
                }
            });
        } catch (IOException e) {
            TurbolinksLog.e("Error injecting script file into webview: " + e.toString());
        }
    }

    static void runRawJavascript(Context context, final WebView webView, final String javascript) {
        runOnMainThread(context, new Runnable() {
            @Override
            public void run() {
                webView.loadUrl("javascript:" + javascript);
            }
        });
    }

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

    static String getContentFromAssetFile(Context context, String filePath) throws IOException {
        InputStream inputStream = context.getAssets().open(filePath);
        byte[] buffer = new byte[inputStream.available()];
        inputStream.read(buffer);
        inputStream.close();
        return Base64.encodeToString(buffer, Base64.NO_WRAP);
    }

    // http://stackoverflow.com/questions/3286067/url-encoding-in-android/8962879#8962879
    static String encodeUrl(String originalUrl) {
        try {
            URL newUrl = new URL(originalUrl);
            URI uri = new URI(newUrl.getProtocol(), newUrl.getUserInfo(), newUrl.getHost(), newUrl.getPort(), newUrl.getPath(), newUrl.getQuery(), newUrl.getRef());
            return uri.toURL().toString();
        } catch (Exception e) {
            return null;
        }
    }

    static void runOnMainThread(Context context, Runnable runnable) {
        Handler handler = new Handler(context.getMainLooper());
        handler.post(runnable);
    }

    // ---------------------------------------------------
    // Private
    // ---------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled")
    private static void configureWebViewDefaults(WebView webView) {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        webView.setWebChromeClient(new WebChromeClient());
    }
}
