import * as edq from '../vendor/edq/js/edq.min.mjs';

import * as storage from './storage.js';
import * as logs from './logs.js';

const JOB_DEFAULTS = {
    'schedule': '0 * * * *',
}

function renderPage(request) {
    let jobsElement = renderExistingJobs();
    let addJobElement = renderAddJobArea();

    document.querySelector('.page-content').replaceChildren(jobsElement, document.createElement('hr'), addJobElement);
}

function renderAddJobArea() {
    let header = document.createElement('h3');
    header.textContent = 'Add Job';

    let container = document.createElement('div');
    container.classList.add('edit-job-area', 'section', 'theme-secondary');

    container.replaceChildren(header, renderEditJob());

    return container;
}

function renderExistingJobs() {
    let container = document.createElement('div');

    edq.render.loading.withPromise(storage.loadJobs(), container).then(function(jobs) {
        for (const job of jobs) {
            container.appendChild(renderDisplayEditJob(job));
        }
    });

    let header = document.createElement('h3');
    header.textContent = 'Current Jobs';

    container.classList.add('existing-jobs', 'section', 'theme-secondary');
    container.replaceChildren(header);

    return container;
}

// Reder a job that can be displayed, with buttons to edit.
function renderDisplayEditJob(job) {
    let header = document.createElement('div');
    header.classList.add('display-header');

    let body = document.createElement('div');
    body.classList.add('display-body');
    body.replaceChildren(renderDisplayJob(job));

    let label = document.createElement('label');
    label.textContent = job.name;
    header.appendChild(label);

    let runNowButton = document.createElement('button');
    runNowButton.textContent = 'Run Now';
    runNowButton.addEventListener('click', function(event) {
        if (window.confirm(`Confirm run of job '${job.name}'.`)) {
            window.FileSync.sync(job);
        }
    });
    header.appendChild(runNowButton);

    let deleteButton = document.createElement('button');
    deleteButton.textContent = 'Delete';
    deleteButton.addEventListener('click', function(event) {
        if (window.confirm(`Confirm deletion of job '${job.name}'.`)) {
            edq.render.loading.withPromise(storage.removeJob(job.name)).then(function () {
                edq.core.routing.reload();
            });
        }
    });
    header.appendChild(deleteButton);

    let editButton = document.createElement('button');
    editButton.textContent = 'Edit';
    editButton.addEventListener('click', function(event) {
        if (editButton.textContent === 'Edit') {
            editButton.textContent = 'Cancel Edit';
            body.replaceChildren(renderEditJob(job, 'Save Job'));
        } else {
            editButton.textContent = 'Edit';
            body.replaceChildren(renderDisplayJob(job));
        }
    });
    header.appendChild(editButton);

    let container = document.createElement('div');
    container.classList.add('subsection');
    container.replaceChildren(header, body);

    return container;
}

function renderDisplayJob(job) {
    return edq.render.code.block(edq.util.json.pretty(job), 'json');
}

function renderEditJob(defaultValues = undefined, addButtonLabel = 'Add Job') {
    if (defaultValues == null) {
        defaultValues = structuredClone(JOB_DEFAULTS);
    }

    let jobInputContainer = document.createElement('div');
    jobInputContainer.classList.add('edit-job');
    jobInputContainer.dataset['type'] = defaultValues['__class__'];

    edq.render.loading.withPromise(storage.loadLocations(), jobInputContainer).then(function(locations) {
        _renderEditJobWithLocations(defaultValues, addButtonLabel, jobInputContainer, locations);
    });

    return jobInputContainer;
}

function _renderEditJobWithLocations(defaultValues, addButtonLabel, jobInputContainer, locations) {
    let sortedLocations = locations.toSorted(function(a, b) {
        return a.name.localeCompare(b.name);
    });

    let locationChoices = {};
    for (const location of sortedLocations) {
        locationChoices[location.name] = location.name;
    }

    let fields = [
        new edq.model.field.TextField({
            name: 'name',
            label: 'Name',
            required: true,
            minLength: 1,
            defaultValue:
            defaultValues['name'],
        }),
        new edq.model.field.DropdownField({
            name: 'sourceName',
            label: 'Source',
            choices: locationChoices,
            required: true,
            defaultValue: defaultValues['sourceName'],
        }),
        new edq.model.field.DropdownField({
            name: 'destName',
            label: 'Dest',
            choices: locationChoices,
            required: true,
            defaultValue: defaultValues['destName'],
        }),
        new edq.model.field.RadioField({
            name: 'conflict-behavior',
            label: 'Conflict Behavior',
            choices: {
                'Skip': 'SKIP',
                'Overwrite': 'OVERWRITE',
            },
            defaultValue: 'SKIP',
            required: true,
        }),
        new edq.model.field.CheckboxField({
            name: 'identity-checks',
            label: 'Identity Checks',
            choices: {
                'File Size': 'SIZE',
                'Modification Time': 'MODTIME',
            },
            defaultValues: defaultValues['identity-checks'] ?? [],
        }),
        new edq.model.field.TextField({
            name: 'schedule',
            label: 'Schedule',
            required: true,
            minLength: 1,
            defaultValue: defaultValues['schedule'],
        }),
    ];

    let fieldSet = new edq.model.field.FieldSet(fields, {includeSubmitButton: false});

    function getJobData() {
        let values = fieldSet.getValues();
        values['identity-checks'] = Object.values(values['identity-checks']);
        return values;
    }

    function checkValid() {
        return (fieldSet.validateInputs(true).length === 0);
    }

    let notificationArea = document.createElement('p');
    notificationArea.classList.add('display-notification');

    let addButton = document.createElement('button');
    addButton.textContent = addButtonLabel;
    addButton.addEventListener('click', function(event) {
        edq.render.loading.withPromise(storage.upsertJob(getJobData())).then(function() {
            edq.core.routing.reload();
        });
    });

    let testButton = document.createElement('button');
    testButton.textContent = 'Test Job';
    testButton.addEventListener('click', function(event) {
        testJob(getJobData(), notificationArea);
    });

    // Only enable the buttons if the fields are valid.
    for (const button of [addButton, testButton]) {
        button.disabled = !checkValid();
        fieldSet.element.addEventListener('change', function(event) {
            button.disabled = !checkValid();
        });
    }

    let header = document.createElement('div');
    header.classList.add('display-header');
    header.replaceChildren(addButton, testButton);

    jobInputContainer.replaceChildren(header, notificationArea, fieldSet.element);

    return jobInputContainer;
}

function testJob(jobData, resultContainer) {
    resultContainer.textContent = 'Testing ...';
    resultContainer.classList.remove('theme-error');

    window.FileSync.testJob(jobData)
        .then(function(result) {
            if (result == null) {
                resultContainer.textContent = 'Job test passed!';
            } else {
                resultContainer.classList.add('theme-error');
                resultContainer.textContent = `Job test failed: '${result}'.`;
            }
        })
        .catch(function(result) {
            resultContainer.classList.add('theme-error');
            resultContainer.textContent = `Job test crashed: '${result}'.`;
        });
    ;
}

export {
    renderPage,

    renderDisplayJob,
    renderEditJob,
}
