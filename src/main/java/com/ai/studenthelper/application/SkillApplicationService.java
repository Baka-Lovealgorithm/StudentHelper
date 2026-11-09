package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.SkillRequest;
import com.ai.studenthelper.common.dto.SkillResponse;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.persistence.SkillRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * T-302 解题技能（DD-09）：技能是一段 Markdown 指令，启用后注入 System Prompt。
 * 为保证可预测性，注入最多 2 条、每条截断 800 字；关闭开关或无限用时不做任何注入。
 */
@Service
public class SkillApplicationService {

    public static final int MAX_INJECTED_SKILLS = 2;
    public static final int MAX_INJECTED_CHARS = 800;
    private static final int MAX_NAME = 32;
    private static final int MAX_DESCRIPTION = 200;
    private static final int MAX_CONTENT = 5000;

    private final SkillRepository repository;
    private final S2FeatureService features;

    public SkillApplicationService(SkillRepository repository, S2FeatureService features) {
        this.repository = repository;
        this.features = features;
    }

    /** 供 Prompt 注入：`[name, content]` 形式，最多 2 条、每条 800 字。 */
    public List<String[]> enabledSnippets() {
        if (!features.isEnabled(S2Feature.SKILL)) {
            return List.of();
        }
        List<String[]> out = new ArrayList<>();
        for (SkillResponse skill : repository.findEnabled(MAX_INJECTED_SKILLS)) {
            if (out.size() >= MAX_INJECTED_SKILLS) {
                break;
            }
            String content = skill.content() == null ? "" : skill.content().trim();
            if (content.isEmpty()) {
                continue;
            }
            if (content.length() > MAX_INJECTED_CHARS) {
                content = content.substring(0, MAX_INJECTED_CHARS);
            }
            out.add(new String[]{skill.name(), content});
        }
        return out;
    }

    public List<SkillResponse> list() {
        features.requireEnabled(S2Feature.SKILL);
        return repository.findAll();
    }

    public SkillResponse create(SkillRequest request) {
        features.requireEnabled(S2Feature.SKILL);
        String name = normalizeName(request);
        String content = normalizeContent(request);
        if (repository.nameExists(name, null)) {
            throw new BusinessException(ErrorCode.E1004, "技能已存在：" + name);
        }
        boolean enabled = request.enabled() != null && request.enabled();
        Long id = repository.insert(name, normalizeDescription(request), content, enabled);
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.E1004, "技能创建失败"));
    }

    public SkillResponse update(Long id, SkillRequest request) {
        features.requireEnabled(S2Feature.SKILL);
        requireSkill(id);
        String name = normalizeName(request);
        String content = normalizeContent(request);
        if (repository.nameExists(name, id)) {
            throw new BusinessException(ErrorCode.E1004, "技能已存在：" + name);
        }
        repository.update(id, name, normalizeDescription(request), content, request.enabled());
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.E1004, "技能不存在"));
    }

    public void delete(Long id) {
        features.requireEnabled(S2Feature.SKILL);
        requireSkill(id);
        repository.delete(id);
    }

    private SkillResponse requireSkill(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.E1004, "技能 id 必填");
        }
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.E1004, "技能不存在"));
    }

    private static String normalizeName(SkillRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new BusinessException(ErrorCode.E1004, "技能名称不能为空");
        }
        String name = request.name().trim();
        if (name.length() > MAX_NAME) {
            throw new BusinessException(ErrorCode.E1004, "技能名称不超过 " + MAX_NAME + " 字");
        }
        return name;
    }

    private static String normalizeDescription(SkillRequest request) {
        if (request == null || request.description() == null || request.description().isBlank()) {
            return null;
        }
        String description = request.description().trim();
        return description.length() > MAX_DESCRIPTION
                ? description.substring(0, MAX_DESCRIPTION) : description;
    }

    private static String normalizeContent(SkillRequest request) {
        if (request == null || request.content() == null || request.content().isBlank()) {
            throw new BusinessException(ErrorCode.E1004, "技能内容不能为空");
        }
        String content = request.content().trim();
        return content.length() > MAX_CONTENT ? content.substring(0, MAX_CONTENT) : content;
    }
}
