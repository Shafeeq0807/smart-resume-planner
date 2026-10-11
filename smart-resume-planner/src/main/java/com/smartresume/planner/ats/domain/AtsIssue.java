package com.smartresume.planner.ats.domain;

/** One finding from an AtsRule, stored inside ats_analyses.issues (JSON array). */
public record AtsIssue(String ruleId, String category, Severity severity, String message, String suggestion) {

    public enum Severity { INFO, WARNING, CRITICAL }
}
