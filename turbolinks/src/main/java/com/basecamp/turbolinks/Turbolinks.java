package com.basecamp.turbolinks;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.MutableContextWrapper;
import android.graphics.Bitmap;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.Date;
import java.util.HashMap;

public class Turbolinks {
    static final String ACTION_ADVANCE = "advance";
    static final String ACTION_RESTORE = "restore";

    static volatile Turbolinks singleton = null;

    private final Context context;
    private final WebView webView;

    boolean turbolinksInjected; // Script injected into DOM
    private boolean turbolinksIsReady; // Script finished and TL fully instantiated
    private boolean restoreWithSnapshot;

    private int progressBarDelay;

    private String location;
    private String currentVisitIdentifier;
    private boolean coldBootInProgress;

    private long previousOverrideTime;

    private HashMap<String, String> restorationIdentifierMap;
    private HashMap<String, Object> javascriptInterfaces;

    private Activity attachedActivity;
    private View progressLayout;
    private View progressBar;
    private TurbolinksView turbolinksView;
    private TurbolinksAdapter turbolinksAdapter;

    // ---------------------------------------------------
    // Initialization
    // ---------------------------------------------------

    public static void initialize(Context context) {
        if (singleton != null) {
            throw new IllegalStateException("Turbolinks.initialize() has already been called.");
        }

        synchronized (Turbolinks.class) {
            if (singleton == null) {
                singleton = new Turbolinks(context);
                singleton.restorationIdentifierMap = new HashMap<>();
                singleton.javascriptInterfaces = new HashMap<>();
            }
        }

        TurbolinksLog.d("Turbolinks initialized");
    }

    // ---------------------------------------------------
    // Required chained methods
    // ---------------------------------------------------

    public static Turbolinks activity(Activity activity) {
        if (singleton == null) {
            throw new IllegalStateException("Turbolinks.initialize() has not been called.");
        }

        singleton.attachedActivity = activity;
        Context webViewContext = singleton.webView.getContext();
        if (webViewContext instanceof MutableContextWrapper) {
            ((MutableContextWrapper) webViewContext).setBaseContext(singleton.attachedActivity);
        }

        return singleton;
    }

    public Turbolinks view(TurbolinksView turbolinksView) {
        if (turbolinksView == null) {
            throw new IllegalArgumentException("TurbolinksView must not be null.");
        }

        singleton.turbolinksView = turbolinksView;
        singleton.turbolinksView.attachWebView(webView);

        return singleton;
    }

    public Turbolinks location(String location) {
        if (TextUtils.isEmpty(location)) {
            throw new IllegalArgumentException("Location must not be empty.");
        }

        singleton.location = location;

        return singleton;
    }

    // ---------------------------------------------------
    // Optional chained methods
    // ---------------------------------------------------

    public Turbolinks adapter(TurbolinksAdapter turbolinksAdapter) {
        singleton.turbolinksAdapter = turbolinksAdapter;
        return singleton;
    }

    public Turbolinks restoreWithSnapshot(boolean restoreWithSnapshot) {
        singleton.restoreWithSnapshot = restoreWithSnapshot;
        return singleton;
    }

    public Turbolinks progressLayout(View progressLayout) {
        singleton.progressLayout = progressLayout;

        return singleton;
    }

    public Turbolinks progressBar(int resId, int progressBarDelay) {
        singleton.progressBar = progressLayout.findViewById(resId);
        singleton.progressBarDelay = progressBarDelay;

        if (progressBar == null) {
            throw new IllegalArgumentException("Progress bar not found in progress layout");
        }

        return singleton;
    }

    // ---------------------------------------------------
    // Execute chained methods
    // ---------------------------------------------------

    public void visit() {
        if (singleton.progressLayout != null) { // Executed from here to account for progress bar delay
            turbolinksView.showProgressView(progressLayout, progressBar, progressBarDelay);
        }

        if (turbolinksIsReady) {
            String action = restoreWithSnapshot ? ACTION_RESTORE : ACTION_ADVANCE;
            runJavascript("webView.visitLocationWithActionAndRestorationIdentifier", TurbolinksHelper.encodeUrl(location), action, getRestorationIdentifierFromMap());
        }

        if (!turbolinksIsReady && !coldBootInProgress) {
            TurbolinksLog.d("Cold booting: " + location);
            webView.loadUrl(location);
        }

        // if (!turbolinksIsReady && coldBootInProgress), we don't fire a new visit. This is typically a slow connection load.
        // This allows the previous cold boot to finish (inject TL). No matter what, if new requests are sent to Turbolinks via
        // Turbolinks.location, we'll always have the last desired location. And when setTurbolinksIsReady(true) is called,
        // we open that last location.
    }

