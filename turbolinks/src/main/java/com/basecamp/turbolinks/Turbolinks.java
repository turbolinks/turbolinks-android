package com.basecamp.turbolinks;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.MutableContextWrapper;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
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
    // Package public vars (allows for greater flexibility and access for testing)
    // ---------------------------------------------------
    boolean turbolinksBridgeInjected; // Script injected into DOM

    static volatile Turbolinks singleton = null;

    boolean coldBootInProgress;
    boolean restoreWithCachedSnapshot;
    boolean turbolinksIsReady; // Script finished and TL fully instantiated
    int progressBarDelay;
    long previousOverrideTime;
    Activity activity;
    HashMap<String, Object> javascriptInterfaces;
    HashMap<String, String> restorationIdentifierMap;
    String location;
    String currentVisitIdentifier;
    TurbolinksAdapter turbolinksAdapter;
    TurbolinksView turbolinksView;
    View progressView;
    View progressBar;

    // ---------------------------------------------------
    // Final vars
    // ---------------------------------------------------

    static final String ACTION_ADVANCE = "advance";
    static final String ACTION_RESTORE = "restore";
    static final String JAVASCRIPT_INTERFACE_NAME = "TurbolinksNative";
    static final int PROGRESS_BAR_DELAY = 500;

    final Context context;
    final WebView webView;

    // ---------------------------------------------------
    // Constructor
    // ---------------------------------------------------

    /**
     * Private constructor called by {@link #initialize(Context)} to return a new Turbolinks instance
     * that can be used as the singleton throughout the app's in-memory session.
     *
     * @param context A standard Android context.
     */
    private Turbolinks(final Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context must not be null.");
        }

        this.context = context;
        this.webView = TurbolinksHelper.createWebView(context);
        this.webView.addJavascriptInterface(this, JAVASCRIPT_INTERFACE_NAME);
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
             * Turbolinks will not call adapter.visitProposedToLocationWithAction in some cases,
             * like target=_blank or when the domain doesn't match. We still route those here. This
             * is only called when links within a webView are clicked and not during loadUrl. So
             * this is safely ignored for the first cold boot.
             * http://stackoverflow.com/a/6739042/3280911
             */
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String location) {
                /**
                 * Prevents firing twice in a row within a few milliseconds of each other, which
                 * happens. So we check for a slight delay between requests, which is plenty of time
                 * to allow for a user to click the same link again.
                 */
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
     * @param context An activity context.
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
     * <p><b>REQUIRED</b> All chained calls to Turbolinks must start here with
     * Turbolinks.activity(activity).</p>
     *
     * <p>>Turbolinks requires an Activity context to be provided for clarity --
     * in other words, you cannot use an ApplicationContext with Turbolinks.</p>
     *
     * <p>It's best to pass a new activity to Turbolinks for each new visit for clarity. This ensures
     * there is a one-to-one relationship maintained between internal activity IDs and visit IDs.</p>
     *
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
     * <p><b>REQUIRED</b> A {@link TurbolinksAdapter} implementation is required to that callbacks
     * during the Turbolinks event lifecycle can be passed back to your app.</p>
     *
     * @param turbolinksAdapter Any class that implements {@link TurbolinksAdapter}.
     * @return The Turbolinks singleton, to continue the chained calls.
     */
    public Turbolinks adapter(Object turbolinksAdapter) {
        if (turbolinksAdapter instanceof TurbolinksAdapter) {
            singleton.turbolinksAdapter = (TurbolinksAdapter) turbolinksAdapter;
            return singleton;
        } else {
            throw new IllegalArgumentException("Class passed as adapter does not implement TurbolinksAdapter interface.");
        }
    }

    /**
     * <p><b>REQUIRED</b> A {@link TurbolinksView} object that's been inflated in a custom layout is
     * required so that library can manage various view-related tasks: attaching/detaching the
     * internal webView, showing/hiding a progress loading view, etc.</p>
     *
     * @param turbolinksView An inflated TurbolinksView from your custom layout.
     * @return The Turbolinks singleton, to continue the chained calls.
     */
    public Turbolinks view(TurbolinksView turbolinksView) {
        singleton.turbolinksView = turbolinksView;
        singleton.turbolinksView.attachWebView(singleton.webView);

        return singleton;
    }

    /**
     * <p><b>REQUIRED</b> The call that executes a Turbolinks visit. Must be called at the end of
     * the chain. All required parameters will first be validated before firing --
     * IllegalArgumentException will be thrown if they aren't all provided.</p>
     *
     * @param location The URL to visit.
     */
    public void visit(String location) {
        TurbolinksLog.d("visit called");

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

        // Reset so that cached snapshot is not the default for the next visit
        singleton.restoreWithCachedSnapshot = false;

        /**
         * if (!turbolinksIsReady && coldBootInProgress), we don't fire a new visit. This is
         * typically a slow connection load. This allows the previous cold boot to finish (inject TL).
         * No matter what, if new requests are sent to Turbolinks via Turbolinks.location, we'll
         * always have the last desired location. And when setTurbolinksIsReady(true) is called,
         * we open that last location.
         */
    }

    // ---------------------------------------------------
    // Optional chained methods
    // ---------------------------------------------------

    /**
     * <p><b>Optional</b> This will override the default progress view/progress bar that's provided
     * out of the box. This allows you to customize how you want the progress view to look while
     * pages are loading.</p>
     *
     * @param progressView     A custom progressView object.
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
     * <p><b>Optional</b> By default Turbolinks will "advance" to the next page and scroll position
     * will not be restored. Optionally calling this method allows you to set the behavior on a
     * per-visitbasis. This will be reset to "false" after each visit.</p>
     *
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

    /**
     * <p><b>JavascriptInterface only</b> Called by Turbolinks when a new visit is initiated from a
     * webView link.</p>
     *
     * <p>Warning: This method is public so it can be used as a Javascript Interface. you should
     * never call this directly as it could lead to unintended behavior.</p>
     *
     * @param location URL to be visited.
     * @param action   Whether to treat the request as an advance (navigating forward) or a replace (back).
     */
    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void visitProposedToLocationWithAction(String location, String action) {
        TurbolinksLog.d("visitProposedToLocationWithAction called");
        
        singleton.turbolinksAdapter.visitProposedToLocationWithAction(location, action);
    }

    /**
     * <p><b>JavascriptInterface only</b> Called by Turbolinks when a new visit has just started.</p>
     *
     * <p>Warning: This method is public so it can be used as a Javascript Interface. you should
     * never call this directly as it could lead to unintended behavior.</p>
     *
     * @param visitIdentifier        A unique identifier for the visit.
     * @param visitHasCachedSnapshot Whether the visit has a cached snapshot available.
     */
    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void visitStarted(String visitIdentifier, boolean visitHasCachedSnapshot) {
        TurbolinksLog.d("visitStarted called");

        singleton.currentVisitIdentifier = visitIdentifier;

        runJavascript("webView.changeHistoryForVisitWithIdentifier", visitIdentifier);
        runJavascript("webView.issueRequestForVisitWithIdentifier", visitIdentifier);
        runJavascript("webView.loadCachedSnapshotForVisitWithIdentifier", visitIdentifier);
    }

    /**
     * <p><b>JavascriptInterface only</b> Called by Turbolinks when the HTTP request has been
     * completed.</p>
     *
     * <p>Warning: This method is public so it can be used as a Javascript Interface. you should
     * never call this directly as it could lead to unintended behavior.</p>
     *
     * @param visitIdentifier A unique identifier for the visit.
     */
    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void visitRequestCompleted(String visitIdentifier) {
        TurbolinksLog.d("visitRequestCompleted called");

        if (TextUtils.equals(visitIdentifier, currentVisitIdentifier)) {
            runJavascript("webView.loadResponseForVisitWithIdentifier", visitIdentifier);
        }
    }

    /**
     * <p><b>JavascriptInterface only</b> Called by Turbolinks when the HTTP request has failed.</p>
     *
     * <p>Warning: This method is public so it can be used as a Javascript Interface. you should
     * never call this directly as it could lead to unintended behavior.</p>
     *
     * @param visitIdentifier A unique identifier for the visit.
     * @param statusCode      The HTTP status code that caused the failure.
     */
    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void visitRequestFailedWithStatusCode(final String visitIdentifier, final int statusCode) {
        TurbolinksLog.d("visitRequestFailedWithStatusCode called");

        if (TextUtils.equals(visitIdentifier, currentVisitIdentifier)) {
            TurbolinksHelper.runOnMainThread(context, new Runnable() {
                @Override
                public void run() {
                    singleton.turbolinksAdapter.requestFailedWithStatusCode(statusCode);
                }
            });
        }
    }

    /**
     * <p><b>JavascriptInterface only</b> Called by Turbolinks once the page has been fully rendered
     * in the webView.</p>
     *
     * <p>Warning: This method is public so it can be used as a Javascript Interface. you should
     * never call this directly as it could lead to unintended behavior.</p>
     *
     * @param visitIdentifier A unique identifier for the visit.
     */
    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void visitRendered(String visitIdentifier) {
        TurbolinksLog.d("visitRendered called, hiding progress view for identifier: " + visitIdentifier);
        hideProgressView(visitIdentifier);
    }

    /**
     * <p><b>JavascriptInterface only</b> Called by Turbolinks when the visit is fully completed --
     * request successful and page rendered.</p>
     *
     * <p>Warning: This method is public so it can be used as a Javascript Interface. you should
     * never call this directly as it could lead to unintended behavior.</p>
     *
     * @param visitIdentifier       A unique identifier for the visit.
     * @param restorationIdentifier A unique identifier for restoring the page and scroll position
     *                              from cache.
     */
    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void visitCompleted(String visitIdentifier, String restorationIdentifier) {
        TurbolinksLog.d("visitCompleted called");

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

    /**
     * <p><b>JavascriptInterface only</b> Called when Turbolinks detects that the page being visited
     * has been invalidated, typically by new resources in the the page HEAD.</p>
     *
     * <p>Warning: This method is public so it can be used as a Javascript Interface. you should
     * never call this directly as it could lead to unintended behavior.</p>
     */
    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void pageInvalidated() {
        TurbolinksLog.d("pageInvalidated called");

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

    /**
     * <p><b>JavascriptInterface only</b> Hides the progress view when the page is fully rendered.</p>
     *
     * <p>Note: This method is public so it can be used as a Javascript Interface. For all practical
     * purposes, you should never call this directly.</p>
     *
     * @param visitIdentifier A unique identifier for the visit.
     */
    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void hideProgressView(final String visitIdentifier) {
        TurbolinksHelper.runOnMainThread(context, new Runnable() {
            @Override
            public void run() {
                /**
                 * pageInvalidated will cold boot, but another in-flight response from
                 * visitResponseLoaded could attempt to hide the progress view. Checking
                 * turbolinksIsReady ensures progress view isn't hidden too soon by the non cold boot.
                 */
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

    /**
     * <p><b>JavascriptInterface only</b> Sets internal flags that indicate whether Turbolinks in
     * the webView is ready for use.</p>
     *
     * <p>Note: This method is public so it can be used as a Javascript Interface. For all practical
     * purposes, you should never call this directly.</p>
     *
     * @param turbolinksIsReady
     */
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

    /**
     * <p><b>JavascriptInterface only</b> Handles the error condition when reaching a page without
     * Turbolinks.</p>
     *
     * <p>Note: This method is public so it can be used as a Javascript Interface. For all practical
     * purposes, you should never call this directly.</p>
     */
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

    /**
     * <p>Provides the ability to add an arbitrary number of custom Javascript Interfaces to the built-in
     * Turbolinks webView.</p>
     *
     * @param object The object with annotated JavascriptInterface methods
     * @param name   The unique name for the interface (must not use the reserved name "TurbolinksNative")
     */
    @SuppressLint("JavascriptInterface")
    public static void addJavascriptInterface(Object object, String name) {
        if (TextUtils.equals(name, JAVASCRIPT_INTERFACE_NAME)) {
            throw new IllegalArgumentException(JAVASCRIPT_INTERFACE_NAME + " is a reserved Javascript Interface name.");
        }

        if (singleton.javascriptInterfaces.get(name) == null) {
            singleton.javascriptInterfaces.put(name, object);
            singleton.webView.addJavascriptInterface(object, name);

            TurbolinksLog.d("Adding JavascriptInterface: " + name + " for " + object.getClass().toString());
        }
    }

    /**
     * <p>Returns the activity attached to the Turbolinks call.</p>
     *
     * @return The attached activity, or null if Turbolinks is not initialized.
     */
    public static Activity getActivity() {
        return isInitialized() ? singleton.activity : null;
    }

    /**
     * <p>Returns the internal WebView used by Turbolinks.</p>
     *
     * @return The WebView used by Turbolinks, or null if Turbolinks is not initialized.
     */
    public static WebView getWebView() {
        return isInitialized() ? singleton.webView : null;
    }

    /**
     * <p>Whether or not the Turboolinks singleton is ready for use.</p>
     *
     * @return True if singleton != null, otherwise false.
     */
    public static boolean isInitialized() {
        return singleton != null;
    }

    /**
     * <p>Sets the singleton to null and Turbolinks into an uninitialized state.</p>
     */
    public static void reset() {
        singleton = null;
    }

    /**
     * <p>Resets the singleton to go through the full cold booting sequence (full page load) on the
     * next Turbolinks visit.</p>
     */
    public static void resetToColdBoot() {
        if (singleton != null) {
            singleton.turbolinksBridgeInjected = false;
            singleton.turbolinksIsReady = false;
            singleton.coldBootInProgress = false;
        }
    }

    /**
     * <p>Runs a Javascript function with any number of arbitrary params in the Turbolinks webView.</p>
     *
     * @param functionName The name of the function, without any parenthesis or params
     * @param params       A comma delimited list of params. Params will be automatically JSONified.
     */
    public static void runJavascript(final String functionName, final Object... params) {
        TurbolinksHelper.runJavascript(singleton.context, singleton.webView, functionName, params);
    }

    /**
     * <p>Runs raw Javascript in webView. Simply wraps the loadUrl("javascript: methodName()") call.</p>
     *
     * @param rawJavascript
     */
    public static void runJavascriptRaw(String rawJavascript) {
        TurbolinksHelper.runJavascriptRaw(singleton.context, singleton.webView, rawJavascript);
    }

    /**
     * <p>Tells the logger whether to allow logging in debug mode.</p>
     *
     * @param enabled If true, debug logging is enabled.
     */
    public static void setDebugLoggingEnabled(boolean enabled) {
        TurbolinksLog.setDebugLoggingEnabled(enabled);
    }

    /**
     * <p>Provides the status of whether Turbolinks is initialized and ready for use.</p>
     *
     * @return True if Turbolinks is both initialized and ready for use.
     */
    public static boolean turbolinksIsReady() {
        return isInitialized() && singleton.turbolinksIsReady;
    }

    /**
     * <p>A convenience method to fire a Turbolinks visit manually.</p>
     *
     * @param location URL to visit.
     * @param action   Whether to treat the request as an advance (navigating forward) or a replace (back).
     */
    public static void visitLocationWithAction(String location, String action) {
        Turbolinks.runJavascript("webView.visitLocationWithActionAndRestorationIdentifier", TurbolinksHelper.encodeUrl(location), action, singleton.getRestorationIdentifierFromMap());
    }

    // ---------------------------------------------------
    // Private
    // ---------------------------------------------------

    /**
     * <p>Adds the restoration (cached scroll position) identifier to the local Hashmap.</p>
     *
     * @param value Restoration ID provided by Turbolinks.
     */
    private void addRestorationIdentifierToMap(String value) {
        if (singleton.activity != null) {
            singleton.restorationIdentifierMap.put(activity.toString(), value);
        }
    }

    /**
     * <p>Gets the restoration ID for the current activity.</p>
     *
     * @return Restoration ID for the current activity.
     */
    private String getRestorationIdentifierFromMap() {
        return singleton.restorationIdentifierMap.get(activity.toString());
    }

    /**
     * <p>Shows the progress view, either a custom one provided or the default.</p>
     *
     * <p>A default progress view is inflated if {@link #progressView} isn't called.
     * If already inflated, progress view is fully detached before being shown since it's reused.</p>
     */
    private void initProgressView() {
        // No custom progress view provided, use default
        if (singleton.progressView == null) {
            singleton.progressView = LayoutInflater.from(context).inflate(R.layout.turbolinks_progress, turbolinksView, false);

            TurbolinksLog.d("Turbolinks background: " + turbolinksView.getBackground());
            singleton.progressView.setBackground(turbolinksView.getBackground());
            singleton.progressBar = singleton.progressView.findViewById(R.id.turbolinks_default_progress_bar);
            singleton.progressBarDelay = PROGRESS_BAR_DELAY;

            Drawable background = turbolinksView.getBackground() != null ? turbolinksView.getBackground() : new ColorDrawable(context.getResources().getColor(android.R.color.white));
            singleton.progressView.setBackground(background);
        }

        // A progress view can be reused, so ensure it's detached from its previous parent first
        if (singleton.progressView.getParent() != null) {
            ((ViewGroup) singleton.progressView.getParent()).removeView(singleton.progressView);
        }

        // Executed from here to account for progress bar delay
        singleton.turbolinksView.showProgressView(progressView, progressBar, progressBarDelay);
    }

    /**
     * <p>Convenience method to simply revisit the current location in the singleton. Useful so that
     * different visit logic can be wrappered around this call in {@link #visit} or
     * {@link #setTurbolinksIsReady(boolean)}</p>
     */
    private void visitCurrentLocationWithTurbolinks() {
        TurbolinksLog.d("Visiting current stored location: " + singleton.location);

        String action = restoreWithCachedSnapshot ? ACTION_RESTORE : ACTION_ADVANCE;
        visitLocationWithAction(TurbolinksHelper.encodeUrl(location), action);
    }

    /**
     * <p>Ensures all required chained calls/parameters ({@link #initialize(Context)}, {@link #activity},
     * {@link #adapter(Object)}, {@link #turbolinksView}, and location}) are
     * set before calling {@link #visit(String)}.</p>
     */
    private void validateRequiredParams() {
        if (singleton == null) {
            throw new IllegalStateException("Turbolinks.initialize(context) must be called.");
        }

        if (singleton.activity == null) {
            throw new IllegalArgumentException("Turbolinks.activity(activity) must be called with a non-null object.");
        }

        if (singleton.turbolinksAdapter == null) {
            throw new IllegalArgumentException("Turbolinks.adapter(turbolinksAdapter) must be called with a non-null object.");
        }

        if (singleton.turbolinksView == null) {
            throw new IllegalArgumentException("Turbolinks.view(turbolinksView) must be called with a non-null object.");
        }

        if (TextUtils.isEmpty(singleton.location)) {
            throw new IllegalArgumentException("Turbolinks.visit(location) location value must not be null.");
        }
    }
}
