package com.ai.studenthelper.common;

import com.ai.studenthelper.common.dto.SectionSchemaDto;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CR-018：学科规范的 SKILL.md 文件夹导入解析器。
 *
 * <p>刻意与 {@link SkillFolderParser} 各自独立（通用技能解析器一字不改），但沿用同一套
 * 安全校验：文件数 / 单文件与总大小、路径越界与绝对路径、根目录唯一、根目录唯一
 * SKILL.md、frontmatter 闭合、name 必填、正文非空。</p>
 *
 * <p>frontmatter 在 name / description / keywords 之外扩展 {@code sections}：
 * 值为单行 JSON {@code {"full":["## ① …"],"idea":["## ① …"]}}；
 * 也兼容 {@code sections_full} / {@code sections_idea} 用 {@code |} 分隔的简写。
 * 未提供时回退默认六段结构。正文即 {@code spec_md}。</p>
 */
public final class SubjectSkillFolderParser {

    public static final int MAX_FILES = 32;
    public static final long MAX_FILE_BYTES = 1024L * 1024L;
    public static final long MAX_TOTAL_BYTES = 2L * 1024L * 1024L;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SubjectSkillFolderParser() {
    }

    /** CR-039：新增 specJson（三段规范）；旧 6 参构造仍可用，等价于只填解题规范。 */
    public record Parsed(String name, String description, String keywords,
                         String specMd, String sectionsJson, String folderName, String specJson) {
        public Parsed(String name, String description, String keywords,
                      String specMd, String sectionsJson, String folderName) {
            this(name, description, keywords, specMd, sectionsJson, folderName, null);
        }
    }

    public static Parsed parse(List<byte[]> fileBytes, List<String> paths) {
        if (fileBytes == null || paths == null || fileBytes.isEmpty()
                || fileBytes.size() != paths.size()) {
            throw invalid("文件与相对路径数量不一致");
        }
        if (fileBytes.size() > MAX_FILES) {
            throw invalid("一次最多导入 " + MAX_FILES + " 个文件");
        }
        long total = 0L;
        for (byte[] bytes : fileBytes) {
            int size = bytes == null ? 0 : bytes.length;
            if (size > MAX_FILE_BYTES) {
                throw invalid("单文件不能超过 1 MB");
            }
            total += size;
        }
        if (total > MAX_TOTAL_BYTES) {
            throw invalid("文件夹总大小不能超过 2 MB");
        }

        String root = null;
        List<String> normalized = new ArrayList<>();
        for (String raw : paths) {
            String path = validatePath(raw);
            String[] parts = path.split("/");
            if (parts.length < 2 || parts[0].isBlank()) {
                throw invalid("文件必须位于所选文件夹内：" + path);
            }
            if (root == null) {
                root = parts[0];
            } else if (!root.equals(parts[0])) {
                throw invalid("一次只能导入一个学科规范文件夹");
            }
            normalized.add(path);
        }

        int skillIndex = -1;
        for (int i = 0; i < normalized.size(); i++) {
            String[] parts = normalized.get(i).split("/");
            if (parts.length == 2 && "SKILL.md".equalsIgnoreCase(parts[1])) {
                if (skillIndex >= 0) {
                    throw invalid("文件夹根目录存在多个 SKILL.md");
                }
                skillIndex = i;
            }
        }
        if (skillIndex < 0) {
            throw invalid("文件夹根目录缺少 SKILL.md");
        }
        byte[] bytes = fileBytes.get(skillIndex);
        if (bytes == null || bytes.length == 0) {
            throw invalid("SKILL.md 内容为空");
        }
        return parseSkillMd(new String(bytes, StandardCharsets.UTF_8), root);
    }

