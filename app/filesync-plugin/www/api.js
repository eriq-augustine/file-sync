const exec = require('cordova/exec');

const SERVICE_NAME = 'FileSyncPlugin';
const BROWSER_PLATFORM = 'browser';

const ACTION_SYNC = 'sync';
const ACTION_TEST_JOB = 'test-job';
const ACTION_TEST_LOCATION = 'test-location';
const ACTION_VERSION = 'version';

const ACTION_DB_JOBS_GET = "db-jobs-get";
const ACTION_DB_JOBS_REMOVE = "db-jobs-remove";
const ACTION_DB_JOBS_UPSERT = "db-jobs-upsert";
const ACTION_DB_LOCATIONS_GET = "db-locations-get";
const ACTION_DB_LOCATIONS_REMOVE = "db-locations-remove";
const ACTION_DB_LOCATIONS_UPSERT = "db-locations-upsert";
const ACTION_DB_LOGS_GET = "db-logs-get";
const ACTION_DB_LOGS_APPEND = "db-logs-append";

// We will mock storage to use in browser testing.
const STORAGE_KEY_PREFIX = 'dev.eriq.filesync.';
const STORAGE_KEY_JOBS = STORAGE_KEY_PREFIX + 'jobs';
const STORAGE_KEY_LOCATIONS = STORAGE_KEY_PREFIX + 'locations';
const STORAGE_KEY_LOGS = STORAGE_KEY_PREFIX + 'logs';

const FileSync = function () { };

FileSync.prototype.getJobs = function() {
    if (window.cordova.platformId !== BROWSER_PLATFORM) {
        return createPluginPromise(ACTION_DB_JOBS_GET, []);
    }

    return localGetList(STORAGE_KEY_JOBS);
};

FileSync.prototype.upsertJob = function(newJob) {
    if (window.cordova.platformId !== BROWSER_PLATFORM) {
        return createPluginPromise(ACTION_DB_JOBS_UPSERT, [newJob]);
    }

    return localUpsertFromListByName(STORAGE_KEY_JOBS, newJob);
};

FileSync.prototype.removeJob = function(newJob) {
    if (window.cordova.platformId !== BROWSER_PLATFORM) {
        return createPluginPromise(ACTION_DB_JOBS_REMOVE, [newJob]);
    }

    return localRemoveFromListByName(STORAGE_KEY_JOBS, newJob);
};

FileSync.prototype.getLocations = function() {
    if (window.cordova.platformId !== BROWSER_PLATFORM) {
        return createPluginPromise(ACTION_DB_LOCATIONS_GET, []);
    }

    return localGetList(STORAGE_KEY_LOCATIONS);
};

FileSync.prototype.upsertLocation = function(newLocation) {
    if (window.cordova.platformId !== BROWSER_PLATFORM) {
        return createPluginPromise(ACTION_DB_LOCATIONS_UPSERT, [newLocation]);
    }

    return localUpsertFromListByName(STORAGE_KEY_LOCATIONS, newLocation);
};

FileSync.prototype.removeLocation = function(newLocation) {
    if (window.cordova.platformId !== BROWSER_PLATFORM) {
        return createPluginPromise(ACTION_DB_LOCATIONS_REMOVE, [newLocation]);
    }

    return localRemoveFromListByName(STORAGE_KEY_LOCATIONS, newLocation);
};

FileSync.prototype.getLogs = function(limit = 0) {
    if (window.cordova.platformId !== BROWSER_PLATFORM) {
        return createPluginPromise(ACTION_DB_LOGS_GET, [limit]);
    }

    let logs = localGetList(STORAGE_KEY_LOGS);
    if (limit > 0) {
        return logs.slice(0, limit);
    }

    return logs;
};

FileSync.prototype.appendLog = function(message, data = undefined) {
    if (window.cordova.platformId !== BROWSER_PLATFORM) {
        return createPluginPromise(ACTION_DB_LOGS_APPEND, [newLocation]);
    }

    let items = window.localStorage.getItem(STORAGE_KEY_LOGS);
    if (items == null) {
        items = [];
    } else {
        items = JSON.parse(items);
    }

    items.push({
        timestamp: (new Date()).valueOf(),
        message: message,
        data: data,
    });

    window.localStorage.setItem(STORAGE_KEY_LOGS, JSON.stringify(items));

    return Promise.resolve(true);
};

function localGetList(key) {
    let items = window.localStorage.getItem(key);
    if (items == null) {
        return Promise.resolve([]);
    }

    return Promise.resolve(JSON.parse(items));
}

function localUpsertFromListByName(key, newItem) {
    let items = window.localStorage.getItem(key);
    if (items == null) {
        items = [];
    } else {
        items = JSON.parse(items);
    }

    let found = false;
    for (let i = 0; i < items.length; i++) {
        if (items[i].name === newItem.name) {
            items[i] = name;
            found = true;
            break;
        }
    }

    if (!found) {
        items.push(newItem);
    }

    window.localStorage.setItem(key, JSON.stringify(items));

    return Promise.resolve(true);
}

function localRemoveFromListByName(key, name) {
    let items = window.localStorage.getItem(key);
    if (items == null) {
        items = [];
    } else {
        items = JSON.parse(items);
    }

    let removeIndex = undefined;
    for (let i = 0; i < items.length; i++) {
        if (items[i].name === name) {
            removeIndex = i;
            break;
        }
    }

    if (removeIndex != null) {
        items.splice(removeIndex, 1);
    }

    window.localStorage.setItem(key, JSON.stringify(items));

    return Promise.resolve(removeIndex != null);
}

FileSync.prototype.sync = function(jobData) {
    if (window.cordova.platformId === BROWSER_PLATFORM) {
        return createMockPluginPromise(ACTION_SYNC, [jobData]);
    }

    return createPluginPromise(ACTION_SYNC, [jobData]);
};

FileSync.prototype.testJob = function(jobData) {
    if (window.cordova.platformId === BROWSER_PLATFORM) {
        return createMockPluginPromise(ACTION_TEST_JOB, [jobData]);
    }

    return createPluginPromise(ACTION_TEST_JOB, [jobData]);
};

FileSync.prototype.testLocation = function(locationData) {
    if (window.cordova.platformId === BROWSER_PLATFORM) {
        return createMockPluginPromise(ACTION_TEST_LOCATION, [locationData]);
    }

    return createPluginPromise(ACTION_TEST_LOCATION, [locationData]);
};

FileSync.prototype.version = function() {
    if (window.cordova.platformId === BROWSER_PLATFORM) {
        return createMockPluginPromise(ACTION_VERSION, []);
    }

    return createPluginPromise(ACTION_VERSION, []);
};

function createPluginPromise(functionName, args) {
    return new Promise(function(resolve, reject) {
        const successCallback = function(result) {
            resolve(result);
        };

        const errorCallback = function(error) {
            reject(new Error(`Plugin failed on call to '${functionName}': '${error}'.`));
        };

        exec(successCallback, errorCallback, SERVICE_NAME, functionName, args);
    });
}

function createMockPluginPromise(functionName, args) {
    return Promise.resolve({
        name: functionName,
        args: args,
    });
}

module.exports = new FileSync();
