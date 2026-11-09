package com.ai.studenthelper.agent;

import com.ai.studenthelper.application.OptionalSectionService;
import com.ai.studenthelper.application.ProfileApplicationService;
import com.ai.studenthelper.common.dto.OptionalSectionsResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * S2 开启态的 System Prompt 增量（DD-05 ~ DD-07）。
 * 关闭态必须原样返回 base，保证 AC-204-1「与 S1 六段逐字一致」与 Gate-2 关闭态不退化。
 */
@Component
public class S2PromptAugmenter {

    private final ProfileApplicationService profileService;
    private final OptionalSectionService optionalSections;

    public S2PromptAugmenter(ProfileApplicationService profileService,
                             OptionalSectionService optionalSections) {
        this.profileService = profileService;
        this.optionalSections = optionalSections;
    }

    public String augment(String base) {
        List<String> memories = profileService.enabledMemorySnippets();
        OptionalSectionsResponse sections = optionalSections.current();
        if (memories.isEmpty() && !sections.any()) {
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
        return sb.toString();
    }
}
