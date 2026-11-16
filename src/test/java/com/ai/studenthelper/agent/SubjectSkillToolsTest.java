package com.ai.studenthelper.agent;

import com.ai.studenthelper.application.SubjectSkillApplicationService;
import com.ai.studenthelper.application.SubjectSkillRuntime;
import com.ai.studenthelper.common.SectionSchema;
import com.ai.studenthelper.common.dto.SubjectSkillResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** CR-018 / AC-501-5：subject_get 工具确实固化命中并把规范与结构返回给模型。 */
class SubjectSkillToolsTest {

    private static final String MATH_JSON = """
            {"full":["## ① 题意与已知条件","## ② 定理与公式","## ③ 推导过程","## ④ 结论与验证","## ⑤ 易错点与适用边界"],
             "idea":["## ① 题意与已知条件","## ② 定理与公式","## ③ 思路要点"]}
            """;

    private final SubjectSkillApplicationService service = mock(SubjectSkillApplicationService.class);
    private final SubjectSkillResponse math = new SubjectSkillResponse(2L, "ADVANCED_MATH",
            "高等数学", "推导型题目规范", "BUILTIN", true, "极限、积分",
            "【记号与术语】lim 必须写清趋近方向。", MATH_JSON,
            LocalDateTime.now(), LocalDateTime.now());

    @Test
    void subjectListReturnsEnabledCatalog() {
        SubjectSkillRuntime runtime = SubjectSkillRuntime.auto(7L, List.of(math));
        String list = new SubjectSkillTools(service, runtime).subjectList();
        assertTrue(list.contains("ADVANCED_MATH"));
        assertTrue(list.contains("高等数学"));
        assertTrue(list.contains("用 subject_get(code) 读入"));
    }

    @Test
    void subjectGetReturnsSpecAndSchemaAndBindsSessionBeforeGeneration() {
        when(service.findEnabledByCode("ADVANCED_MATH")).thenReturn(Optional.of(math));
        SubjectSkillRuntime runtime = SubjectSkillRuntime.auto(7L, List.of(math));
        SubjectSkillTools tools = new SubjectSkillTools(service, runtime);

        String result = tools.subjectGet("ADVANCED_MATH");

        assertTrue(result.contains("【学科规范：高等数学 · 解题】"));
        assertTrue(result.contains("lim 必须写清趋近方向"));
        assertTrue(result.contains("## ⑤ 易错点与适用边界"), "sections_json 全文应返回给模型");
        assertTrue(result.contains("覆盖系统 Prompt 里的默认六段"));
        assertEquals("高等数学", runtime.subjectName());
        assertEquals(5, runtime.schema().full().size());
        assertEquals("ADVANCED_MATH", runtime.hitCode());
        assertTrue(runtime.auditHit().contains("模型按需读入"));
        assertNotNull(runtime.label(com.ai.studenthelper.domain.enums.GuideMode.FULL));
        verify(service).bindToSession(7L, 2L);
    }

    @Test
    void unknownCodeDoesNotResolveAnySubject() {
        when(service.findEnabledByCode("NOPE")).thenReturn(Optional.empty());
        SubjectSkillRuntime runtime = SubjectSkillRuntime.auto(7L, List.of(math));
        String result = new SubjectSkillTools(service, runtime).subjectGet("NOPE");
        assertTrue(result.contains("未找到或未启用"));
        assertNull(runtime.resolved());
        assertTrue(runtime.schema().isDefault());
        verify(service, never()).bindToSession(any(), any());
    }

    @Test
    void customSubjectCanBeReadByCustomId() {
        SubjectSkillResponse custom = new SubjectSkillResponse(9L, null, "离散数学", null,
                "CUSTOM", true, "图论", "规范正文", SectionSchema.defaultSchema().toJson(),
                LocalDateTime.now(), LocalDateTime.now());
        when(service.findEnabledByCode("CUSTOM-9")).thenReturn(Optional.empty());
        when(service.findById(9L)).thenReturn(custom);
        SubjectSkillRuntime runtime = SubjectSkillRuntime.auto(7L, List.of(custom));

        String result = new SubjectSkillTools(service, runtime).subjectGet("CUSTOM-9");

        assertTrue(result.contains("【学科规范：离散数学 · 解题】"));
        assertEquals("CUSTOM-9", runtime.hitCode());
        verify(service).bindToSession(7L, 9L);
    }
}
