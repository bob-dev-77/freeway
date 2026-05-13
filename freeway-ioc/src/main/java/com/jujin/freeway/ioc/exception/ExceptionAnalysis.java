package com.jujin.freeway.ioc.exception;

import java.util.List;

/**
 * An analysis of an exception (including nested exceptions).
 * <p>
 * TODO: Make serializable and/or convert to XML format.
 */
public interface ExceptionAnalysis {
    /**
     * Returns the analyzed exception info for each exception. The are ordered
     * outermost exception to innermost.
     */
    List<ExceptionInfo> getExceptionInfos();
}
