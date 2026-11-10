package dev.eriq.filesync;

import dev.eriq.filesync.Sync;
import dev.eriq.filesync.errors.BadInputError;
import dev.eriq.filesync.util.JSON;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;

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
    private Cron schedule;
    private Sync sync;

    public SyncJob(Cron schedule, Sync sync) {
        this.schedule = schedule;
        this.sync = sync;
    }

    public SyncJob(String scheduleText, Sync sync) {
        this(parseSchedule(scheduleText), sync);
    }

    protected void serializeToMap(Map<String, Object> data) {
        data.put("schedule", schedule.asString());
        data.put("sync", sync.toMap());
    }

    public static Object fromMap(Map<String, Object> data) {
        Cron schedule = parseSchedule(data.get("schedule").toString());
        Sync sync = (Sync)(JSON.fromMap((Map)(data.get("sync"))));

        return new SyncJob(schedule, sync);
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
            this.schedule.asString().equals(otherJob.schedule.asString())
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
    public void runNow() {
        sync.sync();
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
