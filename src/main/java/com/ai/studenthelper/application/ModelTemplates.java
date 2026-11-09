package com.ai.studenthelper.application;

import com.ai.studenthelper.common.dto.ModelTemplateResponse;

import java.util.List;

/**
 * 预置供应商模板（AC-105-4）：只预填地址，不内置任何 Key、不校验厂商身份。
 */
public final class ModelTemplates {

    private ModelTemplates() {
    }

    public static List<ModelTemplateResponse> all() {
        return List.of(
                new ModelTemplateResponse("custom", "自定义 OpenAI 兼容", "", false,
                        "默认模板，适用于任何兼容端点；模型需自行支持图片输入"),
                new ModelTemplateResponse("deepseek", "DeepSeek", "https://api.deepseek.com", false,
                        "需在 extraOptions 中关闭 nativeStructuredOutput"),
                new ModelTemplateResponse("ollama", "本地 Ollama", "http://localhost:11434/v1", false,
                        "本机未安装 Ollama；可留空 API Key"),
                new ModelTemplateResponse("openai-compatible", "其它兼容端点", "", false,
                        "便于保存多个自配端点")
        );
    }
}
