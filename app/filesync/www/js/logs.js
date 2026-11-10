import * as edq from '../vendor/edq/js/edq.min.mjs';

import * as storage from './storage.js';

class Log {
    timestamp = 0;
    message = '';
    data = {};

    constructor(message, data = {}) {
        this.timestamp = edq.model.timestamp.now();
        this.message = message;
        this.data = data;
    }
}

function renderPage(request) {
    let header = document.createElement('h2');
    header.textContent = 'Logs';

    let logsElement = renderExistingLogs();

    document.querySelector('.page-content').replaceChildren(header, logsElement);
}

function renderExistingLogs() {
    let logs = storage.loadLogs();

    let children = [];
    for (let i = 0; i < logs.length; i++) {
        children.push(renderLog(i, logs[i]));
    }

    let element = document.createElement('div');
    element.classList.add('existing-logs');
    element.replaceChildren(...children);

    return element;
}

function renderLog(index, log) {
    let container = document.createElement('div');
    container.classList.add('log');

    let codeContainer = edq.render.code.block(edq.util.json.pretty(log), 'json');

    container.replaceChildren(codeContainer);

    return container;
}

export {
    Log,

    renderPage,
    renderLog,
}
