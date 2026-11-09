package com.ai.studenthelper.infra.model;

import com.ai.studenthelper.common.ErrorCode;

import java.util.Locale;

/** 把模型调用异常归类为 E2001 / E2002 / E2003，且绝不外泄密钥与堆栈。 */
public final class AgentErrorClassifier {

    private AgentErrorClassifier() {
    }

    public static ErrorCode classify(Throwable throwable) {
        String text = describe(throwable);
        if (text.contains("timeout") || text.contains("timed out") || text.contains("timeoutexception")) {
            return ErrorCode.E2003;
        }
        if (text.contains("401") || text.contains("403") || text.contains("unauthorized")
                || text.contains("invalid api key") || text.contains("authentication")) {
            return ErrorCode.E2001;
        }
        return ErrorCode.E2002;
    }

    /** 面向前端的简短原因，只含异常类型与通用描述，不含 Key / 完整 Prompt。 */
    public static String safeDetail(Throwable throwable, String baseUrl) {
        String name = throwable == null ? "UnknownError" : throwable.getClass().getSimpleName();
        return "模型调用失败（" + name + "），端点：" + (baseUrl == null ? "未配置" : baseUrl);
    }

    private static String describe(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth++ < 8) {
            sb.append(current.getClass().getSimpleName()).append(' ')
                    .append(String.valueOf(current.getMessage())).append(' ');
            current = current.getCause();
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }
}
