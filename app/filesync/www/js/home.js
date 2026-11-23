import * as edq from '../vendor/edq/js/edq.min.mjs';

import * as jobsModule from './jobs.js';
import * as logsModule from './logs.js';
import * as storage from './storage.js';

const MAX_JOB_LOGS = 2;

function renderPage(request) {
    let container = document.querySelector('.page-content');
    container.replaceChildren();

    edq.render.loading.withPromise(Promise.all([storage.loadJobs(), storage.loadLogs(1000)]), container).then(function([jobs, logs]) {
        renderJobs(container, jobs, logs);
    });
}

function renderJobs(container, jobs, logs) {
    // Logs are inserted in reverse order (most recent first).
    let logsByJob = {};
    for (const log of logs) {
        if (log.jobName == null) {
            continue;
        }

        if (!Object.hasOwn(logsByJob, log.jobName)) {
            logsByJob[log.jobName] = [];
        }

        logsByJob[log.jobName].unshift(log);
    }

    let activeJobs = [];
    let inactiveJobs = [];
    for (const job of jobs) {
        if (job.active) {
            activeJobs.push(job);
        } else {
            inactiveJobs.push(job);
        }
    }

    for (const [label, jobs] of [['Active', activeJobs], ['Inactive', inactiveJobs]]) {
        let section = document.createElement('div');
        section.classList.add('section', 'theme-secondary');
        container.appendChild(section);

        if (jobs.length == 0) {
            let heading = document.createElement('h3');
            heading.textContent = `No ${label} Jobs`;
            section.appendChild(heading);

            continue;
        }

        let heading = document.createElement('h3');
        heading.textContent = `${label} Jobs`;
        section.appendChild(heading);

        for (const job of jobs) {
            let header = document.createElement('div');
            header.classList.add('display-header');

            let body = document.createElement('div');
            body.classList.add('display-body');
            body.replaceChildren(jobsModule.renderDisplayJob(job));

            let label = document.createElement('label');
            label.textContent = job.name;
            header.appendChild(label);

            let footer = document.createElement('div');
            footer.classList.add('display-footer');

            let footerLabel = document.createElement('label');
            footerLabel.textContent = 'Job Logs';
            footer.appendChild(footerLabel);

            for (const log of (logsByJob[job.name] ?? []).slice(0, MAX_JOB_LOGS)) {
                footer.append(logsModule.renderLog(log, false));
            }

            let stateButton = document.createElement('button');
            let actionText = job.active ? 'Disable' : 'Enable';
            stateButton.textContent = actionText;
            stateButton.addEventListener('click', function(event) {
                if (window.confirm(`${actionText} job '${job.name}'?`)) {
                    let promise = null;
                    if (job.active) {
                        promise = window.FileSync.deactivate(job.name);
                    } else {
                        promise = window.FileSync.activate(job.name);
                    }

                    edq.render.loading.withPromise(promise, container).then(function () {
                        edq.core.routing.reload();
                    });
                }
            });
            header.appendChild(stateButton);

            let jobContainer = document.createElement('div');
            jobContainer.classList.add('subsection');
            jobContainer.replaceChildren(header, body, footer);

            section.appendChild(jobContainer);
        }
    }
}

export {
    renderPage,
}