    // ---------------------------------------------------
    // TLNativeBridge adapter methods
    // ---------------------------------------------------

    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void visitProposedToLocationWithAction(String location, String action) {
        if (turbolinksAdapter != null) turbolinksAdapter.visitProposedToLocationWithAction(location, action);
    }

    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void visitStarted(String visitIdentifier, boolean visitHasSnapshot) {
        singleton.currentVisitIdentifier = visitIdentifier;

        runJavascript("webView.changeHistoryForVisitWithIdentifier", visitIdentifier);
        runJavascript("webView.issueRequestForVisitWithIdentifier", visitIdentifier);
        runJavascript("webView.restoreSnapshotForVisitWithIdentifier", visitIdentifier);
    }

    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void visitRequestCompleted(String visitIdentifier) {
        if (TextUtils.equals(visitIdentifier, currentVisitIdentifier)) {
            runJavascript("webView.loadResponseForVisitWithIdentifier", visitIdentifier);
        }
    }

    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void visitRequestFailedWithStatusCode(final String visitIdentifier, final int statusCode) {
        if (turbolinksAdapter != null && TextUtils.equals(visitIdentifier, currentVisitIdentifier)) {
            TurbolinksHelper.runOnMainThread(context, new Runnable() {
                @Override
                public void run() {
                    turbolinksAdapter.requestFailedWithStatusCode(statusCode);
                }
            });
        }
    }

    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void visitSnapshotRestored(String visitIdentifier) {
        TurbolinksLog.d("visitSnapshotRestored hiding progress view for identifier: " + visitIdentifier);
        hideProgressView(visitIdentifier);
    }

    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void visitResponseLoaded(String visitIdentifier) {
        TurbolinksLog.d("visitResponseLoaded hiding progress view for identifier: " + visitIdentifier);
        hideProgressView(visitIdentifier);
    }

    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void visitCompleted(String visitIdentifier, String restorationIdentifier) {
        addRestorationIdentifierToMap(restorationIdentifier);

        if (turbolinksAdapter != null && TextUtils.equals(visitIdentifier, currentVisitIdentifier)) {
            TurbolinksHelper.runOnMainThread(context, new Runnable() {
                @Override
                public void run() {
                    turbolinksAdapter.visitCompleted();
                }
            });
        }
    }

    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void pageInvalidated() {
        TurbolinksLog.d("Page invalidated");
        resetToColdBoot();

        TurbolinksHelper.runOnMainThread(context, new Runnable() {
            @Override
            public void run() { // route through normal chain so progress view is shown, regular logging, etc.
                if (turbolinksAdapter != null) turbolinksAdapter.pageInvalidated();

                singleton.visit();
            }
        });
    }

    // ---------------------------------------------------
    // TLNativeBridge helper methods
    // ---------------------------------------------------

    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void setTurbolinksIsReady(boolean turbolinksIsReady) {
        singleton.turbolinksIsReady = turbolinksIsReady;

        if (singleton.turbolinksIsReady) {
            TurbolinksHelper.runOnMainThread(context, new Runnable() {
                @Override
                public void run() {
                    TurbolinksLog.d("Turbolinks is ready, visit:" + location);
                    visit();
                }
            });

            coldBootInProgress = false;
        } else {
            TurbolinksLog.d("Turbolinks is not ready. Resetting and throw error.");
            resetToColdBoot();
            visitRequestFailedWithStatusCode(currentVisitIdentifier, 500);
        }
    }

    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void setFirstRestorationIdentifier(String firstRestorationIdentifier) {
        addRestorationIdentifierToMap(firstRestorationIdentifier);
    }

    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void hideProgressView(final String visitIdentifier) {
        TurbolinksHelper.runOnMainThread(context, new Runnable() {
            @Override
            public void run() {
                // pageInvalidated will cold boot, but another in-flight response from visitResponseLoaded could attempt
                // to hide the progress view. Checking turbolinksIsReady ensures progress view isn't hidden too soon by the non cold boot.
                if (turbolinksIsReady && TextUtils.equals(visitIdentifier, currentVisitIdentifier)) {
                    TurbolinksLog.d("Hiding progress view for visitIdentifier: " + visitIdentifier + ", currentVisitIdentifier: " + currentVisitIdentifier);
                    turbolinksView.hideProgressView();
                }
            }
        });
    }

    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void turbolinksDoesNotExist() {
        TurbolinksHelper.runOnMainThread(context, new Runnable() {
            @Override
            public void run() {
                TurbolinksLog.d("Error instantiating turbolinks.js - resetting to cold boot.");
                resetToColdBoot();
                turbolinksView.hideProgressView();
            }
        });
    }

