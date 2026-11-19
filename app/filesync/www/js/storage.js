import * as edq from '../vendor/edq/js/edq.min.mjs';

function loadJobs() {
    return loadItems(window.FileSync.getJobs);
}

function upsertJob(newJob) {
    return upsert(newJob, window.FileSync.upsertJob);
}

function removeJob(name) {
    return remove(name, window.FileSync.removeJob);
}

function loadLocations() {
    return loadItems(window.FileSync.getLocations);
}

function upsertLocation(newLocation) {
    return upsert(newLocation, window.FileSync.upsertLocation);
}

function removeLocation(name) {
    return remove(name, window.FileSync.removeLocation);
}

function loadLogs(limit = 0) {
    return new Promise(function(resolve, reject) {
        window.FileSync.getLogs(limit)
            .then(function(itemsString) {
                resolve(JSON.parse(itemsString));
            })
            .catch(function(error) {
                throw new Error(error);
            })
        ;
    });
}

function appendLog(message, data = undefined) {
    return new Promise(function(resolve, reject) {
        window.FileSync.appendLog(message, data)
            .then(function(_) {
                resolve(true);
            })
            .catch(function(error) {
                throw new Error(error);
            })
        ;
    });
}

function loadItems(getFunction) {
    return new Promise(function(resolve, reject) {
        getFunction()
            .then(function(itemsString) {
                resolve(JSON.parse(itemsString));
            })
            .catch(function(error) {
                throw new Error(error);
            })
        ;
    });
}

function upsert(newItem, upsertFunction) {
    return new Promise(function(resolve, reject) {
        upsertFunction(newItem)
            .then(function(result) {
                resolve(result);
            })
            .catch(function(error) {
                throw new Error(error);
            })
        ;
    });
}

function remove(itemName, removeFunction) {
    return new Promise(function(resolve, reject) {
        removeFunction(itemName)
            .then(function(result) {
                resolve(result);
            })
            .catch(function(error) {
                throw new Error(error);
            })
        ;
    });
}

export {
    loadJobs,
    upsertJob,
    removeJob,

    loadLocations,
    upsertLocation,
    removeLocation,

    loadLogs,
    appendLog,
}
