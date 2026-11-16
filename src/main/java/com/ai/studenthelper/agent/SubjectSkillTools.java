package com.ai.studenthelper.agent;

import com.ai.studenthelper.application.SubjectSkillApplicationService;
import com.ai.studenthelper.application.SubjectSkillRuntime;
import com.ai.studenthelper.common.SubjectSpecJson;
import com.ai.studenthelper.common.dto.SubjectSkillResponse;
import com.ai.studenthelper.domain.enums.AnswerIntent;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.util.List;

/**
 * CR-018：AUTO 模式下供模型按需读入学科规范的工具。
 *
 * <p>刻意不做成 Spring Bean：每个请求 new 一个实例并绑定本请求的
 * {@link SubjectSkillRuntime}，避免并发会话之间串台。{@code subject_get} 在模型
 * 调用时立即把命中条目与输出结构写回 runtime，并把 {@code subject_skill_id}
 * 固化到 {@code t_session}（发生在流式生成之前），使解析器与 t_message 可取到 schema。</p>
 */
public class SubjectSkillTools {

    private final SubjectSkillApplicationService service;
    private final SubjectSkillRuntime runtime;
    private final AnswerIntent intent;

    public SubjectSkillTools(SubjectSkillApplicationService service, SubjectSkillRuntime runtime,
                            AnswerIntent intent) {
        this.service = service;
        this.runtime = runtime;
        this.intent = intent == null ? AnswerIntent.SOLVE : intent;
    }

    /** 兼容 CR-018 的两参构造：等价于解题形态。 */
    public SubjectSkillTools(SubjectSkillApplicationService service, SubjectSkillRuntime runtime) {
        this(service, runtime, AnswerIntent.SOLVE);
    }

    @Tool(name = "subject_list",
            description = "列出当前可用的学科回答规范清单（code / 名称 / 适用描述 / 关键词）。"
                    + "在不确定本题是否需要学科规范时先调用本工具查看。",
            readOnly = true,
            concurrencySafe = true)
    public String subjectList() {
        List<SubjectSkillResponse> catalog = runtime.catalog();
        if (catalog.isEmpty()) {
            return "当前没有启用的学科规范，请按系统给定的默认六段结构作答。";
        }
        StringBuilder sb = new StringBuilder("可用学科规范（一次最多选 1 条主规范）：\n");
        for (SubjectSkillResponse skill : catalog) {
            String code = skill.code() == null ? "CUSTOM-" + skill.id() : skill.code();
            sb.append("- code=").append(code)
                    .append(" | 名称=").append(skill.name())
                    .append(" | 说明=").append(skill.description() == null ? "" : skill.description())
                    .append(" | 关键词=").append(skill.keywords() == null ? "" : skill.keywords())
                    .append('\n');
        }
        sb.append("用 subject_get(code) 读入所选规范全文与输出结构。");
        return sb.toString();
    }

    @Tool(name = "subject_get",
            description = "按 code 读入一条学科回答规范，返回本轮形态对应的规范段与 sections_json 输出结构。"
                    + "读入后必须严格按该结构作答；本题不属于任何学科时不要调用本工具。",
            readOnly = false,
            concurrencySafe = false)
    public String subjectGet(
            @ToolParam(name = "code", required = true,
                    description = "学科规范 code，例如 DATA_STRUCTURE 或 ADVANCED_MATH") String code) {
        if (code == null || code.isBlank()) {
            return "code 不能为空。";
        }
        String normalized = code.trim();
        SubjectSkillResponse skill = service.findEnabledByCode(normalized).orElse(null);
        if (skill == null) {
            // 自定义学科无 code，允许用 CUSTOM-<id> 读取
            if (normalized.startsWith("CUSTOM-")) {
                try {
                    Long id = Long.valueOf(normalized.substring(7));
                    SubjectSkillResponse custom = service.findById(id);
                    if (custom != null && custom.enabled()) {
                        skill = custom;
                    }
                } catch (NumberFormatException ignored) {
                    // 落到统一提示
                }
            }
        }
        if (skill == null) {
            return "未找到或未启用该学科规范（" + normalized + "），请按系统给定的默认六段结构作答。";
        }
        // 工具调用发生在模型开始流式生成正文之前；此处完成 schema 固化
        runtime.markResolved(skill, "模型按需读入 code=" + normalized);
        service.bindToSession(runtime.sessionId(), skill.id());

        // CR-039：只返回本轮形态对应的那一段规范，避免把三段全塞进工具返回
        String spec = SubjectSpecJson.parse(skill.specJson(), skill.specMd()).of(intent);
        boolean custom = spec != null && !spec.isBlank();
        String body = custom
                ? SubjectSkillApplicationService.trimSpec(spec)
                : "（该学科未单独配置「" + intent.label() + "」规范，沿用系统默认" + intent.label() + "规范）";
        String structure = switch (intent) {
            case FOLLOW_UP -> "本轮为自由问答，不套用任何分段结构，sections_json 不生效";
            case EXPLAIN -> "请按 sections_json 中 explain 数组声明的讲解标题逐字、按序输出"
                    + "（缺省时为系统默认五段讲解结构）";
            case SOLVE -> "请按 sections_json 中 full（完整解答）/ idea（仅思路）数组声明的标题逐字、按序输出，"
                    + "该结构覆盖系统 Prompt 里的默认六段";
        };
        return "【学科规范：" + skill.name() + " · " + intent.label() + "】\n" + body
                + "\n\n【本题输出结构 sections_json】" + skill.sectionsJson()
                + "\n" + structure + "；不要输出 ⑦ 及相似练习题，不要用 JSON 包裹答案。";
    }
}
