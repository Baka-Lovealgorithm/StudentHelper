package com.ai.studenthelper.agent;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.SectionSchema;
import com.ai.studenthelper.domain.enums.GuideMode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 按固定标题切段并校验六段结构（AC-102-1、E2005）。 */
@Component
public class SolutionSectionParser {

    /** 由后端统一追加的 AI 生成提示行；模型偶发自己输出时要避免重复。 */
    public static final String DISCLAIMER = "本内容由 AI 生成，请自行核对";

    public record Section(String title, String body) {
    }

    public record ParseResult(List<Section> sections) {
    }

    /** 关闭态 / OFF / 历史 NULL 消息：默认六段，与 S1 逐字一致（AC-204-1）。 */
    public ParseResult parse(String text, GuideMode mode) {
        return parse(text, mode, SectionSchema.defaultSchema());
    }

    /** CR-018：按当前 sections_json 声明的有序标题切段与校验。 */
    public ParseResult parse(String text, GuideMode mode, SectionSchema schema) {
        List<String> titles = (schema == null ? SectionSchema.defaultSchema() : schema).titlesFor(mode);
        return parse(text, titles);
    }

    /** CR-039：直接按标题数组切段（讲解轮传 explain 数组，不区分引导模式）。 */
    public ParseResult parse(String text, List<String> titles) {
        if (text == null || text.isBlank()) {
            throw new BusinessException(ErrorCode.E2005);
        }
        // T-306 一题多解与历史 T-204 补充段落：「## 补充：」「## 附加：」之后的内容不参与 ①~⑥ 校验，
        // 避免 ⑥ 段混入扩展内容而误判；落库仍保留完整原文。
        // CR-024 退役可选输出段落时**刻意保留**该截断逻辑——历史回答里已有「## 补充：」标题，
        // 删掉会让旧消息被判为结构不完整（E2005）。
        int optionalAt = text.indexOf("\n## 补充：");
        int extraAt = text.indexOf("\n## 附加：");
        int cutAt;
        if (optionalAt < 0) {
            cutAt = extraAt;
        } else if (extraAt < 0) {
            cutAt = optionalAt;
        } else {
            cutAt = Math.min(optionalAt, extraAt);
        }
        String core = cutAt > 0 ? text.substring(0, cutAt) : text;
        List<Section> sections = new ArrayList<>();
        for (int i = 0; i < titles.size(); i++) {
            String title = titles.get(i);
            int start = core.indexOf(title);
            if (start < 0) {
                throw new BusinessException(ErrorCode.E2005, "缺少段落：" + title);
            }
            int bodyStart = start + title.length();
            int end = (i + 1 < titles.size()) ? core.indexOf(titles.get(i + 1), bodyStart) : core.length();
            if (end < bodyStart) {
                throw new BusinessException(ErrorCode.E2005, "段落顺序错误：" + title);
            }
            String body = core.substring(bodyStart, end).trim();
            if (body.isEmpty()) {
                throw new BusinessException(ErrorCode.E2005, "段落内容为空：" + title);
            }
            sections.add(new Section(title, body));
        }
        return new ParseResult(List.copyOf(sections));
    }

    /** 追加固定的 AI 生成提示行（AC-102-6），由后端保证，不依赖模型。 */
    public String appendDisclaimer(String text) {
        String trimmed = text == null ? "" : text.trim();
        // 开启态多轮时模型会模仿历史里的提示行自己输出一次，这里去重，避免落库出现两行
        String withoutModelLine = stripDisclaimer(trimmed);
        return withoutModelLine + "\n\n" + DISCLAIMER + "\n";
    }

    /** 去掉末尾已有的提示行（可能因模型模仿而重复多次）。 */
    public static String stripDisclaimer(String text) {
        if (text == null) {
            return "";
        }
        String result = text.trim();
        while (result.endsWith(DISCLAIMER)) {
            result = result.substring(0, result.length() - DISCLAIMER.length()).trim();
        }
        return result;
    }
}
