package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.SkillFolderParser;
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
 *
 * <p>CR-011 新增：从文件夹按 SKILL.md 规范导入（默认停用）、单独启停。
 */
@Service
public class SkillApplicationService {

    public static final int MAX_INJECTED_SKILLS = 2;
    public static final int MAX_INJECTED_CHARS = 800;
    /** CR-026：按需加载清单的上限，防止启用技能过多把 system prompt 撑大。 */
    public static final int MAX_CATALOG_SKILLS = 12;
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

    /** CR-026：供 Prompt / `skill_list` 注入的清单 `[name, description]`，不含正文。 */
    public List<String[]> catalog() {
        if (!features.isEnabled(S2Feature.SKILL)) {
            return List.of();
        }
        List<String[]> out = new ArrayList<>();
        for (SkillResponse skill : repository.findEnabled(MAX_CATALOG_SKILLS)) {
            String name = skill.name() == null ? "" : skill.name().trim();
            String content = skill.content() == null ? "" : skill.content().trim();
            if (name.isEmpty() || content.isEmpty()) {
                continue;
            }
            String description = skill.description() == null ? "" : skill.description().trim();
            out.add(new String[]{name, description.length() > 200 ? description.substring(0, 200) : description});
        }
        return out;
    }

    /** CR-026：供 `skill_get` 按名称读入；只返回启用技能，正文沿用 800 字上限。 */
    public String contentFor(String name) {
        if (!features.isEnabled(S2Feature.SKILL) || name == null || name.isBlank()) {
            return null;
        }
        SkillResponse skill = repository.findEnabledByName(name.trim()).orElse(null);
        if (skill == null || skill.content() == null || skill.content().isBlank()) {
            return null;
        }
        String content = skill.content().trim();
        return content.length() > MAX_INJECTED_CHARS
                ? content.substring(0, MAX_INJECTED_CHARS) : content;
    }

    /** CR-038：供 skill_get 命中后写回会话固化；只认启用技能。 */
    public Long enabledIdFor(String name) {
        if (!features.isEnabled(S2Feature.SKILL) || name == null || name.isBlank()) {
            return null;
        }
        return repository.findEnabledByName(name.trim()).map(SkillResponse::id).orElse(null);
    }

    /** CR-038：按会话固化 id 取启用技能正文（每条 ≤800 字、最多 2 条），供每轮注入；关闭态返回空。 */
    public List<String[]> contentsForIds(List<Long> ids) {
        if (!features.isEnabled(S2Feature.SKILL) || ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<String[]> out = new ArrayList<>();
        for (Long id : ids) {
            if (id == null || out.size() >= MAX_INJECTED_SKILLS) {
                break;
            }
            SkillResponse skill = repository.findById(id).orElse(null);
            if (skill == null || !skill.enabled() || skill.content() == null || skill.content().isBlank()) {
                continue;
            }
            String content = skill.content().trim();
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

    /** CR-011：文件夹导入。校验全部由 SkillFolderParser 承担，入库时固定停用。 */
    public SkillResponse importFromFolder(List<byte[]> fileBytes, List<String> paths) {
        features.requireEnabled(S2Feature.SKILL);
        SkillFolderParser.Parsed parsed = SkillFolderParser.parse(fileBytes, paths);
        String name = normalizeNameValue(parsed.name());
        String description = normalizeDescriptionValue(parsed.description());
        String content = normalizeContentValue(parsed.content());
        if (repository.nameExists(name, null)) {
            throw new BusinessException(ErrorCode.E1004, "技能已存在：" + name);
        }
        Long id = repository.insert(name, description, content, false);
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.E1004, "技能导入失败"));
    }

    /** CR-011：单独启停，不需要重传名称与正文。 */
    public SkillResponse setEnabled(Long id, Boolean enabled) {
        features.requireEnabled(S2Feature.SKILL);
        requireSkill(id);
        if (enabled == null) {
            throw new BusinessException(ErrorCode.E1004, "enabled 不能为空");
        }
        repository.setEnabled(id, enabled);
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.E1004, "技能不存在"));
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
        return normalizeNameValue(request == null ? null : request.name());
    }

    private static String normalizeNameValue(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ErrorCode.E1004, "技能名称不能为空");
        }
        String name = raw.trim();
        if (name.length() > MAX_NAME) {
            throw new BusinessException(ErrorCode.E1004, "技能名称不超过 " + MAX_NAME + " 字");
        }
        return name;
    }

    private static String normalizeDescription(SkillRequest request) {
        return normalizeDescriptionValue(request == null ? null : request.description());
    }

    private static String normalizeDescriptionValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String description = raw.trim();
        return description.length() > MAX_DESCRIPTION
                ? description.substring(0, MAX_DESCRIPTION) : description;
    }

    private static String normalizeContent(SkillRequest request) {
        return normalizeContentValue(request == null ? null : request.content());
    }

    private static String normalizeContentValue(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ErrorCode.E1004, "技能内容不能为空");
        }
        String content = raw.trim();
        return content.length() > MAX_CONTENT ? content.substring(0, MAX_CONTENT) : content;
    }
}
