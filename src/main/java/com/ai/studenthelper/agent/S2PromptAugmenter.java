package com.ai.studenthelper.agent;

import com.ai.studenthelper.application.OptionalSectionService;
import com.ai.studenthelper.application.ProfileApplicationService;
import com.ai.studenthelper.application.S2FeatureService;
import com.ai.studenthelper.application.SkillApplicationService;
import com.ai.studenthelper.application.SubjectApplicationService;
import com.ai.studenthelper.common.dto.OptionalSectionsResponse;
import com.ai.studenthelper.domain.enums.GuideMode;
import com.ai.studenthelper.domain.enums.S2Feature;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 所有扩展对 System Prompt 的唯一增量入口（S2 DD-05~DD-07 / S3 DD-08~DD-10）。
 * 关闭态必须原样返回 base，保证 AC-204-1「与 S1 六段逐字一致」与门禁关闭态不退化。
 */
@Component
public class S2PromptAugmenter {

    private final ProfileApplicationService profileService;
    private final OptionalSectionService optionalSections;
    private final SubjectApplicationService subjects;
    private final SkillApplicationService skills;
    private final S2FeatureService features;

    public S2PromptAugmenter(ProfileApplicationService profileService,
                             OptionalSectionService optionalSections,
                             SubjectApplicationService subjects,
                             SkillApplicationService skills,
                             S2FeatureService features) {
        this.profileService = profileService;
        this.optionalSections = optionalSections;
        this.subjects = subjects;
        this.skills = skills;
        this.features = features;
    }

    /**
     * T-307：先按当前学科构建 base，再叠加各增量。
     * 学科开关关闭时 currentForPrompt() 返回默认学科且不含补充说明，base 与 S1 逐字一致。
     */
    public String buildAndAugment(GuideMode mode) {
        SubjectApplicationService.SubjectContext subject = subjects.currentForPrompt();
        return augment(SolverPromptTemplate.build(mode, subject.name(), subject.hint()));
    }

    public String augment(String base) {
        List<String> memories = profileService.enabledMemorySnippets();
        OptionalSectionsResponse sections = optionalSections.current();
        boolean multiSolution = features.isEnabled(S2Feature.MULTI_SOLUTION);
        boolean mcpEnabled = features.isEnabled(S2Feature.MCP);
        List<String[]> skillSnippets = skills.enabledSnippets();
        if (memories.isEmpty() && !sections.any() && !multiSolution
                && !mcpEnabled && skillSnippets.isEmpty()) {
            return base;
        }
        StringBuilder sb = new StringBuilder(base);
        if (!memories.isEmpty()) {
            sb.append(SolverPromptTemplate.memoryBlock(memories));
        }
        if (sections.any()) {
            sb.append(SolverPromptTemplate.optionalSectionBlock(
                    sections.idea(), sections.knowledge(), sections.code()));
        }
        if (multiSolution) {
            sb.append(SolverPromptTemplate.multiSolutionBlock());
        }
        if (!skillSnippets.isEmpty()) {
            sb.append(SolverPromptTemplate.skillBlock(skillSnippets));
        }
        if (mcpEnabled) {
            sb.append(SolverPromptTemplate.mcpBlock());
        }
        return sb.toString();
    }
}
