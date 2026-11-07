import * as edq from '../vendor/edq/js/edq.min.mjs';

import * as storage from './storage.js';

class Job {
    sourceName = '';
    destName = '';
    schedule = '';
}

function renderPage(request) {
    let header = document.createElement('h2');
    header.textContent = 'Jobs';

    let jobsElement = renderExistingJobs();
    let jobInputContainer = renderEditJob();

    document.querySelector('.page-content').replaceChildren(header, jobsElement, jobInputContainer);
}

function renderExistingJobs() {
    let jobs = storage.loadJobs();

    let children = [];
    for (let i = 0; i < jobs.length; i++) {
        children.push(renderJob(i, jobs[i]));
    }

    let element = document.createElement('div');
    element.classList.add('existing-jobs');
    element.replaceChildren(...children);

    return element;
}

function renderJob(index, job) {
    let container = document.createElement('div');
    container.classList.add('job');

    let label = document.createElement('label');
    label.textContent = `Job ${index}`;

    let codeContainer = edq.render.code.block(edq.util.json.pretty(job), 'json');

    container.replaceChildren(label, codeContainer);

    return container;
}

function renderEditJob() {
    let locationChoices = {};
    for (const locationName of Object.keys(storage.loadLocations())) {
        locationChoices[locationName] = locationName;
    }

    let fields = [
        // TEST
        // new edq.model.field.DropDownField({
        new edq.model.field.RadioField({
            name: 'sourceName',
            label: 'Source',
            choices: locationChoices,
            required: true,
        }),
        // TEST
        // new edq.model.field.DropDownField({
        new edq.model.field.RadioField({
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
            defaultValue: '0 * * * *',
        }),
    ];

    let fieldSet = new edq.model.field.FieldSet(fields, {submitButtonText: 'Add Job', submitCallback: function(values, _) {
        addJob(values);
    }});

    return fieldSet.element;
}

function addJob(job) {
    storage.addJob(job);
}

export {
    renderPage,

    renderJob,
    renderEditJob,
}
