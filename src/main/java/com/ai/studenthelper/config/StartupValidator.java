package com.ai.studenthelper.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 启动自检：数据目录可写、数据库连通。失败即抛明确中文错误，不静默降级（AC-113-3）。
 */
@Component
@Order(0)
public class StartupValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupValidator.class);

    private final DataSource dataSource;
    private final AppProperties properties;

    public StartupValidator(DataSource dataSource, AppProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        checkWritableDataDir();
        if (properties.getStartup().isDbCheck()) {
            checkDatabase();
        } else {
            log.warn("数据库连通性校验已被 studenthelper.startup.db-check=false 跳过（仅限本地冒烟）");
        }
    }

    private void checkWritableDataDir() {
        Path dataDir = Path.of("data");
        try {
            Files.createDirectories(dataDir.resolve("images"));
            Path probe = Files.createTempFile(dataDir, ".write-probe", ".tmp");
            Files.deleteIfExists(probe);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "数据目录 ./data 不可写：请检查工作目录权限（AC-113-3 同类约束）", e);
        }
    }

    private void checkDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(3)) {
                throw new IllegalStateException("数据库连接无效");
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "数据库连接失败：请确认 MySQL 服务已启动、student_helper 库已按 "
                            + "src/main/resources/db/schema.sql 创建、DB_USERNAME / DB_PASSWORD 正确。"
                            + " 原始信息：" + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        } catch (IllegalStateException e) {
            throw e;
        }
        log.info("启动自检通过：./data 可写，数据库连通");
    }
}
