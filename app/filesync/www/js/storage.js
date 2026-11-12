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


// TEST



function loadWithCache(cacheKey, getFunction) {
    if (cache[cacheKey] != null) {
        return Promise.resolve(cache[cacheKey]);
    }

    return new Promise(function(resolve, reject) {
        getFunction()
            .then(function(items) {
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


// TEST



// Get a job or undefined.
function loadJob(name) {
    let jobs = loadJobs();
    return jobs[name];
}

// Overwrites any existing jobs.
function saveJobs(jobs) {
    window.localStorage.setItem(STORAGE_KEY_JOBS, edq.util.json.stringify(jobs));
}

// Save a single job.
function saveJob(name, job) {
    let jobs = loadJobs();
    jobs[name] = job;
    saveJobs(jobs);
}

// Get a location or undefined.
function loadLocation(name) {
    let locations = loadLocations();
    return locations[name];
}

// Overwrites any existing locations.
function saveLocations(locations) {
    window.localStorage.setItem(STORAGE_KEY_LOCATIONS, edq.util.json.stringify(locations));
}

// Save a single location.
function saveLocation(name, location) {
    let locations = loadLocations();
    locations[name] = location;
    saveLocations(locations);
}

// Get all logs from storage.
// Returns: [log, ...].
function loadLogs() {
    let rawLogs = window.localStorage.getItem(STORAGE_KEY_LOGS);
    if (rawLogs == null) {
        return [];
    }

    return JSON.parse(rawLogs);
}

// Overwrites any existing logs.
function saveLogs(logs) {
    window.localStorage.setItem(STORAGE_KEY_LOGS, edq.util.json.stringify(logs));
}

function addLog(log) {
    let logs = loadLogs();
    logs.push(log);
    saveLogs(logs);
}

export {
    loadJob,
    loadJobs,
    upsertJob,
    removeJob,

    loadLocation,
    loadLocations,
    upsertLocation,
    removeLocation,

    loadLogs,
    addLog,
}