    // -----------------------------------------------------------------------
    // Public accessors
    // -----------------------------------------------------------------------

    public static WebView getWebView() {
        return isInitialized() ? singleton.webView : null;
    }

    public static Activity getAttachedActivity() {
        return isInitialized() ? singleton.attachedActivity : null;
    }

    public static boolean isInitialized() {
        return singleton != null;
    }

    public static boolean turbolinksIsReady() {
        return isInitialized() && singleton.turbolinksIsReady;
    }

    public static void reset() {
        singleton = null;
    }

    public static void resetToColdBoot() {
        if (singleton != null) {
            singleton.turbolinksInjected = false;
            singleton.turbolinksIsReady = false;
            singleton.coldBootInProgress = false;
        }
    }

    public static void runJavascript(final String functionName, final Object... params) {
        TurbolinksHelper.runJavascript(singleton.context, singleton.webView, functionName, params);
    }

    public static void runRawJavascript(String javascript) {
        TurbolinksHelper.runRawJavascript(singleton.context, singleton.webView, javascript);
    }

    public static void visitLocationWithAction(String location, String action) {
        Turbolinks.runJavascript("webView.visitLocationWithActionAndRestorationIdentifier", TurbolinksHelper.encodeUrl(location), action, singleton.getRestorationIdentifierFromMap());
    }

    @SuppressLint("JavascriptInterface")
    public static void addJavascriptInterface(Object object, String name) {
        if (singleton.javascriptInterfaces.get(name) == null) {
            singleton.javascriptInterfaces.put(name, object);
            singleton.webView.addJavascriptInterface(object, name);

            TurbolinksLog.d("Adding JavascriptInterface: " + name + " for " + object.getClass().toString());
        }
    }

    public static void setDebugLoggingEnabled(boolean enabled) {
        TurbolinksLog.setDebugLoggingEnabled(enabled);
    }

    // ---------------------------------------------------
    // Private
    // ---------------------------------------------------

    private Turbolinks(final Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context must not be null.");
        }

        this.context = context;
        this.webView = TurbolinksHelper.createWebView(context);
        this.webView.addJavascriptInterface(this, "TLNativeBridge");
        this.webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                coldBootInProgress = true;
            }

            @Override
            public void onPageFinished(WebView view, String location) {
                if (!turbolinksInjected) {
                    TurbolinksHelper.injectTurbolinksBridge(singleton, context, webView);
                    if (turbolinksAdapter != null) turbolinksAdapter.onPageFinished();

                    TurbolinksLog.d("Page finished: " + location);
                }
            }

            /**
             * Turbolinks will not call adapter.visitProposedToLocationWithAction in some cases, like target=_blank
             or when the domain doesn't match. We still route those here. This is only called when links within a
             webView are clicked and not during loadUrl. So this is safely ignored for the first cold boot.
             http://stackoverflow.com/a/6739042/3280911
             */
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String location) {
                // Prevents firing twice in a row within a few milliseconds of each other, which happens sometimes. Great job webview!
                // So we check for a slight delay between requests, which is plenty of time to allow for a user to click
                // the same link again.
                long currentOverrideTime = new Date().getTime();
                if ((currentOverrideTime - previousOverrideTime) > 500) {
                    previousOverrideTime = currentOverrideTime;
                    TurbolinksLog.d("Overriding load: " + location);
                    singleton.visitProposedToLocationWithAction(location, ACTION_ADVANCE);
                }

                return true;
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                resetToColdBoot();

                if (turbolinksAdapter != null) turbolinksAdapter.onReceivedError(errorCode);
                TurbolinksLog.d("onReceivedError: " + errorCode);
            }

            @Override
            @TargetApi(Build.VERSION_CODES.M)
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                super.onReceivedHttpError(view, request, errorResponse);

                if (request.isForMainFrame()) {
                    resetToColdBoot();
                    if (turbolinksAdapter != null) turbolinksAdapter.onReceivedError(errorResponse.getStatusCode());
                    TurbolinksLog.d("onReceivedHttpError: " + errorResponse.getStatusCode());
                }
            }
        });
    }

    private void addRestorationIdentifierToMap(String value) {
        if (attachedActivity != null) {
            restorationIdentifierMap.put(attachedActivity.toString(), value);
        }
    }

    private String getRestorationIdentifierFromMap() {
        return restorationIdentifierMap.get(attachedActivity.toString());
    }
}
