package com.jujin.freeway.ioc.schedule;

/**
 */
public interface PeriodicJob {
    /**
     * Returns the name for the job, supplied when the job is created; this is not
     * unique or meaningful, and primarily exists to assist with debugging.
     *
     * @return name provided for the job
     */
    String getName();

    /**
     * Is this Job currently executing (or queued, awaiting execution)?
     *
     * @return true if executing
     */
    boolean isExecuting();

    /**
     * Has this job been canceled.
     */
    boolean isCanceled();

    /**
     * Cancels the job. If currently executing, the Job will finish (this includes
     * awaiting execution). If not currently executing, the job is discarded
     * immediately.
     */
    void cancel();
}
