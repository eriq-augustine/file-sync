package dev.eriq.filesync.plugin;

import dev.eriq.filesync.Version;
import dev.eriq.filesync.Sync;
import dev.eriq.filesync.SyncJob;
import dev.eriq.filesync.errors.BadInputError;
import dev.eriq.filesync.location.LocalLocation;
import dev.eriq.filesync.location.Location;
import dev.eriq.filesync.location.LocationUtils;
import dev.eriq.filesync.plugin.Database;
import dev.eriq.filesync.util.JSON;
import dev.eriq.filesync.util.Lockfile;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Environment;
import android.os.Process;
import android.provider.Settings;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class FileSyncPlugin extends CordovaPlugin {
    private static final String ACTION_ACTIVATE = "activate";
    private static final String ACTION_DEACTIVATE = "deactivate";
    private static final String ACTION_SYNC = "sync";
    private static final String ACTION_VERSION = "version";

    private static final String ACTION_DB_JOBS_GET = "db-jobs-get";
    private static final String ACTION_DB_JOBS_REMOVE = "db-jobs-remove";
    private static final String ACTION_DB_JOBS_UPSERT = "db-jobs-upsert";
    private static final String ACTION_DB_LOCATIONS_GET = "db-locations-get";
    private static final String ACTION_DB_LOCATIONS_REMOVE = "db-locations-remove";
    private static final String ACTION_DB_LOCATIONS_UPSERT = "db-locations-upsert";
    private static final String ACTION_DB_LOGS_GET = "db-logs-get";
    private static final String ACTION_DB_LOGS_APPEND = "db-logs-append";

    private static final String ACTION_TEST_LOCATION = "test-location";
    private static final String ACTION_TEST_JOB = "test-job";

    private static final String[] PERMISSIONS = {
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.MANAGE_EXTERNAL_STORAGE,
    };

    private Database db;

    public FileSyncPlugin() {
        super();

        db = null;
    }

    @Override
    protected void pluginInitialize() {
        super.pluginInitialize();

        db = setupForjob(cordova.getContext());

        checkPermissions();

        runMaintenance(db, cordova.getContext());
        scheduleMaintenance(cordova.getContext());
    }

    /**
     * Do setup activities that are needed for a job to run and return a database to use.
     * Note that these are necessary outside the scope of a plugin class, since jobs may be run in the background.
     * Hence, they are grouped together in a single function.
     */
    private static Database setupForjob(Context context) {
        // Set the root for relative dirs.
        Path globalBaseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toPath().getParent();
        LocalLocation.setGlobalBaseDir(globalBaseDir);

        Lockfile.setFileContents("" + Process.myPid());

        return new Database(context);
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

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        // A zero length means the grating was interrupted.
        boolean granted = (grantResults.length != 0);

        for (int grantResult : grantResults) {
            if (grantResult == PackageManager.PERMISSION_DENIED) {
                granted = false;
                break;
            }
        }

        if (!granted) {
            System.err.println("Plugin permissions not grated.");
            return;
        }
    }

    private void checkPermissions() {
        // Normal Permissions

        List<String> needPermissions = new ArrayList<String>();
        for (String permission : PERMISSIONS) {
            if (!cordova.hasPermission(permission)) {
                needPermissions.add(permission);
            }
        }

        if (needPermissions.size() > 0) {
            cordova.requestPermissions(this, 0, needPermissions.toArray(new String[0]));
        }

        // Special file access permission.

        if (!Environment.isExternalStorageManager()) {
            Uri uri = Uri.parse("package:" + cordova.getContext().getPackageName());

            // Documentation says `ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION`, but `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` seems to work:
            // https://developer.android.com/training/data-storage/manage-all-files
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri);
            intent.addCategory("android.intent.category.DEFAULT");

            cordova.getContext().startActivity(intent);
        }
    }

    private void dispatchAction(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
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
                appendLog(args.getString(0), args.getString(1), objectToMap(args.getJSONObject(2)), callbackContext);
                return;
            case ACTION_ACTIVATE:
                activate(args.getString(0), callbackContext);
                return;
            case ACTION_DEACTIVATE:
                deactivate(args.getString(0), callbackContext);
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
            jobData.put("active", job.getActive());
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

    private void appendLog(String message, String jobName, Map<String, Object> data, CallbackContext callbackContext) {
        db.appendLog(message, jobName, data);
        callbackContext.success(true);
    }

    private void sync(Map<String, Object> jobData, CallbackContext callbackContext) {
        SyncJob job = buildJob(jobData);
        schedule(job, cordova.getContext(), false, true);
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

    private void activate(String jobName, CallbackContext callbackContext) {
        SyncJob job = db.getJob(jobName);
        if (job == null) {
            callbackContext.error(String.format("Failed to activate job '%s', could not find job in database.", jobName));
            return;
        }

        job.setActive(true);
        db.upsertJob(job);

        schedule(job, cordova.getContext());

        callbackContext.success(true);
    }

    private void deactivate(String jobName, CallbackContext callbackContext) {
        SyncJob job = db.getJob(jobName);
        if (job == null) {
            callbackContext.error(String.format("Failed to deactivate job '%s', could not find job in database.", jobName));
            return;
        }

        job.setActive(false);
        db.upsertJob(job);

        unschedule(job, cordova.getContext());

        callbackContext.success(true);
    }

    private static void schedule(SyncJob job, Context context) {
        schedule(job, context, false, false);
    }

    private static void schedule(SyncJob job, Context context, boolean appendWork, boolean isManual) {
        Data workData = new Data.Builder()
            .putString("jobName", job.getName())
            .putBoolean("isManual", isManual)
            .build()
            ;

        NetworkType requiredNetworkType = NetworkType.UNMETERED;
        if (isManual) {
            requiredNetworkType = NetworkType.CONNECTED;
        }

        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(requiredNetworkType)
            .build()
            ;

        long delaySecs = 1;
        if (!isManual) {
            delaySecs = job.getNextActivationTimeSeconds();
        }

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(SyncWorker.class)
           .setInitialDelay(delaySecs, TimeUnit.SECONDS)
           .setInputData(workData)
           .addTag("dev.eriq.filesync")
           .addTag("dev.eriq.filesync.job.run")
           .build()
           ;

        ExistingWorkPolicy conflictPloicy = ExistingWorkPolicy.KEEP;
        if (appendWork) {
            conflictPloicy = ExistingWorkPolicy.APPEND_OR_REPLACE;
        }

        WorkManager workManager = WorkManager.getInstance(context);
        workManager.enqueueUniqueWork(getWorkKey(job, isManual), conflictPloicy, workRequest);
    }

    private static void unschedule(SyncJob job, Context context) {
        WorkManager workManager = WorkManager.getInstance(context);
        workManager.cancelUniqueWork(getWorkKey(job, false));
    }

    private static void scheduleMaintenance(Context context) {
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(MaintenanceWorker.class, 1, TimeUnit.HOURS)
           .addTag("dev.eriq.filesync")
           .addTag("dev.eriq.filesync.maintenance")
           .build()
           ;

        WorkManager workManager = WorkManager.getInstance(context);
        workManager.enqueueUniquePeriodicWork("dev.eriq.filesync.maintenance", ExistingPeriodicWorkPolicy.KEEP, workRequest);
    }

    private static String getWorkKey(SyncJob job, boolean isManual) {
        String label = "background";
        if (isManual) {
            label = "manual";
        }

        return String.format("dev.eriq.filesync.job.run::%s::%s", job.getName(), label);
    }

    private static void runMaintenance(Database db, Context context) {
        for (SyncJob job : db.getJobs()) {
            if (job.getActive()) {
                schedule(job, context);
            } else {
                unschedule(job, context);
            }
        }
    }

    /**
     * Check if the connected network is one we want to transfer files on.
     * If false, avoid heavy traffic.
     */
    private static boolean networkUsable(Context context) {
        ConnectivityManager manager = (ConnectivityManager)(context.getSystemService(Context.CONNECTIVITY_SERVICE));
        return !manager.isActiveNetworkMetered();
    }

    public static class SyncWorker extends Worker {
        public SyncWorker(Context context, WorkerParameters params) {
            super(context, params);
        }

        @Override
        public ListenableWorker.Result doWork() {
            String jobName = getInputData().getString("jobName");
            boolean isManual = getInputData().getBoolean("isManual", false);

            try {
                prepAndRun(jobName, isManual);
            } catch (Exception ex) {
                ex.printStackTrace(System.err);
                return ListenableWorker.Result.failure();
            }

            return ListenableWorker.Result.success();
        }

        private void prepAndRun(String jobName, boolean isManual) {
            Database db = setupForjob(getApplicationContext());

            SyncJob job = db.getJob(jobName);
            if (job == null) {
                return;
            }

            try {
                run(job, db, isManual);
            } catch (Exception ex) {
                db.appendLog("Failed to run background job.", job.getName(), Map.of("error", ex.toString()));
                throw ex;
            } finally {
                db.close();
            }

            // Schedule the next run, making sure to append it to the uniqie work chain (since this task is still ongoing).
            if (!isManual) {
                schedule(job, getApplicationContext(), true, false);
            }
        }

        private void run(SyncJob job, Database db, boolean isManual) {
            // Check if this job is still active.
            if (!isManual && !job.getActive()) {
                db.appendLog("Job is no longer active, skipping run and not rescheduling.", job.getName());
                return;
            }

            String jobLabel = "Background";
            if (isManual) {
                jobLabel = "Manual";
            }

            if (!isManual && !networkUsable(getApplicationContext())) {
                db.appendLog("Network is not suitable, skipping.", job.getName());
                return;
            }

            // Run the job.
            try {
                db.appendLog(String.format("%s sync started.", jobLabel), job.getName());
                Sync.SyncResult result = job.runNow();
                db.appendLog(String.format("%s sync completed successfully.", jobLabel), job.getName(), result.toMap());
            } catch (Exception ex) {
                ex.printStackTrace(System.err);
                db.appendLog(String.format("%s job run failed.", jobLabel), job.getName(), Map.of("error", ex.toString()));
            }
        }
    }

    public static class MaintenanceWorker extends Worker {
        public MaintenanceWorker(Context context, WorkerParameters params) {
            super(context, params);
        }

        public ListenableWorker.Result doWork() {
            Database db = null;

            try {
                db = setupForjob(getApplicationContext());
                runMaintenance(db, getApplicationContext());
            } catch (Exception ex) {
                ex.printStackTrace(System.err);
                db.appendLog("Failed to run maintenance.", null, Map.of("error", ex.toString()));
                return ListenableWorker.Result.failure();
            } finally {
                db.close();
            }

            return ListenableWorker.Result.success();
        }
    }
}
