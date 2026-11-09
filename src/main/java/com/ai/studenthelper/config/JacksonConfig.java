package com.ai.studenthelper.config;

import org.springframework.context.annotation.Configuration;

/**
 * JSON 序列化扩展位。
 *
 * <p>Spring Boot 4.0.9 使用 Jackson 3（tools.jackson），旧版的
 * {@code org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer}
 * 已不存在；Jackson 3 默认以 ISO-8601 输出 LocalDateTime，时区由
 * {@code spring.jackson.time-zone} 指定，因此 S0 不需要覆盖默认行为。
 * S1 若需自定义序列化，在此注册
 * {@code org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer}。
 */
@Configuration
public class JacksonConfig {
}
