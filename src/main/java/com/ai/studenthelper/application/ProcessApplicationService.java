package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.ProcessItem;
import com.ai.studenthelper.infra.persistence.ProcessRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/** CR-027：模型行为过程只读查询，以及删除会话时的级联清理。 */
@Service
public class ProcessApplicationService {

    private final ProcessRepository repository;

    public ProcessApplicationService(ProcessRepository repository) {
        this.repository = repository;
    }

    public List<ProcessItem> list(Long sessionId) {
        if (sessionId == null) {
            throw new BusinessException(ErrorCode.E1004, "sessionId 必填");
        }
        return repository.listBySession(sessionId);
    }

    public void deleteBySessionId(Long sessionId) {
        repository.deleteBySessionId(sessionId);
    }
}
