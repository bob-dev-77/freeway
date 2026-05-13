package com.jujin.freeway.boot.test;

import com.jujin.freeway.boot.FreewayBootEntry;
import com.jujin.freeway.boot.test.services.Greeter;
import com.jujin.freeway.boot.test.services.GreeterImpl;
import com.jujin.freeway.boot.test.services.Store;
import com.jujin.freeway.boot.test.services.StoreImpl;
import com.jujin.freeway.ioc.ServiceBinder;

/**
 * Test application class — serves as both the @FreewayBootApplication entry
 * point and an IoC module with bind() / build*() methods.
 */
@FreewayBootEntry
public class TestBootApp {

    public static void bind(ServiceBinder binder) {
        binder.bind(Greeter.class, GreeterImpl.class);
        binder.bind(Store.class, StoreImpl.class);
    }
}
