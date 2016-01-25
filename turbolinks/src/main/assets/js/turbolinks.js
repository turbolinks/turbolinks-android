function TLWebView(controller) {
    this.controller = controller
    controller.adapter = this

    var turbolinksIsReady = typeof Turbolinks !== "undefined" && Turbolinks !== null
    TLNativeBridge.setTurbolinksIsReady(turbolinksIsReady);
    TLNativeBridge.setFirstRestorationIdentifier(this.controller.restorationIdentifier);
}

TLWebView.prototype = {
    // Current visit

    visitLocationWithActionAndRestorationIdentifier: function(location, action, restorationIdentifier) {
        this.controller.startVisitToLocationWithAction(location, action, restorationIdentifier)
    },

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

    restoreSnapshotForVisitWithIdentifier: function(identifier) {
        if (identifier == this.currentVisit.identifier) {
            this.currentVisit.restoreSnapshot()
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

    // Adapter

    visitProposedToLocationWithAction: function(location, action) {
        TLNativeBridge.visitProposedToLocationWithAction(location.absoluteURL, action);
    },

    visitStarted: function(visit) {
        this.currentVisit = visit
        TLNativeBridge.visitStarted(visit.identifier, visit.hasSnapshot());
    },

    visitRequestStarted: function(visit) {
    },

    visitRequestCompleted: function(visit) {
        TLNativeBridge.visitRequestCompleted(visit.identifier);
    },

    visitRequestFailedWithStatusCode: function(visit, statusCode) {
        TLNativeBridge.visitRequestFailedWithStatusCode(visit.identifier, statusCode);
    },

    visitRequestFinished: function(visit) {
    },

    visitSnapshotRestored: function(visit) {
        this.afterNextRepaint(function() {
            TLNativeBridge.visitSnapshotRestored(visit.identifier)
        })
    },

    visitResponseLoaded: function(visit) {
        this.afterNextRepaint(function() {
            TLNativeBridge.visitResponseLoaded(visit.identifier)
        })
    },

    visitCompleted: function(visit) {
        TLNativeBridge.visitCompleted(visit.identifier, visit.restorationIdentifier)
    },

    pageInvalidated: function() {
        TLNativeBridge.pageInvalidated()
    },

    // Private

    afterNextRepaint: function(callback) {
      requestAnimationFrame(function() {
        requestAnimationFrame(callback)
      })
    }
}

try {
    window.webView = new TLWebView(Turbolinks.controller)
} catch (e) { // Most likely reached a page where Turbolinks.controller returned "Uncaught ReferenceError: Turbolinks is not defined"
    TLNativeBridge.turbolinksDoesNotExist()
}
