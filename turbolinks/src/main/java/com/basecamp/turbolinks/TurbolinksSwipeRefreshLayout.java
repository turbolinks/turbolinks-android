package com.basecamp.turbolinks;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.AttributeSet;

/**
 * <p>Custom SwipeRefreshLayout for Turbolinks.</p>
 */
public class TurbolinksSwipeRefreshLayout extends SwipeRefreshLayout {
    private CanScrollUpCallback callback;

    /**
     * <p>Constructor to match SwipeRefreshLayout</p>
     *
     * @param context Refer to SwipeRefreshLayout
     */
    public TurbolinksSwipeRefreshLayout(@NonNull Context context) {
        super(context);
    }

    /**
     * <p>Constructor to match SwipeRefreshLayout</p>
     *
     * @param context Refer to SwipeRefreshLayout
     * @param attrs Refer to SwipeRefreshLayout
     */
    public TurbolinksSwipeRefreshLayout(@NonNull Context context, @NonNull AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * <p>Overridden from SwipeRefreshLayout. Uses a custom callback.</p>
     * <p>If the custom callback is null, it falls back to the parent canChildScrollUp()</p>
     *
     * @return True if the child view can scroll up. False otherwise.
     */
    @Override
    public boolean canChildScrollUp() {
        if (callback != null) { return callback.canChildScrollUp(); }
        return super.canChildScrollUp();
    }

    /**
     * <p>Sets the callback to be used in canChildScrollUp().</p>
     * <p>See canChildScrollUp() to see how it's used.</p>
     *
     * @param callback The custom callback to be set
     */
    public void setCallback(@NonNull CanScrollUpCallback callback) { this.callback = callback; }
}
