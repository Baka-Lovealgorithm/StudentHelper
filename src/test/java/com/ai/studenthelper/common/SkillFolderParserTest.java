package com.ai.studenthelper.common;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** CR-011 技能文件夹导入的结构 / 路径 / frontmatter 校验。 */
class SkillFolderParserTest {

    private static final String SKILL_MD = """
            ---
            name: 复杂度标注
            description: 每个算法显式标注时间与空间复杂度
            ---
            每个算法都显式标注时间与空间复杂度，并说明最坏情况。
            """;

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void parsesFrontmatterAndBody() {
        var parsed = SkillFolderParser.parse(
                List.of(bytes(SKILL_MD), bytes("ignored")),
                List.of("my-skill/SKILL.md", "my-skill/notes/readme.md"));

        assertEquals("复杂度标注", parsed.name());
        assertEquals("每个算法显式标注时间与空间复杂度", parsed.description());
        assertTrue(parsed.content().contains("最坏情况"));
        assertEquals("my-skill", parsed.folderName());
    }

    @Test
    void acceptsBomAndWindowsLineEndingsAndQuotedValues() {
        var parsed = SkillFolderParser.parse(
                List.of(bytes("\uFEFF---\r\nname: \"引号技能\"\r\ndescription: '说明'\r\n---\r\n正文内容\r\n")),
                List.of("demo/SKILL.md"));
        assertEquals("引号技能", parsed.name());
        assertEquals("说明", parsed.description());
        assertEquals("正文内容", parsed.content());
    }

    @Test
    void rejectsMissingSkillMdInRoot() {
        assertRejected(List.of(bytes("x")), List.of("demo/notes.md"), "SKILL.md");
    }

    @Test
    void rejectsNestedOnlySkillMd() {
        assertRejected(List.of(bytes(SKILL_MD)), List.of("demo/sub/SKILL.md"), "SKILL.md");
    }

    @Test
    void rejectsDuplicateSkillMd() {
        assertRejected(List.of(bytes(SKILL_MD), bytes(SKILL_MD)),
                List.of("demo/SKILL.md", "demo/skill.md"), "多个 SKILL.md");
    }

    @Test
    void rejectsPathTraversalAndAbsoluteAndBackslash() {
        assertRejected(List.of(bytes(SKILL_MD)), List.of("../demo/SKILL.md"), "越界");
        assertRejected(List.of(bytes(SKILL_MD)), List.of("/demo/SKILL.md"), "绝对路径");
        assertRejected(List.of(bytes(SKILL_MD)), List.of("C:/demo/SKILL.md"), "绝对路径");
        assertRejected(List.of(bytes(SKILL_MD)), List.of("demo\\SKILL.md"), "反斜杠");
    }

    @Test
    void rejectsMultipleRootFolders() {
        assertRejected(List.of(bytes(SKILL_MD), bytes("x")),
                List.of("a/SKILL.md", "b/notes.md"), "一个 skill 文件夹");
    }

    @Test
    void rejectsBadFrontmatterAndEmptyBody() {
        assertRejected(List.of(bytes("没有 frontmatter\n正文")), List.of("demo/SKILL.md"), "frontmatter");
        assertRejected(List.of(bytes("---\nname: 甲\n正文没有闭合")), List.of("demo/SKILL.md"), "闭合");
        assertRejected(List.of(bytes("---\ndescription: 只有说明\n---\n正文")),
                List.of("demo/SKILL.md"), "name");
        assertRejected(List.of(bytes("---\nname: 甲\n---\n   \n")),
                List.of("demo/SKILL.md"), "正文不能为空");
    }

    @Test
    void rejectsOversizedAndTooManyFiles() {
        assertRejected(List.of(new byte[(int) SkillFolderParser.MAX_FILE_BYTES + 1], bytes(SKILL_MD)),
                List.of("demo/big.bin", "demo/SKILL.md"), "1 MB");

        List<byte[]> many = new ArrayList<>();
        List<String> manyPaths = new ArrayList<>();
        many.add(bytes(SKILL_MD));
        manyPaths.add("demo/SKILL.md");
        for (int i = 0; i < SkillFolderParser.MAX_FILES; i++) {
            many.add(bytes("x"));
            manyPaths.add("demo/extra-" + i + ".txt");
        }
        assertRejected(many, manyPaths, "最多导入");
    }

    @Test
    void rejectsMismatchedFileAndPathCounts() {
        assertRejected(List.of(bytes(SKILL_MD)), List.of(), "数量不一致");
    }

    private static void assertRejected(List<byte[]> fileBytes, List<String> paths, String messagePart) {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> SkillFolderParser.parse(fileBytes, paths));
        assertEquals(ErrorCode.E1004, ex.getErrorCode());
        assertTrue(String.valueOf(ex.getDetail()).contains(messagePart),
                "错误提示应包含「" + messagePart + "」，实际：" + ex.getDetail());
    }
}
