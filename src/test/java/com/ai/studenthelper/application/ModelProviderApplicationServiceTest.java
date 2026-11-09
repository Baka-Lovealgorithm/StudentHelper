package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.ModelEntryRequest;
import com.ai.studenthelper.common.dto.ProviderListResponse;
import com.ai.studenthelper.common.dto.ProviderRequest;
import com.ai.studenthelper.infra.crypto.ApiKeyCipher;
import com.ai.studenthelper.infra.persistence.ModelProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** CR-012 通用厂商配置 / 模型条目的校验与默认模型规则。 */
@ExtendWith(MockitoExtension.class)
class ModelProviderApplicationServiceTest {

    @Mock
    private ModelProviderRepository repository;
    @Mock
    private ApiKeyCipher cipher;

    private ModelProviderApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ModelProviderApplicationService(repository, cipher);
    }

    private static ModelProviderRepository.ProviderRow provider(long id) {
        return new ModelProviderRepository.ProviderRow(id, "我的端点", "https://example.com/v1",
                "CIPHER", null);
    }

    private static ModelProviderRepository.ModelRow model(long id, long pid, String name, boolean isDefault) {
        return new ModelProviderRepository.ModelRow(id, pid, name, true, true, null, isDefault);
    }

    @Test
    void createRejectsBlankNameAndInvalidBaseUrl() {
        BusinessException blank = assertThrows(BusinessException.class,
                () -> service.createProvider(new ProviderRequest("  ", "https://example.com/v1", "k")));
        assertEquals(ErrorCode.E1004, blank.getErrorCode());

        assertThrows(BusinessException.class,
                () -> service.createProvider(new ProviderRequest("端点", "file:///etc/passwd", "k")));
        verify(repository, never()).insertProvider(anyString(), anyString(), any());
    }

    @Test
    void createRejectsDuplicateProviderName() {
        when(repository.providerNameExists("重名", null)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createProvider(new ProviderRequest("重名", "https://example.com/v1", "k")));
        assertEquals(ErrorCode.E1004, ex.getErrorCode());
    }

    @Test
    void updateKeepsExistingCipherWhenKeyBlank() {
        when(repository.findProvider(1L)).thenReturn(Optional.of(provider(1L)));
        when(repository.providerNameExists("我的端点", 1L)).thenReturn(false);

        service.updateProvider(1L, new ProviderRequest("我的端点", "https://example.com/v1", "  "));

        verify(repository).updateProvider(1L, "我的端点", "https://example.com/v1", "CIPHER");
        verify(cipher, never()).encrypt(anyString());
    }

    @Test
    void updateEncryptsNewKey() {
        when(repository.findProvider(1L)).thenReturn(Optional.of(provider(1L)));
        when(repository.providerNameExists("我的端点", 1L)).thenReturn(false);
        when(cipher.encrypt("new-key")).thenReturn("NEW_CIPHER");

        service.updateProvider(1L, new ProviderRequest("我的端点", "https://example.com/v1", "new-key"));

        verify(repository).updateProvider(1L, "我的端点", "https://example.com/v1", "NEW_CIPHER");
    }

    @Test
    void batchAddSkipsExistingModels() {
        when(repository.findProvider(1L)).thenReturn(Optional.of(provider(1L)));
        when(repository.findDefaultModelId()).thenReturn(7L);
        when(repository.modelNameExists(eq(1L), eq("model-a"), isNull())).thenReturn(true);
        when(repository.modelNameExists(eq(1L), eq("model-b"), isNull())).thenReturn(false);
        when(repository.insertModel(eq(1L), eq("model-b"), eq(false), eq(true), isNull(), eq(false)))
                .thenReturn(8L);

        var added = service.addModels(1L,
                new ModelEntryRequest(null, List.of("model-a", "model-b"), null, null, null));

        assertEquals(1, added.size());
        assertEquals("model-b", added.get(0).modelName());
        verify(repository, times(1)).insertModel(anyLong(), anyString(), anyBoolean(), anyBoolean(),
                any(), anyBoolean());
    }

    @Test
    void singleAddRejectsDuplicateModelName() {
        when(repository.findProvider(1L)).thenReturn(Optional.of(provider(1L)));
        when(repository.modelNameExists(1L, "dup-model", null)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.addModels(1L,
                        new ModelEntryRequest("dup-model", null, true, true, null)));
        assertEquals(ErrorCode.E1004, ex.getErrorCode());
    }

    @Test
    void updateModelKeepsUnspecifiedAttributes() {
        when(repository.findProvider(1L)).thenReturn(Optional.of(provider(1L)));
        when(repository.findModel(3L)).thenReturn(Optional.of(model(3L, 1L, "old-name", false)));
        when(repository.modelNameExists(1L, "old-name", 3L)).thenReturn(false);

        var response = service.updateModel(1L, 3L,
                new ModelEntryRequest(null, null, false, null, null));

        assertEquals("old-name", response.modelName());
        assertFalse(response.visionSupported(), "只覆盖显式传入的 visionSupported");
        assertTrue(response.streamSupported(), "未传入的 streamSupported 保持原值");
        verify(repository).updateModel(3L, "old-name", false, true, null);
    }

    @Test
    void setDefaultClearsPreviousMark() {
        when(repository.findProvider(1L)).thenReturn(Optional.of(provider(1L)));
        when(repository.findModel(3L)).thenReturn(Optional.of(model(3L, 1L, "m", false)));

        service.setDefault(1L, 3L);

        verify(repository).clearDefault();
        verify(repository).setDefault(3L);
    }

    @Test
    void deleteModelClearsSessionReferences() {
        when(repository.findProvider(1L)).thenReturn(Optional.of(provider(1L)));
        when(repository.findModel(3L)).thenReturn(Optional.of(model(3L, 1L, "m", false)));

        service.deleteModel(1L, 3L);

        verify(repository).clearSessionModel(3L);
        verify(repository).deleteModel(3L);
    }

    @Test
    void listNeverExposesApiKey() {
        when(repository.findProviders()).thenReturn(List.of(provider(1L)));
        when(repository.findModels()).thenReturn(List.of(model(3L, 1L, "m", true)));
        when(repository.findDefaultModelId()).thenReturn(3L);

        ProviderListResponse list = service.list();

        assertEquals(1, list.providers().size());
        assertTrue(list.providers().get(0).hasApiKey());
        assertEquals(3L, list.defaultModelId());
        assertEquals("m", list.providers().get(0).models().get(0).modelName());
        // 响应 record 中不存在任何 apiKey / cipher 字段
        assertThrows(NoSuchFieldException.class,
                () -> list.providers().get(0).getClass().getDeclaredField("apiKey"));
    }

    @Test
    void requireUsableModelRejectsMissing() {
        when(repository.findModel(999L)).thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.requireUsableModel(999L));
        assertEquals(ErrorCode.E3003, ex.getErrorCode());
    }

    @Test
    void fetchModelsRejectsUnknownProvider() {
        when(repository.findProvider(999L)).thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class, () -> service.fetchModels(999L));
        assertEquals(ErrorCode.E3003, ex.getErrorCode());
    }
}
