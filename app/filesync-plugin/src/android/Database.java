package dev.eriq.filesync.plugin;

import dev.eriq.filesync.Sync;
import dev.eriq.filesync.SyncJob;
import dev.eriq.filesync.errors.FileSyncError;
import dev.eriq.filesync.location.Location;
import dev.eriq.filesync.location.LocationUtils;
import dev.eriq.filesync.location.SSHLocation;
import dev.eriq.filesync.util.JSON;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Database extends SQLiteOpenHelper {
    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "FileSync.db";

    /**
     * Both a DB helper and an interface to the database itself.
     */
    public Database(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public void onCreate(SQLiteDatabase db) {
        createAll(db);
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        dropAll(db);
        onCreate(db);
    }

    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }

    public void reset() {
        SQLiteDatabase db = getWritableDatabase();
        dropAll(db);
        onCreate(db);
    }

    public void clearLogs() {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL(SQL_DROP_LOGS);
        db.execSQL(SQL_CREATE_LOGS);
    }

    public List<SyncJob> getJobs() {
        Map<String, Location> locations = getLocationsMap();

        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query("Jobs", null, null, null, null, null, null, null);

        List<SyncJob> jobs = new ArrayList<SyncJob>();
        while (cursor.moveToNext()) {
            String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
            String schedule = cursor.getString(cursor.getColumnIndexOrThrow("schedule"));
            String sourceName = cursor.getString(cursor.getColumnIndexOrThrow("sourceName"));
            String destName = cursor.getString(cursor.getColumnIndexOrThrow("destName"));
            Sync.ConflictBehavior conflictBehavior = Sync.ConflictBehavior.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("conflictBehavior")));

            String rawIdentityChecks = cursor.getString(cursor.getColumnIndexOrThrow("identityChecks")).strip();
            EnumSet<Sync.IdentityCheck> identityChecks = EnumSet.noneOf(Sync.IdentityCheck.class);
            if (rawIdentityChecks.length() > 0) {
                for (String rawCheck : rawIdentityChecks.split(",")) {
                    identityChecks.add(Sync.IdentityCheck.valueOf(rawCheck.strip()));
                }
            }

            Location source = locations.get(sourceName);
            if (source == null) {
                throw new FileSyncError(String.format("Could not find matching source location for name: '%s'.", sourceName));
            }

            Location dest = locations.get(destName);
            if (dest == null) {
                throw new FileSyncError(String.format("Could not find matching dest location for name: '%s'.", destName));
            }

            Sync sync = new Sync(source, dest, identityChecks, conflictBehavior);
            jobs.add(new SyncJob(name, schedule, sync));
        }
        cursor.close();

        return jobs;
    }

    /**
     * Get a job from the DB by name.
     * Return null if no matching job is found.
     */
    public SyncJob getJob(String name) {
        if (name == null) {
            return null;
        }

        List<SyncJob> jobs = getJobs();
        for (SyncJob job : jobs) {
            if (name.equals(job.getName())) {
                return job;
            }
        }

        return null;
    }

    public void upsertJob(SyncJob job) {
        Map<String, Object> jobData = job.toMap();
        Map<String, Object> syncData = (Map<String, Object>)(jobData.get("sync"));

        ContentValues values = new ContentValues();
        values.put("name", (String)(jobData.get("name")));
        values.put("schedule", (String)(jobData.get("schedule")));
        values.put("sourceName", (String)(((Map<String, Object>)(syncData.get("source"))).get("name")));
        values.put("destName", (String)(((Map<String, Object>)(syncData.get("dest"))).get("name")));
        values.put("identityChecks", String.join(",", (List<String>)(syncData.get("identity-checks"))));
        values.put("conflictBehavior", (String)(syncData.get("conflict-behavior")));

        SQLiteDatabase db = getWritableDatabase();
        db.insertWithOnConflict("Jobs", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public boolean removeJob(String name) {
        SQLiteDatabase db = getWritableDatabase();
        int count = db.delete("Jobs", "name = ?", new String[]{name});
        return (count > 0);
    }

    public List<Location> getLocations() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query("Locations", null, null, null, null, null, null, null);

        List<Location> locations = new ArrayList<Location>();
        while (cursor.moveToNext()) {
            String className = cursor.getString(cursor.getColumnIndexOrThrow("class"));
            String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));

            Map<String, Object> data = new HashMap<String, Object>();
            data.put("name", cursor.getString(cursor.getColumnIndexOrThrow("name")));
            data.put("base-dir", cursor.getString(cursor.getColumnIndexOrThrow("baseDir")));
            data.put("hostname", cursor.getString(cursor.getColumnIndexOrThrow("hostname")));
            data.put("port", cursor.getInt(cursor.getColumnIndexOrThrow("port")));
            data.put("username", cursor.getString(cursor.getColumnIndexOrThrow("username")));
            data.put("password", cursor.getString(cursor.getColumnIndexOrThrow("password")));
            data.put("public-key", cursor.getString(cursor.getColumnIndexOrThrow("publicKey")));
            data.put("private-key", cursor.getString(cursor.getColumnIndexOrThrow("privateKey")));

            Location location = LocationUtils.createLocation(className, data);
            locations.add(location);
        }
        cursor.close();

        return locations;
    }

    /**
     * Get locations keyed by name.
     */
    public Map<String, Location> getLocationsMap() {
        Map<String, Location> locations = new HashMap<String, Location>();
        for (Location location : getLocations()) {
            locations.put(location.getName(), location);
        }

        return locations;
    }

    public void upsertLocation(Location location) {
        Map<String, Object> data = location.toMap();

        ContentValues values = new ContentValues();
        values.put("name", (String)(data.get("name")));
        values.put("class", (String)(data.get("__class__")));
        values.put("baseDir", (String)(data.get("base-dir")));
        values.put("hostname", (String)(data.getOrDefault("hostname", null)));
        values.put("port", "" + data.getOrDefault("port", null));
        values.put("username", (String)(data.getOrDefault("username", null)));
        values.put("password", (String)(data.getOrDefault("password", null)));
        values.put("publicKey", (String)(data.getOrDefault("public-key", null)));
        values.put("privateKey", (String)(data.getOrDefault("private-key", null)));

        SQLiteDatabase db = getWritableDatabase();
        db.insertWithOnConflict("Locations", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public boolean removeLocation(String name) {
        SQLiteDatabase db = getWritableDatabase();
        int count = db.delete("Locations", "name = ?", new String[]{name});
        return (count > 0);
    }

    /**
     * Get log records.
     * A limit <= 0 will fetch all records.
     */
    public List<LogRecord> getLogs(int limit) {
        String stringLimit = null;
        if (limit > 0) {
            stringLimit = "" + limit;
        }

        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query("Logs", null, null, null, null, null, "timestamp ASC", stringLimit);

        List<LogRecord> logs = new ArrayList<LogRecord>();
        while (cursor.moveToNext()) {
            long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"));
            String message = cursor.getString(cursor.getColumnIndexOrThrow("message"));
            String jobName = cursor.getString(cursor.getColumnIndexOrThrow("jobName"));
            Map<String, Object> data = JSON.parseObject(cursor.getString(cursor.getColumnIndexOrThrow("data")));

            logs.add(new LogRecord(timestamp, message, jobName, data));
        }
        cursor.close();

        return logs;
    }

    public void appendLog(String message) {
        appendLog(message, null, null);
    }

    public void appendLog(String message, String jobName) {
        appendLog(message, jobName, null);
    }

    public void appendLog(String message, Map<String, Object> data) {
        appendLog(message, null, data);
    }

    public void appendLog(String message, String jobName, Map<String, Object> data) {
        if (data == null) {
            data = new HashMap<String, Object>();
        }

        ContentValues values = new ContentValues();
        values.put("timestamp", System.currentTimeMillis());
        values.put("message", message);
        values.put("jobName", jobName);
        values.put("data", JSON.toJSON(data));

        SQLiteDatabase db = getWritableDatabase();
        db.insertOrThrow("Logs", null, values);
    }

    private void createAll(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_LOGS);
        db.execSQL(SQL_CREATE_LOCATIONS);
        db.execSQL(SQL_CREATE_JOBS);
    }

    private void dropAll(SQLiteDatabase db) {
        db.execSQL(SQL_DROP_JOBS);
        db.execSQL(SQL_DROP_LOCATIONS);
        db.execSQL(SQL_DROP_LOGS);
    }

    private static final String SQL_CREATE_JOBS = String.join("\n",
        "CREATE TABLE Jobs (",
        "    name TEXT PRIMARY KEY,",
        "    schedule TEXT NOT NULL,",
        "    sourceName TEXT REFERENCES Locations(name) NOT NULL,",
        "    destName TEXT REFERENCES Locations(name) NOT NULL,",
        "    identityChecks TEXT NOT NULL,",
        "    conflictBehavior TEXT NOT NULL",
        ");"
    );

    private static final String SQL_CREATE_LOCATIONS = String.join("\n",
        "CREATE TABLE Locations (",
        "    name TEXT PRIMARY KEY,",
        "    class TEXT NOT NULL,",
        "    baseDir TEXT NOT NULL,",
        "    hostname TEXT,",
        "    port INTEGER,",
        "    username TEXT,",
        "    password TEXT,",
        "    publicKey TEXT,",
        "    privateKey TEXT",
        ");"
    );

    private static final String SQL_CREATE_LOGS = String.join("\n",
        "CREATE TABLE Logs (",
        "    timestamp INTEGER NOT NULL,",
        "    message TEXT NOT NULL,",
        "    jobName TEXT,",
        "    data TEXT NOT NULL",
        ");"
    );

    private static final String SQL_DROP_JOBS = String.join("\n",
        "DROP TABLE IF EXISTS Jobs;"
    );

    private static final String SQL_DROP_LOCATIONS = String.join("\n",
        "DROP TABLE IF EXISTS Locations;"
    );

    private static final String SQL_DROP_LOGS = String.join("\n",
        "DROP TABLE IF EXISTS Logs;"
    );

    public static class LogRecord extends JSON.MapConverter {
        public long timestamp;
        public String message;
        public String jobName;
        public Map<String, Object> data;

        public LogRecord(long timestamp, String message, String jobName, Map<String, Object> data) {
            this.timestamp = timestamp;
            this.message = message;
            this.jobName = jobName;

            if (data == null) {
                data = new HashMap<String, Object>();
            }

            this.data = data;
        }

        protected void serializeToMap(Map<String, Object> data) {
            data.put("timestamp", timestamp);
            data.put("message", message);
            data.put("jobName", jobName);
            data.put("data", this.data);
        }
    }
}
