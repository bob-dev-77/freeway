package com.jujin.freeway.test.inject;

import com.jujin.freeway.ioc.ServiceBinder;

/**
 * Module that binds multiple implementations for the same interface.
 */
public class MultiSvcModule {

    public static void bind(ServiceBinder binder) {
        binder.bind(MultiSvc.class, PrimaryMultiSvcImpl.class).withId("PrimaryMultiSvc");
        binder.bind(MultiSvc.class, SecondaryMultiSvcImpl.class).withId("SecondaryMultiSvc");
    }
}
