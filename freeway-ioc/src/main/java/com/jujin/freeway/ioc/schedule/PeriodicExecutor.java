package com.jujin.freeway.ioc.schedule;

/**
 * A service that executes a job at intervals specified by a {@link Schedule}.
 *
 */
public interface PeriodicExecutor {
    /**
     * Adds a job to be executed. The job is executed in a thread pool (via
     * {@link com.jujin.freeway.ioc.ParallelExecutor#invoke(java.util.function.Supplier)}),
     * as determined by the schedule.
     *
     * @param schedule
     *            defines when the job will next execute
     * @param name
     *            a name used in debugging output related to the job
     * @param job
     *            a Runnable object that represents the work to be done
     * @return a PeriodicJob that can be used to query when the job executes, or to
     *         cancel its execution
     */
    PeriodicJob addJob(Schedule schedule, String name, Runnable job);

    /**
     * Initializes this service. <em>Never call this method direclty. It's intended
     * for internal Freeway-IoC usage only</em>.
     */
    public void init();
}
