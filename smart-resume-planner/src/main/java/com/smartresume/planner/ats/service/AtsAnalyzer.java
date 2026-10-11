package com.smartresume.planner.ats.service;

import com.smartresume.planner.ats.domain.AtsAnalysis;
import com.smartresume.planner.ats.domain.AtsIssue;
import com.smartresume.planner.ats.domain.AtsIssue.Severity;
import com.smartresume.planner.resume.domain.ResumeDocument;
import com.smartresume.planner.resume.domain.ResumeDocument.Experience;
import com.smartresume.planner.resume.domain.ResumeDocument.Project;
import com.smartresume.planner.resume.domain.ResumeVersion;
import com.smartresume.planner.resume.parse.Bullets;
import com.smartresume.planner.resume.parse.ParsedResume;
import com.smartresume.planner.resume.parse.ResumeParser;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Rule-based, deterministic ATS readability scorer (no AI, so it is fast, free and reproducible).
 * Five categories add up to 100: parseability 20, structure 25, formatting 20, contact/title 15, content 20.
 */
@Component
public class AtsAnalyzer {

    public static final String VERSION = "1.0";

    private static final Pattern BULLET_GLYPH = Pattern.compile("(?m)^\\s*([•●▪■◦○·►➢✓✔*\\-–—])\\s");
    private static final Pattern DATE_PRESENT = Pattern.compile("(?i)\\b(19|20)\\d{2}\\b|present|current");

    public AtsAnalysis analyze(ResumeVersion version, ParsedResume parsed) {
        String text = version.getExtractedText() == null ? "" : version.getExtractedText();
        ResumeDocument doc = parsed.document();
        List<AtsIssue> issues = new ArrayList<>();
        AtsAnalysis a = new AtsAnalysis(version, VERSION);

        a.setParseabilityScore(parseability(text, issues));
        a.setStructureScore(structure(parsed, issues));
        a.setFormattingScore(formatting(text, version.getPageCount(), doc, issues));
        String title = detectTitle(doc);
        a.setTitleContactScore(titleAndContact(doc, title, issues));
        a.setContentScore(content(doc, text, issues));
        a.recomputeOverall();
        a.setDetectedTitle(title);
        a.setDetectedSections(parsed.sections());
        issues.sort((x, y) -> y.severity().compareTo(x.severity()));
        a.setIssues(issues);
        return a;
    }

    // ------------------------------------------------------------------ parseability (20)

    private int parseability(String text, List<AtsIssue> issues) {
        int score = 0;
        int words = Bullets.words(text);
        if (words >= 250) {
            score += 8;
        } else if (words >= 150) {
            score += 5;
            issues.add(issue("PARSE_SHORT", "PARSEABILITY", Severity.WARNING,
                    "Only " + words + " words were readable.",
                    "Add more detail about your experience. Aim for 300-800 words."));
        } else if (words >= 80) {
            score += 3;
            issues.add(issue("PARSE_VERY_SHORT", "PARSEABILITY", Severity.CRITICAL,
                    "Very little text could be read (" + words + " words).",
                    "Check that the file is text-based, not a scanned image, and add more content."));
        } else {
            issues.add(issue("PARSE_NO_TEXT", "PARSEABILITY", Severity.CRITICAL,
                    "Almost no text could be extracted.",
                    "Export a text-based PDF or DOCX. ATS software cannot read images."));
        }

        long total = text.length();
        long clean = text.chars().filter(c -> Character.isLetterOrDigit(c) || Character.isWhitespace(c)
                || ".,;:()-–—/&+#@%'\"•|$_[]!?".indexOf(c) >= 0).count();
        double cleanRatio = total == 0 ? 0 : (double) clean / total;
        if (cleanRatio >= 0.97) {
            score += 6;
        } else if (cleanRatio >= 0.93) {
            score += 3;
            issues.add(issue("PARSE_SYMBOLS", "PARSEABILITY", Severity.WARNING,
                    "Some unusual symbols or icons were found in the text.",
                    "Replace icons and decorative characters with plain text."));
        } else {
            issues.add(issue("PARSE_GARBLED", "PARSEABILITY", Severity.CRITICAL,
                    "A lot of the text contains unreadable symbols.",
                    "Avoid custom fonts and icon fonts. Re-export using standard fonts."));
        }

        List<String> lines = text.lines().filter(l -> !l.isBlank()).toList();
        double avgWordsPerLine = lines.isEmpty() ? 0 : (double) words / lines.size();
        if (avgWordsPerLine >= 3 && avgWordsPerLine <= 25) {
            score += 6;
        } else {
            score += 2;
            issues.add(issue("PARSE_LINES", "PARSEABILITY", Severity.WARNING,
                    "Line structure looks unusual (average " + Math.round(avgWordsPerLine) + " words per line).",
                    "Multi-column layouts and text boxes can scramble reading order. Use a single column."));
        }
        return score;
    }

