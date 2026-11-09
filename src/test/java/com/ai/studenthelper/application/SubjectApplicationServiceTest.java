package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.SubjectRequest;
import com.ai.studenthelper.common.dto.SubjectResponse;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.persistence.SubjectRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SubjectApplicationServiceTest {

    private final SubjectRepository repository = mock(SubjectRepository.class);
    private final S2FeatureService features = mock(S2FeatureService.class);
    private final SubjectApplicationService service = new SubjectApplicationService(repository, features);

    private static SubjectResponse subject(long id, String name, boolean current) {
        return new SubjectResponse(id, name, null, current, LocalDateTime.now());
    }

    @Test
    void disabledStateFallsBackToDefaultWithoutTouchingTable() {
        when(features.isEnabled(S2Feature.SUBJECT_TEMPLATE)).thenReturn(false);
        var context = service.currentForPrompt();
        assertEquals("数据结构", context.name());
        assertNull(context.hint());
        verifyNoInteractions(repository);
    }

    @Test
    void enabledStateUsesCurrentRow() {
        when(features.isEnabled(S2Feature.SUBJECT_TEMPLATE)).thenReturn(true);
        when(repository.findCurrent()).thenReturn(Optional.of(
                new SubjectResponse(2L, "高等数学", "写出定理名", true, LocalDateTime.now())));
        var context = service.currentForPrompt();
        assertEquals("高等数学", context.name());
        assertEquals("写出定理名", context.hint());
    }

    @Test
    void blankNameAndDuplicateAreRejected() {
        doNothing().when(features).requireEnabled(S2Feature.SUBJECT_TEMPLATE);
        BusinessException blank = assertThrows(BusinessException.class,
                () -> service.create(new SubjectRequest("  ", null)));
        assertEquals(ErrorCode.E1004, blank.getErrorCode());

        when(repository.nameExists("高等数学", null)).thenReturn(true);
        BusinessException dup = assertThrows(BusinessException.class,
                () -> service.create(new SubjectRequest("高等数学", null)));
        assertEquals(ErrorCode.E1004, dup.getErrorCode());
        verify(repository, never()).insert(anyString(), any());
    }

    @Test
    void currentSubjectCannotBeDeleted() {
        doNothing().when(features).requireEnabled(S2Feature.SUBJECT_TEMPLATE);
        when(repository.findById(1L)).thenReturn(Optional.of(subject(1, "数据结构", true)));
        BusinessException error = assertThrows(BusinessException.class, () -> service.delete(1L));
        assertEquals(ErrorCode.E1004, error.getErrorCode());
        verify(repository, never()).delete(any());
    }

    @Test
    void listRequiresToggleEnabled() {
        doThrow(new BusinessException(ErrorCode.E3004)).when(features)
                .requireEnabled(S2Feature.SUBJECT_TEMPLATE);
        BusinessException error = assertThrows(BusinessException.class, () -> service.list());
        assertEquals(ErrorCode.E3004, error.getErrorCode());
    }

    @Test
    void activateSwitchesCurrentRow() {
        doNothing().when(features).requireEnabled(S2Feature.SUBJECT_TEMPLATE);
        when(repository.findById(2L)).thenReturn(Optional.of(subject(2, "高等数学", false)));
        service.activate(2L);
        verify(repository).activate(2L);
    }

    @Test
    void createReturnsPersistedRow() {
        doNothing().when(features).requireEnabled(S2Feature.SUBJECT_TEMPLATE);
        when(repository.nameExists("线性代数", null)).thenReturn(false);
        when(repository.insert("线性代数", "矩阵运算写清步骤")).thenReturn(9L);
        when(repository.findById(9L)).thenReturn(Optional.of(
                new SubjectResponse(9L, "线性代数", "矩阵运算写清步骤", false, LocalDateTime.now())));
        SubjectResponse created = service.create(new SubjectRequest("线性代数", "矩阵运算写清步骤"));
        assertEquals(9L, created.id());
        assertEquals("线性代数", created.name());
        assertFalse(created.current());
        assertNotNull(List.of(created));
    }
}
