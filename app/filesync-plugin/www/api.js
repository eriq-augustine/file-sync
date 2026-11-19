const exec = require('cordova/exec');

const SERVICE_NAME = 'FileSyncPlugin';
const BROWSER_PLATFORM = 'browser';

const ACTION_ACTIVATE = 'activate';
const ACTION_DEACTIVATE = 'deactivate';
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

FileSync.prototype.removeJob = function(name) {
    if (window.cordova.platformId !== BROWSER_PLATFORM) {
        return createPluginPromise(ACTION_DB_JOBS_REMOVE, [name]);
    }

    return localRemoveFromListByName(STORAGE_KEY_JOBS, name);
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

FileSync.prototype.removeLocation = function(name) {
    if (window.cordova.platformId !== BROWSER_PLATFORM) {
        return createPluginPromise(ACTION_DB_LOCATIONS_REMOVE, [name]);
    }

    return localRemoveFromListByName(STORAGE_KEY_LOCATIONS, name);
};

FileSync.prototype.getLogs = function(limit = 0) {
    if (window.cordova.platformId !== BROWSER_PLATFORM) {
        return createPluginPromise(ACTION_DB_LOGS_GET, [limit]);
    }

    let logs = localGetListItems(STORAGE_KEY_LOGS);
    if (limit > 0) {
        logs = logs.slice(0, limit);
    }

    return Promise.resolve(JSON.stringify(logs));
};

FileSync.prototype.appendLog = function(message, jobName = undefined, data = undefined) {
    if (window.cordova.platformId !== BROWSER_PLATFORM) {
        return createPluginPromise(ACTION_DB_LOGS_APPEND, [message, jobName, data]);
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
        jobName: jobName,
        data: data,
    });

    window.localStorage.setItem(STORAGE_KEY_LOGS, JSON.stringify(items));

    return Promise.resolve(true);
};

function localGetList(key) {
    return Promise.resolve(JSON.stringify(localGetListItems(key)));
}

// Note that this returns a list of items (instead of a JSON string that we usually use).
function localGetListItems(key) {
    let itemsString = window.localStorage.getItem(key);
    if (itemsString == null) {
        return [];
    }

    return JSON.parse(itemsString);
}

function localUpsertFromListByName(key, newItem) {
    let items = localGetListItems(key);

    let found = false;
    for (let i = 0; i < items.length; i++) {
        if (items[i].name === newItem.name) {
            items[i] = newItem;
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
    let items = localGetListItems(key);

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

function upsertJob(name, modFunc) {
    let jobs = localGetListItems(STORAGE_KEY_JOBS);

    let targetJob = undefined;
    for (const job of jobs) {
        if (job.name === name) {
            targetJob = job;
            break;
        }
    }

    if (targetJob == null) {
        throw new Error(`Unable to find job to upsert: '${name}'.`);
    }

    modFunc(targetJob);

    return localUpsertFromListByName(STORAGE_KEY_JOBS, targetJob);
}

FileSync.prototype.activate = function(jobName) {
    if (window.cordova.platformId === BROWSER_PLATFORM) {
        FileSync.prototype.appendLog("Completed mock job activation.", jobName);
        return upsertJob(jobName, function(job) {
            job.active = true;
        });
    }

    return createPluginPromise(ACTION_ACTIVATE, [jobName]);
};

FileSync.prototype.deactivate = function(jobName) {
    if (window.cordova.platformId === BROWSER_PLATFORM) {
        FileSync.prototype.appendLog("Completed mock job deactivation.", jobName);
        return upsertJob(jobName, function(job) {
            job.active = false;
        });
    }

    return createPluginPromise(ACTION_DEACTIVATE, [jobName]);
};

FileSync.prototype.sync = function(jobData) {
    if (window.cordova.platformId === BROWSER_PLATFORM) {
        FileSync.prototype.appendLog("Completed mock job sync.", jobData.name);
        return Promise.resolve(true);
    }

    return createPluginPromise(ACTION_SYNC, [jobData]);
};

FileSync.prototype.testJob = function(jobData) {
    if (window.cordova.platformId === BROWSER_PLATFORM) {
        FileSync.prototype.appendLog("Completed mock job test.", jobData.name);
        return Promise.resolve(null);
    }

    return createPluginPromise(ACTION_TEST_JOB, [jobData]);
};

FileSync.prototype.testLocation = function(locationData) {
    if (window.cordova.platformId === BROWSER_PLATFORM) {
        FileSync.prototype.appendLog("Completed mock location test.", null, {'location': locationData.name});
        return Promise.resolve(null);
    }

    return createPluginPromise(ACTION_TEST_LOCATION, [locationData]);
};

FileSync.prototype.version = function() {
    if (window.cordova.platformId === BROWSER_PLATFORM) {
        return Promise.resolve("?.?.?");
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

module.exports = new FileSync();
