import * as edq from '../vendor/edq/js/edq.min.mjs';

let cache = {};

const CACHE_KEY_JOBS = 'jobs';
const CACHE_KEY_LOCATIONS = 'locations';

function loadJobs() {
    return loadWithCache(CACHE_KEY_JOBS, window.FileSync.getJobs);
}

function upsertJob(newJob) {
    return upsertWithCache(CACHE_KEY_JOBS, newJob, window.FileSync.upsertJob);
}

function removeJob(name) {
    return removeWithCache(CACHE_KEY_JOBS, name, window.FileSync.removeJob);
}

function loadLocations() {
    return loadWithCache(CACHE_KEY_LOCATIONS, window.FileSync.getLocations);
}

function upsertLocation(newLocation) {
    return upsertWithCache(CACHE_KEY_LOCATIONS, newLocation, window.FileSync.upsertLocation);
}

function removeLocation(name) {
    return removeWithCache(CACHE_KEY_LOCATIONS, name, window.FileSync.removeLocation);
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

function loadWithCache(cacheKey, getFunction) {
    if (cache[cacheKey] != null) {
        return Promise.resolve(cache[cacheKey]);
    }

    return new Promise(function(resolve, reject) {
        getFunction()
            .then(function(itemsString) {
                let items = JSON.parse(itemsString);
                cache[cacheKey] = items;
                resolve(cache[cacheKey]);
            })
            .catch(function(error) {
                throw new Error(error);
            })
        ;
    });
}

function upsertWithCache(cacheKey, newItem, upsertFunction) {
    return new Promise(function(resolve, reject) {
        upsertFunction(newItem)
            .then(function(result) {
                cache[cacheKey] = null;
                resolve(result);
            })
            .catch(function(error) {
                throw new Error(error);
            })
        ;
    });
}

function removeWithCache(cacheKey, itemName, removeFunction) {
    return new Promise(function(resolve, reject) {
        removeFunction(itemName)
            .then(function(result) {
                cache[cacheKey] = null;
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
