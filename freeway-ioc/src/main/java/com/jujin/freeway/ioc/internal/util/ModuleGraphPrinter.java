package com.jujin.freeway.ioc.internal.util;

import com.jujin.freeway.ioc.*;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.advisor.*;
import com.jujin.freeway.ioc.ServiceDefinition;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.lifecycle.StartupDef;

import java.io.PrintStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 打印 IoC 容器的模块-服务能力全景图。
 * <p>
 * 接收 {@link Collection< ModuleDefinition >} 后，逐模块分析其提供的服务、顾问、
 * 贡献和启动方法，同时跨模块解析贡献目标归属，形成完整的依赖关系视图。
 * <p>
 * 典型的调用方式：
 * <pre>{@code
 *   ModuleGraphPrinter.print(moduleDefs);
 * }</pre>
 */
public class ModuleGraphPrinter {

    private static final String HORIZONTAL = "\u2500";   // ─
    private static final String VERTICAL = "\u2502";     // │
    private static final String BRANCH = "\u251C";       // ├
    private static final String LAST = "\u2514";         // └
    private static final String BULLET_SERVICE = "\u2714";  // ✔
    private static final String BULLET_ADVISOR = "\u2726";   // ✦
    private static final String BULLET_CONTRIB = "\u21E2";   // ⇢
    private static final String BULLET_STARTUP = "\u25B6";   // ▶

    private final Collection<? extends ModuleDefinition> moduleDefs;
    private final PrintStream out;

    // 全局索引：serviceId → (ModuleDefinition, ServiceDefinition)
    private final Map<String, ServiceEntry> globalServiceIndex = new LinkedHashMap<>();

    // 模块列表（保持顺序）
    private final List<ModuleEntry> modules = new ArrayList<>();

    private static class ServiceEntry {
        final ModuleDefinition moduleDef;
        final ServiceDefinition serviceDef;
        ServiceEntry(ModuleDefinition moduleDef, ServiceDefinition serviceDef) {
            this.moduleDef = moduleDef;
            this.serviceDef = serviceDef;
        }
    }

    private static class ModuleEntry {
        final ModuleDefinition moduleDef;
        final List<ServiceDefinition> services = new ArrayList<>();
        final List<AdvisorDefinition> advisors = new ArrayList<>();
        final List<ContributionDef> contributions = new ArrayList<>();
        final List<StartupDef> startups = new ArrayList<>();
        ModuleEntry(ModuleDefinition moduleDef) {
            this.moduleDef = moduleDef;
        }
    }

    private ModuleGraphPrinter(Collection<? extends ModuleDefinition> moduleDefs, PrintStream out) {
        this.moduleDefs = moduleDefs;
        this.out = out;
    }

    /**
     * 打印能力全景图到标准输出。
     */
    public static void print(Collection<? extends ModuleDefinition> moduleDefs) {
        print(moduleDefs, System.out);
    }

    /**
     * 打印能力全景图到指定输出流。
     */
    public static void print(Collection<? extends ModuleDefinition> moduleDefs, PrintStream out) {
        new ModuleGraphPrinter(moduleDefs, out).doPrint();
    }

    private void doPrint() {
        buildIndex();
        printHeader();
        for (int i = 0; i < modules.size(); i++) {
            printModule(modules.get(i), i == modules.size() - 1);
        }
        printFooter();
    }

    private void buildIndex() {
        // 第一遍：构建全局服务索引
        for (ModuleDefinition def : moduleDefs) {
            for (String serviceId : def.getServiceIds()) {
                ServiceDefinition sd = def.getServiceDef(serviceId);
                if (sd != null) {
                    globalServiceIndex.put(serviceId.toLowerCase(), new ServiceEntry(def, sd));
                }
            }
        }

        // 第二遍：按 ModuleDefinition 分类子元素
        for (ModuleDefinition def : moduleDefs) {
            ModuleEntry entry = new ModuleEntry(def);

            // 服务
            for (String serviceId : def.getServiceIds()) {
                ServiceDefinition sd = def.getServiceDef(serviceId);
                if (sd != null) {
                    entry.services.add(sd);
                }
            }

            // 顾问
            if (def.getAdvisorDefs() != null) {
                entry.advisors.addAll(def.getAdvisorDefs());
            }

            // 贡献
            if (def.getContributionDefs() != null) {
                entry.contributions.addAll(def.getContributionDefs());
            }

            // 启动
            if (def.getStartups() != null) {
                entry.startups.addAll(def.getStartups());
            }

            modules.add(entry);
        }
    }

