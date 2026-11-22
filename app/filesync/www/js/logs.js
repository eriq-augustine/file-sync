import * as edq from '../vendor/edq/js/edq.min.mjs';

import * as storage from './storage.js';

function renderPage(request) {
    document.querySelector('.page-content').replaceChildren(renderExistingLogs());
}

function renderExistingLogs() {
    let container = document.createElement('div');
    container.classList.add('existing-logs', 'section', 'theme-secondary');

    let header = document.createElement('h3');
    header.textContent = 'Logs';
    container.appendChild(header);

    edq.render.loading.withPromise(storage.loadLogs(), container).then(function(logs) {
        for (let i = (logs.length - 1); i >= 0; i--) {
            container.appendChild(renderLog(logs[i], true, ['subsection', 'theme-primary']));
        }
    });

    return container;
}

function renderLog(log, includeJobName = true, classes = []) {
    let container = document.createElement('div');
    container.classList.add('log');
    container.classList.add(...classes);

    let children = [];

    let message = edq.model.timestamp.timestampToString(log.timestamp, true);
    if (includeJobName && (log.jobName != null)) {
        message += ` [${log.jobName}]`;
    }
    message += ` -- ${log.message}`;

    let text = document.createElement('span');
    text.textContent = message;
    children.push(text);

    if ((log.data != null) && (Object.keys(log.data).length > 0)) {
        children.push(edq.render.code.block(edq.util.json.pretty(log.data), 'json'));
    }

    container.replaceChildren(...children);

    return container;
}

export {
    renderPage,
    renderLog,
}
