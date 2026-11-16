package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.SectionSchema;
import com.ai.studenthelper.common.SubjectSpecJson;
import com.ai.studenthelper.common.dto.SubjectSkillRequest;
import com.ai.studenthelper.common.dto.SubjectSkillResponse;
import com.ai.studenthelper.domain.Session;
import com.ai.studenthelper.infra.persistence.SubjectSkillRepository;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** CR-018 / AC-501-1 / AC-501-2 / AC-501-3 / AC-501-6：CRUD、导入、三态解析。 */
class SubjectSkillApplicationServiceTest {

    private static final String MATH_JSON = """
            {"full":["## ① 题意与已知条件","## ② 定理与公式","## ③ 推导过程","## ④ 结论与验证","## ⑤ 易错点与适用边界"],
             "idea":["## ① 题意与已知条件","## ② 定理与公式","## ③ 思路要点"]}
            """;

    private final SubjectSkillRepository repository = mock(SubjectSkillRepository.class);
    private final SubjectSkillApplicationService service =
            new SubjectSkillApplicationService(repository);

    /** CR-060：原内置条目归一为普通条目（scope=CUSTOM，code 保留）。 */
    private static SubjectSkillResponse preset() {
        return new SubjectSkillResponse(1L, "DATA_STRUCTURE", "数据结构", null, "CUSTOM", true,
                "栈", "规范", SectionSchema.defaultSchema().toJson(),
                LocalDateTime.now(), LocalDateTime.now());
    }

    private static SubjectSkillResponse custom() {
        return new SubjectSkillResponse(9L, null, "离散数学", null, "CUSTOM", true,
                "图论", "规范", MATH_JSON, LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    void invalidFormIsRejectedAndNeverPersisted() {
        BusinessException empty = assertThrows(BusinessException.class,
                () -> service.create(new SubjectSkillRequest("", null, null, "规范", MATH_JSON, false)));
        assertEquals(ErrorCode.E1004, empty.getErrorCode());
        BusinessException badSchema = assertThrows(BusinessException.class,
                () -> service.create(new SubjectSkillRequest("坏结构", null, null, "规范", "{}", false)));
        assertEquals(ErrorCode.E1004, badSchema.getErrorCode());
        verify(repository, never()).insert(any(), any(), any(), any(), anyBoolean(),
                any(), any(), any(), any());
    }

    /** CR-060：取消内置只读，任何条目均可编辑 / 删除 / 停用。 */
    @Test
    void everyEntryCanBeEditedDeletedAndDisabled() {
        when(repository.findById(1L)).thenReturn(Optional.of(preset()));
        service.update(1L, new SubjectSkillRequest("改名", null, null, "规范", MATH_JSON, false));
        verify(repository).update(eq(1L), eq("改名"), any(), any(), any(), any(), any(), any());

        service.delete(1L);
        verify(repository).unbindSessions(1L);
        verify(repository).delete(1L);

        service.setEnabled(1L, false);
        verify(repository).setEnabled(1L, false);
    }

    @Test
    void folderImportIsDisabledByDefault() {
        String md = "---\nname: 概率论\ndescription: 概率题规范\n---\n【推理要求】写明分布。\n";
        when(repository.nameExists(eq("概率论"), isNull())).thenReturn(false);
        when(repository.insert(any(), any(), any(), any(), anyBoolean(), any(), any(), any(), any()))
                .thenReturn(9L);
        when(repository.findById(9L)).thenReturn(Optional.of(custom()));
        service.importFromFolder(List.of(md.getBytes(StandardCharsets.UTF_8)),
                List.of("probability/SKILL.md"));
        verify(repository).insert(isNull(), eq("概率论"), any(), eq("CUSTOM"), eq(false),
                any(), any(), any(), any());
    }

    /** CR-039：功能开关移除，但会话 OFF 仍不读学科表。 */
    @Test
    void offModeDoesNotReadAnySubjectTable() {
        Session session = new Session();
        session.setId(7L);
        session.setSubjectMode("OFF");
        SubjectSkillRuntime runtime = service.resolveRuntime(session);
        assertTrue(runtime.isOff());
        verifyNoInteractions(repository);
    }

    /** CR-039：三段规范写入 spec_json，并把 solve 双写到 spec_md 以兼容旧读取方。 */
    @Test
    void threeSpecSegmentsArePersistedAndSolved() {
        when(repository.nameExists(eq("线性代数"), isNull())).thenReturn(false);
        when(repository.insert(any(), any(), any(), any(), anyBoolean(), any(), any(), any(), any()))
                .thenReturn(9L);
        when(repository.findById(9L)).thenReturn(Optional.of(custom()));
        service.create(new SubjectSkillRequest("线性代数", null, null, "解题规范", MATH_JSON, false,
                SubjectSpecJson.toJson(new SubjectSpecJson.Specs("解题规范", "追问规范", "讲解规范"))));
        verify(repository).insert(isNull(), eq("线性代数"), any(), eq("CUSTOM"), eq(false),
                any(), eq("解题规范"), any(), argThat(json -> json != null
                        && json.contains("\"followUp\":\"追问规范\"")
                        && json.contains("\"explain\":\"讲解规范\"")));
    }

    /** CR-039：三段全空、或 specJson 不是对象时拒绝落库。 */
    @Test
    void blankOrMalformedSpecJsonIsRejected() {
        assertEquals(ErrorCode.E1004, assertThrows(BusinessException.class,
                () -> service.create(new SubjectSkillRequest("空规范", null, null, "", MATH_JSON, false,
                        "{\"solve\":\"\",\"followUp\":\"\",\"explain\":\"\"}"))).getErrorCode());
        assertEquals(ErrorCode.E1004, assertThrows(BusinessException.class,
                () -> service.create(new SubjectSkillRequest("坏规范", null, null, "", MATH_JSON, false,
                        "[1,2,3]"))).getErrorCode());
    }

    @Test
    void autoFirstRoundNeedsToolsAndBoundSessionDoesNot() {
        when(repository.findEnabled()).thenReturn(List.of(preset()));
        when(repository.findById(9L)).thenReturn(Optional.of(custom()));
        Session first = new Session();
        first.setId(7L);
        first.setSubjectMode("AUTO");
        assertTrue(service.resolveRuntime(first).needsTools());

        Session bound = new Session();
        bound.setId(7L);
        bound.setSubjectMode("AUTO");
        bound.setSubjectSkillId(9L);
        SubjectSkillRuntime reused = service.resolveRuntime(bound);
        assertFalse(reused.needsTools(), "首轮固化后不再调工具");
        assertEquals("离散数学", reused.subjectName());
        assertEquals(5, reused.schema().full().size());

        Session manual = new Session();
        manual.setId(7L);
        manual.setSubjectMode("MANUAL");
        manual.setSubjectSkillId(9L);
        assertFalse(service.resolveRuntime(manual).needsTools());

        Session off = new Session();
        off.setId(7L);
        off.setSubjectMode("OFF");
        assertTrue(service.resolveRuntime(off).isOff());
    }
}
