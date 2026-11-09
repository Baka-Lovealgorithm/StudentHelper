package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.SubjectRequest;
import com.ai.studenthelper.common.dto.SubjectResponse;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.persistence.SubjectRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * T-307 学科扩展（DD-08）：可配置学科 Prompt 模板，同一时刻只有一个当前学科。
 * 开关关闭时 currentForPrompt() 返回默认学科且不带补充说明，System Prompt 与 S1 逐字一致。
 */
@Service
public class SubjectApplicationService {

    static final String DEFAULT_SUBJECT = "数据结构";
    private static final int MAX_NAME = 32;
    private static final int MAX_HINT = 1000;

    private final SubjectRepository repository;
    private final S2FeatureService features;

    public SubjectApplicationService(SubjectRepository repository, S2FeatureService features) {
        this.repository = repository;
        this.features = features;
    }

    /** 注入用上下文；关闭态固定返回默认学科、无补充说明。 */
    public record SubjectContext(String name, String hint) {
    }

    public SubjectContext currentForPrompt() {
        if (!features.isEnabled(S2Feature.SUBJECT_TEMPLATE)) {
            return new SubjectContext(DEFAULT_SUBJECT, null);
        }
        return repository.findCurrent()
                .map(s -> new SubjectContext(s.name(),
                        s.promptHint() == null || s.promptHint().isBlank() ? null : s.promptHint()))
                .orElse(new SubjectContext(DEFAULT_SUBJECT, null));
    }

    public String currentName() {
        return currentForPrompt().name();
    }

    public List<SubjectResponse> list() {
        features.requireEnabled(S2Feature.SUBJECT_TEMPLATE);
        return repository.findAll();
    }

    public SubjectResponse create(SubjectRequest request) {
        features.requireEnabled(S2Feature.SUBJECT_TEMPLATE);
        String name = normalizeName(request);
        String hint = normalizeHint(request);
        if (repository.nameExists(name, null)) {
            throw new BusinessException(ErrorCode.E1004, "学科已存在：" + name);
        }
        Long id = repository.insert(name, hint);
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.E1004, "学科创建失败"));
    }

    public SubjectResponse update(Long id, SubjectRequest request) {
        features.requireEnabled(S2Feature.SUBJECT_TEMPLATE);
        requireSubject(id);
        String name = normalizeName(request);
        String hint = normalizeHint(request);
        if (repository.nameExists(name, id)) {
            throw new BusinessException(ErrorCode.E1004, "学科已存在：" + name);
        }
        repository.update(id, name, hint);
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.E1004, "学科不存在"));
    }

    public SubjectResponse activate(Long id) {
        features.requireEnabled(S2Feature.SUBJECT_TEMPLATE);
        requireSubject(id);
        repository.activate(id);
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.E1004, "学科不存在"));
    }

    public void delete(Long id) {
        features.requireEnabled(S2Feature.SUBJECT_TEMPLATE);
        SubjectResponse subject = requireSubject(id);
        if (subject.current()) {
            throw new BusinessException(ErrorCode.E1004, "当前学科不能删除，请先切换到其它学科");
        }
        repository.delete(id);
    }

    private SubjectResponse requireSubject(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.E1004, "学科 id 必填");
        }
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.E1004, "学科不存在"));
    }

    private static String normalizeName(SubjectRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new BusinessException(ErrorCode.E1004, "学科名称不能为空");
        }
        String name = request.name().trim();
        if (name.length() > MAX_NAME) {
            throw new BusinessException(ErrorCode.E1004, "学科名称不超过 " + MAX_NAME + " 字");
        }
        return name;
    }

    private static String normalizeHint(SubjectRequest request) {
        if (request == null || request.promptHint() == null || request.promptHint().isBlank()) {
            return null;
        }
        String hint = request.promptHint().trim();
        return hint.length() > MAX_HINT ? hint.substring(0, MAX_HINT) : hint;
    }
}
