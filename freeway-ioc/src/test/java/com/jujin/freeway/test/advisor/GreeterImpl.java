package com.jujin.freeway.test.advisor;

public class GreeterImpl implements Greeter {

    @Override
    public String greet(String name) {
        return "Hello, " + name;
    }

    @Override
    public String farewell(String name) {
        return "Goodbye, " + name;
    }

    @Override
    public int countChars(String input) {
        return input != null ? input.length() : 0;
    }
}
