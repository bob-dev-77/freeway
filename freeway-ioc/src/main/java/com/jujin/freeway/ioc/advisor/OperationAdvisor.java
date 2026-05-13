package com.jujin.freeway.ioc.advisor;

/**
 * Used from a {@linkplain com.jujin.freeway.ioc.annotations.Advise service
 * advice method} to identify methods with the
 * {@link com.jujin.freeway.ioc.annotations.Operation} annotation, and add
 * advice for those methods. This advice should typically be provided first, or
 * nearly first, among all advice, to maximize the benefit of tracking
 * operations.
 *
 */
public interface OperationAdvisor {
    /**
     * Adds {@linkplain #createAdvice advice} to methods with the
     * {@link com.jujin.freeway.ioc.annotations.Operation} annotation.
     */
    void addOperationAdvice(MethodAdviceReceiver receiver);

    /**
     * Creates advice for a method.
     *
     * @param description
     *            the text (or format) used to display describe the operation for
     *            the method
     * @return method advice
     * @see com.jujin.freeway.ioc.annotations.Operation#value()
     */
    MethodAdvice createAdvice(String description);
}
