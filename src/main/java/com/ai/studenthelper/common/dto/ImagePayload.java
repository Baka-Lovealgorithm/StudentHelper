package com.ai.studenthelper.common.dto;

/** 已校验的图片载荷；bytes 只用于构造 data URL 与落盘。 */
public record ImagePayload(String originalName, String mimeType, String extension, byte[] bytes) {
}
