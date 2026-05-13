package com.jujin.freeway.test.inject;

import com.jujin.freeway.ioc.annotations.Inject;

public class FreewayInjectServiceImpl implements FreewayInjectService {
    @Inject
    private TestService1 service1;

    @Inject("Greeter")
    private Greeter namedGreeter;

    @Override
    public String greet() {
        return namedGreeter.sayHello();
    }

    @Override
    public String getGreeterName() {
        return service1.sayHello();
    }
}
