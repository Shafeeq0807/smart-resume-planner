package com.smartresume.planner.resume.parse;

import com.smartresume.planner.resume.domain.ResumeDocument;
import java.util.List;

/** Result of segmenting raw text: the structured document plus which sections were recognised. */
public record ParsedResume(ResumeDocument document, List<String> sections, List<String> headerLines) {

    public boolean has(String section) {
        return sections.contains(section);
    }
}
