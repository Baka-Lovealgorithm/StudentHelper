package com.ai.studenthelper.common;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** CR-018 / AC-501-3：学科 SKILL.md 文件夹导入校验与 sections 扩展。 */
class SubjectSkillFolderParserTest {

    private static final String SKILL_MD = """
            ---
            name: 线性代数
            description: 矩阵与向量空间题目的规范
            keywords: 矩阵、秩、特征值
            sections: {"full":["## ① 题意","## ② 定理","## ③ 推导","## ④ 结论"],"idea":["## ① 题意","## ② 定理"]}
            ---
            【记号与术语】矩阵用大写字母，向量用小写粗体。
            【推理要求】每步注明所用定理。
            """;

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static SubjectSkillFolderParser.Parsed parse(String md) {
        return SubjectSkillFolderParser.parse(List.of(bytes(md)), List.of("linear/SKILL.md"));
    }

    @Test
    void parsesFrontmatterSpecAndDeclaredSections() {
        var parsed = parse(SKILL_MD);
        assertEquals("线性代数", parsed.name());
        assertEquals("矩阵与向量空间题目的规范", parsed.description());
        assertTrue(parsed.keywords().contains("秩"));
        assertTrue(parsed.specMd().contains("每步注明所用定理"));
        SectionSchema schema = SectionSchema.parse(parsed.sectionsJson());
        assertEquals(4, schema.full().size());
        assertEquals(2, schema.idea().size());
        assertEquals("linear", parsed.folderName());
    }

    @Test
    void missingSectionsFallsBackToDefaultSixSections() {
        String md = "---\nname: 离散数学\n---\n【推理要求】写出依据。\n";
        SectionSchema schema = SectionSchema.parse(parse(md).sectionsJson());
        assertTrue(schema.isDefault());
    }

    @Test
    void shorthandSectionsAreAccepted() {
        String md = "---\nname: 概率论\nsections_full: ① 题意|② 公式|③ 推导|④ 结论\nsections_idea: ① 题意|② 公式\n---\n正文。\n";
        SectionSchema schema = SectionSchema.parse(parse(md).sectionsJson());
        assertEquals(4, schema.full().size());
        assertEquals("## ④ 结论", schema.full().get(3));
        assertEquals(2, schema.idea().size());
    }

    @Test
    void invalidSectionsRejectedWithE1004() {
        String md = "---\nname: 坏结构\nsections: {oops\n---\n正文。\n";
        assertEquals(ErrorCode.E1004,
                assertThrows(BusinessException.class, () -> parse(md)).getErrorCode());
    }

    @Test
    void pathAndFolderChecksAreEnforced() {
        assertInvalid(List.of(bytes(SKILL_MD)), List.of("../linear/SKILL.md"));
        assertInvalid(List.of(bytes(SKILL_MD)), List.of("/tmp/linear/SKILL.md"));
        assertInvalid(List.of(bytes(SKILL_MD)), List.of("linear\\SKILL.md"));
        assertInvalid(List.of(bytes(SKILL_MD), bytes(SKILL_MD)),
                List.of("a/SKILL.md", "b/SKILL.md"));
        assertInvalid(List.of(bytes(SKILL_MD)), List.of("linear/a/SKILL.md"));
        assertInvalid(List.of(), List.of());
    }

    @Test
    void frontmatterAndBodyChecksAreEnforced() {
        assertInvalid(List.of(bytes("正文没有 frontmatter")), List.of("x/SKILL.md"));
        assertInvalid(List.of(bytes("---\nname: 断开\n正文")), List.of("x/SKILL.md"));
        assertInvalid(List.of(bytes("---\ndescription: 缺 name\n---\n正文")), List.of("x/SKILL.md"));
        assertInvalid(List.of(bytes("---\nname: 空正文\n---\n   ")), List.of("x/SKILL.md"));
    }

    @Test
    void fileCountAndSizeLimitsAreEnforced() {
        List<byte[]> many = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        for (int i = 0; i <= SubjectSkillFolderParser.MAX_FILES; i++) {
            many.add(bytes("x"));
            paths.add("folder/f" + i + ".txt");
        }
        assertInvalid(many, paths);
        assertInvalid(List.of(new byte[(int) SubjectSkillFolderParser.MAX_FILE_BYTES + 1]),
                List.of("folder/SKILL.md"));
    }

    private static void assertInvalid(List<byte[]> fileBytes, List<String> paths) {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> SubjectSkillFolderParser.parse(fileBytes, paths));
        assertEquals(ErrorCode.E1004, ex.getErrorCode());
    }
}
