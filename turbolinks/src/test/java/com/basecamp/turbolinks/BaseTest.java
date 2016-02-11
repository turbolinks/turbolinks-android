package com.basecamp.turbolinks;

import android.content.Context;
import android.os.Build;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = TestBuildConfig.class, sdk = Build.VERSION_CODES.LOLLIPOP)
public class BaseTest {
    public Context context;

    @Before
    public void setup() {
        ShadowLog.stream = System.out;
        context = Robolectric.buildActivity(TurbolinksTestActivity.class).create().get();
    }

    @Test
    public void baseTest() {
        assertThat(1).isEqualTo(1);
    }
}
