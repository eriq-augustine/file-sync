import * as edq from '../vendor/edq/js/edq.min.mjs';

import * as storage from './storage.js';

const LOCATION_TYPE_LOCAL = 'dev.eriq.filesync.location.LocalLocation';
const LOCATION_TYPE_SSH = 'dev.eriq.filesync.location.SSHLocation';

const LOCATION_DEFAULTS = {
    'base-dir': '.',
    'port': 22,
};

function renderPage(request) {
    let locationsElement = renderExistingLocations();
    let addLocationElement = renderAddLocationArea();

    document.querySelector('.page-content').replaceChildren(locationsElement, document.createElement('hr'), addLocationElement);
}

function renderAddLocationArea() {
    let header = document.createElement('h3');
    header.textContent = 'Add Location';

    let container = document.createElement('div');
    container.classList.add('edit-location-area', 'section', 'theme-secondary');

    container.replaceChildren(header, renderEditLocation());

    return container;
}

function renderExistingLocations() {
    let children = [];
    for (const [name, location] of Object.entries(storage.loadLocations())) {
        children.push(renderDisplayEditLocation(location));
    }

    let header = document.createElement('h3');
    header.textContent = 'Current Locations';

    let element = document.createElement('div');
    element.classList.add('existing-locations', 'section', 'theme-secondary');
    element.replaceChildren(header, ...children);

    return element;
}

// Reder a location that can be displayed, with buttons to edit.
function renderDisplayEditLocation(location, classes = []) {
    let header = document.createElement('div');
    header.classList.add('display-header');

    let body = document.createElement('div');
    body.classList.add('display-body');
    body.replaceChildren(renderDisplayLocation(location));

    let label = document.createElement('label');
    label.textContent = location.name;
    header.appendChild(label);

    let deleteButton = document.createElement('button');
    deleteButton.textContent = 'Delete';
    deleteButton.addEventListener('click', function(event) {
        storage.removeLocation(location.name);
        edq.core.routing.reload();
    });
    header.appendChild(deleteButton);

    let editButton = document.createElement('button');
    editButton.textContent = 'Edit';
    editButton.addEventListener('click', function(event) {
        if (editButton.textContent === 'Edit') {
            editButton.textContent = 'Cancel Edit';
            body.replaceChildren(renderEditLocation(location, 'Save Location'));
        } else {
            editButton.textContent = 'Edit';
            body.replaceChildren(renderDisplayLocation(location));
        }
    });
    header.appendChild(editButton);

    let container = document.createElement('div');
    container.classList.add('subsection');
    container.classList.add(...classes);
    container.replaceChildren(header, body);

    return container;
}

function renderDisplayLocation(location) {
    return edq.render.code.block(edq.util.json.pretty(location), 'json');
}

function renderEditLocation(defaultValues = undefined, addButtonLabel = 'Add Location') {
    if (defaultValues == null) {
        defaultValues = structuredClone(LOCATION_DEFAULTS);
    }

    let locationInputContainer = document.createElement('div');
    locationInputContainer.classList.add('edit-location');
    locationInputContainer.dataset['type'] = defaultValues['__class__'];

    // Define the type field specially, so we can control the inputs that show based on the type.
    let typeField = new edq.model.field.DropdownField({
        name: '__class__',
        label: 'Type',
        choices: {
            'Local': LOCATION_TYPE_LOCAL,
            'SSH': LOCATION_TYPE_SSH,
        },
        required: true,
        defaultValue: defaultValues['__class__'],
    });

    let fields = [
        new edq.model.field.TextField({name: 'name', label: 'Name', required: true, minLength: 1, defaultValue: defaultValues['name']}),
        typeField,

        new edq.model.field.TextField({name: 'base-dir', label: 'Base Dir', required: true, defaultValue: defaultValues['base-dir']}),

        new edq.model.field.TextField({name: 'hostname', label: 'Host Name', required: true, defaultValue: defaultValues['hostname'],
                dataset: {'type': LOCATION_TYPE_SSH}}),

        new edq.model.field.IntegerField({name: 'port', label: 'Port', required: true, defaultValue: defaultValues['port'],
                dataset: {'type': LOCATION_TYPE_SSH}}),

        new edq.model.field.TextField({name: 'username', label: 'Username', required: true, defaultValue: defaultValues['username'],
                dataset: {'type': LOCATION_TYPE_SSH}}),

        new edq.model.field.SecretField({name: 'password', label: 'Password', defaultValue: defaultValues['password'],
                dataset: {'type': LOCATION_TYPE_SSH}}),

        new edq.model.field.SecretField({name: 'private-key', label: 'Private Key', defaultValue: defaultValues['private-key'],
                dataset: {'type': LOCATION_TYPE_SSH}}),
        new edq.model.field.SecretField({name: 'public-key', label: 'Public Key', defaultValue: defaultValues['public-key'],
                dataset: {'type': LOCATION_TYPE_SSH}}),
    ];

    function checkValid() {
        let currentType = typeField.getValue();
        for (const field of fields) {
            let fieldType = field.options.dataset['type'];
            if ((fieldType != null) && (currentType !== fieldType)) {
                continue;
            }

            if (field.validateInput(false, true) != null) {
                return false;
            }
        }

        return true;
    }

    function updateTypeFields() {
        let currentType = typeField.getValue();
        for (const field of fields) {
            let fieldType = field.options.dataset['type'];
            if (fieldType == null) {
                continue;
            }

            if (currentType === fieldType) {
                field.element.classList.remove('hidden');
            } else {
                field.element.classList.add('hidden');
            }
        }
    }

    typeField.element.addEventListener('change', updateTypeFields);
    updateTypeFields();

    let fieldSet = new edq.model.field.FieldSet(fields, {includeSubmitButton: false});

    let notificationArea = document.createElement('p');
    notificationArea.classList.add('display-notification');

    let addButton = document.createElement('button');
    addButton.textContent = addButtonLabel;
    addButton.addEventListener('click', function(event) {
        let values = fieldSet.getValues();
        storage.saveLocation(values.name, values);
        edq.core.routing.reload();
    });

    let testButton = document.createElement('button');
    testButton.textContent = 'Test Location';
    testButton.addEventListener('click', function(event) {
        testLocation(fieldSet.getValues(), notificationArea);
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

    locationInputContainer.replaceChildren(header, notificationArea, fieldSet.element);

    return locationInputContainer;
}

function testLocation(values, resultContainer) {
    resultContainer.textContent = 'Testing ...';
    resultContainer.classList.remove('theme-error');

    window.FileSync.testLocation(values)
        .then(function(result) {
            if (result == null) {
                resultContainer.textContent = 'Location test passed!';
            } else {
                resultContainer.classList.add('theme-error');
                resultContainer.textContent = `Location test failed: '${result}'.`;
            }
        })
        .catch(function(result) {
            resultContainer.classList.add('theme-error');
            resultContainer.textContent = `Location test crashed: '${result}'.`;
        });
    ;
}

export {
    renderPage,

    renderDisplayLocation,
    renderEditLocation,
}
