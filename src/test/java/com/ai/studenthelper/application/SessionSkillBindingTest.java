package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.dto.SkillResponse;
import com.ai.studenthelper.domain.Session;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.memory.SessionSummaryStore;
import com.ai.studenthelper.infra.persistence.KnowledgePointRepository;
import com.ai.studenthelper.infra.persistence.MessageFeedbackRepository;
import com.ai.studenthelper.infra.persistence.MessageRepository;
import com.ai.studenthelper.infra.persistence.ModelProviderRepository;
import com.ai.studenthelper.infra.persistence.SessionRepository;
import com.ai.studenthelper.infra.persistence.SkillRepository;
import com.ai.studenthelper.infra.persistence.StepUnlockRepository;
import com.ai.studenthelper.infra.storage.ImageStorage;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** CR-038 / AC-505-1~3：会话级技能绑定、写回与正文预算。 */
class SessionSkillBindingTest {

    private final SessionRepository sessions = mock(SessionRepository.class);
    private final SkillRepository skills = mock(SkillRepository.class);

    private SessionApplicationService service() {
        return new SessionApplicationService(sessions, mock(MessageRepository.class),
                mock(MessageFeedbackRepository.class), mock(ImageStorage.class),
                mock(KnowledgePointRepository.class),
                mock(StepUnlockRepository.class), mock(ModelProviderRepository.class),
                mock(SessionSummaryStore.class));
    }

    private static Session session(String raw) {
        Session session = new Session();
        session.setId(7L);
        session.setTitle("测试会话");
        session.setSkillIds(raw);
        return session;
    }

    @Test
    void parseSkillIdsIsTolerantAndDeduplicates() {
        assertEquals(List.of(), SessionApplicationService.parseSkillIds(null));
        assertEquals(List.of(), SessionApplicationService.parseSkillIds("  "));
        assertEquals(List.of(1L, 9L),
                SessionApplicationService.parseSkillIds("1,9,1,x,, 9"));
        assertEquals(List.of(2L), SessionApplicationService.parseSkillIds("2"));
    }

    @Test
    void setSkillsRejectsMoreThanTwoAndClearsWithEmptyList() {
        when(sessions.findById(7L)).thenReturn(Optional.of(session("1,2")));
        when(sessions.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SessionApplicationService service = service();

        assertThrows(BusinessException.class,
                () -> service.setSkills(7L, List.of(1L, 2L, 3L)), "最多 2 条");

        assertEquals(List.of(),
                service.setSkills(7L, List.of()).skillIds(), "空数组清除固化回到自动");
    }

    @Test
    void setSkillsRejectsDisabledOrMissingSkill() {
        SessionApplicationService service = service();
        ReflectionTestUtils.setField(service, "skillRepository", skills);
        when(sessions.findById(7L)).thenReturn(Optional.of(session(null)));
        when(skills.findById(1L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.setSkills(7L, List.of(1L)));

        when(skills.findById(2L)).thenReturn(Optional.of(
                new SkillResponse(2L, "停用", null, "正文", false, LocalDateTime.now())));
        assertThrows(BusinessException.class, () -> service.setSkills(7L, List.of(2L)));
    }

    @Test
    void bindSkillAppendsDeduplicatesAndCaps() {
        Session stored = session("1");
        when(sessions.findById(7L)).thenReturn(Optional.of(stored));
        when(sessions.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SessionApplicationService service = service();

        service.bindSkill(7L, 1L);
        assertEquals("1", stored.getSkillIds(), "已固化不重复写入");
        verify(sessions, never()).save(any(Session.class));

        service.bindSkill(7L, 2L);
        assertEquals("1,2", stored.getSkillIds());

        service.bindSkill(7L, 3L);
        assertEquals("1,2", stored.getSkillIds(), "达到 2 条上限后不再追加");
    }

    @Test
    void contentsForIdsHonoursSwitchCapAndCharLimit() {
        S2FeatureService features = mock(S2FeatureService.class);
        SkillApplicationService service = new SkillApplicationService(skills, features);
        when(features.isEnabled(S2Feature.SKILL)).thenReturn(false);
        assertTrue(service.contentsForIds(List.of(1L)).isEmpty(), "开关关闭态不读正文");

        when(features.isEnabled(S2Feature.SKILL)).thenReturn(true);
        when(skills.findById(1L)).thenReturn(Optional.of(
                new SkillResponse(1L, "甲", null, "x".repeat(1200), true, LocalDateTime.now())));
        when(skills.findById(2L)).thenReturn(Optional.of(
                new SkillResponse(2L, "乙", null, "短正文", true, LocalDateTime.now())));
        when(skills.findById(3L)).thenReturn(Optional.of(
                new SkillResponse(3L, "丙", null, "第三条", true, LocalDateTime.now())));
        when(skills.findEnabledByName("甲")).thenReturn(Optional.of(
                new SkillResponse(1L, "甲", null, "正文", true, LocalDateTime.now())));
        when(skills.findEnabledByName("不存在")).thenReturn(Optional.empty());

        List<String[]> rows = service.contentsForIds(List.of(1L, 2L, 3L));
        assertEquals(2, rows.size(), "最多 2 条");
        assertEquals("甲", rows.get(0)[0]);
        assertEquals(SkillApplicationService.MAX_INJECTED_CHARS, rows.get(0)[1].length());
        assertEquals("乙", rows.get(1)[0]);
        assertEquals(1L, service.enabledIdFor("甲"));
        assertNull(service.enabledIdFor("不存在"));
    }
}
