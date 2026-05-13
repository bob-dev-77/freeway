package com.jujin.freeway.db.internal;

import com.jujin.freeway.db.Database;
import com.jujin.freeway.db.DbHub;

import java.util.Collections;
import java.util.Map;

public class DbHubImpl implements DbHub {

    private final Map<String, Database> map;

    public DbHubImpl(Map<String, Database> map) {
        this.map = Collections.unmodifiableMap(map);
    }

    @Override
    public Database get(String name) {
        return map.get(name);
    }

    @Override
    public Map<String, Database> all() {
        return map;
    }
}
