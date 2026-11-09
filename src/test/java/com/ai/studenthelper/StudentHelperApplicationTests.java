package com.ai.studenthelper;

import com.ai.studenthelper.config.AppProperties;
import com.ai.studenthelper.infra.crypto.ApiKeyCipher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = "studenthelper.startup.db-check=false")
class StudentHelperApplicationTests {

    /** 测试用主密钥每次运行随机生成，仓库内不存在任何真实密钥。 */
    @DynamicPropertySource
    static void masterKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("studenthelper.crypto.master-key", () -> Base64.getEncoder().encodeToString(key));
    }

    @Autowired
    private ApiKeyCipher apiKeyCipher;

    @Autowired
    private AppProperties appProperties;

    @Test
    void contextLoads() {
        assertNotNull(apiKeyCipher);
        assertNotNull(appProperties);
        assertNotNull(appProperties.getChat());
    }
}
