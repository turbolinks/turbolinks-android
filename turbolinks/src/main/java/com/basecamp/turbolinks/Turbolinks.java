package com.basecamp.turbolinks;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.MutableContextWrapper;
import android.graphics.Bitmap;
import android.os.Build;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.Date;
import java.util.HashMap;

/**
 * <p>The main concrete class to use Turbolinks 5 in your app.</p>
 */
public class Turbolinks {

    // ---------------------------------------------------
    // Package public vars
    // ---------------------------------------------------
    boolean turbolinksBridgeInjected; // Script injected into DOM

    static volatile Turbolinks singleton = null;

    // ---------------------------------------------------
    // Final vars
    // ---------------------------------------------------

    private static final String ACTION_ADVANCE = "advance";
    private static final String ACTION_RESTORE = "restore";
    private static final int TURBOLINKS_PROGRESS_BAR_DELAY = 500;

    private final Context context;
    private final WebView webView;

    // ---------------------------------------------------
    // Private vars
    // ---------------------------------------------------

    private boolean coldBootInProgress;
    private boolean restoreWithCachedSnapshot;
    private boolean turbolinksIsReady; // Script finished and TL fully instantiated
    private int progressBarDelay;
    private long previousOverrideTime;
    private Activity activity;
    private HashMap<String, Object> javascriptInterfaces;
    private HashMap<String, String> restorationIdentifierMap;
    private String location;
    private String currentVisitIdentifier;
    private TurbolinksAdapter turbolinksAdapter;
    private TurbolinksView turbolinksView;
    private View progressView;
    private View progressBar;

    // ---------------------------------------------------
    // Constructor
    // ---------------------------------------------------

    /**
     * Private constructor called by {@link #initialize(Context)} to return a new Turbolinks instance
     * that can be used as the singleton throughout the app's in-memory session.
     * @param context A standard Android context.
     */
    private Turbolinks(final Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context must not be null.");
        }

