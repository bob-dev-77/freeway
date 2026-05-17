package com.jujin.freeway.ioc;

import com.jujin.freeway.ioc.annotations.Builtin;
import com.jujin.freeway.ioc.annotations.Marker;
import com.jujin.freeway.ioc.scan.ClassNameLocator;
import com.jujin.freeway.ioc.scan.ClassPathScanner;
import com.jujin.freeway.ioc.scan.ClassPathURLConverter;
import com.jujin.freeway.ioc.scan.internal.ClassNameLocatorImpl;
import com.jujin.freeway.ioc.scan.internal.ClassPathScannerImpl;
import com.jujin.freeway.ioc.scan.internal.ClassPathURLConverterImpl;

@Marker(Builtin.class)
public class ScanModule {

    public static void bind(ServiceBinder binder) {
        binder.bind(ClassNameLocator.class, ClassNameLocatorImpl.class);
        binder.bind(ClassPathScanner.class, ClassPathScannerImpl.class);
        binder.bind(
            ClassPathURLConverter.class,
            ClassPathURLConverterImpl.class
        );
    }
}
