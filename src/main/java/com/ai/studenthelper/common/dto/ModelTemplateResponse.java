package com.ai.studenthelper.common.dto;

/** 预置模板：只预填地址，不含 Key、不绑定厂商。 */
public record ModelTemplateResponse(
        String provider,
        String label,
        String baseUrl,
        Boolean visionSupported,
        String note
) {
}
