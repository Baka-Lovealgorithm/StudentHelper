package com.ai.studenthelper.common;

import com.ai.studenthelper.domain.enums.AnswerIntent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CR-039：学科规范三段（解题 / 追问 / 讲解）的 JSON 读写。
 *
 * <p>存储形如 {@code {"solve":"...","followUp":"...","explain":"..."}}。读路径容忍脏数据：
 * 非 JSON、缺键、非字符串一律按空处理，并回落到旧列 {@code spec_md} 作为 {@code solve}，
 * 保证 `upgrade-s11.sql` 未执行或迁移中断时仍能作答。写路径由 {@code SubjectSkillApplicationService}
 * 逐段校验长度后落库。</p>
 */
public final class SubjectSpecJson {

    public static final String SOLVE = "solve";
    public static final String FOLLOW_UP = "followUp";
    public static final String EXPLAIN = "explain";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SubjectSpecJson() {
    }

    /** 三段规范；空串表示「留空，回落系统默认」。 */
    public record Specs(String solve, String followUp, String explain) {

        public String of(AnswerIntent intent) {
            if (intent == null) {
                return solve;
            }
            return switch (intent) {
                case FOLLOW_UP -> followUp;
                case EXPLAIN -> explain;
                case SOLVE -> solve;
            };
        }

        public boolean allBlank() {
            return blank(solve) && blank(followUp) && blank(explain);
        }

        private static boolean blank(String value) {
            return value == null || value.isBlank();
        }
    }

    /** 读路径：容忍脏数据；{@code spec_json} 为空时用旧 {@code spec_md} 当 solve。 */
    public static Specs parse(String json, String legacySpecMd) {
        String legacy = legacySpecMd == null ? "" : legacySpecMd.trim();
        if (json == null || json.isBlank()) {
            return new Specs(legacy, "", "");
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node == null || !node.isObject()) {
                return new Specs(legacy, "", "");
            }
            String solve = text(node, SOLVE);
            if (solve.isEmpty()) {
                // 迁移中断 / 旧客户端只写 specMd 时兜底
                solve = legacy;
            }
            return new Specs(solve, text(node, FOLLOW_UP), text(node, EXPLAIN));
        } catch (Exception e) {
            return new Specs(legacy, "", "");
        }
    }

    /** 写路径：严格校验——必须是对象，三段都必须是字符串（缺键视为空）。 */
    public static Specs parseStrict(String json) {
        if (json == null || json.isBlank()) {
            return new Specs("", "", "");
        }
        JsonNode node;
        try {
            node = MAPPER.readTree(json);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.E1004, "specJson 必须是 JSON 对象");
        }
        if (node == null || !node.isObject()) {
            throw new BusinessException(ErrorCode.E1004, "specJson 必须是 JSON 对象");
        }
        for (String key : new String[]{SOLVE, FOLLOW_UP, EXPLAIN}) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull() && !value.isTextual()) {
                throw new BusinessException(ErrorCode.E1004, "specJson." + key + " 必须是字符串");
            }
        }
        return new Specs(text(node, SOLVE), text(node, FOLLOW_UP), text(node, EXPLAIN));
    }

    /** 序列化三段；空段写空串而非省略，便于前端直接回显。 */
    public static String toJson(Specs specs) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(SOLVE, specs == null || specs.solve() == null ? "" : specs.solve().trim());
        map.put(FOLLOW_UP, specs == null || specs.followUp() == null ? "" : specs.followUp().trim());
        map.put(EXPLAIN, specs == null || specs.explain() == null ? "" : specs.explain().trim());
        try {
            return MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalStateException("SUBJECT_SPEC_SERIALIZE_FAILED", e);
        }
    }

    private static String text(JsonNode node, String key) {
        JsonNode value = node.get(key);
        return value == null || !value.isTextual() ? "" : value.asText().trim();
    }
}
