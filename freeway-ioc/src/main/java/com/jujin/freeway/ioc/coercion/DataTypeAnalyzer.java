package com.jujin.freeway.ioc.coercion;

import com.jujin.freeway.ioc.annotations.UsesMappedConfiguration;
import com.jujin.freeway.ioc.annotations.UsesOrderedConfiguration;
import com.jujin.freeway.ioc.property.PropertyAdapter;

/**
 * Used by {@link com.jujin.freeway.beanmodel.services.BeanModelSource} to
 * identify the type of data associated with a particular property (represented
 * as a {@link PropertyAdapter}). The data type is a string used to determine
 * what kind of interface to use for displaying the value of the property, or
 * what kind of interface to use for editing the value of the property. Common
 * property types are "text", "enum", "checkbox", but the list is extensible.
 * <p>
 * Different strategies for identifying the data type are encapsulated in the
 * DataTypeAnalyzer service, forming a chain of command.
 * <p>
 * The DefaultDataTypeAnalyzer service maps property types to data type names.
 * <p>
 * The DataTypeAnalyzer service is an extensible
 * {@linkplain com.jujin.freeway.ioc.advisor.ChainBuilder chain of command}),
 * that (by default) includes
 * {@link com.jujin.freeway.ioc.internal.AnnotationDataTypeAnalyzer} and the
 * {@link com.jujin.freeway.ioc.internal.DefaultDataTypeAnalyzer} service
 * (ordered last). It uses an ordered configuration.
 */
@UsesOrderedConfiguration(DataTypeAnalyzer.class)
@UsesMappedConfiguration(key = Class.class, value = String.class)
public interface DataTypeAnalyzer {
    /**
     * Identifies the data type, if known, or returns null if not known.
     */
    String identifyDataType(PropertyAdapter adapter);
}
