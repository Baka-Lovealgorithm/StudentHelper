package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.ModelConfigRequest;
import com.ai.studenthelper.common.dto.ModelConfigResponse;
import com.ai.studenthelper.domain.ModelConfig;
import com.ai.studenthelper.infra.crypto.ApiKeyCipher;
import com.ai.studenthelper.infra.model.ModelConfigResolver;
import com.ai.studenthelper.infra.persistence.ModelConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModelConfigApplicationServiceTest {

    @Mock
    private ModelConfigRepository repository;

    private ApiKeyCipher cipher;
    private ModelConfigApplicationService service;

    private static ModelConfigRequest request(String apiKey, Boolean vision) {
        return new ModelConfigRequest("我的视觉模型", "custom", "https://example.com/v1",
                apiKey, "vision-model-01", vision, true, null);
    }

    @BeforeEach
    void setUp() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        cipher = new ApiKeyCipher(key);
        service = new ModelConfigApplicationService(repository, cipher);
    }

    @Test
    void createEncryptsApiKeyAndDoesNotActivateAutomatically() {
        when(repository.save(any(ModelConfig.class))).thenAnswer(invocation -> {
            ModelConfig c = invocation.getArgument(0);
            c.setId(1L);
            return c;
        });

        Long id = service.create(request("sk-plain-secret", true));

        assertEquals(1L, id);
        ArgumentCaptor<ModelConfig> captor = ArgumentCaptor.forClass(ModelConfig.class);
        verify(repository).save(captor.capture());
        ModelConfig saved = captor.getValue();
        assertNotEquals("sk-plain-secret", saved.getApiKeyCipher());
        assertEquals("sk-plain-secret", cipher.decrypt(saved.getApiKeyCipher()));
        assertEquals(0, saved.getIsCurrent());
        assertEquals(1, saved.getVisionSupported());
    }

    @Test
    void listNeverExposesApiKeyOnlyHasApiKeyFlag() {
        ModelConfig config = new ModelConfig();
        config.setId(7L);
        config.setName("cfg");
        config.setProvider("custom");
        config.setBaseUrl("https://example.com/v1");
        config.setModelName("m");
        config.setApiKeyCipher(cipher.encrypt("sk-secret"));
        config.setIsCurrent(1);
        when(repository.findAllOrdered()).thenReturn(List.of(config));

        List<ModelConfigResponse> result = service.list();

        assertEquals(1, result.size());
        assertTrue(result.get(0).hasApiKey());
        assertTrue(result.get(0).isCurrent());
        // 响应 record 中不存在 apiKey 字段，序列化结果天然不含任何片段
        assertFalse(result.get(0).toString().contains("sk-secret"));
    }

    @Test
    void updateWithBlankApiKeyKeepsExistingCipherText() {
        ModelConfig existing = new ModelConfig();
        existing.setId(3L);
        existing.setApiKeyCipher(cipher.encrypt("sk-old"));
        when(repository.findById(3L)).thenReturn(Optional.of(existing));
        when(repository.save(any(ModelConfig.class))).thenAnswer(i -> i.getArgument(0));

        service.update(3L, request("   ", false));

        ArgumentCaptor<ModelConfig> captor = ArgumentCaptor.forClass(ModelConfig.class);
        verify(repository).save(captor.capture());
        assertEquals("sk-old", cipher.decrypt(captor.getValue().getApiKeyCipher()));
        assertEquals(0, captor.getValue().getVisionSupported());
    }

    @Test
    void activateClearsPreviousCurrentInSameServiceCall() {
        ModelConfig target = new ModelConfig();
        target.setId(9L);
        when(repository.findById(9L)).thenReturn(Optional.of(target));
        when(repository.save(any(ModelConfig.class))).thenAnswer(i -> i.getArgument(0));

        service.activate(9L);

        verify(repository).clearCurrent();
        assertEquals(1, target.getIsCurrent());
    }

    @Test
    void deletingCurrentConfigIsRejected() {
        ModelConfig current = new ModelConfig();
        current.setId(5L);
        current.setIsCurrent(1);
        when(repository.findById(5L)).thenReturn(Optional.of(current));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(5L));
        assertEquals(ErrorCode.E3001, ex.getErrorCode());
        verify(repository, never()).deleteById(any());
    }

    @Test
    void invalidBaseUrlAndMissingVisionAreRejected() {
        ModelConfigRequest badUrl = new ModelConfigRequest("n", "custom", "ftp://bad",
                "k", "m", true, true, null);
        assertThrows(BusinessException.class, () -> service.create(badUrl));
        assertThrows(BusinessException.class, () -> service.create(request("k", null)));
    }

    @Test
    void templatesAreAtLeastThreeAndContainNoKey() {
        assertTrue(service.templates().size() >= 3);
        assertTrue(service.templates().stream().noneMatch(t -> t.baseUrl().contains("sk-")));
    }

    @Test
    void resolverThrowsE3002WhenNoCurrentConfig() {
        when(repository.findCurrent()).thenReturn(Optional.empty());
        ModelConfigResolver resolver = new ModelConfigResolver(repository, cipher);

        BusinessException ex = assertThrows(BusinessException.class, resolver::resolveCurrent);
        assertEquals(ErrorCode.E3002, ex.getErrorCode());
    }
}
