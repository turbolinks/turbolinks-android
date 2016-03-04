package com.basecamp.turbolinks;

import android.app.Activity;
import android.widget.FrameLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Matchers.any;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = TestBuildConfig.class)
public class TurbolinkSessionTest extends BaseTest {
    @Mock Activity activity;
    @Mock TurbolinksAdapter adapter;
    @Mock TurbolinksView view;
    @Mock FrameLayout progressView;

    private static final String LOCATION = "https://basecamp.com";
    private static final String VISIT_IDENTIFIER = "visitIdentifierValue";
    private static final String RESTORATION_IDENTIFIER = "restorationIdentifierValue";

    private TurbolinksSession turbolinksSession;

    @Before
    public void setup() {
        super.setup();
        MockitoAnnotations.initMocks(this);

        turbolinksSession = TurbolinksSession.getNew(context);
    }

    // -----------------------------------------------------------------------
    // Initializing
    // -----------------------------------------------------------------------

    @Test
    public void getNewIsAlwaysNewInstance() {
        TurbolinksSession defaultInstance = TurbolinksSession.getNew(context);

        assertThat(defaultInstance).isNotEqualTo(TurbolinksSession.getNew(context));
    }

    @Test
    public void getDefaultReturnsSameInstance() {
        TurbolinksSession defaultInstance = TurbolinksSession.getDefault(context);

        assertThat(defaultInstance).isEqualTo(TurbolinksSession.getDefault(context));
    }

    @Test
    public void resetDefaultGetsNewDefaultInstance() {
        TurbolinksSession defaultInstance = TurbolinksSession.getDefault(context);
        TurbolinksSession.resetDefault();

        assertThat(defaultInstance).isNotEqualTo(TurbolinksSession.getDefault(context));
    }

    // -----------------------------------------------------------------------
    // Visit parameters
    // -----------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void activityIsNull() {
        turbolinksSession.activity(null)
            .adapter(adapter)
            .view(view)
            .visit(LOCATION);
    }

    @Test(expected = IllegalArgumentException.class)
    public void adapterNotProvided() {
        turbolinksSession.activity(activity)
            .view(view)
            .visit(LOCATION);
    }

    @Test(expected = IllegalArgumentException.class)
    public void progressViewWithInvalidProgressIndicator() {
        turbolinksSession.activity(activity)
            .adapter(adapter)
            .progressView(progressView, 123, 0)
            .view(view)
            .visit(LOCATION);
    }

    @Test(expected = IllegalArgumentException.class)
    public void viewNotProvided() {
        turbolinksSession.activity(activity)
            .adapter(adapter)
            .visit(LOCATION);
    }

    @Test(expected = IllegalArgumentException.class)
    public void visitWithoutLocation() {
        turbolinksSession.activity(activity)
            .adapter(adapter)
            .view(view)
            .visit("");
    }

    // -----------------------------------------------------------------------
    // Adapter
    // -----------------------------------------------------------------------

    @Test
    public void visitProposedToLocationWithActionCallsAdapter() {
        turbolinksSession.activity(activity)
            .adapter(adapter);
        turbolinksSession.visitProposedToLocationWithAction(LOCATION, TurbolinksSession.ACTION_ADVANCE, "<a href='/' />");

        verify(adapter).visitProposedToLocationWithAction(any(String.class), any(String.class), any(String.class));
    }

    @Test
    public void visitStartedSavesCurrentVisitIdentifier() {
        // Mock doesn't seem to work for running on the main thread
        TurbolinksTestActivity activity = new TurbolinksTestActivity();

        turbolinksSession.currentVisitIdentifier = null;

        assertThat(turbolinksSession.currentVisitIdentifier).isNotEqualTo(VISIT_IDENTIFIER);

        turbolinksSession.activity(activity)
            .adapter(adapter);
        turbolinksSession.visitStarted(VISIT_IDENTIFIER, true);

        assertThat(turbolinksSession.currentVisitIdentifier).isEqualTo(VISIT_IDENTIFIER);
    }

    @Test
    public void visitRequestFailedWithStatusCodeCallsAdapter() {
        // Mock doesn't seem to work for running on the main thread
        TurbolinksTestActivity activity = new TurbolinksTestActivity();

        turbolinksSession.activity(activity)
            .adapter(adapter);
        turbolinksSession.currentVisitIdentifier = VISIT_IDENTIFIER;
        turbolinksSession.visitRequestFailedWithStatusCode(VISIT_IDENTIFIER, 0);

        verify(adapter).requestFailedWithStatusCode(any(int.class));
    }

    @Test
    public void visitCompletedCallsAdapter() {
        // Mock doesn't seem to work for running on the main thread
        TurbolinksTestActivity activity = new TurbolinksTestActivity();

        turbolinksSession.activity(activity)
            .adapter(adapter);
        turbolinksSession.currentVisitIdentifier = VISIT_IDENTIFIER;
        turbolinksSession.visitCompleted(VISIT_IDENTIFIER, RESTORATION_IDENTIFIER);

        verify(adapter).visitCompleted();
    }

    @Test
    public void visitCompletedSavesRestorationIdentifier() {
        assertThat(turbolinksSession.restorationIdentifierMap.size()).isEqualTo(0);

        turbolinksSession.activity(activity)
            .adapter(adapter);
        turbolinksSession.visitCompleted(VISIT_IDENTIFIER, RESTORATION_IDENTIFIER);

        assertThat(turbolinksSession.restorationIdentifierMap.size()).isEqualTo(1);
    }

