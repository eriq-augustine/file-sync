import * as edq from '../vendor/edq/js/edq.min.mjs';

import * as home from './home.js';
import * as jobs from './jobs.js';
import * as locations from './locations.js';
import * as logs from './logs.js';

const ROUTES = [
    new edq.core.routing.NavExactRoute(new edq.core.routing.StringRoute('home', home.renderPage)),
    new edq.core.routing.NavExactRoute(new edq.core.routing.StringRoute('locations', locations.renderPage)),
    new edq.core.routing.NavExactRoute(new edq.core.routing.StringRoute('jobs', jobs.renderPage)),
    new edq.core.routing.NavExactRoute(new edq.core.routing.StringRoute('logs', logs.renderPage)),

    new edq.core.routing.StringRoute('', edq.core.routing.makeRedirectHandler('home')),
];

function main() {
    edq.render.icons.replacePlaceholders();
    edq.util.brightness.initBrightmode(false);

    for (const route of ROUTES) {
        edq.core.routing.addRoute(route);
    }

    // Some Android/Cordova situations can cause duplicate requests we don't want to miss.
    edq.core.routing.setOption('ignoreDuplicateRequests', false);

    edq.core.routing.init();
}

document.addEventListener("DOMContentLoaded", function() {
    // This is a special Cordova event that call the listener even if the event has already fired.
    document.addEventListener("deviceready", main);
});
