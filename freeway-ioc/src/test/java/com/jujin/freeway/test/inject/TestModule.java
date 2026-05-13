package com.jujin.freeway.test.inject;

import com.jujin.freeway.ioc.ServiceBinder;

public class TestModule {

    public static void bind(ServiceBinder binder) {
        binder.bind(Greeter.class, GreeterImpl.class).withId("Greeter");
        binder.bind(TestService2.class, TestServiceImpl2.class);
        binder.bind(TestService3.class, TestServiceImpl3.class);
        binder.bind(FreewayInjectService.class, FreewayInjectServiceImpl.class);
    }

    public TestService1 buildTest1(TestService3 t3) {
        return new TestServiceImpl1(t3);
    }
}
