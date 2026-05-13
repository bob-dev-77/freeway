package com.jujin.freeway.test.inject;

public class TestServiceImpl1 implements TestService1 {
    private TestService3 t3;

    public TestServiceImpl1(TestService3 t3) {
        this.t3 = t3;
    }

    @Override
    public String sayHello() {
        return "I'm TestService-1";
    }

    @Override
    public String greetingTest3() {
        return "Hi TestService3" + t3.sayHelloWorld();
    }
}
