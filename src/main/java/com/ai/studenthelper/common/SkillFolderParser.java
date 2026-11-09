package com.ai.studenthelper.common;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CR-011：从浏览器选中的 skill 文件夹里解析并校验 SKILL.md。
 *
 * <p>输入是「文件字节列表 + 与之一一对应的 webkitRelativePath 列表」。后端只读字节、
 * 不落盘、不执行目录内任何其它文件；所有拒绝都走 BusinessException(E1004) 中文提示。
 */
public final class SkillFolderParser {

    public static final int MAX_FILES = 32;
    public static final long MAX_FILE_BYTES = 1024L * 1024L;
    public static final long MAX_TOTAL_BYTES = 2L * 1024L * 1024L;

    private SkillFolderParser() {
    }

    public record Parsed(String name, String description, String content, String folderName) {
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
                throw invalid("一次只能导入一个 skill 文件夹");
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

    /** frontmatter 只支持 key: value 单行，足够覆盖 Skill 规范且避免过度解析 YAML。 */
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
            throw invalid("SKILL.md 的正文不能为空");
        }
        return new Parsed(name, emptyToNull(frontmatter.get("description")), body, folderName);
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
        return value == null || value.isBlank() ? null : value;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.E1004, "技能导入失败：" + message);
    }
}
