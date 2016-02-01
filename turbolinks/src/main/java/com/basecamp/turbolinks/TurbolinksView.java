package com.basecamp.turbolinks;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;

public class TurbolinksView extends FrameLayout {
    private View progressView = null;

    // ---------------------------------------------------
    // Constructors
    // ---------------------------------------------------

    public TurbolinksView(Context context) {
        super(context);
    }

    public TurbolinksView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TurbolinksView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public TurbolinksView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    // ---------------------------------------------------
    // Package public
    // ---------------------------------------------------

    void showProgressView(final View progressView, final View progressBar, int delay) {
        removeProgressView();

        this.progressView = progressView;
        progressView.setClickable(true);
        addView(progressView);

        progressBar.setVisibility(View.GONE);

        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(View.VISIBLE);
            }
        }, delay);
    }

    void removeProgressView() {
        removeView(progressView);
    }

    void attachWebView(WebView webView) {
        ViewGroup parent = (ViewGroup) webView.getParent();
        if (parent != null) {
            parent.removeView(webView);
        }

        // Set the webview background to match the container background
        if (getBackground() instanceof ColorDrawable) {
            webView.setBackgroundColor(((ColorDrawable) getBackground()).getColor());
        }

        addView(webView, 0);
    }
}
