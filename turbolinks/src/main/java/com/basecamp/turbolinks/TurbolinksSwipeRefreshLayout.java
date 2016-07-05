package com.basecamp.turbolinks;

import android.content.Context;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.AttributeSet;

public class TurbolinksSwipeRefreshLayout extends SwipeRefreshLayout {
    private CanScrollUpCallback callback;

    public TurbolinksSwipeRefreshLayout(Context context) {
        super(context);
    }

    public TurbolinksSwipeRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean canChildScrollUp() {
        if (callback != null) {
            return callback.canChildScrollUp();
        }
        return super.canChildScrollUp();
    }

    public void setCallback(CanScrollUpCallback callback) { this.callback = callback; }

}
