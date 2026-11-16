package com.ai.studenthelper.common.dto;

import java.util.List;

/**
 * CR-020 厂商级「全部测试」汇总：覆盖该厂商模型列表下的全部模型。
 *
 * <p>{@code results} 顺序与模型列表一致，前端据此逐行对位；该厂商下无模型时
 * {@code total=0} 且 {@code message} 给出说明，不抛业务异常。</p>
 */
public record ProviderTestResponse(Long providerId, String providerName, int total, int success,
                                   int failed, long elapsedMs, String message,
                                   List<ModelTestResult> results) {
}
