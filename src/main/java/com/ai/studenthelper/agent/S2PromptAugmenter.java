package com.ai.studenthelper.agent;

import com.ai.studenthelper.application.MemoryInjectionService;
import com.ai.studenthelper.application.S2FeatureService;
import com.ai.studenthelper.application.FeatureSnapshot;
import com.ai.studenthelper.application.SkillApplicationService;
import com.ai.studenthelper.application.SubjectSkillRuntime;
import com.ai.studenthelper.common.SectionSchema;
import com.ai.studenthelper.common.dto.SubjectSkillResponse;
import com.ai.studenthelper.domain.enums.AnswerIntent;
import com.ai.studenthelper.domain.enums.GuideMode;
import com.ai.studenthelper.domain.enums.S2Feature;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 所有扩展对 System Prompt 的唯一增量入口（S2 DD-05~DD-07 / S3 DD-08~DD-10 / S5 CR-018 / CR-039）。
 *
 * <p>CR-039：base 由形态（解题 / 追问 / 知识点讲解）决定，学科对应段非空时整体替换该段默认规范，
 * 硬约束段由 {@link SolverPromptTemplate} 固定在规范段之后。增量顺序：
 * 知识点约束（仅解题）→ 学科清单 → 固化技能正文 → 技能清单 → 长期记忆 → 多解法（仅解题）→ MCP。</p>
 */
@Component
public class S2PromptAugmenter {

    private final MemoryInjectionService memoryInjection;
    private final SkillApplicationService skills;
    private final S2FeatureService features;

    /** CR-019：旧学科服务依赖已移除，装配器不再读任何学科表。 */
    public S2PromptAugmenter(MemoryInjectionService memoryInjection,
                             SkillApplicationService skills,
                             S2FeatureService features) {
        this.memoryInjection = memoryInjection;
        this.skills = skills;
        this.features = features;
    }

    /** CR-019：首行恒为学科通用称呼，不再读取旧学科表；无增量时逐字返回 base。 */
    public String buildAndAugment(GuideMode mode) {
        return augment(SolverPromptTemplate.build(mode));
    }

    /** CR-018：带会话学科运行状态的装配入口；不带固化技能时保持 CR-026 口径。 */
    public String buildAndAugment(GuideMode mode, SubjectSkillRuntime runtime) {
        return buildAndAugment(AnswerIntent.SOLVE, mode, runtime, List.of());
    }

    /** CR-038：带会话固化技能的装配入口（形态默认为解题，兼容旧调用）。 */
    public String buildAndAugment(GuideMode mode, SubjectSkillRuntime runtime,
                                  List<String[]> fixedSkills) {
        return buildAndAugment(AnswerIntent.SOLVE, mode, runtime, fixedSkills);
    }

    /**
     * CR-039：三态装配入口。
     *
     * @param intent      本轮形态
     * @param runtime     会话学科运行状态（OFF 时传 {@code SubjectSkillRuntime.off(...)}）
     * @param fixedSkills 会话固化技能的 {@code [名称, 正文]} 列表
     */
    public String buildAndAugment(AnswerIntent intent, GuideMode mode, SubjectSkillRuntime runtime,
                                  List<String[]> fixedSkills) {
        return buildAndAugment(intent, mode, runtime, fixedSkills, null);
    }

    /**
     * CR-043：带请求级开关快照的入口。
     *
     * <p>本轮「多解法 / MCP / 知识点」三个判断共用同一份快照，一次请求只读一次开关表；
     * {@code snapshot == null} 时退回逐次现读，兼容既有单测与直连调用。</p>
     */
    public String buildAndAugment(AnswerIntent intent, GuideMode mode, SubjectSkillRuntime runtime,
                                  List<String[]> fixedSkills, FeatureSnapshot snapshot) {
        AnswerIntent form = intent == null ? AnswerIntent.SOLVE : intent;
        boolean enabled = runtime != null && !runtime.isOff();
        SectionSchema schema = enabled ? runtime.schemaFor(form) : SectionSchema.defaultSchema();
        String base = SolverPromptTemplate.buildForIntent(form, mode, schema,
                enabled ? runtime.subjectName() : null,
                enabled ? runtime.specFor(form) : null);
        return augment(base, enabled ? runtime : null, schema, mode, form, fixedSkills, snapshot);
    }