    // ========== 打印方法 ==========

    private void printHeader() {
        int totalServices = globalServiceIndex.size();
        int totalModules = modules.size();
        String line = repeat("\u2550", 60); // ═

        out.println();
        out.println("  " + line);
        out.println("    Freeway IoC " + color("\u80FD\u529B\u5168\u666F\u56FE", 1)); // 加粗"能力全景图"
        out.println("  " + line);
        out.println("    \u626B\u63CF\u5230 " + totalModules + " \u4E2A\u6A21\u5757\uFF0C\u5171 " + totalServices + " \u4E2A\u670D\u52A1");
        out.println();
    }

    private void printModule(ModuleEntry entry, boolean isLastModule) {
        String prefix = isLastModule ? "  " + LAST + HORIZONTAL : "  " + BRANCH + HORIZONTAL;
        String connector = isLastModule ? "   " : "  " + VERTICAL + " ";

        Class<?> builder = entry.moduleDef.getBuilderClass();
        String builderName = builder != null ? builder.getCanonicalName() : "(unknown)";

        out.println("  " + prefix + HORIZONTAL + " \u6A21\u5757: " + color(simpleName(builderName), 1));

        // 构建器路径（缩进）
        out.println("  " + connector + "  \u6784\u5EFA\u5668: " + builderName);

        // 子元素计数
        int svcCnt = entry.services.size();
        int advCnt = entry.advisors.size();
        int conCnt = entry.contributions.size();
        int stuCnt = entry.startups.size();
        boolean hasChildren = svcCnt > 0 || advCnt > 0 || conCnt > 0 || stuCnt > 0;

        if (!hasChildren) {
            out.println("  " + connector + "  (\u7A7A\u6A21\u5757)");
            return;
        }

        List<Section> sections = new ArrayList<>();
        if (svcCnt > 0) sections.add(new Section("\u670D\u52A1", svcCnt, true));
        if (advCnt > 0) sections.add(new Section("\u987E\u95EE", advCnt, false));
        if (conCnt > 0) sections.add(new Section("\u8D21\u732E", conCnt, false));
        if (stuCnt > 0) sections.add(new Section("\u542F\u52A8", stuCnt, false));

        for (int s = 0; s < sections.size(); s++) {
            Section sec = sections.get(s);
            boolean isLastSection = (s == sections.size() - 1);
            String sectionPrefix = isLastSection ? "  " + connector + LAST + HORIZONTAL + HORIZONTAL + " "
                    : "  " + connector + BRANCH + HORIZONTAL + HORIZONTAL + " ";

            out.println(sectionPrefix + color(sec.name, 3) + " (" + sec.count + ")");

            List<?> items = getSectionItems(entry, sec);
            for (int i = 0; i < items.size(); i++) {
                boolean isLastItem = (i == items.size() - 1);
                String itemPrefix = isLastSection
                        ? "  " + connector + "   "
                        : "  " + connector + "  " + VERTICAL + "  ";
                String itemConn = isLastItem ? LAST + HORIZONTAL + HORIZONTAL + " " : BRANCH + HORIZONTAL + HORIZONTAL + " ";

                out.print("  " + itemPrefix + itemConn);
                printItem(entry, items.get(i), sec, isLastItem);
            }
        }
    }

    private List<?> getSectionItems(ModuleEntry entry, Section sec) {
        return switch (sec.name) {
            case "服务" -> entry.services;
            case "顾问" -> entry.advisors;
            case "贡献" -> entry.contributions;
            case "启动" -> entry.startups;
            default -> List.of();
        };
    }

