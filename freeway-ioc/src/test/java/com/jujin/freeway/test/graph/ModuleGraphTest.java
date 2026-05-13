package com.jujin.freeway.test.graph;

import com.jujin.freeway.ioc.ModuleDefinition;
import com.jujin.freeway.ioc.internal.DefaultModuleDefinition;
import com.jujin.freeway.ioc.internal.JdkProxyFactory;
import com.jujin.freeway.ioc.internal.util.ModuleGraphPrinter;
import com.jujin.freeway.test.advisor.AdvisorOnlyTestModule;
import com.jujin.freeway.test.inject.TestModule;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

/**
 * 验证 ModuleGraphPrinter 可以正常输出能力全景图。
 */
public class ModuleGraphTest {

    @Test
    void printGraph() {
        Logger logger = LoggerFactory.getLogger(ModuleGraphTest.class);
        JdkProxyFactory proxyFactory = new JdkProxyFactory(
                Thread.currentThread().getContextClassLoader());

        Collection<? extends ModuleDefinition> defs = List.of(
                new DefaultModuleDefinition(AdvisorOnlyTestModule.class, logger, proxyFactory),
                new DefaultModuleDefinition(TestModule.class, logger, proxyFactory)
        );

        ModuleGraphPrinter.print(defs);

        System.out.println("\n--- 图打印完成 ---");
    }
}
