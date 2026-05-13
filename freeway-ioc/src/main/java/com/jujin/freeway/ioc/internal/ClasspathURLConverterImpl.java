package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.classpath.ClassPathURLConverter;

import java.net.URL;

/**
 * Default implementation that returns the URLs unchanged.
 */
public class ClassPathURLConverterImpl implements ClassPathURLConverter {
    @Override
    public URL convert(URL url) {
        return url;
    }
}
