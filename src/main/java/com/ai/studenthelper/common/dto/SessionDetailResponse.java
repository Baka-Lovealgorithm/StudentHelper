package com.ai.studenthelper.common.dto;

import java.util.List;

/** 会话详情：会话本体 + 按时间正序的消息列表。 */
public record SessionDetailResponse(SessionResponse session, List<MessageResponse> messages) {
}