//    TODO: Robolectric having trouble with local resources directory
//    @Test
//    public void pageInvalidatedCallsAdapter() {
//    }
//
//    @Test
//    public void pageInvalidatedResetsToColdBoot() {
//    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    @Test
    public void hideProgressViewNullsView() {
        // Mock doesn't seem to work for running on the main thread
        TurbolinksTestActivity activity = new TurbolinksTestActivity();

        turbolinksSession.activity(activity);
        turbolinksSession.turbolinksIsReady = true;
        turbolinksSession.turbolinksView = view;
        turbolinksSession.progressView = new FrameLayout(context);
        turbolinksSession.currentVisitIdentifier = VISIT_IDENTIFIER;
        turbolinksSession.hideProgressView(VISIT_IDENTIFIER);

        assertThat(turbolinksSession.progressView).isNull();
    }


    // -----------------------------------------------------------------------
    // Public
    // -----------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void addJavascriptInterfaceWithReservedName() {
        turbolinksSession.addJavascriptInterface(new Object(), TurbolinksSession.JAVASCRIPT_INTERFACE_NAME);
    }

    @Test
    public void addJavascriptInterfaceAddsToMap() {
        assertThat(turbolinksSession.javascriptInterfaces.size()).isEqualTo(0);
        turbolinksSession.addJavascriptInterface(new Object(), "TestJavascriptInterface");
        assertThat(turbolinksSession.javascriptInterfaces.size()).isEqualTo(1);
    }

    @Test
    public void activityIsNullIfNotInitialized() {
        assertThat(turbolinksSession.getActivity()).isNull();
    }

    @Test
    public void webViewIsNullIfNotInitialized() {
        assertThat(turbolinksSession.getActivity()).isNull();
    }

    @Test
    public void resetToColdBoot() {
        turbolinksSession.activity(activity)
            .adapter(adapter);
        turbolinksSession.turbolinksBridgeInjected = true;
        turbolinksSession.turbolinksIsReady = true;
        turbolinksSession.coldBootInProgress = false;
        turbolinksSession.resetToColdBoot();

        assertThat(turbolinksSession.turbolinksBridgeInjected).isFalse();
        assertThat(turbolinksSession.turbolinksIsReady).isFalse();
        assertThat(turbolinksSession.coldBootInProgress).isFalse();
    }

    @Test
    public void turbolinksIsReady() {
        turbolinksSession.turbolinksIsReady = true;

        assertThat(turbolinksSession.turbolinksIsReady()).isTrue();
    }
}
