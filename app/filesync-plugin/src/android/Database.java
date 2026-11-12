package dev.eriq.filesync.plugin;

import dev.eriq.filesync.SyncJob;
import dev.eriq.filesync.location.Location;
import dev.eriq.filesync.util.JSON;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

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

    public List<SyncJob> getJobs() {
        SQLiteDatabase db = getReadableDatabase();

        // TEST
        return null;
    }

    public void upsertJob(SyncJob job) {
        SQLiteDatabase db = getWritableDatabase();

        // TEST
    }

    public boolean removeJob(String name) {
        // TEST
        return false;
    }

    public List<Location> getLocations() {
        // TEST
        return null;
    }

    public void upsertLocation(Location location) {
        // TEST
    }

    public boolean removeLocation(String name) {
        // TEST
        return false;
    }

    private void createAll(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_LOCATIONS);
    }

    private void dropAll(SQLiteDatabase db) {
        db.execSQL(SQL_DROP_LOCATIONS);
    }

    private static final String SQL_CREATE_LOCATIONS = String.join("\n",
        "CREATE TABLE Locations (",
        "    name TEXT UNIQUE,",
        "    data TEXT,",
        ");"
    );

    private static final String SQL_DROP_LOCATIONS = String.join("\n",
        "DROP TABLE IF EXISTS Locations;"
    );
}