    private void printItem(ModuleEntry entry, Object item, Section sec, boolean isLastItem) {
        if (item instanceof ServiceDefinition sd) {
            printService(sd);
        } else if (item instanceof AdvisorDefinition ad) {
            printAdvisor(ad);
        } else if (item instanceof ContributionDef cd) {
            printContribution(cd);
        } else if (item instanceof StartupDef) {
            out.print("\u542F\u52A8\u65B9\u6CD5");
        }
        out.println();
    }

    private void printService(ServiceDefinition sd) {
        String id = sd.getServiceId();
        Class<?> iface = sd.getServiceInterface();
        String ifaceName = iface != null ? simpleName(iface.getCanonicalName()) : "?";

        out.print(BULLET_SERVICE + " " + color(id, 2) + " \u2192 " + ifaceName);

        // 生命周期
        String scope = sd.getServiceScope();
        if (scope != null && !scope.isEmpty() && !"SINGLETON".equalsIgnoreCase(scope)) {
            out.print(" [" + scope.toUpperCase() + "]");
        } else {
            out.print(" [S]");
        }

        // EagerLoad
        if (sd.isEagerLoad()) {
            out.print(" EAGER");
        }

        // 标记
        Set<Class<?>> markers = sd.getMarkers();
        if (markers != null && !markers.isEmpty()) {
            String markerStr = markers.stream()
                    .map(m -> simpleName(m.getCanonicalName()))
                    .collect(Collectors.joining(", "));
            out.print(" @" + markerStr);
        }
    }

    private void printAdvisor(AdvisorDefinition ad) {
        String id = ad.getAdvisorId();
        Class<?> iface = ad.getServiceInterface();
        String target = iface != null && iface != Object.class
                ? simpleName(iface.getCanonicalName())
                : "*";
        out.print(BULLET_ADVISOR + " " + color(id, 4) + " \u2192 " + target);
        String[] constraints = ad.getConstraints();
        if (constraints != null && constraints.length > 0) {
            out.print(" [" + String.join(", ", constraints) + "]");
        }
    }

    private void printContribution(ContributionDef cd) {
        String targetId = cd.getServiceId();
        // 查找目标服务所在的模块
        ServiceEntry targetEntry = targetId != null ? globalServiceIndex.get(targetId.toLowerCase()) : null;
        String targetModule = targetEntry != null ? simpleName(targetEntry.moduleDef.getBuilderClass().getCanonicalName()) : "?";

        out.print(BULLET_CONTRIB + " \u2192 \"" + targetId + "\"");
        if (targetEntry != null) {
            out.print(" [" + targetModule + "]");
        }
        if (cd.isOptional()) {
            out.print(" (Optional)");
        }
        // 标记
        Set<Class<?>> markers = cd.getMarkers();
        if (markers != null && !markers.isEmpty()) {
            String markerStr = markers.stream()
                    .map(m -> simpleName(m.getCanonicalName()))
                    .collect(Collectors.joining(", "));
            out.print(" @" + markerStr);
        }
    }

    private void printFooter() {
        String line = repeat("\u2550", 60);
        out.println("  " + line);
        out.println("  \u5C3E\u90E8\u8BF4\u660E: [S]=SINGLETON [P]=PROTOTYPE  \u2726=\u987E\u95EE  \u21E2=\u8D21\u732E  \u25B6=\u542F\u52A8");
        out.println();
    }

    // ========== 工具方法 ==========

    private static String simpleName(String canonicalName) {
        if (canonicalName == null) return "?";
        int idx = canonicalName.lastIndexOf('.');
        return idx >= 0 ? canonicalName.substring(idx + 1) : canonicalName;
    }

    private static String repeat(String ch, int times) {
        StringBuilder sb = new StringBuilder(times);
        for (int i = 0; i < times; i++) sb.append(ch);
        return sb.toString();
    }

    /** ANSI 颜色（仅当输出到支持彩色的终端时启用） */
    private static String color(String text, int colorCode) {
        // 简单实现：直接返回原文（可扩展支持 ANSI）
        return text;
    }

    private record Section(String name, int count, boolean isService) {}
}
