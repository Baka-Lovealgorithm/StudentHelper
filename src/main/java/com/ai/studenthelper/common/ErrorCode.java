package com.ai.studenthelper.common;

/**
 * 业务错误码（技术规范 3.3）。E1004 为 T-002 新增的参数校验码（TCR-002）；
 * E1005 为移除 S0 探针后新增的「资源不存在」码，避免 404 被兜成 E5000（TCR-012）。
 */
public enum ErrorCode {

    OK("OK", 200, "success"),
    E1001("E1001", 400, "请输入题目文字或上传图片"),
    E1002("E1002", 400, "仅支持 png / jpg / webp 图片，单张不超过 10 MB"),
    E1003("E1003", 404, "会话不存在"),
    E1004("E1004", 400, "请求参数不合法"),
    E1005("E1005", 404, "请求的资源不存在"),
    E2001("E2001", 502, "鉴权失败，请检查模型配置"),
    E2002("E2002", 502, "模型服务不可达"),
    E2003("E2003", 504, "生成超时，请稍后重试或调整模型"),
    E2004("E2004", 400, "当前模型不支持图片，请切换为支持视觉的模型或改用文字输入"),
    E2005("E2005", 502, "生成结果格式异常，请重试"),
    E3001("E3001", 409, "请先切换到其它模型配置再删除"),
    E3002("E3002", 409, "请先在设置中配置模型"),
    E3003("E3003", 404, "模型配置不存在"),
    E3004("E3004", 403, "该功能当前已关闭，请在「功能开关」中开启"),
    /** CR-053：用户主动停止生成；仅用于 SSE error 终态与 t_message.error_code，不抛 HTTP 异常。 */
    E1006("E1006", 499, "生成已由用户停止"),
    E5000("E5000", 500, "发生未知错误，请查看日志");

    private final String code;
    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(String code, int httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
