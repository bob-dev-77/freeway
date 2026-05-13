package com.jujin.freeway.ioc.advisor;

/**
 * Provided by a {@link AdvisorDefinition} to perform the
 * advice (by invoking methods on a {@link MethodAdviceReceiver}).
 *
 */
public interface ServiceAdvisor {
    /**
     * Passed the reciever, allows the code (usually a method on a module class) to
     * advice some or all methods.
     *
     * @param methodAdviceReceiver
     */
    void advise(MethodAdviceReceiver methodAdviceReceiver);
}