    // ------------------------------------------------------------------ structure (25)

    private int structure(ParsedResume parsed, List<AtsIssue> issues) {
        int score = 0;
        if (parsed.has(ResumeParser.EXPERIENCE)) {
            score += 8;
        } else {
            issues.add(issue("SECTION_EXPERIENCE", "STRUCTURE", Severity.CRITICAL,
                    "No \"Experience\" section found.",
                    "Add a heading named \"Work Experience\" or \"Professional Experience\"."));
        }
        if (parsed.has(ResumeParser.EDUCATION)) {
            score += 6;
        } else {
            issues.add(issue("SECTION_EDUCATION", "STRUCTURE", Severity.WARNING,
                    "No \"Education\" section found.", "Add a heading named \"Education\"."));
        }
        if (parsed.has(ResumeParser.SKILLS)) {
            score += 6;
        } else {
            issues.add(issue("SECTION_SKILLS", "STRUCTURE", Severity.WARNING,
                    "No \"Skills\" section found.",
                    "Add a \"Skills\" section. ATS keyword matching relies on it."));
        }
        if (parsed.has(ResumeParser.SUMMARY)) {
            score += 3;
        } else {
            issues.add(issue("SECTION_SUMMARY", "STRUCTURE", Severity.INFO,
                    "No summary section found.", "A 2-3 line \"Summary\" tailored to the role helps recruiters."));
        }
        if (parsed.has(ResumeParser.PROJECTS) || parsed.has(ResumeParser.CERTIFICATIONS)
                || parsed.has(ResumeParser.AWARDS)) {
            score += 2;
        } else {
            issues.add(issue("SECTION_EXTRAS", "STRUCTURE", Severity.INFO,
                    "No projects, certifications or awards section.",
                    "Add a \"Projects\" or \"Certifications\" section to show initiative."));
        }
        return score;
    }

    // ------------------------------------------------------------------ formatting (20)

