package com.ai.studenthelper.common.dto;

import java.util.List;

/**
 * CR-012 模型条目入参。
 * 单个添加 / 修改用 {@code modelName + visionSupported + streamSupported + extraOptions}；
 * 从端点批量勾选添加用 {@code modelNames}（此时视觉等属性取默认值，可在条目上再编辑）。
 */
public record ModelEntryRequest(
        String modelName,
        List<String> modelNames,
        Boolean visionSupported,
        Boolean streamSupported,
        String extraOptions
) {
}
