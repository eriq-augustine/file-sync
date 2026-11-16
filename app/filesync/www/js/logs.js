import * as edq from '../vendor/edq/js/edq.min.mjs';

import * as storage from './storage.js';

function renderPage(request) {
    let header = document.createElement('h2');
    header.textContent = 'Logs';

    let logsElement = renderExistingLogs();

    document.querySelector('.page-content').replaceChildren(header, logsElement);
}

function renderExistingLogs() {
    let container = document.createElement('div');
    container.classList.add('existing-logs', 'section', 'theme-secondary');

    edq.render.loading.withPromise(storage.loadLogs(), container).then(function(logs) {
        for (let i = (logs.length - 1); i >= 0; i--) {
            container.appendChild(renderLog(logs[i]));
        }
    });

    return container;
}

function renderLog(log) {
    let container = document.createElement('div');
    container.classList.add('log');

    let codeContainer = edq.render.code.block(edq.util.json.pretty(log), 'json');

    container.replaceChildren(codeContainer);

    return container;
}

export {
    renderPage,
    renderLog,
}