    static Parsed parseSkillMd(String raw, String folderName) {
        String text = raw == null ? "" : raw;
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        if (lines.length == 0 || !"---".equals(lines[0].trim())) {
            throw invalid("SKILL.md 必须以 --- frontmatter 开始");
        }
        int close = -1;
        for (int i = 1; i < lines.length; i++) {
            if ("---".equals(lines[i].trim())) {
                close = i;
                break;
            }
        }
        if (close < 0) {
            throw invalid("SKILL.md 的 frontmatter 缺少闭合 ---");
        }
        Map<String, String> frontmatter = new LinkedHashMap<>();
        for (int i = 1; i < close; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0) {
                String key = lines[i].substring(0, colon).trim().toLowerCase();
                String value = unquote(lines[i].substring(colon + 1).trim());
                frontmatter.put(key, value);
            }
        }
        String name = frontmatter.get("name");
        if (name == null || name.isBlank()) {
            throw invalid("SKILL.md 的 frontmatter 缺少 name");
        }
        String body = String.join("\n", Arrays.copyOfRange(lines, close + 1, lines.length)).trim();
        if (body.isEmpty()) {
            throw invalid("SKILL.md 的正文（回答规范）不能为空");
        }
        return new Parsed(name, emptyToNull(frontmatter.get("description")),
                emptyToNull(frontmatter.get("keywords")), body,
                sections(frontmatter), folderName, specJson(frontmatter, body));
    }

    /** CR-039：frontmatter 可用 spec_json（三段单行 JSON）；缺省时正文整体作为解题规范。 */
    private static String specJson(Map<String, String> frontmatter, String body) {
        String json = frontmatter.get("spec_json");
        if (json != null && !json.isBlank()) {
            SubjectSpecJson.Specs specs;
            try {
                specs = SubjectSpecJson.parseStrict(json);
            } catch (BusinessException e) {
                throw invalid("spec_json 必须是含 solve / followUp / explain 的单行 JSON");
            }
            String solve = specs.solve().isBlank() ? body : specs.solve();
            return SubjectSpecJson.toJson(new SubjectSpecJson.Specs(solve, specs.followUp(), specs.explain()));
        }
        return SubjectSpecJson.toJson(new SubjectSpecJson.Specs(body, "", ""));
    }

    private static String sections(Map<String, String> frontmatter) {
        String json = frontmatter.get("sections");
        String fullRaw = frontmatter.get("sections_full");
        String ideaRaw = frontmatter.get("sections_idea");
        String explainRaw = frontmatter.get("sections_explain");
        List<String> full;
        List<String> idea;
        List<String> explain;
        if (json != null && !json.isBlank()) {
            try {
                SectionSchemaDto dto = MAPPER.readValue(json, SectionSchemaDto.class);
                full = dto == null ? null : dto.full();
                idea = dto == null ? null : dto.idea();
                explain = dto == null ? null : dto.explain();
            } catch (Exception e) {
                throw invalid("sections 必须是含 full / idea 数组的单行 JSON");
            }
        } else if (fullRaw != null || ideaRaw != null) {
            full = splitTitles(fullRaw);
            idea = splitTitles(ideaRaw);
            explain = explainRaw == null ? null : splitTitles(explainRaw);
        } else {
            full = SectionSchema.DEFAULT_FULL;
            idea = SectionSchema.DEFAULT_IDEA;
            explain = explainRaw == null ? null : splitTitles(explainRaw);
        }
        String candidate;
        try {
            candidate = MAPPER.writeValueAsString(new SectionSchemaDto(full, idea, explain));
        } catch (Exception e) {
            throw invalid("输出结构序列化失败");
        }
        // 复用统一的标题校验（含 "## " 前缀、非空、去重）
        SectionSchema.parse(candidate);
        return candidate;
    }

    private static List<String> splitTitles(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : raw.split("\\|")) {
            String title = part.trim();
            if (!title.isEmpty()) {
                out.add(title.startsWith("## ") ? title : "## " + title);
            }
        }
        return out;
    }

    private static String validatePath(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid("文件相对路径为空");
        }
        String path = raw.trim();
        if (path.contains("\\")) {
            throw invalid("文件相对路径不能包含反斜杠");
        }
        if (path.startsWith("/") || path.matches("^[A-Za-z]:.*")) {
            throw invalid("文件相对路径不能是绝对路径");
        }
        for (String segment : path.split("/")) {
            if ("..".equals(segment)) {
                throw invalid("文件相对路径越界：" + path);
            }
        }
        return path;
    }

    private static String unquote(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.E1004, "学科规范导入失败：" + message);
    }
}
