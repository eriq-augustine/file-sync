import * as edq from '../vendor/edq/js/edq.min.mjs';

import * as jobs from './jobs.js';
import * as locations from './locations.js';
import * as logs from './logs.js';

const ROUTES = [
    new edq.core.routing.NavExactRoute(new edq.core.routing.StringRoute('', routeHome)),
    new edq.core.routing.NavExactRoute(new edq.core.routing.StringRoute('locations', locations.renderPage)),
    new edq.core.routing.NavExactRoute(new edq.core.routing.StringRoute('jobs', jobs.renderPage)),
    new edq.core.routing.NavExactRoute(new edq.core.routing.StringRoute('logs', logs.renderPage)),
];

// TEST
function pluginTest() {
    let button = document.createElement('button');
    button.textContent = 'Version';
    button.addEventListener('click', function(event) {
        window.FileSync.version().then(function(result) {
            console.debug("FileSync Version", result);
        });
    });

    document.querySelector('.page-content').appendChild(button);

    // TEST
    console.log(window.FileSync);
}

function routeHome(request) {
    // TEST
    console.log(request);
    document.querySelector('.page-content').innerHTML = '<h2>Home</h2>';
}

function main() {
    edq.render.icons.replacePlaceholders();
    edq.util.brightness.initBrightmode();

    for (const route of ROUTES) {
        edq.core.routing.addRoute(route);
    }
    edq.core.routing.init();
}

document.addEventListener("DOMContentLoaded", function() {
    // This is a special Cordova event that call the listener even if the event has already fired.
    document.addEventListener("deviceready", main);
});
