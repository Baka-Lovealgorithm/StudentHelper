package com.ai.studenthelper.domain.enums;

/** 引导模式：0 完整解答 / 1 仅思路（FR-108）。 */
public enum GuideMode {

    FULL(0), IDEA_ONLY(1);

    private final int code;

    GuideMode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static GuideMode fromCode(int code) {
        for (GuideMode mode : values()) {
            if (mode.code == code) {
                return mode;
            }
        }
        throw new IllegalArgumentException("未知的引导模式：" + code);
    }
}
