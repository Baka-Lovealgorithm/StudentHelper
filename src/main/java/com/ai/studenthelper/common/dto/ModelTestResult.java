package com.ai.studenthelper.common.dto;

/**
 * CR-020 单个模型的连通性测试结果。
 *
 * <p>测试失败是**正常结果**而不是业务异常，因此以数据形式返回：前端逐行展示通过 / 失败原因。
 * {@code httpStatus} 在连接失败或超时时为 {@code null}；{@code latencyMs} 为本次请求的墙钟耗时。
 * 响应正文不回显，避免把模型输出或端点回显的敏感信息带进页面。</p>
 */
public record ModelTestResult(Long modelId, String modelName, boolean ok,
                              Integer httpStatus, long latencyMs, String message) {
}
