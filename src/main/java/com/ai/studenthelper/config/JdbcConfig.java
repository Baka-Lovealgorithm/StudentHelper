package com.ai.studenthelper.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.core.dialect.JdbcDialect;
import org.springframework.data.jdbc.core.dialect.JdbcMySqlDialect;

/**
 * 显式声明 MySQL 方言。
 *
 * <p>Spring Boot 4.0.9 的 {@code DataJdbcRepositoriesAutoConfiguration#jdbcDialect}
 * 带 {@code @ConditionalOnMissingBean(JdbcDialect)}；不提供本 Bean 时，它会在启动期
 * 通过 JDBC 连接探测方言，导致数据库不可用时抛出隐晦的 30 秒超时异常。
 * 提供本 Bean 后，启动期不再隐式连库，连通性统一由 {@link StartupValidator}
 * 以明确的中文错误报告（AC-113-3）。
 */
@Configuration
public class JdbcConfig {

    @Bean
    public JdbcDialect jdbcDialect() {
        return JdbcMySqlDialect.INSTANCE;
    }
}
