package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.SectionSchema;
import com.ai.studenthelper.common.SubjectSkillFolderParser;
import com.ai.studenthelper.common.SubjectSpecJson;
import com.ai.studenthelper.common.dto.SubjectSkillRequest;
import com.ai.studenthelper.common.dto.SubjectSkillResponse;
import com.ai.studenthelper.domain.Session;
import com.ai.studenthelper.infra.persistence.SubjectSkillRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * CR-018：学科规范用例（与通用技能 {@code SkillApplicationService} 完全独立）。
 *
 * <p>关闭态不读表；**CR-060 起取消内置只读概念**，所有条目均可编辑、停用、删除；
 * 停用后不进入 AUTO 清单。</p>
 */
@Service
public class SubjectSkillApplicationService {

    /** 学科段独立配额：一次只注入 1 条主规范，跨学科不叠加。 */
    public static final int MAX_INJECTED_SUBJECTS = 1;
    public static final int MAX_SUBJECT_SPEC_CHARS = 600;

    private static final int MAX_NAME = 32;
    private static final int MAX_DESCRIPTION = 200;
    private static final int MAX_KEYWORDS = 512;
    private static final int MAX_SPEC = 5000;

    private final SubjectSkillRepository repository;

    /** CR-039：`subjectSkill` 功能开关已移除，学科规范恒可用，构造不再依赖开关服务。 */
    public SubjectSkillApplicationService(SubjectSkillRepository repository) {
        this.repository = repository;
    }

    // ------------------------------------------------------------------
    // CRUD
    // ------------------------------------------------------------------

    public List<SubjectSkillResponse> list() {
        return repository.findAll();
    }

    public SubjectSkillResponse create(SubjectSkillRequest request) {
        String name = normalizeName(request == null ? null : request.name());
        String description = normalizeDescription(request == null ? null : request.description());
        String keywords = normalizeKeywords(request == null ? null : request.keywords());
        SubjectSpecJson.Specs specs = normalizeSpecs(
                request == null ? null : request.specJson(),
                request == null ? null : request.specMd());
        String sections = normalizeSections(request == null ? null : request.sectionsJson());
        if (repository.nameExists(name, null)) {
            throw new BusinessException(ErrorCode.E1004, "学科规范已存在：" + name);
        }
        boolean enabled = request != null && Boolean.TRUE.equals(request.enabled());
        Long id = repository.insert(null, name, description, "CUSTOM", enabled, keywords,
                specs.solve(), sections, SubjectSpecJson.toJson(specs));
        return require(id);
    }

    public SubjectSkillResponse update(Long id, SubjectSkillRequest request) {
        require(id);
        String name = normalizeName(request == null ? null : request.name());
        if (repository.nameExists(name, id)) {
            throw new BusinessException(ErrorCode.E1004, "学科规范已存在：" + name);
        }
        SubjectSpecJson.Specs specs = normalizeSpecs(
                request == null ? null : request.specJson(),
                request == null ? null : request.specMd());
        repository.update(id, name,
                normalizeDescription(request == null ? null : request.description()),
                normalizeKeywords(request == null ? null : request.keywords()),
                specs.solve(),
                normalizeSections(request == null ? null : request.sectionsJson()),
                SubjectSpecJson.toJson(specs),
                request == null ? null : request.enabled());
        return require(id);
    }

    public void delete(Long id) {
        require(id);
        repository.unbindSessions(id);
        repository.delete(id);
    }

    /** 任意条目可停用；停用后不出现在 AUTO 清单。 */
    public SubjectSkillResponse setEnabled(Long id, Boolean enabled) {
        require(id);
        if (enabled == null) {
            throw new BusinessException(ErrorCode.E1004, "enabled 不能为空");
        }
        repository.setEnabled(id, enabled);
        return require(id);
    }

    /** SKILL.md 文件夹导入，独立解析器，导入后默认停用。 */
    public SubjectSkillResponse importFromFolder(List<byte[]> fileBytes, List<String> paths) {
        SubjectSkillFolderParser.Parsed parsed = SubjectSkillFolderParser.parse(fileBytes, paths);
        String name = normalizeName(parsed.name());
        String description = normalizeDescription(parsed.description());
        String keywords = normalizeKeywords(parsed.keywords());
        SubjectSpecJson.Specs specs = normalizeSpecs(parsed.specJson(), parsed.specMd());
        String sections = normalizeSections(parsed.sectionsJson());
        if (repository.nameExists(name, null)) {
            throw new BusinessException(ErrorCode.E1004, "学科规范已存在：" + name);
        }
        Long id = repository.insert(null, name, description, "CUSTOM", false,
                keywords, specs.solve(), sections, SubjectSpecJson.toJson(specs));
        return require(id);
    }

    // ------------------------------------------------------------------
    // 运行时（解题主链路 / 工具链路）
    // ------------------------------------------------------------------

