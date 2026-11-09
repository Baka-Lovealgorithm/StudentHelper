package com.ai.studenthelper.config;

import com.ai.studenthelper.infra.crypto.ApiKeyCipher;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * 主密钥装配：环境变量优先，其次本地文件；二者皆缺失时启动明确报错（NFR-108）。
 */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class CryptoConfig {

    @Bean
    public ApiKeyCipher apiKeyCipher(AppProperties properties) {
        String encoded = properties.getCrypto().getMasterKey();
        if (encoded == null || encoded.isBlank()) {
            Path file = Path.of(properties.getCrypto().getMasterKeyFile());
            if (Files.isReadable(file)) {
                try {
                    encoded = Files.readString(file).trim();
                } catch (IOException e) {
                    throw new IllegalStateException("主密钥文件无法读取：" + file, e);
                }
            }
        }
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException(
                    "未配置主密钥：请设置环境变量 STUDENTHELPER_MASTER_KEY（Base64 编码的 32 字节），"
                            + "或在 ./data/.master_key 放置同一值。应用拒绝以明文方式存储 API Key。");
        }
        final byte[] key;
        try {
            key = Base64.getDecoder().decode(encoded.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("主密钥不是合法的 Base64 字符串", e);
        }
        if (key.length != 32) {
            throw new IllegalStateException("主密钥长度错误：需要 32 字节，实际 " + key.length + " 字节");
        }
        return new ApiKeyCipher(key);
    }
}
