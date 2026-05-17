package com.jujin.freeway.ioc.coercion.internal;

import com.jujin.freeway.ioc.coercion.Coercion;
import com.jujin.freeway.ioc.coercion.CoercionTuple;
import com.jujin.freeway.ioc.coercion.StringToEnumCoercion;
import com.jujin.freeway.ioc.coercion.TypeCoercer;
import com.jujin.freeway.ioc.config.Configuration;
import com.jujin.freeway.ioc.config.MappedConfiguration;
import com.jujin.freeway.ioc.schedule.TimeInterval;
import java.io.File;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.util.*;

/**
 * Class that provides Freeway-IoC's basic type coercions.
 *
 * @see TypeCoercer
 * @see Coercion
 */
public class BasicTypeCoercions {

    /**
     * Provides the basic type coercions to a {@link MappedConfiguration} instance.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void provideBasicTypeCoercions(
        MappedConfiguration<CoercionTuple.Key, CoercionTuple> configuration
    ) {
        // String --> String (identity, included for completeness)
        add(
            configuration,
            String.class,
            String.class,
            new Coercion<String, String>() {
                @Override
                public String coerce(String input) {
                    return input;
                }
            }
        );

        // Object --> Boolean
        add(
            configuration,
            Object.class,
            Boolean.class,
            new Coercion<Object, Boolean>() {
                @Override
                public Boolean coerce(Object input) {
                    if (input == null) {
                        return false;
                    }
                    if (input instanceof Boolean) {
                        return (Boolean) input;
                    }
                    return true;
                }
            }
        );

        // String --> Double
        add(
            configuration,
            String.class,
            Double.class,
            new Coercion<String, Double>() {
                @Override
                public Double coerce(String input) {
                    return Double.parseDouble(input);
                }
            }
        );

        // String --> BigDecimal
        add(
            configuration,
            String.class,
            BigDecimal.class,
            new Coercion<String, BigDecimal>() {
                @Override
                public BigDecimal coerce(String input) {
                    return new BigDecimal(input.trim());
                }
            }
        );

        // BigDecimal --> Double
        add(
            configuration,
            BigDecimal.class,
            Double.class,
            new Coercion<BigDecimal, Double>() {
                @Override
                public Double coerce(BigDecimal input) {
                    return input.doubleValue();
                }
            }
        );

        // String --> BigInteger
        add(
            configuration,
            String.class,
            BigInteger.class,
            new Coercion<String, BigInteger>() {
                @Override
                public BigInteger coerce(String input) {
                    return new BigInteger(input.trim());
                }
            }
        );

        // String --> Long
        add(
            configuration,
            String.class,
            Long.class,
            new Coercion<String, Long>() {
                @Override
                public Long coerce(String input) {
                    return Long.parseLong(input.trim());
                }
            }
        );

        // Long --> Byte
        add(
            configuration,
            Long.class,
            Byte.class,
            new Coercion<Long, Byte>() {
                @Override
                public Byte coerce(Long input) {
                    return input.byteValue();
                }
            }
        );

        // Long --> Short
        add(
            configuration,
            Long.class,
            Short.class,
            new Coercion<Long, Short>() {
                @Override
                public Short coerce(Long input) {
                    return input.shortValue();
                }
            }
        );

        // Long --> Integer
        add(
            configuration,
            Long.class,
            Integer.class,
            new Coercion<Long, Integer>() {
                @Override
                public Integer coerce(Long input) {
                    return input.intValue();
                }
            }
        );

        // Number --> Long
        add(
            configuration,
            Number.class,
            Long.class,
            new Coercion<Number, Long>() {
                @Override
                public Long coerce(Number input) {
                    return input.longValue();
                }
            }
        );

        // Double --> Float
        add(
            configuration,
            Double.class,
            Float.class,
            new Coercion<Double, Float>() {
                @Override
                public Float coerce(Double input) {
                    return input.floatValue();
                }
            }
        );

        // Long --> Double
        add(
            configuration,
            Long.class,
            Double.class,
            new Coercion<Long, Double>() {
                @Override
                public Double coerce(Long input) {
                    return input.doubleValue();
                }
            }
        );

        // String --> Boolean
        add(
            configuration,
            String.class,
            Boolean.class,
            new Coercion<String, Boolean>() {
                @Override
                public Boolean coerce(String input) {
                    if (input == null) {
                        return false;
                    }

                    input = input.trim();

                    if (input.equalsIgnoreCase("true")) {
                        return true;
                    }

                    if (input.equalsIgnoreCase("yes")) {
                        return true;
                    }

                    if (input.equals("1")) {
                        return true;
                    }

                    return false;
                }
            }
        );

        // Number --> Boolean
        add(
            configuration,
            Number.class,
            Boolean.class,
            new Coercion<Number, Boolean>() {
                @Override
                public Boolean coerce(Number input) {
                    return input.intValue() != 0;
                }
            }
        );

        // Void --> Boolean
        add(
            configuration,
            Void.class,
            Boolean.class,
            new Coercion<Void, Boolean>() {
                @Override
                public Boolean coerce(Void input) {
                    return false;
                }
            }
        );

        // Collection --> Boolean
        add(
            configuration,
            Collection.class,
            Boolean.class,
            new Coercion<Collection, Boolean>() {
                @Override
                public Boolean coerce(Collection input) {
                    return !input.isEmpty();
                }
            }
        );

        // Object --> List
        add(
            configuration,
            Object.class,
            List.class,
            new Coercion<Object, List>() {
                @Override
                public List coerce(Object input) {
                    List<Object> list = new ArrayList<Object>(1);
                    list.add(input);
                    return list;
                }
            }
        );

        // Object[] --> List
        add(
            configuration,
            Object[].class,
            List.class,
            new Coercion<Object[], List>() {
                @Override
                public List coerce(Object[] input) {
                    return Arrays.asList(input);
                }
            }
        );

        // Object[] --> Boolean
        add(
            configuration,
            Object[].class,
            Boolean.class,
            new Coercion<Object[], Boolean>() {
                @Override
                public Boolean coerce(Object[] input) {
                    return input.length > 0;
                }
            }
        );

        // Float --> Double
        add(
            configuration,
            Float.class,
            Double.class,
            new Coercion<Float, Double>() {
                @Override
                public Double coerce(Float input) {
                    return input.doubleValue();
                }
            }
        );

        // String --> File
        add(
            configuration,
            String.class,
            File.class,
            new Coercion<String, File>() {
                @Override
                public File coerce(String input) {
                    return new File(input);
                }
            }
        );

        // String --> TimeInterval
        add(
            configuration,
            String.class,
            TimeInterval.class,
            new Coercion<String, TimeInterval>() {
                @Override
                public TimeInterval coerce(String input) {
                    return new TimeInterval(input);
                }
            }
        );

        // TimeInterval --> Long
        add(
            configuration,
            TimeInterval.class,
            Long.class,
            new Coercion<TimeInterval, Long>() {
                @Override
                public Long coerce(TimeInterval input) {
                    return input.milliseconds();
                }
            }
        );

        // Object --> Object[]
        add(
            configuration,
            Object.class,
            Object[].class,
            new Coercion<Object, Object[]>() {
                @Override
                public Object[] coerce(Object input) {
                    return new Object[] { input };
                }
            }
        );

        // Collection --> Object[]
        add(
            configuration,
            Collection.class,
            Object[].class,
            new Coercion<Collection, Object[]>() {
                @Override
                public Object[] coerce(Collection input) {
                    return input.toArray();
                }
            }
        );
    }

    /**
     * Provides the JSR 310 (Date and Time API) type coercions.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void provideJSR310TypeCoercions(
        MappedConfiguration<CoercionTuple.Key, CoercionTuple> configuration
    ) {
        // String --> Instant
        add(
            configuration,
            String.class,
            Instant.class,
            new Coercion<String, Instant>() {
                @Override
                public Instant coerce(String input) {
                    return Instant.parse(input);
                }
            }
        );

        // String --> LocalDate
        add(
            configuration,
            String.class,
            LocalDate.class,
            new Coercion<String, LocalDate>() {
                @Override
                public LocalDate coerce(String input) {
                    return LocalDate.parse(input);
                }
            }
        );

        // String --> LocalTime
        add(
            configuration,
            String.class,
            LocalTime.class,
            new Coercion<String, LocalTime>() {
                @Override
                public LocalTime coerce(String input) {
                    return LocalTime.parse(input);
                }
            }
        );

        // String --> LocalDateTime
        add(
            configuration,
            String.class,
            LocalDateTime.class,
            new Coercion<String, LocalDateTime>() {
                @Override
                public LocalDateTime coerce(String input) {
                    return LocalDateTime.parse(input);
                }
            }
        );

        // String --> ZonedDateTime
        add(
            configuration,
            String.class,
            ZonedDateTime.class,
            new Coercion<String, ZonedDateTime>() {
                @Override
                public ZonedDateTime coerce(String input) {
                    return ZonedDateTime.parse(input);
                }
            }
        );

        // String --> OffsetDateTime
        add(
            configuration,
            String.class,
            OffsetDateTime.class,
            new Coercion<String, OffsetDateTime>() {
                @Override
                public OffsetDateTime coerce(String input) {
                    return OffsetDateTime.parse(input);
                }
            }
        );

        // String --> OffsetTime
        add(
            configuration,
            String.class,
            OffsetTime.class,
            new Coercion<String, OffsetTime>() {
                @Override
                public OffsetTime coerce(String input) {
                    return OffsetTime.parse(input);
                }
            }
        );

        // String --> Duration
        add(
            configuration,
            String.class,
            Duration.class,
            new Coercion<String, Duration>() {
                @Override
                public Duration coerce(String input) {
                    return Duration.parse(input);
                }
            }
        );

        // String --> Period
        add(
            configuration,
            String.class,
            Period.class,
            new Coercion<String, Period>() {
                @Override
                public Period coerce(String input) {
                    return Period.parse(input);
                }
            }
        );

        // String --> Year
        add(
            configuration,
            String.class,
            Year.class,
            new Coercion<String, Year>() {
                @Override
                public Year coerce(String input) {
                    return Year.parse(input);
                }
            }
        );

        // String --> YearMonth
        add(
            configuration,
            String.class,
            YearMonth.class,
            new Coercion<String, YearMonth>() {
                @Override
                public YearMonth coerce(String input) {
                    return YearMonth.parse(input);
                }
            }
        );

        // String --> MonthDay
        add(
            configuration,
            String.class,
            MonthDay.class,
            new Coercion<String, MonthDay>() {
                @Override
                public MonthDay coerce(String input) {
                    return MonthDay.parse(input);
                }
            }
        );

        // Long --> Instant
        add(
            configuration,
            Long.class,
            Instant.class,
            new Coercion<Long, Instant>() {
                @Override
                public Instant coerce(Long input) {
                    return Instant.ofEpochMilli(input);
                }
            }
        );

        // Long --> LocalDate
        add(
            configuration,
            Long.class,
            LocalDate.class,
            new Coercion<Long, LocalDate>() {
                @Override
                public LocalDate coerce(Long input) {
                    return Instant.ofEpochMilli(input)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
                }
            }
        );

        // Long --> LocalDateTime
        add(
            configuration,
            Long.class,
            LocalDateTime.class,
            new Coercion<Long, LocalDateTime>() {
                @Override
                public LocalDateTime coerce(Long input) {
                    return Instant.ofEpochMilli(input)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime();
                }
            }
        );

        // Instant --> Long
        add(
            configuration,
            Instant.class,
            Long.class,
            new Coercion<Instant, Long>() {
                @Override
                public Long coerce(Instant input) {
                    return input.toEpochMilli();
                }
            }
        );

        // LocalDate --> Instant
        add(
            configuration,
            LocalDate.class,
            Instant.class,
            new Coercion<LocalDate, Instant>() {
                @Override
                public Instant coerce(LocalDate input) {
                    return input
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant();
                }
            }
        );

        // LocalDateTime --> Instant
        add(
            configuration,
            LocalDateTime.class,
            Instant.class,
            new Coercion<LocalDateTime, Instant>() {
                @Override
                public Instant coerce(LocalDateTime input) {
                    return input.atZone(ZoneId.systemDefault()).toInstant();
                }
            }
        );
    }

    private static <S, T> void add(
        MappedConfiguration<CoercionTuple.Key, CoercionTuple> configuration,
        Class<S> sourceType,
        Class<T> targetType,
        Coercion<S, T> coercion
    ) {
        CoercionTuple.Key key = new CoercionTuple.Key(sourceType, targetType);
        CoercionTuple tuple = new CoercionTuple(
            sourceType,
            targetType,
            coercion
        );
        configuration.add(key, tuple);
    }
}
