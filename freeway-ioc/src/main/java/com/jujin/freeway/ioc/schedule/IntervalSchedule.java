package com.jujin.freeway.ioc.schedule;

/**
 * A very simple schedule, that simply executes the desired job at fixed
 * intervals.
 *
 */
public class IntervalSchedule implements Schedule {
    private final long interval;

    /**
     * Interval at which the schedule should execute jobs. The first execution is
     * delayed from current time by the interval as well.
     *
     * @param interval
     *            in milliseconds
     */
    public IntervalSchedule(long interval) {
        assert interval > 0;

        this.interval = interval;
    }

    @Override
    public long firstExecution() {
        return nextExecution(System.currentTimeMillis());
    }

    @Override
    public long nextExecution(long previousExecution) {
        return previousExecution + interval;
    }
}
