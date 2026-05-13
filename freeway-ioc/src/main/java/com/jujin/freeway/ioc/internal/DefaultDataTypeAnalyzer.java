package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.advisor.StrategyRegistry;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.coercion.DataTypeAnalyzer;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.property.PropertyAdapter;
import java.util.Map;

/**
 * The default data type analyzer, which is based entirely on the type of the
 * property (and not on annotations or naming conventions). This is based on a
 * configuration of property type class to string provided as an IoC service
 * configuration.
 */
public class DefaultDataTypeAnalyzer implements DataTypeAnalyzer, Runnable {

    private final StrategyRegistry<String> registry;

    public DefaultDataTypeAnalyzer(Map<Class<?>, String> configuration) {
        registry = StrategyRegistry.newInstance(String.class, configuration);
    }

    /**
     * Clears the registry on an invalidation event (this is because the registry
     * caches results, and the keys are classes that may be component classes from
     * the invalidated component class loader).
     */
    public void run() {
        registry.clearCache();
    }

    public String identifyDataType(PropertyAdapter adapter) {
        Class<?> propertyType = adapter.getType();

        String dataType = registry.get(propertyType);

        // To avoid "no strategy" exceptions, we expect a contribution of Object.class
        // to the empty
        // string. We convert that back to a null.

        if (dataType.equals(""))
            return null;

        return dataType;
    }
}
