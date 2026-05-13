package com.jujin.freeway.boot.test.services;

import java.util.Map;

public interface Store {
    void put(String key, String value);

    String get(String key);

    Map<String, String> snapshot();
}
