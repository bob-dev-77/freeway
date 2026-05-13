package com.jujin.freeway.boot.test.services;

public class GreeterImpl implements Greeter {
    @Override
    public String greet(String name) {
        return "Hello, " + name + "!";
    }
}
