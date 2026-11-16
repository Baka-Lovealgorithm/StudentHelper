package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.SkillRequest;
import com.ai.studenthelper.common.dto.SkillResponse;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.persistence.SkillRepository;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SkillApplicationServiceTest {

    private final SkillRepository repository = mock(SkillRepository.class);
    private final S2FeatureService features = mock(S2FeatureService.class);
    private final SkillApplicationService service = new SkillApplicationService(repository, features);

    @Test
    void disabledStateInjectsNothing() {
        when(features.isEnabled(S2Feature.SKILL)).thenReturn(false);
        assertTrue(service.enabledSnippets().isEmpty());
        verifyNoInteractions(repository);
    }

    @Test
    void snippetsAreCappedAndTruncated() {
        when(features.isEnabled(S2Feature.SKILL)).thenReturn(true);
        String longContent = "x".repeat(1200);
        when(repository.findEnabled(SkillApplicationService.MAX_INJECTED_SKILLS)).thenReturn(List.of(
                new SkillResponse(1L, "甲", null, longContent, true, LocalDateTime.now()),
                new SkillResponse(2L, "乙", null, "短内容", true, LocalDateTime.now()),
                new SkillResponse(3L, "丙", null, "第三条", true, LocalDateTime.now())));
        List<String[]> snippets = service.enabledSnippets();
        assertEquals(2, snippets.size());
        assertEquals(SkillApplicationService.MAX_INJECTED_CHARS, snippets.get(0)[1].length());
        assertEquals("乙", snippets.get(1)[0]);
    }

    @Test
    void emptyContentIsSkipped() {
        when(features.isEnabled(S2Feature.SKILL)).thenReturn(true);
        when(repository.findEnabled(SkillApplicationService.MAX_INJECTED_SKILLS)).thenReturn(List.of(
                new SkillResponse(1L, "空技能", null, "   ", true, LocalDateTime.now()),
                new SkillResponse(2L, "有内容", null, "按步骤写", true, LocalDateTime.now())));
        List<String[]> snippets = service.enabledSnippets();
        assertEquals(1, snippets.size());
        assertEquals("有内容", snippets.get(0)[0]);
    }

    /** CR-026：清单只含启用且非空正文技能的名称与描述，超长描述截断 200。 */
    @Test
    void catalogListsNameAndDescriptionOnly() {
        when(features.isEnabled(S2Feature.SKILL)).thenReturn(true);
        when(repository.findEnabled(SkillApplicationService.MAX_CATALOG_SKILLS)).thenReturn(List.of(
                new SkillResponse(1L, "甲", "d".repeat(260), "正文甲", true, LocalDateTime.now()),
                new SkillResponse(2L, "空正文", "说明", "   ", true, LocalDateTime.now())));
        List<String[]> catalog = service.catalog();
        assertEquals(1, catalog.size());
        assertEquals("甲", catalog.get(0)[0]);
        assertEquals(200, catalog.get(0)[1].length());
        assertFalse(catalog.get(0)[1].contains("正文甲"), "清单不含技能正文");
    }

    /** CR-026：开关关闭时清单为空且不读库。 */
    @Test
    void catalogIsEmptyWhenDisabled() {
        when(features.isEnabled(S2Feature.SKILL)).thenReturn(false);
        assertTrue(service.catalog().isEmpty());
        verifyNoInteractions(repository);
    }

    /** CR-026：skill_get 只读启用技能，正文沿用 800 字上限；未命中返回 null。 */
    @Test
    void contentForReadsEnabledSkillAndTruncates() {
        when(features.isEnabled(S2Feature.SKILL)).thenReturn(true);
        when(repository.findEnabledByName("长技能")).thenReturn(Optional.of(
                new SkillResponse(5L, "长技能", null, "y".repeat(1200), true, LocalDateTime.now())));
        when(repository.findEnabledByName("停用")).thenReturn(Optional.empty());
        assertEquals(SkillApplicationService.MAX_INJECTED_CHARS, service.contentFor("长技能").length());
        assertNull(service.contentFor("停用"));
        assertNull(service.contentFor(null));
        when(features.isEnabled(S2Feature.SKILL)).thenReturn(false);
        assertNull(service.contentFor("长技能"), "开关关闭时不读入技能正文");
    }

    @Test
    void emptyContentIsRejectedOnCreate() {
        doNothing().when(features).requireEnabled(S2Feature.SKILL);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(new SkillRequest("技能", null, "  ", true)));
        assertEquals(ErrorCode.E1004, error.getErrorCode());
    }

    @Test
    void duplicateNameIsRejected() {
        doNothing().when(features).requireEnabled(S2Feature.SKILL);
        when(repository.nameExists("复杂度标注", null)).thenReturn(true);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(new SkillRequest("复杂度标注", null, "内容", true)));
        assertEquals(ErrorCode.E1004, error.getErrorCode());
        verify(repository, never()).insert(anyString(), any(), anyString(), anyBoolean());
    }

    private static final String IMPORT_SKILL_MD = """
            ---
            name: 复杂度标注
            description: 每个算法显式标注时间与空间复杂度
            ---
            每个算法都显式标注时间与空间复杂度，并说明最坏情况。
            """;

    @Test
    void importFromFolderStoresDisabledSkill() {
        doNothing().when(features).requireEnabled(S2Feature.SKILL);
        when(repository.nameExists("复杂度标注", null)).thenReturn(false);
        when(repository.insert(anyString(), any(), anyString(), anyBoolean())).thenReturn(7L);
        when(repository.findById(7L)).thenReturn(Optional.of(new SkillResponse(
                7L, "复杂度标注", "每个算法显式标注时间与空间复杂度", "正文", false, LocalDateTime.now())));

        var response = service.importFromFolder(
                List.of(IMPORT_SKILL_MD.getBytes(StandardCharsets.UTF_8)), List.of("demo/SKILL.md"));

        assertEquals("复杂度标注", response.name());
        assertFalse(response.enabled(), "CR-011：导入后默认停用");
        verify(repository).insert(eq("复杂度标注"), eq("每个算法显式标注时间与空间复杂度"),
                contains("最坏情况"), eq(false));
    }

    @Test
    void importRejectsDuplicateName() {
        doNothing().when(features).requireEnabled(S2Feature.SKILL);
        when(repository.nameExists("复杂度标注", null)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.importFromFolder(
                List.of(IMPORT_SKILL_MD.getBytes(StandardCharsets.UTF_8)), List.of("demo/SKILL.md")));
        assertEquals(ErrorCode.E1004, ex.getErrorCode());
        verify(repository, never()).insert(anyString(), any(), anyString(), anyBoolean());
    }

    @Test
    void setEnabledTogglesSingleSkill() {
        doNothing().when(features).requireEnabled(S2Feature.SKILL);
        when(repository.findById(3L)).thenReturn(
                Optional.of(new SkillResponse(3L, "甲", null, "内容", false, LocalDateTime.now())),
                Optional.of(new SkillResponse(3L, "甲", null, "内容", true, LocalDateTime.now())));

        var response = service.setEnabled(3L, true);

        assertTrue(response.enabled());
        verify(repository).setEnabled(3L, true);
        verify(repository, never()).update(anyLong(), anyString(), any(), anyString(), any());
    }

    @Test
    void setEnabledRejectsNullFlag() {
        doNothing().when(features).requireEnabled(S2Feature.SKILL);
        when(repository.findById(3L)).thenReturn(Optional.of(
                new SkillResponse(3L, "甲", null, "内容", false, LocalDateTime.now())));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.setEnabled(3L, null));
        assertEquals(ErrorCode.E1004, ex.getErrorCode());
        verify(repository, never()).setEnabled(anyLong(), anyBoolean());
    }
}