        this.context = context;
        this.webView = TurbolinksHelper.createWebView(context);
        this.webView.addJavascriptInterface(this, "TurbolinksNative");
        this.webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                coldBootInProgress = true;
            }

            @Override
            public void onPageFinished(WebView view, String location) {
                if (!turbolinksBridgeInjected) {
                    TurbolinksHelper.injectTurbolinksBridge(singleton, context, webView);
                    turbolinksAdapter.onPageFinished();

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
                    visitProposedToLocationWithAction(location, ACTION_ADVANCE);
                }

                return true;
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                resetToColdBoot();

                turbolinksAdapter.onReceivedError(errorCode);
                TurbolinksLog.d("onReceivedError: " + errorCode);
            }

            @Override
            @TargetApi(Build.VERSION_CODES.M)
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                super.onReceivedHttpError(view, request, errorResponse);

                if (request.isForMainFrame()) {
                    resetToColdBoot();
                    turbolinksAdapter.onReceivedError(errorResponse.getStatusCode());
                    TurbolinksLog.d("onReceivedHttpError: " + errorResponse.getStatusCode());
                }
            }
        });
    }

    // ---------------------------------------------------
    // Initialization
    // ---------------------------------------------------

    /**
     * <p>Must be the first step in using Turbolinks that instantiates a new singleton object that will
     * be used throughout the lifetime of the app while in memory.</p>
     *
     * <p>You only need to initialize Turbolinks once, and you can check if it's already initialized
     * with {@link #isInitialized()}.</p>
     *
     * @param context
     */
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

    /**
     * <p><b>REQUIRED</b> Turbolinks requires an Activity context to be provided for clarity -- in other words, you cannot
     * use an ApplicationContext with Turbolinks.</p>
     *
     * <p>It's best to pass a new activity to Turbolinks for each new visit for clarity. This ensures there is
     * a one-to-one relationship maintained between internal activity IDs and visit IDs.</p>
     * @param activity An Android Activity, one per visit.
     * @return The Turbolinks singleton, to continue the chained calls.
     */
    public static Turbolinks activity(Activity activity) {
        singleton.activity = activity;

        Context webViewContext = singleton.webView.getContext();
        if (webViewContext instanceof MutableContextWrapper) {
            ((MutableContextWrapper) webViewContext).setBaseContext(singleton.activity);
        }

        return singleton;
    }

    /**
     * <p><b>REQUIRED</b> A {@link TurbolinksAdapter} implementation is required to that callbacks during the Turbolinks
     * event lifecycle can be passed back to your app.</p>
     * @param turbolinksAdapter Any class that ipmlements {@link TurbolinksAdapter}.
     * @return The Turbolinks singleton, to continue the chained calls.
     */
    public Turbolinks adapter(TurbolinksAdapter turbolinksAdapter) {
        singleton.turbolinksAdapter = turbolinksAdapter;
        return singleton;
    }

    /**
     * <p><b>REQUIRED</b> A {@link TurbolinksView} object that's been inflated in a custom layout is required so that
     * library can manage various view-related tasks: attaching/detaching the internal web view,
     * showing/hiding a progress loading view, etc.</p>
     * @param turbolinksView An inflated TurbolinksView from your custom layout.
     * @return The Turbolinks singleton, to continue the chained calls.
     */
    public Turbolinks view(TurbolinksView turbolinksView) {
        singleton.turbolinksView = turbolinksView;
        singleton.turbolinksView.attachWebView(singleton.webView);

        return singleton;
    }

    /**
     * <p><b>REQUIRED</b> The call that executes a Turbolinks visit. Must be called at the end of the chain.
     * All required parameters will first be validated before firing -- IllegalArgumentException will
     * be thrown if they aren't all provided.</p>
     * @param location The URL to visit.
     */
    public void visit(String location) {
        singleton.location = location;

        validateRequiredParams();
        initProgressView();

        if (singleton.turbolinksIsReady) {
            visitCurrentLocationWithTurbolinks();
        }

        if (!singleton.turbolinksIsReady && !singleton.coldBootInProgress) {
            TurbolinksLog.d("Cold booting: " + location);
            singleton.webView.loadUrl(location);
        }

        // if (!turbolinksIsReady && coldBootInProgress), we don't fire a new visit. This is typically a slow connection load.
        // This allows the previous cold boot to finish (inject TL). No matter what, if new requests are sent to Turbolinks via
        // Turbolinks.location, we'll always have the last desired location. And when setTurbolinksIsReady(true) is called,
        // we open that last location.
    }

    // ---------------------------------------------------
    // Optional chained methods
    // ---------------------------------------------------

    /**
     * <p><b>OPTIONAL</b> This will override the default progress view/progress bar that's provided
     * out of the box. This allows you to customize how you want the progress view to look while
     * pages are loading.</p>
     * @param progressView A custom progressView object.
     * @param progressBarResId The resource ID of a progressBar object inside the progressView.
     * @param progressBarDelay The delay, in milliseconds, before the progress bar should be displayed
     *                         inside the progress view (default is 500 ms).
     * @return The Turbolinks singleton, to continue the chained calls.
     */
    public Turbolinks progressView(View progressView, int progressBarResId, int progressBarDelay) {
        singleton.progressView = progressView;
        singleton.progressBar = progressView.findViewById(progressBarResId);
        singleton.progressBarDelay = progressBarDelay;

        if (singleton.progressBar == null) {
            throw new IllegalArgumentException("A progress bar view must be provided in your custom progressView.");
        }

        return singleton;
    }

    /**
     * <p><b>OPTIONAL</b> By default Turbolinks will "advance" to the next page and scroll position
     * will not be restored. Optionally calling this method allows you to set the behavior on a per-visit
     * basis.</p>
     *
     * <p>NOTE: The cache behavior is maintained between requests, so if you set this manually, you
     * should set it for every visit call.</p>
     * @param restoreWithCachedSnapshot If true, will restore scroll position. If false, will not restore
     *                                  scroll position.
     * @return The Turbolinks singleton, to continue the chained calls.
     */
    public Turbolinks restoreWithCachedSnapshot(boolean restoreWithCachedSnapshot) {
        singleton.restoreWithCachedSnapshot = restoreWithCachedSnapshot;
        return singleton;
    }

    // ---------------------------------------------------
    // TurbolinksNative adapter methods
    // ---------------------------------------------------

    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void visitProposedToLocationWithAction(String location, String action) {
        singleton.turbolinksAdapter.visitProposedToLocationWithAction(location, action);
    }

    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void visitStarted(String visitIdentifier, boolean visitHasCachedSnapshot) {
        singleton.currentVisitIdentifier = visitIdentifier;

        runJavascript("webView.changeHistoryForVisitWithIdentifier", visitIdentifier);
        runJavascript("webView.issueRequestForVisitWithIdentifier", visitIdentifier);
        runJavascript("webView.loadCachedSnapshotForVisitWithIdentifier", visitIdentifier);
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
        if (TextUtils.equals(visitIdentifier, currentVisitIdentifier)) {
            TurbolinksHelper.runOnMainThread(context, new Runnable() {
                @Override
                public void run() {
                    singleton.turbolinksAdapter.requestFailedWithStatusCode(statusCode);
                }
            });
        }
    }

    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void visitRendered(String visitIdentifier) {
        TurbolinksLog.d("visitRendered hiding progress view for identifier: " + visitIdentifier);
        hideProgressView(visitIdentifier);
    }

    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void visitCompleted(String visitIdentifier, String restorationIdentifier) {
        addRestorationIdentifierToMap(restorationIdentifier);

        if (TextUtils.equals(visitIdentifier, currentVisitIdentifier)) {
            TurbolinksHelper.runOnMainThread(context, new Runnable() {
                @Override
                public void run() {
                    singleton.turbolinksAdapter.visitCompleted();
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
                singleton.turbolinksAdapter.pageInvalidated();

                visit(singleton.location);
            }
        });
    }

    // ---------------------------------------------------
    // TurbolinksNative helper methods
    // ---------------------------------------------------

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
                    singleton.turbolinksView.removeProgressView();
                    singleton.progressView = null;
                }
            }
        });
    }

    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void setFirstRestorationIdentifier(String firstRestorationIdentifier) {
        addRestorationIdentifierToMap(firstRestorationIdentifier);
    }

    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void setTurbolinksIsReady(boolean turbolinksIsReady) {
        singleton.turbolinksIsReady = turbolinksIsReady;

        if (singleton.turbolinksIsReady) {
            TurbolinksHelper.runOnMainThread(context, new Runnable() {
                @Override
                public void run() {
                    TurbolinksLog.d("Turbolinks is ready");
                    visitCurrentLocationWithTurbolinks();
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
    public void turbolinksDoesNotExist() {
        TurbolinksHelper.runOnMainThread(context, new Runnable() {
            @Override
            public void run() {
                TurbolinksLog.d("Error instantiating turbolinks_bridge.js - resetting to cold boot.");
                resetToColdBoot();
                singleton.turbolinksView.removeProgressView();
            }
        });
    }

    // -----------------------------------------------------------------------
    // Public
    // -----------------------------------------------------------------------

    @SuppressLint("JavascriptInterface")
    public static void addJavascriptInterface(Object object, String name) {
        if (singleton.javascriptInterfaces.get(name) == null) {
            singleton.javascriptInterfaces.put(name, object);
            singleton.webView.addJavascriptInterface(object, name);

            TurbolinksLog.d("Adding JavascriptInterface: " + name + " for " + object.getClass().toString());
        }
    }

    public static Activity getActivity() {
        return isInitialized() ? singleton.activity : null;
    }

    public static WebView getWebView() {
        return isInitialized() ? singleton.webView : null;
    }

    public static boolean isInitialized() {
        return singleton != null;
    }

    public static void reset() {
        singleton = null;
    }

    public static void resetToColdBoot() {
        if (singleton != null) {
            singleton.turbolinksBridgeInjected = false;
            singleton.turbolinksIsReady = false;
            singleton.coldBootInProgress = false;
        }
    }

    public static void runJavascript(final String functionName, final Object... params) {
        TurbolinksHelper.runJavascript(singleton.context, singleton.webView, functionName, params);
    }

    public static void runJavascriptRaw(String rawJavascript) {
        TurbolinksHelper.runJavascriptRaw(singleton.context, singleton.webView, rawJavascript);
    }

    public static void setDebugLoggingEnabled(boolean enabled) {
        TurbolinksLog.setDebugLoggingEnabled(enabled);
    }

    public static boolean turbolinksIsReady() {
        return isInitialized() && singleton.turbolinksIsReady;
    }

    public static void visitLocationWithAction(String location, String action) {
        Turbolinks.runJavascript("webView.visitLocationWithActionAndRestorationIdentifier", TurbolinksHelper.encodeUrl(location), action, singleton.getRestorationIdentifierFromMap());
    }

    // ---------------------------------------------------
    // Private
    // ---------------------------------------------------

    private void addRestorationIdentifierToMap(String value) {
        if (singleton.activity != null) {
            singleton.restorationIdentifierMap.put(activity.toString(), value);
        }
    }

    private String getRestorationIdentifierFromMap() {
        return singleton.restorationIdentifierMap.get(activity.toString());
    }

    private void initProgressView() {
        // No custom progress view provided, use default
        if (singleton.progressView == null) {
            singleton.progressView = LayoutInflater.from(context).inflate(R.layout.turbolinks_progress, turbolinksView, false);
            singleton.progressView.setBackground(turbolinksView.getBackground());
            singleton.progressBar = singleton.progressView.findViewById(R.id.turbolinks_default_progress_bar);
            singleton.progressBarDelay = TURBOLINKS_PROGRESS_BAR_DELAY;
        }

        // A progress view can be reused, so ensure it's detached from its previous parent first
        if (singleton.progressView.getParent() != null) {
            ((ViewGroup) singleton.progressView.getParent()).removeView(singleton.progressView);
        }

        // Executed from here to account for progress bar delay
        singleton.turbolinksView.showProgressView(progressView, progressBar, progressBarDelay);
    }

    private void visitCurrentLocationWithTurbolinks() {
        TurbolinksLog.d("Visiting current stored location: " + singleton.location);

        String action = restoreWithCachedSnapshot ? ACTION_RESTORE : ACTION_ADVANCE;
        visitLocationWithAction(TurbolinksHelper.encodeUrl(location), action);
    }

    private void validateRequiredParams() {
        if (singleton == null) {
            throw new IllegalStateException("Turbolinks.initialize(context) must be called.");
        }

        if (singleton.activity == null) {
            throw new IllegalStateException("Turbolinks.activity(activity) must be called.");
        }

        if (singleton.turbolinksAdapter == null) {
            throw new IllegalArgumentException("Turbolinks.adapter(turbolinksAdapter) must be called.");
        }

        if (singleton.turbolinksView == null) {
            throw new IllegalArgumentException("Turbolinks.view(turbolinksView) must be called.");
        }

        if (TextUtils.isEmpty(singleton.location)) {
            throw new IllegalArgumentException("Turbolinks.visit(location) location value must not be null.");
        }
    }
}
