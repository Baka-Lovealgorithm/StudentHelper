package com.ai.studenthelper.agent;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.domain.enums.GuideMode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 按固定标题切段并校验六段结构（AC-102-1、E2005）。 */
@Component
public class SolutionSectionParser {

    public record Section(String title, String body) {
    }

    public record ParseResult(List<Section> sections) {
    }

    public ParseResult parse(String text, GuideMode mode) {
        if (text == null || text.isBlank()) {
            throw new BusinessException(ErrorCode.E2005);
        }
        List<String> titles = SolverPromptTemplate.titlesFor(mode);
        List<Section> sections = new ArrayList<>();
        for (int i = 0; i < titles.size(); i++) {
            String title = titles.get(i);
            int start = text.indexOf(title);
            if (start < 0) {
                throw new BusinessException(ErrorCode.E2005, "缺少段落：" + title);
            }
            int bodyStart = start + title.length();
            int end = (i + 1 < titles.size()) ? text.indexOf(titles.get(i + 1), bodyStart) : text.length();
            if (end < bodyStart) {
                throw new BusinessException(ErrorCode.E2005, "段落顺序错误：" + title);
            }
            String body = text.substring(bodyStart, end).trim();
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
        return trimmed + "\n\n本内容由 AI 生成，请自行核对\n";
    }
}
