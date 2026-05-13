package com.jujin.freeway.test.advisor;

public interface Greeter {

    String greet(String name);

    String farewell(String name);

    int countChars(String input);

    default String greetWithDefault(String name) {
        return "Default: Hello, " + name;
    }
}
