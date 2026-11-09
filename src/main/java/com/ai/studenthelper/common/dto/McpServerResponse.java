package com.ai.studenthelper.common.dto;

import java.time.LocalDateTime;

/** T-301 远程 MCP Server（DD-10：仅远程传输，不启用 stdio）。 */
public record McpServerResponse(Long id, String name, String transport, String url, boolean enabled,
                                String lastStatus, String lastError, int toolCount,
                                LocalDateTime updatedAt) {
}
