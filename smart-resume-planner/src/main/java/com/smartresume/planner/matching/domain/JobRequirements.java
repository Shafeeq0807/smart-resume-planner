package com.smartresume.planner.matching.domain;

import java.util.List;

/**
 * LLM-extracted requirements of a job description (Spring AI structured output target),
 * cached in job_descriptions.extracted_skills so the JD is analysed only once.
 */
public record JobRequirements(
        List<Requirement> hardSkills,
        List<Requirement> softSkills,
        List<Requirement> tools,
        List<Requirement> certifications,
        String seniority
) {
    public record Requirement(String name, SkillImportance importance) { }
}
