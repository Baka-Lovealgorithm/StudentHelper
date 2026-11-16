package com.ai.studenthelper.application;

import com.ai.studenthelper.common.SectionSchema;
import com.ai.studenthelper.domain.enums.AnswerIntent;
import com.ai.studenthelper.domain.enums.GuideMode;
import com.ai.studenthelper.domain.enums.S2Feature;

import java.util.List;

/**
 * 本轮回答形态的完整决策（CR-043 / 候选 01）。
 *
 * <p>改造前「这轮是解题 / 追问 / 讲解」这个判断被 9 个类各自重新推导：工具是否注册、
 * 结构怎么取、知识点要不要抽、落库写什么、裁剪点在哪、标签怎么写，散落成 6 处 {@code if}
 * 与 2 处 {@code switch}。现在全部收敛到本对象构造一次，下游只读字段。</p>
 *
 * <p><b>为什么 schema 不是普通字段</b>：AUTO 学科模式下，模型可能在本轮中途通过
 * {@code subject_get} 才读入自定义结构，生成前的 schema 必为默认六段。若把生成前的 schema
 * 快照固定进 plan，「仅思路」裁剪会按旧的 ④ 去找切断点，自定义结构下裁不掉（历史缺陷）。
 * 因此 {@link #currentSchema()} 每次都向 runtime 取当前结构：工具命中后的帧自然用新结构。</p>
 *
 * @param intent             本轮形态
 * @param mode               引导模式（完整 / 仅思路）
 * @param systemPrompt       已装配好的 system prompt（含各段增量）
 * @param needsSubjectTools  是否注册 {@code subject_get}
 * @param needsSkillTools    是否注册 {@code skill_list} / {@code skill_get}
 * @param maxIters           ReAct 迭代上限（按需工具各 +1）
 * @param autoTitle          首轮是否追加会话标题指令
 * @param runtime            学科运行时（取结构、标签、审计码；不可为 null）
 * @param features           本轮开关快照（CR-043；下游据此判断，避免重复查库）
 */
