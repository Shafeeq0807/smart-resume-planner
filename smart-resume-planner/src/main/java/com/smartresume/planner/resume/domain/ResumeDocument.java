package com.smartresume.planner.resume.domain;

import java.util.List;

/**
 * Structured snapshot of a resume version, stored in resume_versions.structured_content (JSON).
 * Produced by the section segmenter after Tika extraction; consumed by the AI optimizer,
 * the version diff and the PDF exporter (which renders from this, never from raw text).
 */
public record ResumeDocument(
        Contact contact,
        String summary,
        List<Experience> experience,
        List<Education> education,
        List<String> skills,
        List<Project> projects,
        List<String> certifications
) {
    public record Contact(String fullName, String headline, String email, String phone,
                          String location, List<String> links) { }

    public record Experience(String title, String company, String location,
                             String startDate, String endDate, List<String> bullets) { }

    public record Education(String degree, String institution, String startDate,
                            String endDate, String details) { }

    public record Project(String name, String url, String description,
                          List<String> bullets, List<String> technologies) { }
}