    /**
     * 按会话三态与首轮固化结果构造单请求运行状态。
     * 开关关闭时直接返回 OFF，不读任何学科表（AC-501-10 关闭态逐字一致）。
     */
    public SubjectSkillRuntime resolveRuntime(Session session) {
        Long sessionId = session == null ? null : session.getId();
        SubjectSkillRuntime.Mode mode = parseMode(session == null ? null : session.getSubjectMode());
        if (mode == SubjectSkillRuntime.Mode.OFF) {
            return SubjectSkillRuntime.off(sessionId);
        }
        SubjectSkillResponse bound = session != null && session.getSubjectSkillId() != null
                ? repository.findById(session.getSubjectSkillId()).orElse(null) : null;
        if (bound != null && !bound.enabled()) {
            bound = null;
        }
        if (mode == SubjectSkillRuntime.Mode.MANUAL) {
            return bound == null
                    ? SubjectSkillRuntime.off(sessionId)
                    : SubjectSkillRuntime.fixed(sessionId, mode, bound);
        }
        if (bound != null) {
            // 首轮已固化：后续轮与追问直接注入，不再注册工具（AC-501-6）
            return SubjectSkillRuntime.fixed(sessionId, SubjectSkillRuntime.Mode.AUTO, bound);
        }
        return SubjectSkillRuntime.auto(sessionId, repository.findEnabled());
    }

    /** 供 subject_get 工具：只返回启用中的条目。 */
    public Optional<SubjectSkillResponse> findEnabledByCode(String code) {
        return repository.findEnabledByCode(code);
    }

    /** 供 subject_get 工具：自定义学科按 CUSTOM-&lt;id&gt; 读取。 */
    public SubjectSkillResponse findById(Long id) {
        return repository.findById(id).orElse(null);
    }

    /** 供 subject_get 工具：命中后立即固化到会话，发生在流式生成之前。 */
    public void bindToSession(Long sessionId, Long skillId) {
        repository.bindToSession(sessionId, skillId);
    }

    public static SubjectSkillRuntime.Mode parseMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return SubjectSkillRuntime.Mode.AUTO;
        }
        try {
            return SubjectSkillRuntime.Mode.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.E1004, "学科选择模式只能是 AUTO / MANUAL / OFF");
        }
    }

    /** 截断到学科段独立预算（AC-501-10）。 */
    public static String trimSpec(String spec) {
        if (spec == null) {
            return "";
        }
        String trimmed = spec.trim();
        return trimmed.length() > MAX_SUBJECT_SPEC_CHARS
                ? trimmed.substring(0, MAX_SUBJECT_SPEC_CHARS) : trimmed;
    }

    private SubjectSkillResponse require(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.E1004, "学科规范 id 必填");
        }
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.E1005, "学科规范不存在"));
    }

    private static String normalizeName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ErrorCode.E1004, "学科名称不能为空");
        }
        String name = raw.trim();
        if (name.length() > MAX_NAME) {
            throw new BusinessException(ErrorCode.E1004, "学科名称不超过 " + MAX_NAME + " 字");
        }
        return name;
    }

    private static String normalizeDescription(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        return value.length() > MAX_DESCRIPTION ? value.substring(0, MAX_DESCRIPTION) : value;
    }

    private static String normalizeKeywords(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        return value.length() > MAX_KEYWORDS ? value.substring(0, MAX_KEYWORDS) : value;
    }

    /**
     * CR-039：三段规范至少填一段；单段超过 {@link #MAX_SPEC} 视为非法（E1004），
     * 不再静默截断——避免用户以为保存成功、实际被截掉半段规范。
     */
    private static SubjectSpecJson.Specs normalizeSpecs(String specJson, String specMd) {
        SubjectSpecJson.Specs specs;
        if (specJson != null && !specJson.isBlank()) {
            specs = SubjectSpecJson.parseStrict(specJson);
        } else {
            specs = new SubjectSpecJson.Specs(specMd == null ? "" : specMd.trim(), "", "");
        }
        if (specs.allBlank()) {
            throw new BusinessException(ErrorCode.E1004, "学科回答规范不能为空（三段至少填写一段）");
        }
        return new SubjectSpecJson.Specs(checkSpecLength(specs.solve(), "解题"),
                checkSpecLength(specs.followUp(), "追问"),
                checkSpecLength(specs.explain(), "讲解"));
    }

    private static String checkSpecLength(String value, String form) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() > MAX_SPEC) {
            throw new BusinessException(ErrorCode.E1004,
                    form + "规范不能超过 " + MAX_SPEC + " 字");
        }
        return trimmed;
    }

    private static String normalizeSections(String raw) {
        SectionSchema schema = SectionSchema.parse(raw);
        return schema.toJson();
    }
}
