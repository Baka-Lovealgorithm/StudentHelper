package com.ai.studenthelper.common.dto;

/** 新建会话请求；title 为空时使用默认标题。 */
public record SessionCreateRequest(String title) {
}
