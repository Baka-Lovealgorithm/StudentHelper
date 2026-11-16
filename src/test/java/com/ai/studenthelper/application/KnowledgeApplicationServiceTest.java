package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.KnowledgePageResponse;
import com.ai.studenthelper.common.dto.KnowledgePointResponse;
import com.ai.studenthelper.infra.persistence.KnowledgePointRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/** CR-040 / AC-507-1 / AC-507-2 / AC-507-3：日期区间校验、手动新增与删除的用例层口径。 */
class KnowledgeApplicationServiceTest {

    private final KnowledgePointRepository repository = mock(KnowledgePointRepository.class);
    private final S2FeatureService features = mock(S2FeatureService.class);
    private final KnowledgeApplicationService service =
            new KnowledgeApplicationService(repository, features);

    /** AC-507-1：日期格式非法直接拒绝，不落到仓储。 */
    @Test
    void malformedDateIsRejected() {
        assertEquals(ErrorCode.E1004, assertThrows(BusinessException.class,
                () -> service.list(null, "2026-13-01", null)).getErrorCode());
        assertEquals(ErrorCode.E1004, assertThrows(BusinessException.class,
                () -> service.list(null, null, "2026/11/13")).getErrorCode());
        verifyNoInteractions(repository);
    }

    /** AC-507-1：起始晚于结束拒绝。 */
    @Test
    void fromAfterToIsRejected() {
        assertEquals(ErrorCode.E1004, assertThrows(BusinessException.class,
                () -> service.list(null, "2026-11-13", "2026-11-01")).getErrorCode());
        verify(repository, never()).list(any(), any(), any());
    }

    /** AC-507-1：单日区间（from == to）合法，且两端都可为空。 */
    @Test
    void parsedDatesArePassedThrough() {
        when(repository.list(any(), any(), any())).thenReturn(List.of());
        service.list("数据结构", "2026-11-13", "2026-11-13");
        verify(repository).list(eq("数据结构"), eq(LocalDate.of(2026, 11, 13)),
                eq(LocalDate.of(2026, 11, 13)));

        service.list(null, "", null);
        verify(repository).list(isNull(), isNull(), isNull());
    }

    /** AC-507-2：学科与名称必填。 */
    @Test
    void blankSubjectOrNameIsRejected() {
        assertEquals(ErrorCode.E1004, assertThrows(BusinessException.class,
                () -> service.create("  ", null, "栈顶指针")).getErrorCode());
        assertEquals(ErrorCode.E1004, assertThrows(BusinessException.class,
                () -> service.create("数据结构", null, "")).getErrorCode());
        verify(repository, never()).insertManual(any(), any(), any());
    }

