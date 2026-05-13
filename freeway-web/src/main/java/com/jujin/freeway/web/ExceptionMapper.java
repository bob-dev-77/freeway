package com.jujin.freeway.web;

/**
 * Handles exceptions thrown during request processing. Mappers are tried in
 * order; the first to return {@code true} wins.
 *
 * <p>Contributed via {@code @Contribute(ExceptionMapperChain.class)}.</p>
 */
@FunctionalInterface
public interface ExceptionMapper {

    /**
     * Attempt to handle the exception. Return {@code true} if handled
     * (chain stops), {@code false} to try the next mapper.
     */
    boolean handle(HttpContext ctx, Exception e);
}
