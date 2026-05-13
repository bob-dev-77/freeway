package com.jujin.freeway.web;

import java.util.List;

import com.jujin.freeway.ioc.annotations.UsesOrderedConfiguration;

/**
 * Collects contributed {@link HttpFilter} instances in order.
 * User modules contribute via {@code @Contribute(HttpFilterChain.class)}.
 */
@UsesOrderedConfiguration(HttpFilter.class)
public record HttpFilterChain(List<HttpFilter> filters) {
    public HttpFilterChain { filters = List.copyOf(filters); }
}
