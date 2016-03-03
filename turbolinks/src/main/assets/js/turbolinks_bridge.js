function TLWebView(controller) {
    this.controller = controller
    controller.adapter = this

    var turbolinksIsReady = typeof Turbolinks !== "undefined" && Turbolinks !== null
    TurbolinksNative.setTurbolinksIsReady(turbolinksIsReady);
}

TLWebView.prototype = {
    // -----------------------------------------------------------------------
    // Starting point
    // -----------------------------------------------------------------------

    visitLocationWithActionAndRestorationIdentifier: function(location, action, restorationIdentifier) {
        this.controller.startVisitToLocationWithAction(location, action, restorationIdentifier)
    },

    // -----------------------------------------------------------------------
    // Current visit
    // -----------------------------------------------------------------------

    issueRequestForVisitWithIdentifier: function(identifier) {
        if (identifier == this.currentVisit.identifier) {
            this.currentVisit.issueRequest()
        }
    },

    changeHistoryForVisitWithIdentifier: function(identifier) {
        if (identifier == this.currentVisit.identifier) {
            this.currentVisit.changeHistory()
        }
    },

    loadCachedSnapshotForVisitWithIdentifier: function(identifier) {
        if (identifier == this.currentVisit.identifier) {
            this.currentVisit.loadCachedSnapshot()
        }
    },

    loadResponseForVisitWithIdentifier: function(identifier) {
        if (identifier == this.currentVisit.identifier) {
            this.currentVisit.loadResponse()
        }
    },

    cancelVisitWithIdentifier: function(identifier) {
        if (identifier == this.currentVisit.identifier) {
            this.currentVisit.cancel()
        }
    },

    // -----------------------------------------------------------------------
    // Adapter
    // -----------------------------------------------------------------------

    visitProposedToLocationWithAction: function(location, action, target) {
        TurbolinksNative.visitProposedToLocationWithAction(location.absoluteURL, action, target.outerHTML);
    },

    visitStarted: function(visit) {
        this.currentVisit = visit
        TurbolinksNative.visitStarted(visit.identifier, visit.hasCachedSnapshot());
    },

    visitRequestStarted: function(visit) {
        // Purposely left unimplemented. visitStarted covers most cases and we'll keep an eye
        // on whether this is needed in the future
    },

    visitRequestCompleted: function(visit) {
        TurbolinksNative.visitRequestCompleted(visit.identifier);
    },

    visitRequestFailedWithStatusCode: function(visit, statusCode) {
        TurbolinksNative.visitRequestFailedWithStatusCode(visit.identifier, statusCode);
    },

    visitRequestFinished: function(visit) {
        // Purposely left unimplemented. visitRequestCompleted covers most cases and we'll keep
        // an eye on whether this is needed in the future
    },

    visitRendered: function(visit) {
        this.afterNextRepaint(function() {
            TurbolinksNative.visitRendered(visit.identifier)
        })
    },

    visitCompleted: function(visit) {
        TurbolinksNative.visitCompleted(visit.identifier, visit.restorationIdentifier)
    },

    pageInvalidated: function() {
        TurbolinksNative.pageInvalidated()
    },

    // -----------------------------------------------------------------------
    // Private
    // -----------------------------------------------------------------------

    afterNextRepaint: function(callback) {
      requestAnimationFrame(function() {
        requestAnimationFrame(callback)
      })
    }
}

try {
    window.webView = new TLWebView(Turbolinks.controller)
} catch (e) { // Most likely reached a page where Turbolinks.controller returned "Uncaught ReferenceError: Turbolinks is not defined"
    TurbolinksNative.turbolinksDoesNotExist()
}
