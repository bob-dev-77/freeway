package com.jujin.freeway.test.inject;

import javax.inject.Inject;

public class TestServiceImpl3 implements TestService3 {
    private TestService2 testService2;

    @Inject
    public TestServiceImpl3(TestService2 ts2) {
        this.testService2 = ts2;
    }

    @Override
    public String sayHelloWorld() {

        return "Hello World!" + " TestService2 : " + testService2.sayBye();
    }
}
