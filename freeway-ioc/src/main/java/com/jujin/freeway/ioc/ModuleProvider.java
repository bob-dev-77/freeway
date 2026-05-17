package com.jujin.freeway.ioc;

/**
 * JDK {@link java.util.ServiceLoader} SPI for auto-discovering IoC module
 * classes.
 *
 * <p>
 * Third-party libraries can provide implementations of this interface,
 * declared in
 * {@code META-INF/services/com.jujin.freeway.ioc.ModuleProvider}, to have
 * their module classes automatically discovered and loaded by the IoC
 * container. Each provider may declare one or more module classes. Sub-modules
 * should be composed via
 * {@link com.jujin.freeway.ioc.annotations.ImportModule @ImportModule}.
 * </p>
 *
 * <p>
 * Example {@code META-INF/services/com.jujin.freeway.ioc.ModuleProvider}:
 * </p>
 *
 * <pre>{@code
 * com.example.mylib.MyLibModuleProvider
 * }</pre>
 *
 * <pre>{@code
 * public class MyLibModuleProvider implements ModuleProvider {
 *     public Class<?>[] modules() {
 *         return new Class<?>[] { MyLibModule.class };
 *     }
 * }
 * }</pre>
 *
 * @see Registry.Builder#autoDiscover()
 */
public interface ModuleProvider {
    /**
     * Returns the module classes provided by this SPI implementation. Each
     * returned class should follow the standard module class conventions (static
     * bind/build/contribute/decorate/advise methods). Sub-modules are composed via
     * {@link com.jujin.freeway.ioc.annotations.ImportModule @ImportModule}.
     *
     * @return an array of module classes (must not be null)
     */
    Class<?>[] modules();
}
