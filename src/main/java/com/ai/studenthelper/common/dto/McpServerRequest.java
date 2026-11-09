package com.ai.studenthelper.common.dto;

/** T-301 新增 / 修改 MCP Server 的入参；`enabled` 省略表示不修改。 */
public record McpServerRequest(String name, String transport, String url, Boolean enabled) {
}
