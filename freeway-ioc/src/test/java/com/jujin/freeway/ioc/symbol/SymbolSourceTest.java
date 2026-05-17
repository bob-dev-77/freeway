package com.jujin.freeway.ioc.symbol;

import static org.junit.jupiter.api.Assertions.*;

import com.jujin.freeway.ioc.internal.SymbolSourceImpl;
import com.jujin.freeway.ioc.symbol.internal.MapSymbolProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SymbolSourceTest {

    private SymbolSource createSymbolSource(Map<String, String> symbols) {
        var provider = new MapSymbolProvider(symbols);
        return new SymbolSourceImpl(List.of(provider));
    }

    @Test
    void shouldResolveSimpleSymbol() {
        var symbols = createSymbolSource(
            Map.of("app.name", "MyApp", "app.version", "1.0.0")
        );

        assertEquals("MyApp", symbols.resolve("app.name"));
        assertEquals("1.0.0", symbols.resolve("app.version"));
    }

    @Test
    void shouldExpandSymbolsInTemplate() {
        var symbols = createSymbolSource(
            Map.of("app.name", "MyApp", "app.port", "8080")
        );

        String result = symbols.expand(
            "Welcome to ${app.name} on port ${app.port}!"
        );
        assertEquals("Welcome to MyApp on port 8080!", result);
    }

    @Test
    void shouldRecursivelyExpandNestedSymbols() {
        var symbols = createSymbolSource(
            Map.of(
                "db.host",
                "localhost",
                "db.port",
                "5432",
                "db.url",
                "jdbc:postgresql://${db.host}:${db.port}/mydb"
            )
        );

        // resolve 应该自动展开嵌套引用
        assertEquals(
            "jdbc:postgresql://localhost:5432/mydb",
            symbols.resolve("db.url")
        );
    }

    @Test
    void shouldSupportDefaultValueSyntax() {
        var symbols = createSymbolSource(Map.of("app.name", "MyApp"));

        // 未定义的符号使用默认值
        assertEquals("fallback", symbols.expand("${undefined:fallback}"));

        // 已定义的符号忽略默认值
        assertEquals("MyApp", symbols.expand("${app.name:ignored}"));
    }

    @Test
    void shouldHandleMissingSymbolWithoutDefault() {
        var symbols = createSymbolSource(Map.of());

        assertThrows(RuntimeException.class, () -> {
            symbols.resolve("nonexistent");
        });
    }

    @Test
    void shouldDetectCircularReference() {
        var symbols = createSymbolSource(
            Map.of("a", "${b}", "b", "${c}", "c", "${a}")
        );

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            symbols.resolve("a");
        });

        assertTrue(ex.getMessage().contains("defined in terms of itself"));
    }

    @Test
    void shouldCheckSymbolExistence() {
        var symbols = createSymbolSource(Map.of("existing", "value"));

        assertTrue(symbols.contains("existing"));
        assertFalse(symbols.contains("nonexistent"));
    }

    @Test
    void shouldCacheResolvedSymbols() {
        var symbols = createSymbolSource(Map.of("app.name", "MyApp"));

        // 第一次解析
        String first = symbols.resolve("app.name");

        // 第二次应该从缓存获取（性能测试）
        long start = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            symbols.resolve("app.name");
        }
        long elapsed = System.nanoTime() - start;

        // 10000 次查询应该在 100ms 内完成（缓存命中）
        assertTrue(
            elapsed < 100_000_000,
            "Cache should be fast: " + elapsed + "ns"
        );
    }

    @Test
    void shouldExpandComplexTemplate() {
        var symbols = createSymbolSource(
            Map.of(
                "env.HOME",
                "/home/user",
                "app.name",
                "MyApp",
                "config.dir",
                "${env.HOME}/configs/${app.name}"
            )
        );

        String result = symbols.expand("${config.dir}/settings.json");
        assertEquals("/home/user/configs/MyApp/settings.json", result);
    }

    @Test
    void shouldHandleEmptyString() {
        var symbols = createSymbolSource(Map.of());

        assertEquals("", symbols.expand(""));

        // resolve 对未定义的符号抛异常（这是预期行为）
        assertThrows(RuntimeException.class, () -> {
            symbols.resolve("nonexistent");
        });
    }

    @Test
    void shouldReturnStringAsIsWhenNoSymbols() {
        var symbols = createSymbolSource(Map.of());

        String input = "Hello World! No symbols here.";
        assertEquals(input, symbols.expand(input));
    }

    @Test
    void shouldHandleMalformedSymbolReference() {
        var symbols = createSymbolSource(Map.of());

        // 缺少闭合括号
        assertThrows(RuntimeException.class, () -> {
            symbols.expand("${unclosed");
        });
    }

    @Test
    void shouldSupportNestedDefaultValues() {
        var symbols = createSymbolSource(
            Map.of("outer", "${inner:fallback_inner}")
        );

        // inner 未定义，使用 fallback_inner
        assertEquals("fallback_inner", symbols.resolve("outer"));
    }
}