public record AnswerPlan(
        AnswerIntent intent,
        GuideMode mode,
        String systemPrompt,
        boolean needsSubjectTools,
        boolean needsSkillTools,
        int maxIters,
        boolean autoTitle,
        SubjectSkillRuntime runtime,
        FeatureSnapshot features
) {

    /** 追问轮：自由问答，无结构、不抽知识点、不注册工具。 */
    public boolean followUp() {
        return intent == AnswerIntent.FOLLOW_UP;
    }

    /**
     * CR-047：作图工具只在结构化轮（解题 / 讲解）注册——追问轮是自由问答，不画图。
     *
     * <p>是否真正注册还要看运行环境（后端能找到 Chrome / Edge），由
     * {@code ChatApplicationService} 结合 {@code DiagramRenderer.available()} 判定；
     * 这里只表达「本轮形态是否需要图」。</p>
     */
    public boolean needsDiagramTool() {
        return !followUp();
    }

    /**
     * 只有解题轮抽知识点（讲解与追问都不抽）。
     *
     * <p>CR-043：开关判断前移到本对象——改造前 prompt 侧与抽取侧各判一次
     * {@code knowledgeExtract}，中途翻转会出现「要求写但没人抽」。快照为 null 时按开启处理，
     * 保持单测直连与历史行为。</p>
     */
    public boolean extractKnowledge() {
        return intent == AnswerIntent.SOLVE
                && (features == null || features.enabled(S2Feature.KNOWLEDGE_EXTRACT));
    }

    /** 只有解题 + 仅思路才强制裁剪；追问与讲解都不裁。 */
    public boolean trimOnIdeaOnly() {
        return intent == AnswerIntent.SOLVE && mode == GuideMode.IDEA_ONLY;
    }

    /**
     * 当前生效的结构：追问返回 {@code null}（自由文本无结构）；
     * 解题 / 讲解向 runtime 现取，AUTO 未命中时回落默认结构。
     */
    public SectionSchema currentSchema() {
        if (followUp()) {
            return null;
        }
        SectionSchema schema = runtime == null ? null : runtime.schemaFor(intent);
        return schema == null ? SectionSchema.defaultSchema() : schema;
    }

    /** 仅思路模式的切断点标题；非解题或非仅思路返回 {@code null}。 */
    public String cutTitle() {
        if (!trimOnIdeaOnly()) {
            return null;
        }
        SectionSchema schema = currentSchema();
        return schema == null ? null : schema.firstExcludedTitle();
    }

    /** 切段标题：讲解用 explain 数组，解题按引导模式取 full / idea；追问无标题。 */
    public List<String> parseTitles(SectionSchema finalSchema) {
        if (followUp()) {
            return List.of();
        }
        SectionSchema schema = finalSchema == null ? currentSchema() : finalSchema;
        if (schema == null) {
            return List.of();
        }
        return intent == AnswerIntent.EXPLAIN ? schema.explainTitles() : schema.titlesFor(mode);
    }

    /**
     * 落库结构：追问写 {@code FREE_TEXT}；讲解默认结构写哨兵 {@code EXPLAIN}；
     * 解题沿用「仅非默认学科结构才写 JSON」的口径。
     */
    public String storedSchema(SectionSchema finalSchema) {
        if (followUp()) {
            return SectionSchema.FREE_TEXT;
        }
        SectionSchema schema = finalSchema == null ? currentSchema() : finalSchema;
        if (intent == AnswerIntent.EXPLAIN) {
            return schema != null && schema.isDefaultExplain() ? SectionSchema.EXPLAIN : schema.toJson();
        }
        return runtime != null && runtime.resolved() != null && schema != null && !schema.isDefault()
                ? schema.toJson() : null;
    }

    /** 前端「本次采用」标签；追问不显示。 */
    public String subjectLabel(SectionSchema finalSchema) {
        if (followUp() || runtime == null) {
            return null;
        }
        return runtime.labelFor(intent, mode);
    }

    /**
     * 构造本轮的完整决策。
     *
     * @param subjectToolsAvailable 学科工具是否可用（subjectSkills 非空且 runtime 需要工具）
     * @param skillToolsAvailable   技能工具是否可用（技能开关开启且清单非空）
     */
    public static AnswerPlan build(AnswerIntent intent,
                                   GuideMode mode,
                                   String systemPrompt,
                                   boolean subjectToolsAvailable,
                                   boolean skillToolsAvailable,
                                   boolean autoTitle,
                                   SubjectSkillRuntime runtime) {
        return build(intent, mode, systemPrompt, subjectToolsAvailable, skillToolsAvailable,
                autoTitle, runtime, null);
    }

    /** CR-043：带开关快照的构造；旧签名委托本方法并传 null（下游回退逐次现读）。 */
    public static AnswerPlan build(AnswerIntent intent,
                                   GuideMode mode,
                                   String systemPrompt,
                                   boolean subjectToolsAvailable,
                                   boolean skillToolsAvailable,
                                   boolean autoTitle,
                                   SubjectSkillRuntime runtime,
                                   FeatureSnapshot features) {
        AnswerIntent effective = intent == null ? AnswerIntent.SOLVE : intent;
        GuideMode effectiveMode = mode == null ? GuideMode.FULL : mode;
        boolean structured = effective.structured();
        boolean subjectTools = structured && subjectToolsAvailable;
        boolean skillTools = structured && skillToolsAvailable;
        // CR-047：结构化解题 / 讲解轮另注册 diagram_render，工具调用后要多留一轮生成
        boolean diagramTool = structured;
        return new AnswerPlan(effective, effectiveMode, systemPrompt,
                subjectTools, skillTools,
                3 + (subjectTools ? 1 : 0) + (skillTools ? 1 : 0) + (diagramTool ? 1 : 0),
                autoTitle && structured, runtime, features);
    }
}
