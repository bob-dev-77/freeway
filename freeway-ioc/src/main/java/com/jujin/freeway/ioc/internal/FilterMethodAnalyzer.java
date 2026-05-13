package com.jujin.freeway.ioc.internal;

/**
 * Used by {@link com.jujin.freeway.ioc.internal.PipelineBuilderImpl} to analyze
 * service interface methods against filter interface methods to find the
 * position of the extra service parameter (in the filter method).
 */
public class FilterMethodAnalyzer {

    private final Class<?> serviceInterface;

    FilterMethodAnalyzer(Class<?> serviceInterface) {
        this.serviceInterface = serviceInterface;
    }

    public int findServiceInterfacePosition(
        MethodSignature ms,
        MethodSignature fms) {
        if (ms.getReturnType() != fms.getReturnType())
            return -1;

        if (!ms.getName().equals(fms.getName()))
            return -1;

        Class<?>[] filterParameters = fms.getParameterTypes();
        int filterParameterCount = filterParameters.length;
        Class<?>[] serviceParameters = ms.getParameterTypes();

        if (filterParameterCount != (serviceParameters.length + 1))
            return -1;

        // TODO: check compatible exceptions!

        // This needs work; it assumes the first occurance of the service interface
        // in the filter interface method signature is the right match. That will suit
        // most of the time.

        boolean found = false;
        int result = -1;

        for (int i = 0; i < filterParameterCount; i++) {
            if (filterParameters[i] == serviceInterface) {
                result = i;
                found = true;
                break;
            }
        }

        if (!found)
            return -1;

        // Check that all the parameters before and after the service interface still
        // match.

        for (int i = 0; i < result; i++) {
            if (filterParameters[i] != serviceParameters[i])
                return -1;
        }

        for (int i = result + 1; i < filterParameterCount; i++) {
            if (filterParameters[i] != serviceParameters[i - 1])
                return -1;
        }

        return result;
    }
}
