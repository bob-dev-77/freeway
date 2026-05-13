package com.jujin.freeway.ioc.advisor;

/**
 * An advisor that identifies methods which can be evaluated lazily and advises
 * them. A method can be evaluated lazily if it returns an interface type and if
 * it throws no checked exceptions. Lazy evaluation should be handled carefully,
 * as if any of the parameters to a method are mutable, or the internal state of
 * the invoked service changes, the lazily evaluated results may not match the
 * immediately evaluated result. This effect is greatly exaggerated if the lazy
 * return object is evaluated in a different thread than when it was generated.
 * <p>
 * Another consideration is that exceptions that would occur immediately in the
 * non-lazy case are also deferred, often losing much context in the process.
 * <p>
 * Use laziness with great care.
 * <p>
 * Use the {@link com.jujin.freeway.ioc.annotations.NotLazy} annotation on
 * methods that should not be advised.
 *
 */
public interface LazyAdvisor {

    void addLazyMethodInvocationAdvice(MethodAdviceReceiver methodAdviceReceiver);

}