    private int formatting(String text, Integer pages, ResumeDocument doc, List<AtsIssue> issues) {
        int score = 0;
        if (pages == null) {
            score += 5;
        } else if (pages <= 2) {
            score += 6;
        } else if (pages == 3) {
            score += 3;
            issues.add(issue("FORMAT_LENGTH_PAGES", "FORMATTING", Severity.WARNING,
                    "The resume is 3 pages long.", "Trim to 1-2 pages. Recruiters skim."));
        } else {
            issues.add(issue("FORMAT_LENGTH_PAGES", "FORMATTING", Severity.CRITICAL,
                    "The resume is " + pages + " pages long.", "Cut it down to 2 pages or fewer."));
        }

        int words = Bullets.words(text);
        if (words >= 300 && words <= 900) {
            score += 5;
        } else if ((words >= 200 && words < 300) || (words > 900 && words <= 1200)) {
            score += 3;
            issues.add(issue("FORMAT_WORDS", "FORMATTING", Severity.INFO,
                    "Word count is " + words + ".", "The sweet spot is 300-900 words."));
        } else {
            score += 1;
            issues.add(issue("FORMAT_WORDS", "FORMATTING", Severity.WARNING,
                    "Word count is " + words + ", outside the 200-1200 range.",
                    words < 200 ? "Add more detail." : "Cut older or less relevant material."));
        }

        int bulletCount = allBullets(doc).size();
        if (bulletCount >= 5) {
            score += 4;
        } else if (bulletCount > 0) {
            score += 2;
            issues.add(issue("FORMAT_BULLETS_FEW", "FORMATTING", Severity.INFO,
                    "Only " + bulletCount + " bullet points found.",
                    "Describe each role with 3-6 bullet points."));
        } else {
            issues.add(issue("FORMAT_NO_BULLETS", "FORMATTING", Severity.WARNING,
                    "No bullet points were detected in experience or projects.",
                    "Use bullet points, one achievement per line."));
        }

        Set<String> glyphs = new HashSet<>();
        var m = BULLET_GLYPH.matcher(text);
        while (m.find()) {
            glyphs.add(m.group(1));
        }
        if (glyphs.size() <= 1) {
            score += 2;
        } else {
            score += 1;
            issues.add(issue("FORMAT_BULLET_STYLE", "FORMATTING", Severity.INFO,
                    "Mixed bullet styles found (" + glyphs.size() + ").", "Use one bullet style throughout."));
        }

        List<Experience> exp = doc.experience() == null ? List.of() : doc.experience();
        if (exp.isEmpty()) {
            score += 1;
        } else {
            long dated = exp.stream().filter(e -> hasDate(e.startDate()) || hasDate(e.endDate())).count();
            if ((double) dated / exp.size() >= 0.8) {
                score += 3;
            } else {
                score += 1;
                issues.add(issue("FORMAT_DATES", "FORMATTING", Severity.WARNING,
                        "Some jobs are missing clear dates.",
                        "Add dates like \"Jan 2021 - Present\" to every role."));
            }
        }
        return Math.min(score, 20);
    }

    private static boolean hasDate(String s) {
        return s != null && DATE_PRESENT.matcher(s).find();
    }

    // ------------------------------------------------------------------ title + contact (15)

    private int titleAndContact(ResumeDocument doc, String title, List<AtsIssue> issues) {
        int score = 0;
        var c = doc.contact();
        if (!c.email().isBlank()) {
            score += 5;
        } else {
            issues.add(issue("CONTACT_EMAIL", "CONTACT", Severity.CRITICAL,
                    "No email address found.", "Put your email at the top of the page, in plain text."));
        }
        if (!c.phone().isBlank()) {
            score += 3;
        } else {
            issues.add(issue("CONTACT_PHONE", "CONTACT", Severity.WARNING,
                    "No phone number found.", "Add a phone number next to your email."));
        }
        boolean linkedin = c.links().stream().anyMatch(l -> l.toLowerCase().contains("linkedin"));
        boolean other = c.links().stream().anyMatch(l -> !l.toLowerCase().contains("linkedin"));
        if (linkedin) {
            score += 2;
        } else {
            issues.add(issue("CONTACT_LINKEDIN", "CONTACT", Severity.INFO,
                    "No LinkedIn link found.", "Add your LinkedIn profile URL."));
        }
        if (other) {
            score += 1;
        }
        if (!c.fullName().isBlank()) {
            score += 2;
        } else {
            issues.add(issue("CONTACT_NAME", "CONTACT", Severity.WARNING,
                    "Could not identify your name at the top.", "Put your full name alone on the first line."));
        }
        if (title != null && !title.isBlank()) {
            score += 2;
        } else {
            issues.add(issue("TITLE_MISSING", "CONTACT", Severity.WARNING,
                    "No job title detected.",
                    "Add a headline under your name, e.g. \"Senior Java Developer\", matching the role you want."));
        }
        return score;
    }

    static String detectTitle(ResumeDocument doc) {
        if (doc.contact() != null && !doc.contact().headline().isBlank()) {
            return doc.contact().headline();
        }
        if (doc.experience() != null) {
            return doc.experience().stream().map(Experience::title)
                    .filter(t -> t != null && !t.isBlank()).findFirst().orElse(null);
        }
        return null;
    }

    // ------------------------------------------------------------------ content quality (20)