    /** AC-507-2：重复条目报错且不覆盖原记录。 */
    @Test
    void duplicateIsRejectedWithoutInsert() {
        when(repository.exists("数据结构", "栈", "栈顶指针")).thenReturn(true);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create("数据结构", "栈", "栈顶指针"));
        assertEquals(ErrorCode.E1004, error.getErrorCode());
        assertTrue(error.getMessage().contains("已存在"));
        verify(repository, never()).insertManual(any(), any(), any());
    }

    /** AC-507-2：新增成功后回读落库行，父级去空格、空父级落空串。 */
    @Test
    void createReturnsPersistedRow() {
        when(repository.exists("数据结构", "", "循环队列")).thenReturn(false);
        when(repository.insertManual("数据结构", "", "循环队列")).thenReturn(9L);
        when(repository.findById(9L)).thenReturn(Optional.of(
                new KnowledgePointResponse(9L, "数据结构", "", "循环队列", 0, "2026-11-13 16:05")));

        KnowledgePointResponse created = service.create("数据结构", null, "循环队列");
        assertEquals(9L, created.id());
        assertEquals(0, created.hitCount());
        assertEquals("2026-11-13 16:05", created.createdAt());
    }

    /** AC-507-3：删除不存在的 id 返回 E1005。 */
    @Test
    void deleteMissingReturnsNotFound() {
        when(repository.delete(404L)).thenReturn(0);
        assertEquals(ErrorCode.E1005, assertThrows(BusinessException.class,
                () -> service.delete(404L)).getErrorCode());
    }

    /** AC-507-3：删除成功不发生异常，空 id 由入口参数校验挡住。 */
    @Test
    void deleteExistingPassesThrough() {
        when(repository.delete(9L)).thenReturn(1);
        assertDoesNotThrow(() -> service.delete(9L));
        assertEquals(ErrorCode.E1004, assertThrows(BusinessException.class,
                () -> service.delete(null)).getErrorCode());
        verify(repository, never()).delete(isNull());
    }

    /** AC-507-7：页码与页大小越界收敛（负数页按 0、超出上限按 50），不抛错。 */
    @Test
    void pageClampsPageAndSize() {
        when(repository.listPage(any(), any(), any(), anyInt(), anyLong())).thenReturn(List.of());
        when(repository.count(any(), any(), any())).thenReturn(0L);

        service.page(null, null, null, -3, 999);
        verify(repository).listPage(isNull(), isNull(), isNull(), eq(50), eq(0L));

        service.page(null, null, null, 2, 0);
        verify(repository).listPage(isNull(), isNull(), isNull(), eq(1), eq(2L));
    }

    /** AC-507-7：hasMore 由「offset + 本页条数 < total」决定，末页必须为 false。 */
    @Test
    void pageComputesHasMore() {
        List<KnowledgePointResponse> ten = List.of(
                new KnowledgePointResponse(1L, "数据结构", "栈", "栈顶指针", 0, "2026-11-13 09:00"));
        when(repository.listPage(any(), any(), any(), anyInt(), anyLong())).thenReturn(ten);

        when(repository.count(any(), any(), any())).thenReturn(25L);
        KnowledgePageResponse more = service.page(null, null, null, 0, 10);
        assertTrue(more.hasMore());
        assertEquals(25L, more.total());

        when(repository.count(any(), any(), any())).thenReturn(1L);
        KnowledgePageResponse last = service.page(null, null, null, 0, 10);
        assertFalse(last.hasMore(), "本页取满且 total 等于条数时必须没有下一页");
    }

    /** AC-507-7：分页沿用同一套日期校验，非法日期同样拒绝。 */
    @Test
    void pageRejectsInvalidDateRange() {
        assertEquals(ErrorCode.E1004, assertThrows(BusinessException.class,
                () -> service.page(null, "2026-11-13", "2026-11-01", 0, 10)).getErrorCode());
        verify(repository, never()).listPage(any(), any(), any(), anyInt(), anyLong());
    }

    /** AC-507-4：响应契约不再包含掌握度，新增 createdAt。 */
    @Test
    void responseContractHasNoMastery() {
        List<String> fields = java.util.Arrays.stream(KnowledgePointResponse.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();
        assertEquals(List.of("id", "subject", "parentName", "name", "hitCount", "createdAt"), fields);
        assertFalse(fields.contains("mastery"));
        assertFalse(fields.contains("masteryLabel"));
    }

    /** AC-507-6：开关关闭时读写均被拒（requireEnabled 抛 E3004）。 */
    @Test
    void closedFeatureRejectsAllOperations() {
        doThrow(new BusinessException(ErrorCode.E3004, "已关闭"))
                .when(features).requireEnabled(com.ai.studenthelper.domain.enums.S2Feature.KNOWLEDGE_EXTRACT);
        assertEquals(ErrorCode.E3004, assertThrows(BusinessException.class,
                () -> service.list(null, null, null)).getErrorCode());
        assertEquals(ErrorCode.E3004, assertThrows(BusinessException.class,
                () -> service.create("数据结构", null, "栈")).getErrorCode());
        assertEquals(ErrorCode.E3004, assertThrows(BusinessException.class,
                () -> service.delete(1L)).getErrorCode());
        assertTrue(assertThrows(BusinessException.class,
                () -> service.create("数据结构", null, "栈")).getMessage().contains("已关闭"));
        verify(repository, never()).insertManual(anyString(), any(), anyString());
    }
}
