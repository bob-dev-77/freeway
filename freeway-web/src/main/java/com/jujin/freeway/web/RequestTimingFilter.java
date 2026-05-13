package com.jujin.freeway.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link HttpFilter} that logs request method, path, status, and duration.
 * Contribute to {@link HttpFilterChain} to enable.
 *
 * <pre>{@code
 * @Contribute(HttpFilterChain.class)
 * public static void filters(OrderedConfiguration<HttpFilter> config) {
 *     config.add("timing", RequestTimingFilter.create());
 * }
 * }</pre>
 */
public final class RequestTimingFilter {

    private static final Logger logger = LoggerFactory.getLogger(RequestTimingFilter.class);

    private RequestTimingFilter() {}

    public static HttpFilter create() {
        return (ctx, next) -> {
            long start = System.nanoTime();
            try {
                next.handle(ctx);
            } finally {
                long elapsed = (System.nanoTime() - start) / 1_000;
                logger.info("{} {} {}μs",
                    ctx.method(),
                    ctx.path(),
                    elapsed);
            }
        };
    }
}
