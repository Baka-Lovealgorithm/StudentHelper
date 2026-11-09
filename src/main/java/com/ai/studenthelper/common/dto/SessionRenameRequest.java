package com.ai.studenthelper.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 重命名会话请求（标题 1~100 字符，后端会 trim）。 */
public record SessionRenameRequest(
        @NotBlank @Size(max = 100) String title
) {
}
