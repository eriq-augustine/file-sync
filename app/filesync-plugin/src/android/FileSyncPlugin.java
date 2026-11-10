package dev.eriq.filesync.plugin;

import dev.eriq.filesync.Version;
import dev.eriq.filesync.SyncJob;
import dev.eriq.filesync.location.Location;
import dev.eriq.filesync.util.JSON;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class FileSyncPlugin extends CordovaPlugin {
    private static final String ACTION_SYNC = "sync";
    private static final String ACTION_TEST_LOCATION = "test-location";
    private static final String ACTION_TEST_JOB = "test-job";
    private static final String ACTION_VERSION = "version";

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        switch (action) {
            case ACTION_SYNC:
                sync(args.getJSONObject(0), callbackContext);
                return true;
            case ACTION_TEST_JOB:
                testJob(args.getJSONObject(0), callbackContext);
                return true;
            case ACTION_TEST_LOCATION:
                testLocation(args.getJSONObject(0), callbackContext);
                return true;
            case ACTION_VERSION:
                callbackContext.success(Version.get());
                return true;
            default:
                callbackContext.error(String.format("Unknown action: '%s'.", action));
                return false;
        }
    }

    private void sync(JSONObject data, CallbackContext callbackContext) {
        SyncJob job = null;
        try {
            job = (SyncJob)(JSON.fromJSON(data.toString()));
        } catch (Exception ex) {
            callbackContext.error(String.format("Failed to deserialize job: '%s'.", ex));
            return;
        }

        final SyncJob finalJob = job;
        this.cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    finalJob.runNow();
                    callbackContext.success(true);
                } catch (Exception ex) {
                    callbackContext.error(String.format("Failed to run job: '%s'.", ex));
                }
            }
        });
    }

    private void testJob(JSONObject data, CallbackContext callbackContext) {
        SyncJob job = null;
        try {
            job = (SyncJob)(JSON.fromJSON(data.toString()));
        } catch (Exception ex) {
            callbackContext.error(String.format("Failed to deserialize job: '%s'.", ex));
            return;
        }

        final SyncJob finalJob = job;
        this.cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    callbackContext.success(finalJob.test());
                } catch (Exception ex) {
                    callbackContext.error(String.format("Failed to test job: '%s'.", ex));
                }
            }
        });
    }

    private void testLocation(JSONObject data, CallbackContext callbackContext) {
        Location location = null;
        try {
            location = (Location)(JSON.fromJSON(data.toString()));
        } catch (Exception ex) {
            callbackContext.error(String.format("Failed to deserialize location: '%s'.", ex));
            return;
        }

        final Location finalLocation = location;
        this.cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    callbackContext.success(finalLocation.test());
                } catch (Exception ex) {
                    callbackContext.error(String.format("Failed to test location: '%s'.", ex));
                }
            }
        });
    }
}
