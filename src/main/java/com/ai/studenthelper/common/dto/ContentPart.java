package com.ai.studenthelper.common.dto;

/**
 * 本轮用户消息的有序内容段：文字与图片按用户输入顺序排列（CR-011）。
 *
 * <p>{@code type=text} 时 {@code value} 为文字内容；{@code type=image} 时 {@code index}
 * 指向 {@link ChatCommand#images()} 中的下标。前端用富文本输入框按 DOM 顺序生成该列表。
 */
public record ContentPart(String type, String value, Integer index) {

    public static ContentPart text(String value) {
        return new ContentPart("text", value == null ? "" : value, null);
    }

    public boolean isImage() {
        return "image".equals(type);
    }
}
