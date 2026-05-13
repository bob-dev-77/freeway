package com.jujin.freeway.ioc.exception;
import com.jujin.freeway.ioc.*;

/**
 * Analyzes an exception, providing an analysis. The analysis easily exposes
 * properties of the exception, the stack trace, and nested exceptions.
 */
public interface ExceptionAnalyzer {
    ExceptionAnalysis analyze(Throwable rootException);
}
