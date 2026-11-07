import * as edq from '../vendor/edq/js/edq.min.mjs';

const STORAGE_KEY_PREFIX = 'dev.eriq.filesync.';
const STORAGE_KEY_JOBS = STORAGE_KEY_PREFIX + 'jobs';
const STORAGE_KEY_LOCATIONS = STORAGE_KEY_PREFIX + 'locations';
const STORAGE_KEY_LOGS = STORAGE_KEY_PREFIX + 'logs';

// Get all jobs from storage.
// Returns: [job, ...].
function loadJobs() {
    let rawJobs = window.localStorage.getItem(STORAGE_KEY_JOBS);
    if (rawJobs == null) {
        return [];
    }

    return JSON.parse(rawJobs);
}

// Overwrites any existing jobs.
function saveJobs(jobs) {
    window.localStorage.setItem(STORAGE_KEY_JOBS, edq.util.json.stringify(jobs));
}

// Save a single job.
function saveJob(index, job) {
    let jobs = loadJobs();
    jobs[index] = job;
    saveJobs(jobs);
}

function addJob(job) {
    let jobs = loadJobs();
    jobs.push(job);
    saveJobs(jobs);
}

function removeJob(index) {
    let jobs = loadJobs();
    jobs.splice(index, 1);
    saveJobs(jobs);
}

// Get all locations from storage.
// Returns: {name: location, ...}.
function loadLocations() {
    let rawLocations = window.localStorage.getItem(STORAGE_KEY_LOCATIONS);
    if (rawLocations == null) {
        return {};
    }

    return JSON.parse(rawLocations);
}

// Get a location or undefined.
function loadLocation(name) {
    let locations = loadLocations();
    return loadtions[name];
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

function removeLocation(name) {
    let locations = loadLocations();
    delete locations[name];
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
    loadJobs,
    saveJob,
    addJob,
    removeJob,

    loadLocation,
    loadLocations,
    saveLocation,
    removeLocation,

    loadLogs,
    addLog,
}
