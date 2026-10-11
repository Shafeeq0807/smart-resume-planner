package com.smartresume.planner.resume.parse;

import com.smartresume.planner.resume.domain.ResumeDocument;
import com.smartresume.planner.resume.domain.ResumeDocument.Contact;
import com.smartresume.planner.resume.domain.ResumeDocument.Education;
import com.smartresume.planner.resume.domain.ResumeDocument.Experience;
import com.smartresume.planner.resume.domain.ResumeDocument.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Turns a structured document back into plain text with the same headings {@link ResumeParser}
 * recognises, so edited and AI-rewritten versions can be re-analysed and diffed like uploads.
 */
public final class ResumeTextRenderer {

    private ResumeTextRenderer() {
    }

    public static String toPlainText(ResumeDocument doc) {
        StringBuilder sb = new StringBuilder();
        Contact c = doc.contact();
        if (c != null) {
            line(sb, c.fullName());
            line(sb, c.headline());
            List<String> contactBits = new ArrayList<>();
            addIfPresent(contactBits, c.email());
            addIfPresent(contactBits, c.phone());
            addIfPresent(contactBits, c.location());
            if (c.links() != null) {
                c.links().forEach(l -> addIfPresent(contactBits, l));
            }
            line(sb, String.join(" | ", contactBits));
        }
        if (notBlank(doc.summary())) {
            heading(sb, "SUMMARY");
            line(sb, doc.summary());
        }
        if (doc.experience() != null && !doc.experience().isEmpty()) {
            heading(sb, "EXPERIENCE");
            for (Experience e : doc.experience()) {
                line(sb, join(" | ", e.title(), e.company(), e.location()));
                line(sb, dates(e.startDate(), e.endDate()));
                bullets(sb, e.bullets());
                sb.append('\n');
            }
        }
        if (doc.projects() != null && !doc.projects().isEmpty()) {
            heading(sb, "PROJECTS");
            for (Project p : doc.projects()) {
                line(sb, join(" | ", p.name(), p.url()));
                line(sb, p.description());
                bullets(sb, p.bullets());
                if (p.technologies() != null && !p.technologies().isEmpty()) {
                    line(sb, "Technologies: " + String.join(", ", p.technologies()));
                }
                sb.append('\n');
            }
        }
        if (doc.education() != null && !doc.education().isEmpty()) {
            heading(sb, "EDUCATION");
            for (Education e : doc.education()) {
                line(sb, join(" | ", e.degree(), e.institution(), dates(e.startDate(), e.endDate())));
                line(sb, e.details());
            }
        }
        if (doc.skills() != null && !doc.skills().isEmpty()) {
            heading(sb, "SKILLS");
            line(sb, String.join(", ", doc.skills()));
        }
        if (doc.certifications() != null && !doc.certifications().isEmpty()) {
            heading(sb, "CERTIFICATIONS");
            bullets(sb, doc.certifications());
        }
        return sb.toString().replaceAll("\n{3,}", "\n\n").trim();
    }

    private static void heading(StringBuilder sb, String name) {
        if (!sb.isEmpty()) {
            sb.append('\n');
        }
        sb.append(name).append('\n');
    }

    private static void line(StringBuilder sb, String text) {
        if (notBlank(text)) {
            sb.append(text.strip()).append('\n');
        }
    }

    private static void bullets(StringBuilder sb, List<String> bullets) {
        if (bullets != null) {
            bullets.stream().filter(ResumeTextRenderer::notBlank).forEach(b -> sb.append("• ").append(b.strip()).append('\n'));
        }
    }

    private static String dates(String start, String end) {
        if (!notBlank(start) && !notBlank(end)) {
            return "";
        }
        return notBlank(start) && notBlank(end) ? start + " - " + end : (notBlank(start) ? start : end);
    }

    private static String join(String sep, String... parts) {
        List<String> p = new ArrayList<>();
        for (String s : parts) {
            if (notBlank(s)) {
                p.add(s.strip());
            }
        }
        return p.stream().collect(Collectors.joining(sep));
    }

    private static void addIfPresent(List<String> list, String s) {
        if (notBlank(s)) {
            list.add(s.strip());
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