    public String augment(String base) {
        return augment(base, List.of());
    }

    /** 兼容旧调用：等价于解题形态、无学科运行状态。 */
    public String augment(String base, List<String[]> fixedSkills) {
        return augment(base, null, SectionSchema.defaultSchema(), GuideMode.FULL,
                AnswerIntent.SOLVE, fixedSkills, null);
    }

    /** 快照优先；未提供时逐次现读（单测直连路径）。 */
    private boolean isOn(FeatureSnapshot snapshot, S2Feature feature) {
        return snapshot != null ? snapshot.enabled(feature) : features.isEnabled(feature);
    }

    private String augment(String base, SubjectSkillRuntime runtime, SectionSchema schema,
                           GuideMode mode, AnswerIntent intent, List<String[]> fixedSkills,
                           FeatureSnapshot snapshot) {
        FeatureSnapshot flags = snapshot;
        List<String[]> fixed = fixedSkills == null ? List.of() : fixedSkills;
        String sessionSkillBlock = fixed.isEmpty() ? "" : SolverPromptTemplate.sessionSkillBlock(fixed);
        // T-401：长期记忆统一来自 MEMORY.md（用户手写区 + 模型维护区），不再读 t_memory
        String memoryBlock = memoryInjection.block();
        boolean multiSolution = intent == AnswerIntent.SOLVE && isOn(flags, S2Feature.MULTI_SOLUTION);
        boolean mcpEnabled = isOn(flags, S2Feature.MCP);
        // CR-025：知识点约束只作用于解题轮（讲解结构没有「涉及知识点」段）
        String knowledgeBlock = intent == AnswerIntent.SOLVE
                ? knowledgeBlock(isOn(flags, S2Feature.KNOWLEDGE_EXTRACT), schema, mode)
                : "";
        // CR-026 的按需清单始终注入；CR-038 已有固化正文时两者并存，按需读取不关闭
        List<String[]> skillCatalog = skills.catalog();
        String subjectCatalog = runtime == null ? "" : subjectCatalogBlock(runtime);
        if (memoryBlock.isEmpty() && !multiSolution && !mcpEnabled && skillCatalog.isEmpty()
                && knowledgeBlock.isEmpty() && sessionSkillBlock.isEmpty()
                && subjectCatalog.isEmpty()) {
            return base;
        }
        StringBuilder sb = new StringBuilder(base);
        if (!knowledgeBlock.isEmpty()) {
            sb.append(knowledgeBlock);
        }
        if (!subjectCatalog.isEmpty()) {
            sb.append(subjectCatalog);
        }
        if (!sessionSkillBlock.isEmpty()) {
            sb.append(sessionSkillBlock);
        }
        if (!skillCatalog.isEmpty()) {
            sb.append(SolverPromptTemplate.skillCatalogBlock(skillCatalog));
        }
        if (!memoryBlock.isEmpty()) {
            sb.append(memoryBlock);
        }
        if (multiSolution) {
            sb.append(SolverPromptTemplate.multiSolutionBlock(schema));
        }
        if (mcpEnabled) {
            sb.append(SolverPromptTemplate.mcpBlock(schema));
        }
        return sb.toString();
    }

    /** CR-025：开关关闭或结构不足 2 段时返回空串，保证关闭态 Prompt 逐字不变。 */
    private static String knowledgeBlock(boolean enabled, SectionSchema schema, GuideMode mode) {
        return enabled ? SolverPromptTemplate.knowledgeBlock(schema, mode) : "";
    }

    /** AUTO 未固化 → 清单 + 工具指令；MANUAL / 已固化 → 规范已随 base 注入，这里返回空串。 */
    private static String subjectCatalogBlock(SubjectSkillRuntime runtime) {
        if (!runtime.needsTools()) {
            return "";
        }
        List<String[]> rows = new ArrayList<>();
        for (SubjectSkillResponse skill : runtime.catalog()) {
            String code = skill.code() == null ? "CUSTOM-" + skill.id() : skill.code();
            rows.add(new String[]{code, skill.name(),
                    skill.description() == null ? "" : skill.description(),
                    skill.keywords() == null ? "" : skill.keywords()});
        }
        return SolverPromptTemplate.subjectCatalogBlock(rows);
    }
}
