package com.jujin.freeway.ioc.annotations;

/**
 * Constants for documenting the context wherein the freeway-provided
 * annotations may be used, in conjunction with
 * {@link com.jujin.freeway.ioc.annotations.UseWith}.
 *
 */
public enum AnnotationUseContext {
    /**
     * Annotation may be used on modules
     */
    MODULE,

    /**
     * Annotation may be used on/in services
     */
    SERVICE,

    /**
     * Annotation may be used for service decorators
     */
    SERVICE_DECORATOR,

    /**
     * Annotation may be used on/in arbitrary java beans.
     */
    BEAN,



    /**
     * Annotation may be used on/in components.
     */
    COMPONENT,

    /**
     * Annotation may be used on/in mixins.
     */
    MIXIN,

    /**
     * Annotation may be used on/in pages.
     */
    PAGE

}
