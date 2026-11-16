package com.ai.studenthelper.common.dto;

/** CR-018：会话级三态选择；MANUAL 时 subjectSkillId 必填。 */
public record SessionSubjectRequest(String mode, Long subjectSkillId) {
}
