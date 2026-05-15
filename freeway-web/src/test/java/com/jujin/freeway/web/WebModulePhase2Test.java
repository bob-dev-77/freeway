package com.jujin.freeway.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 Web 模块第二阶段修复的功能
 */
@DisplayName("Web 模块第二阶段修复测试")
class WebModulePhase2Test {

    // ========== CorsFilter Builder 默认值测试 ==========

    @Test
    @DisplayName("CorsFilter Builder 默认应该允许所有源（与 IoC 一致）")
    void testCorsFilterBuilderDefaultAllowsAllOrigins() {
        var filter = CorsFilter.builder().build();
        
        // 通过反射检查 allowAll 字段
        try {
            var field = CorsFilter.class.getDeclaredField("allowAll");
            field.setAccessible(true);
            boolean allowAll = (boolean) field.get(filter);
            
            assertTrue(allowAll, "Builder 默认应该允许所有源（allowAll=true）");
        } catch (Exception e) {
            fail("无法访问 allowAll 字段: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("CorsFilter Builder 可以显式配置特定源")
    void testCorsFilterBuilderCanSetSpecificOrigins() {
        var filter = CorsFilter.builder()
            .allowedOrigins("https://example.com")
            .build();
        
        // 通过反射检查 allowedOriginList
        try {
            var field = CorsFilter.class.getDeclaredField("allowedOriginList");
            field.setAccessible(true);
            String[] origins = (String[]) field.get(filter);
            
            assertEquals(1, origins.length);
            assertEquals("https://example.com", origins[0]);
        } catch (Exception e) {
            fail("无法访问 allowedOriginList 字段: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("CorsFilter Builder 可以使用 allowAllOrigins()")
    void testCorsFilterBuilderAllowAllOriginsMethod() {
        var filter = CorsFilter.builder()
            .allowAllOrigins()
            .build();
        
        // 通过反射检查 allowAll 字段
        try {
            var field = CorsFilter.class.getDeclaredField("allowAll");
            field.setAccessible(true);
            boolean allowAll = (boolean) field.get(filter);
            
            assertTrue(allowAll, "allowAllOrigins() 应该设置 allowAll=true");
        } catch (Exception e) {
            fail("无法访问 allowAll 字段: " + e.getMessage());
        }
    }

    // ========== RequestBodyTooLargeException 测试 ==========

    @Test
    @DisplayName("RequestBodyTooLargeException 应该包含 maxSize")
    void testRequestBodyTooLargeExceptionContainsMaxSize() {
        long maxSize = 1024 * 1024; // 1MB
        var ex = new RequestBodyTooLargeException(maxSize);
        
        assertEquals(maxSize, ex.getMaxSize());
        assertTrue(ex.getMessage().contains("1048576"));
        assertTrue(ex.getMessage().contains("Request body exceeds maximum size"));
    }

    @Test
    @DisplayName("RequestBodyTooLargeException 应该是 IOException")
    void testRequestBodyTooLargeExceptionIsIOException() {
        var ex = new RequestBodyTooLargeException(1024);
        
        assertInstanceOf(java.io.IOException.class, ex);
    }
}
