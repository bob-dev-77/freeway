package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.Resource;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.internal.util.InternalUtils;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.symbol.SymbolProvider;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.TreeMap;
import java.util.Properties;

/**
 * Makes a {@link com.jujin.freeway.ioc.Resource} available as a
 * {@link com.jujin.freeway.ioc.symbol.SymbolProvider}
 *
 */
public class ResourceSymbolProvider implements SymbolProvider {
    private final Resource resource;

    private final Map<String, String> properties = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public ResourceSymbolProvider(final Resource resource) {
        this.resource = resource;

        readProperties();
    }

    private void readProperties() {
        Properties p = new Properties();

        InputStream is = null;

        try {
            is = resource.openStream();

            p.load(is);

            is.close();

            is = null;

            for (Map.Entry<Object, Object> entry : p.entrySet()) {
                String key = entry.getKey().toString();

                properties.put(key, p.getProperty(key));
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        } finally {
            InternalUtils.close(is);
        }
    }

    @Override
    public String valueForSymbol(String symbolName) {
        return properties.get(symbolName);
    }
}
