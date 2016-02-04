package com.basecamp.turbolinks.test;

import android.app.Activity;

import com.basecamp.turbolinks.Turbolinks;
import com.basecamp.turbolinks.TurbolinksTestActivity;
import com.basecamp.turbolinks.TurbolinksView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = TestBuildConfig.class)
public class TurbolinksTest extends BaseTest {
    @Before
    public void setup() {
        super.setup();
        Turbolinks.reset();
    }

    @Test(expected = IllegalStateException.class)
    public void multipleInitializationsThrowsException() {
        Turbolinks.initialize(context);
        Turbolinks.initialize(context);
    }

    @Test(expected = IllegalArgumentException.class)
    public void noAdapterProvidedInVisit() {
        Turbolinks.initialize(context);
        TurbolinksView view = new TurbolinksView(context);
        Activity activity = new TurbolinksTestActivity();

        Turbolinks.activity(activity)
            .view(view)
            .visit("http://basecamp.com");
    }

    @Test(expected = IllegalArgumentException.class)
    public void adapterDoesNotImplementInterface() {
        Turbolinks.initialize(context);
        TurbolinksView view = new TurbolinksView(context);
        Activity activity = new TurbolinksTestActivity();
        Object object = new Object();

        Turbolinks.activity(activity)
            .adapter(object)
            .view(view)
            .visit("http://basecamp.com");
    }
}
