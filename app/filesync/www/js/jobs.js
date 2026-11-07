import * as edq from '../vendor/edq/js/edq.min.mjs';

import * as storage from './storage.js';

class Job {
    sourceName = '';
    destName = '';
    schedule = '';
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
    let children = [];

    let jobs = storage.loadJobs();
    for (let i = 0; i < jobs.length; i++) {
        children.push(renderDisplayEditJob(jobs[i], i));
    }

    let header = document.createElement('h3');
    header.textContent = 'Current Jobs';

    let element = document.createElement('div');
    element.classList.add('existing-jobs', 'section', 'theme-secondary');
    element.replaceChildren(header, ...children);

    return element;
}

// Reder a job that can be displayed, with buttons to edit.
function renderDisplayEditJob(job, jobIndex, classes = []) {
    let header = document.createElement('div');
    header.classList.add('display-header');

    let body = document.createElement('div');
    body.classList.add('display-body');
    body.replaceChildren(renderDisplayJob(job));

    let label = document.createElement('label');
    label.textContent = `Job Index ${jobIndex}`;
    header.appendChild(label);

    let deleteButton = document.createElement('button');
    deleteButton.textContent = 'Delete';
    deleteButton.addEventListener('click', function(event) {
        storage.removeJob(jobIndex);
        edq.core.routing.reload();
    });
    header.appendChild(deleteButton);

    let editButton = document.createElement('button');
    editButton.textContent = 'Edit';
    editButton.addEventListener('click', function(event) {
        body.replaceChildren(renderEditJob(job));
    });
    header.appendChild(editButton);

    let container = document.createElement('div');
    container.classList.add('subsection');
    container.classList.add(...classes);
    container.replaceChildren(header, body);

    return container;
}

function renderDisplayJob(job) {
    return edq.render.code.block(edq.util.json.pretty(job), 'json');
}

function renderEditJob(defaultValues = {}) {
    Object.assign(defaultValues, {
        'schedule': '0 * * * *',
    });

    let locationChoices = {};
    for (const locationName of Object.keys(storage.loadLocations())) {
        locationChoices[locationName] = locationName;
    }

    let fields = [
        new edq.model.field.DropdownField({
            name: 'sourceName',
            label: 'Source',
            choices: locationChoices,
            required: true,
        }),
        new edq.model.field.DropdownField({
            name: 'destName',
            label: 'Dest',
            choices: locationChoices,
            required: true,
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

    function checkValid() {
        return (fieldSet.validateInputs(true).length === 0);
    }

    let addButton = document.createElement('button');
    addButton.textContent = 'Add Job';
    addButton.addEventListener('click', function(event) {
        let values = fieldSet.getValues();
        storage.addJob(values);
        edq.core.routing.reload();
    });

    let testButton = document.createElement('button');
    testButton.textContent = 'Test Job';
    testButton.addEventListener('click', function(event) {
        testJob(fieldSet.getValues());
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

    let jobInputContainer = document.createElement('div');
    jobInputContainer.classList.add('edit-job');
    jobInputContainer.dataset['type'] = defaultValues['__class__'];
    jobInputContainer.replaceChildren(header, fieldSet.element);

    return jobInputContainer;
}

function testJob(values) {
    // TEST
    console.log("Test Job", values);
}

export {
    renderPage,

    renderDisplayJob,
    renderEditJob,
}
