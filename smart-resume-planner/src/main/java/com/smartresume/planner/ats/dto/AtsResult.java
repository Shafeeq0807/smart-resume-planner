package com.smartresume.planner.ats.dto;

import com.smartresume.planner.ats.domain.AtsAnalysis;
import com.smartresume.planner.ats.domain.AtsIssue;
import java.time.Instant;
import java.util.List;

public record AtsResult(
        Long id,
        Long resumeVersionId,
        int overallScore,
        String grade,
        List<Category> categories,
        String detectedTitle,
        List<String> detectedSections,
        List<AtsIssue> issues,
        String aiSummary,
        String analyzerVersion,
        Instant createdAt
) {

    public record Category(String name, int score, int max) { }

    public static AtsResult from(AtsAnalysis a) {
        return new AtsResult(
                a.getId(),
                a.getResumeVersion().getId(),
                a.getOverallScore(),
                grade(a.getOverallScore()),
                List.of(
                        new Category("PARSEABILITY", a.getParseabilityScore(), 20),
                        new Category("STRUCTURE", a.getStructureScore(), 25),
                        new Category("FORMATTING", a.getFormattingScore(), 20),
                        new Category("CONTACT_AND_TITLE", a.getTitleContactScore(), 15),
                        new Category("CONTENT", a.getContentScore(), 20)),
                a.getDetectedTitle(),
                a.getDetectedSections() == null ? List.of() : a.getDetectedSections(),
                a.getIssues() == null ? List.of() : a.getIssues(),
                a.getAiSummary(),
                a.getAnalyzerVersion(),
                a.getCreatedAt());
    }

    static String grade(int score) {
        if (score >= 85) {
            return "EXCELLENT";
        }
        if (score >= 70) {
            return "GOOD";
        }
        if (score >= 50) {
            return "NEEDS_WORK";
        }
        return "POOR";
    }
}
