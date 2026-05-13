package com.jujin.freeway.test.inject;

import javax.inject.Inject;

public class TestServiceImpl2 implements TestService2 {
    @Inject
    private TestService1 test1;

    @Override
    public String sayBye() {
        return test1.sayHello() + " T1 Injected " + "bye TestService 2";
    }
}
