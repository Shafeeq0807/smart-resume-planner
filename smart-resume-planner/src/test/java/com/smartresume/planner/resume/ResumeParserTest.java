package com.smartresume.planner.resume;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartresume.planner.resume.parse.ParsedResume;
import com.smartresume.planner.resume.parse.ResumeParser;
import org.junit.jupiter.api.Test;

class ResumeParserTest {

    private static final String SAMPLE = """
            Priya Sharma
            priya@example.com | +91 98765 43210

            SUMMARY
            Backend engineer with six years of experience building REST APIs.

            EXPERIENCE
            Senior Engineer, Acme Technologies | Jan 2021 - Present
            - Reduced API latency by 38% with Redis caching
            - Led a team of 4 engineers

            EDUCATION
            B.Tech Computer Science, VIT | 2014 - 2018

            SKILLS
            Java, Spring Boot, MySQL, Docker
            """;

    @Test
    void detectsStandardSections() {
        ParsedResume parsed = new ResumeParser().parse(SAMPLE);
        assertThat(parsed.has(ResumeParser.EXPERIENCE)).isTrue();
        assertThat(parsed.has(ResumeParser.EDUCATION)).isTrue();
        assertThat(parsed.has(ResumeParser.SKILLS)).isTrue();
    }

    @Test
    void emptyTextDoesNotThrow() {
        ParsedResume parsed = new ResumeParser().parse("");
        assertThat(parsed.sections()).isEmpty();
    }
}
