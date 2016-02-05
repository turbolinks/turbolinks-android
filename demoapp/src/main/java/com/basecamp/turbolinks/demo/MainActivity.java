package com.basecamp.turbolinks.demo;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.basecamp.turbolinks.Turbolinks;
import com.basecamp.turbolinks.TurbolinksAdapter;
import com.basecamp.turbolinks.TurbolinksView;

public class MainActivity extends AppCompatActivity implements TurbolinksAdapter {
    private static final String BASE_URL = "http://server.10.0.1.100.xip.io/";
    private static final String INTENT_URL = "intentUrl";

    private String location;
    private TurbolinksView turbolinksView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        turbolinksView = (TurbolinksView) findViewById(R.id.turbolinks_view);

        if (!Turbolinks.isInitialized()) {
            Turbolinks.initialize(this);
        }

        Turbolinks.setDebugLoggingEnabled(true);

        location = getIntent().getStringExtra(INTENT_URL) != null ? getIntent().getStringExtra(INTENT_URL) : BASE_URL;

        Turbolinks.activity(this)
            .adapter(this)
            .view(turbolinksView)
            .restoreWithCachedSnapshot(false)
            .visit(location);
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        Turbolinks.activity(this)
            .adapter(this)
            .restoreWithCachedSnapshot(true)
            .view(turbolinksView)
            .visit(location);
    }

    @Override
    public void onPageFinished() {

    }

    @Override
    public void onReceivedError(int errorCode) {

    }

    @Override
    public void pageInvalidated() {

    }

    @Override
    public void requestFailedWithStatusCode(int statusCode) {
        Turbolinks.activity(this)
            .adapter(this)
            .restoreWithCachedSnapshot(false)
            .view(turbolinksView)
            .visit(BASE_URL + "/error");
    }

    @Override
    public void visitCompleted() {

    }

    @Override
    public void visitProposedToLocationWithAction(String location, String action) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(INTENT_URL, location);

        this.startActivity(intent);
    }
}
