package com.basecamp.turbolinks;

import android.widget.FrameLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = TestBuildConfig.class)
public class TurbolinksTest extends BaseTest {
    @Mock TurbolinksTestActivity activity;
    @Mock TurbolinksAdapter adapter;
    @Mock TurbolinksView view;
    @Mock FrameLayout progressView;

    private static final String LOCATION = "https://basecamp.com";
    private static final String VISIT_IDENTIFIER = "visitIdentifierValue";
    private static final String RESTORATION_IDENTIFIER = "restorationIdentifierValue";

    @Before
    public void setup() {
        super.setup();
        MockitoAnnotations.initMocks(this);

        Turbolinks.reset();
    }

    // -----------------------------------------------------------------------
    // Visit parameters
    // -----------------------------------------------------------------------

    @Test(expected = IllegalStateException.class)
    public void initializedMultipleTimes() {
        Turbolinks.initialize(context);
        Turbolinks.initialize(context);
    }

    @Test(expected = IllegalArgumentException.class)
    public void activityIsNull() {
        Turbolinks.initialize(context);
        Turbolinks.activity(null)
            .adapter(adapter)
            .view(view)
            .visit(LOCATION);
    }

    @Test(expected = IllegalArgumentException.class)
    public void adapterNotProvided() {
        Turbolinks.initialize(context);
        Turbolinks.activity(activity)
            .view(view)
            .visit(LOCATION);
    }

    @Test(expected = IllegalArgumentException.class)
    public void adapterInterfaceNotImplemented() {
        Turbolinks.initialize(context);
        Turbolinks.activity(activity)
            .adapter(new Object())
            .view(view)
            .visit(LOCATION);
    }

    @Test(expected = IllegalArgumentException.class)
    public void progressViewWithInvalidProgressBar() {
        Turbolinks.initialize(context);
        Turbolinks.activity(activity)
            .adapter(adapter)
            .progressView(progressView, 123, 0)
            .view(view)
            .visit(LOCATION);
    }

    @Test(expected = IllegalArgumentException.class)
    public void viewNotProvided() {
        Turbolinks.initialize(context);
        Turbolinks.activity(activity)
            .adapter(adapter)
            .visit(LOCATION);
    }

    @Test(expected = IllegalArgumentException.class)
    public void visitWithoutLocation() {
        Turbolinks.initialize(context);
        Turbolinks.activity(activity)
            .adapter(adapter)
            .view(view)
            .visit("");
    }

    // -----------------------------------------------------------------------
    // Adapter
    // -----------------------------------------------------------------------

    @Test
    public void visitProposedToLocationWithActionCallsAdapter() {
        Turbolinks.initialize(context);
        Turbolinks.activity(activity)
            .adapter(adapter);
        Turbolinks.singleton.visitProposedToLocationWithAction(LOCATION, Turbolinks.ACTION_ADVANCE);

        verify(adapter).visitProposedToLocationWithAction(any(String.class), any(String.class));
    }

    @Test
    public void visitStartedSavesCurrentVisitIdentifier() {
        Turbolinks.initialize(context);
        Turbolinks.singleton.currentVisitIdentifier = null;

        assertThat(Turbolinks.singleton.currentVisitIdentifier).isNotEqualTo(VISIT_IDENTIFIER);

        Turbolinks.activity(activity)
            .adapter(adapter);
        Turbolinks.singleton.visitStarted(VISIT_IDENTIFIER, true);

        assertThat(Turbolinks.singleton.currentVisitIdentifier).isEqualTo(VISIT_IDENTIFIER);
    }

    @Test
    public void visitRequestFailedWithStatusCodeCallsAdapter() {
        Turbolinks.initialize(context);
        Turbolinks.activity(activity)
            .adapter(adapter);
        Turbolinks.singleton.currentVisitIdentifier = VISIT_IDENTIFIER;
        Turbolinks.singleton.visitRequestFailedWithStatusCode(VISIT_IDENTIFIER, 0);

        verify(adapter).requestFailedWithStatusCode(any(int.class));
    }

    @Test
    public void visitCompletedCallsAdapter() {
        Turbolinks.initialize(context);
        Turbolinks.activity(activity)
            .adapter(adapter);
        Turbolinks.singleton.currentVisitIdentifier = VISIT_IDENTIFIER;
        Turbolinks.singleton.visitCompleted(VISIT_IDENTIFIER, RESTORATION_IDENTIFIER);

        verify(adapter).visitCompleted();
    }

    @Test
    public void visitCompletedSavesRestorationIdentifier() {
        Turbolinks.initialize(context);

        assertThat(Turbolinks.singleton.restorationIdentifierMap.size()).isEqualTo(0);

        Turbolinks.activity(activity)
            .adapter(adapter);
        Turbolinks.singleton.visitCompleted(VISIT_IDENTIFIER, RESTORATION_IDENTIFIER);

        assertThat(Turbolinks.singleton.restorationIdentifierMap.size()).isEqualTo(1);
    }


    // -----------------------------------------------------------------------
    // Public
    // -----------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void addJavascriptInterfaceWithReservedName() {
        Turbolinks.initialize(context);
        Turbolinks.addJavascriptInterface(new Object(), Turbolinks.JAVASCRIPT_INTERFACE_NAME);
    }

    @Test
    public void addJavascriptInterface() {
        Turbolinks.initialize(context);

        assertThat(Turbolinks.singleton.javascriptInterfaces.size()).isEqualTo(0);
        Turbolinks.addJavascriptInterface(new Object(), "TestJavascriptInterface");
        assertThat(Turbolinks.singleton.javascriptInterfaces.size()).isEqualTo(1);
    }
}
