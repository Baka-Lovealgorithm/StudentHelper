package com.ai.studenthelper.common.dto;

import java.util.List;

/** CR-012 后端代理获取端点模型列表的结果；失败时给中文 error，仍可手动添加。 */
public record FetchModelsResponse(List<String> models, String error) {
}
