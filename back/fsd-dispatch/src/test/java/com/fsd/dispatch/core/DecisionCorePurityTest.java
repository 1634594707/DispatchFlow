package com.fsd.dispatch.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * §7.1 的架构守卫：决策内核必须是纯函数层。
 *
 * <p>一旦有人把 {@code @ConfigurationProperties} bean、Mapper 或 Redis 客户端塞进
 * {@code com.fsd.dispatch.core}，影子对照、离线回放与换语言移植就同时失效，而这正是
 * 抽这一层的全部理由。所以把它写成测试而不是注释。
 */
class DecisionCorePurityTest {

    private static final Path CORE_SOURCE = Path.of("src", "main", "java", "com", "fsd", "dispatch", "core");

    @Test
    void corePackageHasNoSpringOrmOrIoDependencies() throws IOException {
        assertTrue(Files.isDirectory(CORE_SOURCE), "找不到决策内核源码目录，工作目录应为 fsd-dispatch 模块根: "
                + CORE_SOURCE.toAbsolutePath());

        List<Path> sources;
        try (Stream<Path> files = Files.list(CORE_SOURCE)) {
            sources = files.filter(path -> path.toString().endsWith(".java")).toList();
        }
        assertFalse(sources.isEmpty(), "决策内核目录里一个文件都没有");

        for (Path source : sources) {
            for (String line : Files.readAllLines(source, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (!trimmed.startsWith("import ")) {
                    continue;
                }
                assertFalse(trimmed.contains("org.springframework"),
                        source.getFileName() + " 引入了 Spring：" + trimmed);
                assertFalse(trimmed.contains(".mapper") || trimmed.contains(".entity")
                                || trimmed.contains("redis") || trimmed.contains(".config."),
                        source.getFileName() + " 引入了基础设施或配置 bean：" + trimmed);
            }
        }
    }
}
