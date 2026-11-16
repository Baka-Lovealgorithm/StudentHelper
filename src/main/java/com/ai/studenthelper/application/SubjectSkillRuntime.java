package com.ai.studenthelper.application;

import com.ai.studenthelper.common.SectionSchema;
import com.ai.studenthelper.common.SubjectSpecJson;
import com.ai.studenthelper.common.dto.SubjectSkillResponse;
import com.ai.studenthelper.domain.enums.AnswerIntent;
import com.ai.studenthelper.domain.enums.GuideMode;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * CR-018：单请求内的学科规范运行状态（不做成 Bean，避免并发串台）。
 *
 * <p>AUTO 首轮由模型通过 {@code subject_get} 工具按需读入规范；工具把命中条目与
 * 输出结构写回本对象，并同步固化到 {@code t_session.subject_skill_id}。
 * 后续轮与追问直接复用已固化条目，不再注册工具。</p>
 */
public final class SubjectSkillRuntime {

    public enum Mode { AUTO, MANUAL, OFF }

    private final Long sessionId;
    private final Mode mode;
    private final SubjectSkillResponse fixed;
    private final List<SubjectSkillResponse> catalog;
    private final AtomicReference<SubjectSkillResponse> resolved = new AtomicReference<>();
    private final AtomicReference<String> hitReason = new AtomicReference<>();

    private SubjectSkillRuntime(Long sessionId, Mode mode, SubjectSkillResponse fixed,
                                List<SubjectSkillResponse> catalog) {
        this.sessionId = sessionId;
        this.mode = mode;
        this.fixed = fixed;
        this.catalog = catalog == null ? List.of() : List.copyOf(catalog);
        if (fixed != null) {
            this.resolved.set(fixed);
            this.hitReason.set(mode == Mode.MANUAL ? "用户手动指定" : "会话首轮已固化");
        }
    }

    /** 关闭态 / OFF：不读表、不注册工具、不注入清单与规范。 */
    public static SubjectSkillRuntime off(Long sessionId) {
        return new SubjectSkillRuntime(sessionId, Mode.OFF, null, List.of());
    }

    /** AUTO 且会话尚未固化：注入清单，注册 subject_list / subject_get。 */
    public static SubjectSkillRuntime auto(Long sessionId, List<SubjectSkillResponse> enabled) {
        return new SubjectSkillRuntime(sessionId, Mode.AUTO, null, enabled);
    }

    /** MANUAL 或 AUTO 已固化：直接注入规范全文，不列清单、不注册工具。 */
    public static SubjectSkillRuntime fixed(Long sessionId, Mode mode, SubjectSkillResponse skill) {
        return new SubjectSkillRuntime(sessionId, mode, skill, List.of());
    }

    public Long sessionId() { return sessionId; }
    public Mode mode() { return mode; }
    public boolean isOff() { return mode == Mode.OFF; }
    public boolean needsTools() { return mode == Mode.AUTO && fixed == null && !catalog.isEmpty(); }
    public List<SubjectSkillResponse> catalog() { return catalog; }

    /** 由 subject_get 工具在流式生成开始前调用。 */
    public void markResolved(SubjectSkillResponse skill, String reason) {
        if (skill != null) {
            resolved.set(skill);
            hitReason.set(reason);
        }
    }

    public SubjectSkillResponse resolved() {
        SubjectSkillResponse current = resolved.get();
        return current != null ? current : fixed;
    }

    public SectionSchema schema() {
        return schemaFor(AnswerIntent.SOLVE);
    }

    /** CR-039：按本轮形态取结构；三种形态共用同一份 sections_json（full / idea / explain）。 */
    public SectionSchema schemaFor(AnswerIntent intent) {
        SubjectSkillResponse skill = resolved();
        if (skill == null) {
            return SectionSchema.defaultSchema();
        }
        SectionSchema parsed = SectionSchema.parseOrNull(skill.sectionsJson());
        return parsed == null ? SectionSchema.defaultSchema() : parsed;
    }

    /** 注入 Prompt 用的规范全文；未命中返回 null。 */
    public String spec() {
        return specFor(AnswerIntent.SOLVE);
    }

    /**
     * CR-039：按本轮形态取学科对应段；未命中学科返回 null，该段留空返回空串
     * （调用方据此回落系统默认规范）。
     */
    public String specFor(AnswerIntent intent) {
        SubjectSkillResponse skill = resolved();
        if (skill == null) {
            return null;
        }
        return SubjectSpecJson.parse(skill.specJson(), skill.specMd()).of(intent);
    }

    public String subjectName() {
        SubjectSkillResponse skill = resolved();
        return skill == null ? null : skill.name();
    }

    public String hitCode() {
        SubjectSkillResponse skill = resolved();
        return skill == null ? null : (skill.code() == null ? "CUSTOM-" + skill.id() : skill.code());
    }

    public String hitReason() { return hitReason.get(); }

    /** 「本次采用：<学科> · <结构>」；未命中返回 null。 */
    public String label(GuideMode mode) {
        return labelFor(AnswerIntent.SOLVE, mode);
    }

    /** CR-039：标签随形态变化（六段结构 / 自由问答 / 五段讲解结构）。 */
    public String labelFor(AnswerIntent intent, GuideMode mode) {
        SubjectSkillResponse skill = resolved();
        if (skill == null) {
            return null;
        }
        String form = switch (intent == null ? AnswerIntent.SOLVE : intent) {
            case FOLLOW_UP -> "自由问答";
            case EXPLAIN -> schemaFor(intent).labelForExplain();
            case SOLVE -> schemaFor(intent).labelFor(mode);
        };
        return skill.name() + " · " + form;
    }

    /** 写进 t_call_log.subject_hit，供审计命中理由。 */
    public String auditHit() {
        SubjectSkillResponse skill = resolved();
        if (skill == null) {
            return null;
        }
        String code = skill.code() == null ? "CUSTOM-" + skill.id() : skill.code();
        String reason = hitReason.get() == null ? "" : hitReason.get();
        return (code + " | " + reason).substring(0, Math.min(500, (code + " | " + reason).length()));
    }
}
