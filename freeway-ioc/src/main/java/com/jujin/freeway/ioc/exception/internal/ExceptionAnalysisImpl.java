package com.jujin.freeway.ioc.exception.internal;

import com.jujin.freeway.ioc.exception.ExceptionAnalysis;
import com.jujin.freeway.ioc.exception.ExceptionInfo;

import java.util.List;

import static java.util.Collections.unmodifiableList;

/**
 *
 */
public class ExceptionAnalysisImpl implements ExceptionAnalysis {
    private final List<ExceptionInfo> infos;

    public ExceptionAnalysisImpl(final List<ExceptionInfo> infos) {
        this.infos = unmodifiableList(infos);
    }

    @Override
    public List<ExceptionInfo> getExceptionInfos() {
        return infos;
    }

    @Override
    public String toString() {
        ExceptionInfo first = infos.get(0);

        return String.format("ExceptionAnalysis[%s -- %s]", first.getClassName(), first
            .getMessage());
    }
}