    private int content(ResumeDocument doc, String text, List<AtsIssue> issues) {
        List<String> bullets = allBullets(doc);
        if (bullets.isEmpty()) {
            issues.add(issue("CONTENT_NO_ACHIEVEMENTS", "CONTENT", Severity.WARNING,
                    "No achievement statements to evaluate.",
                    "List what you accomplished in each role, not just your duties."));
            return Bullets.usesFirstPerson(text) ? 0 : 2;
        }
        int score = 0;
        long strong = bullets.stream().filter(Bullets::startsWithStrongVerb).count();
        double verbRatio = (double) strong / bullets.size();
        if (verbRatio >= 0.7) {
            score += 6;
        } else if (verbRatio >= 0.4) {
            score += 4;
            issues.add(issue("CONTENT_VERBS", "CONTENT", Severity.INFO,
                    "Only " + Math.round(verbRatio * 100) + "% of bullets start with a strong action verb.",
                    "Start bullets with verbs like Built, Led, Reduced, Launched."));
        } else {
            score += 1;
            issues.add(issue("CONTENT_VERBS", "CONTENT", Severity.WARNING,
                    "Few bullets start with a strong action verb (" + Math.round(verbRatio * 100) + "%).",
                    "Start bullets with verbs like Built, Led, Reduced, Launched."));
        }

        long metrics = bullets.stream().filter(Bullets::hasMetric).count();
        double metricRatio = (double) metrics / bullets.size();
        if (metricRatio >= 0.4) {
            score += 6;
        } else if (metricRatio >= 0.2) {
            score += 4;
            issues.add(issue("CONTENT_METRICS", "CONTENT", Severity.INFO,
                    "Only " + Math.round(metricRatio * 100) + "% of bullets include numbers.",
                    "Add measurable results: %, time saved, users, revenue, team size."));
        } else if (metrics > 0) {
            score += 2;
            issues.add(issue("CONTENT_METRICS", "CONTENT", Severity.WARNING,
                    "Very few bullets include measurable results.",
                    "Add numbers to your achievements (%, $, users, time saved)."));
        } else {
            issues.add(issue("CONTENT_METRICS", "CONTENT", Severity.WARNING,
                    "None of your bullets include a measurable result.",
                    "Add numbers to your achievements (%, $, users, time saved)."));
        }

        double avgWords = bullets.stream().mapToInt(Bullets::words).average().orElse(0);
        if (avgWords >= 10 && avgWords <= 28) {
            score += 4;
        } else {
            score += 2;
            issues.add(issue("CONTENT_BULLET_LENGTH", "CONTENT", Severity.INFO,
                    "Average bullet length is " + Math.round(avgWords) + " words.",
                    avgWords < 10 ? "Add context and impact to short bullets." : "Split long bullets into two."));
        }

        if (Bullets.usesFirstPerson(text)) {
            issues.add(issue("CONTENT_FIRST_PERSON", "CONTENT", Severity.INFO,
                    "First-person pronouns (I, my) were found.", "Drop \"I\" and \"my\". Write in implied first person."));
        } else {
            score += 2;
        }
        long weak = bullets.stream().filter(Bullets::startsWeak).count();
        if (weak == 0) {
            score += 2;
        } else {
            issues.add(issue("CONTENT_WEAK_OPENERS", "CONTENT", Severity.INFO,
                    weak + " bullet(s) start with phrases like \"Responsible for\" or \"Helped\".",
                    "Replace them with a direct action verb and a result."));
        }
        return score;
    }

    /** Bullets from experience and projects, the text the content rules judge. */
    public static List<String> allBullets(ResumeDocument doc) {
        List<String> all = new ArrayList<>();
        if (doc.experience() != null) {
            for (Experience e : doc.experience()) {
                if (e.bullets() != null) {
                    e.bullets().stream().filter(b -> !b.isBlank()).forEach(all::add);
                }
            }
        }
        if (doc.projects() != null) {
            for (Project p : doc.projects()) {
                if (p.bullets() != null) {
                    p.bullets().stream().filter(b -> !b.isBlank()).forEach(all::add);
                }
            }
        }
        return all;
    }

    private static AtsIssue issue(String rule, String category, Severity sev, String message, String suggestion) {
        return new AtsIssue(rule, category, sev, message, suggestion);
    }
}
