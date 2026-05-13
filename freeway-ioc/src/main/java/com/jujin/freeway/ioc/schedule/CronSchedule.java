package com.jujin.freeway.ioc.schedule;


import java.text.ParseException;
import java.util.Date;
import java.util.TimeZone;

public class CronSchedule implements Schedule {
    private CronExpression cron;

    public CronSchedule(String cronExpression) {
        this(cronExpression, TimeZone.getDefault());
    }

    public CronSchedule(String cronExpression, TimeZone timeZone) {
        try {
            this.cron = new CronExpression(cronExpression);
            this.cron.setTimeZone(timeZone);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public long firstExecution() {
        return getNextValidTimeAfterNow();
    }

    @Override
    public long nextExecution(long previousExecution) {
        return getNextValidTimeAfterNow();
    }

    private long getNextValidTimeAfterNow() {
        final Date time = cron.getNextValidTimeAfter(new Date());

        return time == null ? 0 : time.getTime();
    }
}
