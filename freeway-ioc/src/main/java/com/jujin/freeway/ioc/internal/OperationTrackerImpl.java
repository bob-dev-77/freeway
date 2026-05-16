package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.advisor.OperationTracker;
import com.jujin.freeway.ioc.internal.util.StringUtils;
import com.jujin.freeway.ioc.internal.util.ExceptionUtils;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.function.Supplier;
import org.slf4j.Logger;

/**
 * Core implementation that manages a logger and catches and reports exception.
 *
 * @see com.jujin.freeway.ioc.internal.PerThreadOperationTracker
 */
public class OperationTrackerImpl implements OperationTracker {

    private final Logger logger;

    private final Deque<String> operations = new ArrayDeque<>();

    private boolean logged;

    public OperationTrackerImpl(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void run(String description, final Runnable operation) {
        assert StringUtils.isNonBlank(description);
        assert operation != null;

        long startNanos = start(description);

        try {
            operation.run();

            finish(description, startNanos);
        } catch (RuntimeException ex) {
            logAndRethrow(ex);
        } catch (Error ex) {
            handleError(ex);
        } finally {
            handleFinally();
        }
    }

    @Override
    public <T> T invoke(String description, Supplier<T> operation) {
        assert StringUtils.isNonBlank(description);
        assert operation != null;

        long startNanos = start(description);

        try {
            T result = operation.get();

            finish(description, startNanos);

            return result;
        } catch (RuntimeException ex) {
            return handleRuntimeException(ex);
        } catch (Error ex) {
            return handleError(ex);
        } finally {
            handleFinally();
        }
    }

    @Override
    public <T> T perform(String description, IOOperation<T> operation)
        throws IOException {
        StringUtils.isNonBlank(description);
        assert operation != null;

        long startNanos = start(description);

        try {
            T result = operation.perform();

            finish(description, startNanos);

            return result;
        } catch (RuntimeException ex) {
            return handleRuntimeException(ex);
        } catch (Error ex) {
            return handleError(ex);
        } catch (IOException ex) {
            return logAndRethrow(ex);
        } finally {
            handleFinally();
        }
    }

    private <T> T handleRuntimeException(RuntimeException ex) {
        // This is to prevent the error level log messages
        if (
            ExceptionUtils.isAnnotationInStackTrace(
                ex,
                NonLoggableException.class
            )
        ) // pass through without logging
        throw ex;
        else return logAndRethrow(ex);
    }

    private void handleFinally() {
        operations.pop();
        // We've finally backed out of the operation stack ... but there may be more to
        // come!

        if (operations.isEmpty()) {
            logged = false;
        }
    }

    private <T> T handleError(Error error) {
        if (!logged) {
            log(error);
            logged = true;
        }

        throw error;
    }

    private void finish(String description, long startNanos) {
        if (logger.isDebugEnabled()) {
            long elapsedNanos = System.nanoTime() - startNanos;
            double elapsedMillis = ((double) elapsedNanos) / 1000000.d;

            logger.debug(
                String.format(
                    "[%3d] <-- %s [%,.2f ms]",
                    operations.size(),
                    description,
                    elapsedMillis
                )
            );
        }
    }

    private long start(String description) {
        long startNanos = -1l;

        if (logger.isDebugEnabled()) {
            startNanos = System.nanoTime();
            logger.debug(
                String.format(
                    "[%3d] --> %s",
                    operations.size() + 1,
                    description
                )
            );
        }

        operations.push(description);
        return startNanos;
    }

    private <T> T logAndRethrow(RuntimeException ex) {
        if (!logged) {
            String[] trace = log(ex);

            logged = true;

            throw new OperationException(ex, trace);
        }

        throw ex;
    }

    private <T> T logAndRethrow(IOException ex) throws IOException {
        if (!logged) {
            String[] trace = log(ex);

            logged = true;

            throw new OperationException(ex, trace);
        }

        throw ex;
    }

    private String[] log(Throwable ex) {
        logger.error(ExceptionUtils.toMessage(ex));
        logger.error("Operations trace:");

        var snapshot = new ArrayList<>(operations);
        Collections.reverse(snapshot);
        String[] trace = new String[snapshot.size()];

        for (int i = 0; i < snapshot.size(); i++) {
            trace[i] = snapshot.get(i).toString();

            logger.error(String.format("[%2d] %s", i + 1, trace[i]));
        }

        return trace;
    }

    boolean isEmpty() {
        return operations.isEmpty();
    }
}
