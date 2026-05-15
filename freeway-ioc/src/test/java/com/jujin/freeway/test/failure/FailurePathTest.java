package com.jujin.freeway.test.failure;

import com.jujin.freeway.ioc.Registry;
import com.jujin.freeway.ioc.RegistryShutdownHub;
import com.jujin.freeway.ioc.coercion.TypeCoercer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 IoC 容器的失败路径和错误处理。
 */
@DisplayName("IoC 失败路径测试")
class FailurePathTest {
    
    @Test
    @DisplayName("TypeCoercer 应该缓存转换路径")
    void testCoercionCaching() {
        var registry = Registry.Builder.startAndBuild(SimpleModule.class);
        var coercer = registry.getService(TypeCoercer.class);
        
        // 第一次转换
        Integer result1 = coercer.coerce("123", Integer.class);
        assertEquals(123, result1);
        
        // 第二次转换（使用缓存）
        Integer result2 = coercer.coerce("456", Integer.class);
        assertEquals(456, result2);
        
        // 清除缓存
        coercer.clearCache();
        
        // 再次转换
        Integer result3 = coercer.coerce("789", Integer.class);
        assertEquals(789, result3);
        
        registry.shutdown();
    }
    
    @Test
    @DisplayName("Shutdown listener 应该按顺序执行")
    void testShutdownListenerOrder() {
        var registry = Registry.Builder.startAndBuild(SimpleModule.class);
        var shutdownHub = registry.getService(RegistryShutdownHub.class);
        
        int[] order = {0};
        int[] firstOrder = new int[1];
        int[] secondOrder = new int[1];
        
        shutdownHub.addRegistryWillShutdownListener(() -> {
            firstOrder[0] = ++order[0];
        });
        
        shutdownHub.addRegistryShutdownListener(() -> {
            secondOrder[0] = ++order[0];
        });
        
        registry.shutdown();
        
        assertTrue(firstOrder[0] < secondOrder[0], 
            "WillShutdown should execute before DidShutdown");
    }
    
    @Test
    @DisplayName("无法转换的类型应该抛出异常")
    void testImpossibleCoercion() {
        var registry = Registry.Builder.startAndBuild(SimpleModule.class);
        var coercer = registry.getService(TypeCoercer.class);
        
        assertThrows(RuntimeException.class, () -> {
            coercer.coerce(new Object(), Runnable.class);
        });
        
        registry.shutdown();
    }
    
    // ========== 简单测试模块 ==========
    
    public static class SimpleModule {
        public static Runnable buildService() {
            return () -> System.out.println("Test");
        }
    }
}
