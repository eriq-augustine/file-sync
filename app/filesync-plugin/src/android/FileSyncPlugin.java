package dev.eriq.filesync.plugin;

import dev.eriq.filesync.Version;
import dev.eriq.filesync.SyncJob;
import dev.eriq.filesync.location.Location;
import dev.eriq.filesync.plugin.Database;
import dev.eriq.filesync.util.JSON;

import android.content.Context;
import androidx.work.ListenableWorker;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class FileSyncPlugin extends CordovaPlugin {
    private static final String ACTION_SYNC = "sync";
    private static final String ACTION_TEST_LOCATION = "test-location";
    private static final String ACTION_TEST_JOB = "test-job";
    private static final String ACTION_VERSION = "version";

    private static final String ACTION_DB_GET_JOBS = "db-get-jobs";
    private static final String ACTION_DB_REMOVE_JOB = "db-remove-job";
    private static final String ACTION_DB_UPSERT_JOB = "db-upsert-job";
    private static final String ACTION_DB_GET_LOCATIONS = "db-get-locations";
    private static final String ACTION_DB_REMOVE_LOCATION = "db-remove-location";
    private static final String ACTION_DB_UPSERT_LOCATION = "db-upsert-location";

    private Database db;

    public FileSyncPlugin() {
        super();

        db = null;
    }

    @Override
    protected void pluginInitialize() {
        super.pluginInitialize();

        db = new Database(cordova.getContext());
    }

    @Override
    public void onDestroy() {
        db.close();

        super.onDestroy();
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this.cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    dispatchAction(action, args, callbackContext);
                } catch (Exception ex) {
                    callbackContext.error(String.format("Failed to run action '%s': '%s'.", action, ex));
                }
            }
        });

        return true;
    }

    public void dispatchAction(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        switch (action) {
            case ACTION_DB_GET_JOBS:
                getJobs(callbackContext);
                return;
            case ACTION_DB_REMOVE_JOB:
                removeJob(args.getString(0), callbackContext);
                return;
            case ACTION_DB_UPSERT_JOB:
                upsertJob(args.getJSONObject(0), callbackContext);
                return;
            case ACTION_DB_GET_LOCATIONS:
                getLocations(callbackContext);
                return;
            case ACTION_DB_REMOVE_LOCATION:
                removeLocation(args.getString(0), callbackContext);
                return;
            case ACTION_DB_UPSERT_LOCATION:
                upsertLocation(args.getJSONObject(0), callbackContext);
                return;
            case ACTION_SYNC:
                sync(args.getJSONObject(0), callbackContext);
                return;
            case ACTION_TEST_JOB:
                testJob(args.getJSONObject(0), callbackContext);
                return;
            case ACTION_TEST_LOCATION:
                testLocation(args.getJSONObject(0), callbackContext);
                return;
            case ACTION_VERSION:
                callbackContext.success(Version.get());
                return;
            default:
                callbackContext.error(String.format("Unknown action: '%s'.", action));
                return;
        }
    }

    private void getJobs(CallbackContext callbackContext) {
        List<SyncJob> jobs = db.getJobs();
        callbackContext.success(JSON.toJSON(jobs));
    }

    private void upsertJob(JSONObject jobData, CallbackContext callbackContext) {
        SyncJob job = null;
        try {
            job = (SyncJob)(JSON.fromJSON(jobData.toString()));
        } catch (Exception ex) {
            callbackContext.error(String.format("Failed to deserialize job: '%s'.", ex));
            return;
        }

        db.upsertJob(job);
        callbackContext.success(true);
    }

    private void removeJob(String name, CallbackContext callbackContext) {
        callbackContext.success(db.removeJob(name));
    }

    private void getLocations(CallbackContext callbackContext) {
        List<Location> locations = db.getLocations();
        callbackContext.success(JSON.toJSON(locations));
    }

    private void upsertLocation(JSONObject locationData, CallbackContext callbackContext) {
        Location location = null;
        try {
            location = (Location)(JSON.fromJSON(locationData.toString()));
        } catch (Exception ex) {
            callbackContext.error(String.format("Failed to deserialize location: '%s'.", ex));
            return;
        }

        db.upsertLocation(location);
        callbackContext.success(true);
    }

    private void removeLocation(String name, CallbackContext callbackContext) {
        callbackContext.success(db.removeLocation(name));
    }

    private void sync(JSONObject data, CallbackContext callbackContext) {
        SyncJob job = null;
        try {
            job = (SyncJob)(JSON.fromJSON(data.toString()));
        } catch (Exception ex) {
            callbackContext.error(String.format("Failed to deserialize job: '%s'.", ex));
            return;
        }

        try {
            job.runNow();
            callbackContext.success(true);
        } catch (Exception ex) {
            callbackContext.error(String.format("Failed to run job: '%s'.", ex));
        }
    }

    private void testJob(JSONObject data, CallbackContext callbackContext) {
        SyncJob job = null;
        try {
            job = (SyncJob)(JSON.fromJSON(data.toString()));
        } catch (Exception ex) {
            callbackContext.error(String.format("Failed to deserialize job: '%s'.", ex));
            return;
        }

        try {
            callbackContext.success(job.test());
        } catch (Exception ex) {
            callbackContext.error(String.format("Failed to test job: '%s'.", ex));
        }
    }

    private void testLocation(JSONObject data, CallbackContext callbackContext) {
        Location location = null;
        try {
            location = (Location)(JSON.fromJSON(data.toString()));
        } catch (Exception ex) {
            callbackContext.error(String.format("Failed to deserialize location: '%s'.", ex));
            return;
        }

        try {
            callbackContext.success(location.test());
        } catch (Exception ex) {
            callbackContext.error(String.format("Failed to test location: '%s'.", ex));
        }
    }

    /* TEST
    // TEST - Schedule tasks in background?
    private void schedule() {
        // TEST

        WorkManager workManager = WorkManager.getInstance(cordova.getContext());
    }

    private static class SyncWorker extends Worker {
        public SyncWorker(Context context, WorkerParameters params) {
            super(context, params);
        }

        public ListenableWorker.Result doWork() {
            // TEST
        }
    }
    */
}
