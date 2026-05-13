package com.jujin.freeway.web;

import java.util.List;

import com.jujin.freeway.ioc.annotations.UsesOrderedConfiguration;

/**
 * Collects contributed {@link ExceptionMapper} instances in order.
 * The first mapper to return {@code true} wins.
 */
@UsesOrderedConfiguration(ExceptionMapper.class)
public record ExceptionMapperChain(List<ExceptionMapper> mappers) {
    public ExceptionMapperChain { mappers = List.copyOf(mappers); }
}
