package com.basecamp.android.turbolinks;

public interface TurbolinksAdapter {
    void requestFailedWithStatusCode(int statusCode);
    void onPageFinished();
    void onReceivedError(int errorCode);
    void visitCompleted();
    void visitProposedToLocationWithAction(String location, String action);
    void pageInvalidated();
}
