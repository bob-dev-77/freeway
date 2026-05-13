package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.exception.ExceptionAnalysis;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.exception.ExceptionInfo;

import static java.util.Collections.unmodifiableList;

import java.util.List;

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
