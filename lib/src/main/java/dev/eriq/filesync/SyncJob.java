package dev.eriq.filesync;

import dev.eriq.filesync.Sync;
import dev.eriq.filesync.errors.BadInputError;
import dev.eriq.filesync.util.JSON;
import dev.eriq.filesync.util.Lockfile;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.Map;

/**
 * A representation of some Sync that occurs periodically.
 *
 * Schedule specification is done with a Unix Cron expression.
 * See:
 *  - <a href="https://en.wikipedia.org/wiki/Cron">Wikipedia</a>
 *  - <a href="https://crontab.guru/">Helper Tool</a>
 */
public class SyncJob extends JSON.MapConverter {
    /**
     * The name of a job.
     * Jobs with the same name are considered the same job in terms for storage (DB key) and locking.
     */
    private String name;

    private Cron schedule;
    private Sync sync;
    private boolean active;

    public SyncJob(String name, Cron schedule, Sync sync, boolean active) {
        this.name = name;
        this.schedule = schedule;
        this.sync = sync;
        this.active = active;
    }

    public SyncJob(String name, String scheduleText, Sync sync, boolean active) {
        this(name, parseSchedule(scheduleText), sync, active);
    }

    public SyncJob(String name, Cron schedule, Sync sync) {
        this(name, schedule, sync, false);
    }

    public SyncJob(String name, String scheduleText, Sync sync) {
        this(name, parseSchedule(scheduleText), sync);
    }

    public SyncJob(String scheduleText, Sync sync) {
        this("", scheduleText, sync);
    }

    public Sync getSync() {
        return sync;
    }

    public String getName() {
        return name;
    }

    public String getSchedule() {
        return schedule.asString();
    }

    public boolean getActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    protected void serializeToMap(Map<String, Object> data) {
        data.put("name", name);
        data.put("schedule", schedule.asString());
        data.put("sync", sync.toMap());
        data.put("active", Boolean.valueOf(active));
    }

    public static Object fromMap(Map<String, Object> data) {
        String name = data.getOrDefault("name", "").toString();
        Cron schedule = parseSchedule(data.get("schedule").toString());
        Sync sync = (Sync)(JSON.fromMap((Map)(data.get("sync"))));
        boolean active = Boolean.TRUE.equals(data.getOrDefault("active", Boolean.FALSE));

        return new SyncJob(name, schedule, sync, active);
    }

    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }

        if (other == null || !(other instanceof SyncJob)) {
            return false;
        }

        SyncJob otherJob = (SyncJob)other;
        return (
            (this.active == otherJob.active)
            && this.name.equals(otherJob.name)
            && this.schedule.asString().equals(otherJob.schedule.asString())
            && this.sync.equals(otherJob.sync)
        );
    }

    /**
     * Test that this job is valid.
     * Return null on success, and an error message on failure.
     */
    public String test() {
        return sync.test();
    }

    /**
     * Run a sync job now regardless of the schedule.
     */
    public Sync.SyncResult runNow(Path lockPath) {
        Lockfile lock = new Lockfile(lockPath);

        Sync.SyncResult result = null;
        try {
            lock.acquire();
            result = sync.sync();
        } finally {
            lock.release();
        }

        return result;
    }

    /**
     * Like runNow(), but with the default lock path for this job.
     */
    public Sync.SyncResult runNow() {
        return runNow(Paths.get(System.getProperty("java.io.tmpdir"), getLockName()));
    }

    /**
     * Get the recommended locking key for this job.
     * Will use the name if one exists, and fallback to a general one (which will lock any other jobs without a name).
     */
    public String getLockName() {
        String textName = "";
        if (name != null) {
            textName = name;
        }

        // Clean
        textName = textName.strip().replaceAll("\\W", "");

        // Add prefix.
        if (textName.length() != 0) {
            textName = "-" + textName;
        }

        return String.format("filesync-lock%s.pid", textName);
    }

    /**
     * Get the next time (after now) that this job should activate,
     * or null if there are no more matching times.
     */
    public ZonedDateTime getNextActivationTime() {
        ZonedDateTime now = ZonedDateTime.now();
        ExecutionTime executionTime = ExecutionTime.forCron(schedule);
        return executionTime.nextExecution(now).orElse(null);
    }

    /**
     * Get the number of seconds between the next activation time and now.
     */
    public long getNextActivationTimeSeconds() {
        ZonedDateTime nextTime = getNextActivationTime();
        long nowSecs = System.currentTimeMillis() / 1000;

        return nextTime.toEpochSecond() - nowSecs;
    }

    /**
     * Get the most recent previous time (before now) that this job should have activated,
     * or null if there are no matching times.
     */
    public ZonedDateTime getPreviousActivationTime() {
        ZonedDateTime now = ZonedDateTime.now();
        ExecutionTime executionTime = ExecutionTime.forCron(schedule);
        return executionTime.lastExecution(now).orElse(null);
    }

    public static Cron parseSchedule(String scheduleText) {
        CronParser parser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));

        try {
            return parser.parse(scheduleText);
        } catch (Exception ex) {
            throw new BadInputError(String.format("Schedule text ('%s') is not a valid Unix Cron expression.", scheduleText), ex);
        }
    }
}
