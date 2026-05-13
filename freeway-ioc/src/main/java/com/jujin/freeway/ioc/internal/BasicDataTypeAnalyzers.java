package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.config.MappedConfiguration;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.config.OrderedConfiguration;
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
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Class that provides Freeway's basic default data type analyzers.
 */
public class BasicDataTypeAnalyzers {

    public static void contributeDataTypeAnalyzer(
        OrderedConfiguration<DataTypeAnalyzer> configuration,
        DataTypeAnalyzer defaultDataTypeAnalyzer) {
        if (defaultDataTypeAnalyzer == null) {
            defaultDataTypeAnalyzer = createDefaultDataTypeAnalyzer();
        }
        configuration.add("Default", defaultDataTypeAnalyzer, "after:*");
    }

    public static DataTypeAnalyzer createDefaultDataTypeAnalyzer() {
        var mappedConfiguration = new DefaultDataTypeAnalyzerMappedConfiguration();
        provideDefaultDataTypeAnalyzers(mappedConfiguration);
        return new CombinedDataTypeAnalyzer(
            new DefaultDataTypeAnalyzer(mappedConfiguration.getMap()));
    }

    /**
     * Maps property types to data type names:
     * <ul>
     * <li>String --&gt; text
     * <li>Number --&gt; number
     * <li>Enum --&gt; enum
     * <li>Boolean --&gt; boolean
     * <li>Date --&gt; date
     * </ul>
     */
    public static void provideDefaultDataTypeAnalyzers(
        MappedConfiguration<Class<?>, String> configuration) {
        // This is a special case contributed to avoid exceptions when a
        // property type can't be
        // matched. DefaultDataTypeAnalyzer converts the empty string to null.

        configuration.add(Object.class, "");

        configuration.add(String.class, "text"); // DataTypeConstants.TEXT
        configuration.add(Number.class, "number"); // DataTypeConstants.NUMBER);
        configuration.add(Enum.class, "enum"); // DataTypeConstants.ENUM);
        configuration.add(Boolean.class, "boolean"); // DataTypeConstants.BOOLEAN);
        configuration.add(Date.class, "date"); // DataTypeConstants.DATE);
        configuration.add(Calendar.class, "calendar"); // DataTypeConstants.CALENDAR);
    }

    private static final class DefaultDataTypeAnalyzerMappedConfiguration
        implements MappedConfiguration<Class<?>, String> {

        final Map<Class<?>, String> map = new HashMap<>();

        @Override
        public void add(Class<?> key, String value) {
            map.put(key, value);
        }

        @Override
        public void override(Class<?> key, String value) {
            throw new RuntimeException("Not implemented");
        }

        @Override
        public void addInstance(Class<?> key, Class<? extends String> clazz) {
            throw new RuntimeException("Not implemented");
        }

        @Override
        public void overrideInstance(
            Class<?> key,
            Class<? extends String> clazz) {
            throw new RuntimeException("Not implemented");
        }

        public Map<Class<?>, String> getMap() {
            return map;
        }
    }

    private static final class CombinedDataTypeAnalyzer
        implements DataTypeAnalyzer {

        private final DataTypeAnalyzer[] analyzers;

        public CombinedDataTypeAnalyzer(DataTypeAnalyzer... analyzers) {
            this.analyzers = analyzers;
        }

        @Override
        public String identifyDataType(PropertyAdapter adapter) {
            String type = null;
            for (DataTypeAnalyzer analyzer : analyzers) {
                type = analyzer.identifyDataType(adapter);
                if (type != null) {
                    break;
                }
            }
            return type;
        }
    }
}
