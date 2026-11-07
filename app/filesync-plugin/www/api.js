const exec = require('cordova/exec');

const SERVICE_NAME = 'FileSyncPlugin';
const BROWSER_PLATFORM = 'browser';

const ACTION_VERSION = 'version';
const ACTION_TEST_LOCATION = 'test-location';

const FileSync = function () { };

FileSync.prototype.version = function() {
    if (window.cordova.platformId === BROWSER_PLATFORM) {
        return createMockPluginPromise(ACTION_VERSION, []);
    }

    return createPluginPromise(ACTION_VERSION, []);
};

FileSync.prototype.testLocation = function(locationData) {
    if (window.cordova.platformId === BROWSER_PLATFORM) {
        return createMockPluginPromise(ACTION_TEST_LOCATION, [locationData]);
    }

    return createPluginPromise(ACTION_TEST_LOCATION, [locationData]);
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
