package dev.eriq.filesync.plugin;

import dev.eriq.filesync.Version;
import dev.eriq.filesync.Sync;
import dev.eriq.filesync.SyncJob;
import dev.eriq.filesync.errors.BadInputError;
import dev.eriq.filesync.location.Location;
import dev.eriq.filesync.location.LocationUtils;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileSyncPlugin extends CordovaPlugin {
    private static final String ACTION_SYNC = "sync";
    private static final String ACTION_TEST_LOCATION = "test-location";
    private static final String ACTION_TEST_JOB = "test-job";
    private static final String ACTION_VERSION = "version";

    private static final String ACTION_DB_JOBS_GET = "db-jobs-get";
    private static final String ACTION_DB_JOBS_REMOVE = "db-jobs-remove";
    private static final String ACTION_DB_JOBS_UPSERT = "db-jobs-upsert";
    private static final String ACTION_DB_LOCATIONS_GET = "db-locations-get";
    private static final String ACTION_DB_LOCATIONS_REMOVE = "db-locations-remove";
    private static final String ACTION_DB_LOCATIONS_UPSERT = "db-locations-upsert";
    private static final String ACTION_DB_LOGS_GET = "db-logs-get";
    private static final String ACTION_DB_LOGS_APPEND = "db-logs-append";

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
        if (db != null) {
            db.close();
            db = null;
        }

        super.onDestroy();
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this.cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    dispatchAction(action, args, callbackContext);
                } catch (Exception ex) {
                    ex.printStackTrace(System.err);
                    callbackContext.error(String.format("Failed to run action '%s': '%s'.", action, ex));
                }
            }
        });

        return true;
    }

    public void dispatchAction(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        switch (action) {
            case ACTION_DB_JOBS_GET:
                getJobs(callbackContext);
                return;
            case ACTION_DB_JOBS_REMOVE:
                removeJob(args.getString(0), callbackContext);
                return;
            case ACTION_DB_JOBS_UPSERT:
                upsertJob(objectToMap(args.getJSONObject(0)), callbackContext);
                return;
            case ACTION_DB_LOCATIONS_GET:
                getLocations(callbackContext);
                return;
            case ACTION_DB_LOCATIONS_REMOVE:
                removeLocation(args.getString(0), callbackContext);
                return;
            case ACTION_DB_LOCATIONS_UPSERT:
                upsertLocation(objectToMap(args.getJSONObject(0)), callbackContext);
                return;
            case ACTION_DB_LOGS_GET:
                getLogs(args.getInt(0), callbackContext);
                return;
            case ACTION_DB_LOGS_APPEND:
                appendLog(args.getString(0), objectToMap(args.getJSONObject(1)), callbackContext);
                return;
            case ACTION_SYNC:
                sync(objectToMap(args.getJSONObject(0)), callbackContext);
                return;
            case ACTION_TEST_JOB:
                testJob(objectToMap(args.getJSONObject(0)), callbackContext);
                return;
            case ACTION_TEST_LOCATION:
                testLocation(objectToMap(args.getJSONObject(0)), callbackContext);
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

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (SyncJob job : jobs) {
            Map<String, Object> jobData = job.getSync().toMap();
            jobData.remove("__class__");

            jobData.put("name", job.getName());
            jobData.put("schedule", job.getSchedule());

            jobData.put("sourceName", job.getSync().getSource().getName());
            jobData.put("destName", job.getSync().getDest().getName());
            jobData.remove("source");
            jobData.remove("dest");

            result.add(jobData);
        }

        callbackContext.success(JSON.toJSONFromListMap(result));
    }

    private void upsertJob(Map<String, Object> jobData, CallbackContext callbackContext) {
        db.upsertJob(buildJob(jobData));
        callbackContext.success(true);
    }

    private void removeJob(String name, CallbackContext callbackContext) {
        callbackContext.success(db.removeJob(name));
    }

    private void getLocations(CallbackContext callbackContext) {
        List<Location> locations = db.getLocations();
        callbackContext.success(JSON.toJSON(locations));
    }

    private void upsertLocation(Map<String, Object> locationData, CallbackContext callbackContext) {
        db.upsertLocation(buildLocation(locationData));
        callbackContext.success(true);
    }

    private void removeLocation(String name, CallbackContext callbackContext) {
        callbackContext.success(db.removeLocation(name));
    }

    private void getLogs(int limit, CallbackContext callbackContext) {
        List<Database.LogRecord> records = db.getLogs(limit);
        callbackContext.success(JSON.toJSON(records));
    }

    private void appendLog(String message, Map<String, Object> data, CallbackContext callbackContext) {
        db.appendLog(message, data);
        callbackContext.success(true);
    }

    private void sync(Map<String, Object> jobData, CallbackContext callbackContext) {
        SyncJob job = buildJob(jobData);

        try {
            job.runNow();
        } catch (Exception ex) {
            callbackContext.error(String.format("Failed to run job: '%s'.", ex));
            return;
        }

        callbackContext.success(true);
    }

    private void testJob(Map<String, Object> jobData, CallbackContext callbackContext) {
        SyncJob job = buildJob(jobData);

        try {
            callbackContext.success(job.test());
        } catch (Exception ex) {
            callbackContext.error(String.format("Failed to test job: '%s'.", ex));
        }
    }

    private void testLocation(Map<String, Object> locationData, CallbackContext callbackContext) {
        Location location = buildLocation(locationData);

        try {
            callbackContext.success(location.test());
        } catch (Exception ex) {
            callbackContext.error(String.format("Failed to test location: '%s'.", ex));
        }
    }

    private SyncJob buildJob(Map<String, Object> jobData) {
        Map<String, Location> locations = db.getLocationsMap();

        String sourceName = jobData.getOrDefault("sourceName", "").toString();
        Location source = locations.get(sourceName);
        if (source == null) {
            throw new BadInputError(String.format("Could not find source: '%s'.", sourceName));
        }

        String destName = jobData.getOrDefault("destName", "").toString();
        Location dest = locations.get(destName);
        if (dest == null) {
            throw new BadInputError(String.format("Could not find dest: '%s'.", destName));
        }

        Map<String, Object> syncData = new HashMap<String, Object>();
        syncData.put("__class__", Sync.class.getName());
        syncData.put("source", source.toMap());
        syncData.put("dest", dest.toMap());
        syncData.put("identity-checks", jobData.get("identity-checks"));
        syncData.put("conflict-behavior", jobData.get("conflict-behavior"));

        jobData.put("__class__", SyncJob.class.getName());
        jobData.put("sync", syncData);

        try {
            return (SyncJob)(JSON.fromMap(jobData));
        } catch (Exception ex) {
            throw new BadInputError(String.format("Failed to deserialize job: '%s'.", ex), ex);
        }
    }

    private Location buildLocation(Map<String, Object> locationData) {
        String className = locationData.getOrDefault("__class__", "").toString();
        if (className == null) {
            throw new BadInputError(String.format("No class/type specified for location '%s'.", locationData.getOrDefault("name", "<unknown>")));
        }

        return LocationUtils.createLocation(className, locationData);
    }

    /**
     * Convert a JSON object to a map (while being careful to avoid issues with mismatched org.json versions).
     * Will return an empty map if null is passed in.
     */
    private Map<String, Object> objectToMap(JSONObject data) {
        if (data == null) {
            return new HashMap<String, Object>();
        }

        return JSON.parseObject(data.toString());
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
