package com.smartresume.planner.resume.parse;

import com.smartresume.planner.resume.domain.ResumeDocument;
import com.smartresume.planner.resume.domain.ResumeDocument.Contact;
import com.smartresume.planner.resume.domain.ResumeDocument.Education;
import com.smartresume.planner.resume.domain.ResumeDocument.Experience;
import com.smartresume.planner.resume.domain.ResumeDocument.Project;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Heuristic section segmenter: turns extracted resume text into a {@link ResumeDocument}.
 * It is deliberately forgiving: anything it cannot classify is kept as free text rather than dropped,
 * and the user can always correct the structure through the edit endpoint.
 */
@Component
public class ResumeParser {

    public static final String SUMMARY = "SUMMARY";
    public static final String EXPERIENCE = "EXPERIENCE";
    public static final String EDUCATION = "EDUCATION";
    public static final String SKILLS = "SKILLS";
    public static final String PROJECTS = "PROJECTS";
    public static final String CERTIFICATIONS = "CERTIFICATIONS";
    public static final String AWARDS = "AWARDS";
    public static final String OTHER = "OTHER";

    private static final Map<String, String> HEADINGS = new LinkedHashMap<>();

    static {
        put(SUMMARY, "summary", "professional summary", "career summary", "executive summary", "profile",
                "professional profile", "objective", "career objective", "about me", "about", "personal statement");
        put(EXPERIENCE, "experience", "work experience", "professional experience", "employment",
                "employment history", "work history", "career history", "relevant experience", "internships",
                "internship experience", "industry experience");
        put(EDUCATION, "education", "academic background", "academics", "education and training",
                "educational qualifications", "academic qualifications", "qualifications");
        put(SKILLS, "skills", "technical skills", "core competencies", "key skills", "technologies",
                "skills and tools", "tech stack", "core skills", "areas of expertise", "technical proficiency",
                "skills and technologies", "technical expertise");
        put(PROJECTS, "projects", "personal projects", "academic projects", "selected projects", "key projects",
                "side projects", "technical projects", "open source");
        put(CERTIFICATIONS, "certifications", "certificates", "licenses", "licenses and certifications",
                "courses", "training", "certifications and courses");
        put(AWARDS, "awards", "honors", "honours", "achievements", "accomplishments", "awards and honors");
        put(OTHER, "languages", "interests", "hobbies", "volunteer", "volunteering", "volunteer experience",
                "publications", "references", "extracurricular activities", "activities");
    }

    private static void put(String section, String... phrases) {
        for (String p : phrases) {
            HEADINGS.put(p, section);
        }
    }

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<![\\d])(\\+?\\d[\\d\\s().-]{7,}\\d)(?![\\d])");
    private static final Pattern URL = Pattern.compile(
            "(?i)\\b((?:https?://)?(?:www\\.)?(?:linkedin\\.com/in/|github\\.com/|gitlab\\.com/|"
                    + "[a-z0-9-]+\\.(?:com|io|dev|me|net|org|app)/?)[^\\s,|;()]*)");
    private static final String MONTH = "(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\.?";
    private static final String DATE = "(?:" + MONTH + "\\s+\\d{4}|\\d{1,2}/\\d{4}|\\d{4})";
    private static final Pattern DATE_RANGE = Pattern.compile(
            "(?i)(" + DATE + ")\\s*(?:-|–|—|to)\\s*(present|current|now|ongoing|" + DATE + ")");
    private static final Pattern BULLET = Pattern.compile("^\\s*(?:[•●▪■◦○·►➢✓✔]\\s*|[*\\-–—]\\s+)(.*)$");
    private static final Pattern DEGREE = Pattern.compile(
            "(?i)\\b(b\\.?\\s?tech|b\\.?\\s?e\\.?|b\\.?\\s?sc|b\\.?\\s?a\\.?|b\\.?\\s?s\\.?|m\\.?\\s?tech|m\\.?\\s?sc|"
                    + "m\\.?\\s?s\\.?|m\\.?\\s?a\\.?|mba|mca|bca|ph\\.?\\s?d|bachelor|master|diploma|associate|"
                    + "doctorate|b\\.?\\s?com|m\\.?\\s?com)\\b");
    private static final Pattern INSTITUTION = Pattern.compile(
            "(?i)\\b(university|college|institute|school|academy|polytechnic)\\b");
    private static final Pattern COMPANY_HINT = Pattern.compile(
            "(?i)\\b(inc|llc|ltd|limited|corp|corporation|technologies|technology|solutions|systems|labs|gmbh|"
                    + "pvt|private|company|co\\.|group|consulting|services|software|bank|studio|ai)\\b\\.?");
    private static final Pattern TECH_LINE = Pattern.compile(
            "(?i)^(?:tech(?:nologies)?(?: stack)?|built with|tools|stack|technologies used)\\s*[:\\-]\\s*(.+)$");

    public ParsedResume parse(String text) {
        List<String> lines = text.lines().map(String::strip).toList();

        // 1) Split into sections by heading lines.
        List<String> header = new ArrayList<>();
        Map<String, List<String>> sections = new LinkedHashMap<>();
        String current = null;
        for (String line : lines) {
            String heading = headingOf(line);
            if (heading != null) {
                current = heading;
                sections.computeIfAbsent(current, k -> new ArrayList<>());
                continue;
            }
            if (current == null) {
                header.add(line);
            } else {
                sections.get(current).add(line);
            }
        }

        // 2) Parse each section into the structured model.
        Contact contact = parseContact(header);
        String summary = joinNonBlank(sections.get(SUMMARY));
        List<Experience> experience = parseExperience(sections.get(EXPERIENCE));
        List<Education> education = parseEducation(sections.get(EDUCATION));
        List<String> skills = parseSkills(sections.get(SKILLS));
        List<Project> projects = parseProjects(sections.get(PROJECTS));
        List<String> certifications = parseSimpleList(sections.get(CERTIFICATIONS));

        ResumeDocument doc = new ResumeDocument(contact, summary, experience, education, skills, projects,
                certifications);
        List<String> detected = new ArrayList<>();
        if (!contact.email().isBlank() || !contact.phone().isBlank()) {
            detected.add("CONTACT");
        }
        for (String s : sections.keySet()) {
            if (!sections.get(s).stream().allMatch(String::isBlank)) {
                detected.add(s);
            }
        }
        return new ParsedResume(doc, detected, header.stream().filter(l -> !l.isBlank()).limit(8).toList());
    }

    // ------------------------------------------------------------------ headings

    static String headingOf(String line) {
        if (line.isBlank() || line.length() > 45) {
            return null;
        }
        String norm = line.toLowerCase(Locale.ROOT)
                .replace("&", " and ").replace("/", " and ")
                .replaceAll("[^a-z ]", " ").replaceAll("\\s+", " ").trim();
        return HEADINGS.get(norm);
    }

    // ------------------------------------------------------------------ contact

    private Contact parseContact(List<String> header) {
        List<String> head = header.stream().filter(l -> !l.isBlank()).limit(10).toList();
        String email = "";
        String phone = "";
        Set<String> links = new LinkedHashSet<>();
        for (String line : head) {
            Matcher m = EMAIL.matcher(line);
            if (email.isEmpty() && m.find()) {
                email = m.group();
            }
            Matcher p = PHONE.matcher(line);
            if (phone.isEmpty() && p.find()) {
                phone = p.group(1).trim();
            }
            Matcher u = URL.matcher(EMAIL.matcher(line).replaceAll(" "));
            while (u.find()) {
                String link = u.group(1);
                if (link.toLowerCase(Locale.ROOT).matches(".*\\.(png|jpg|pdf)$")) {
                    continue;
                }
                links.add(link.replaceAll("[.,;]+$", ""));
            }
        }
        String name = "";
        String headline = "";
        String location = "";
        for (String line : head) {
            String cleaned = stripContact(line);
            if (cleaned.isBlank()) {
                continue;
            }
            String[] parts = cleaned.split("\\s*[|•·●]\\s*");
            for (String part : parts) {
                String t = part.trim();
                if (t.isEmpty()) {
                    continue;
                }
                if (name.isEmpty() && looksLikeName(t)) {
                    name = t;
                } else if (location.isEmpty() && looksLikeLocation(t)) {
                    location = t;
                } else if (headline.isEmpty() && !name.isEmpty() && looksLikeHeadline(t)) {
                    headline = t;
                }
            }
        }
        return new Contact(name, headline, email, phone, location, new ArrayList<>(links));
    }

    private static String stripContact(String line) {
        String s = EMAIL.matcher(line).replaceAll(" ");
        s = URL.matcher(s).replaceAll(" ");
        s = PHONE.matcher(s).replaceAll(" ");
        return s.replaceAll("(?i)\\b(email|e-mail|phone|mobile|tel|linkedin|github|portfolio|website)\\s*[:\\-]?", " ")
                .replaceAll("^[\\s|•·●,;-]+|[\\s|•·●,;-]+$", "")
                .replaceAll("\\s{2,}", " ").trim();
    }

    private static boolean looksLikeName(String s) {
        String[] words = s.split("\\s+");
        if (words.length < 2 || words.length > 5 || s.length() > 50 || s.matches(".*\\d.*")) {
            return false;
        }
        if (s.contains(",") && words.length > 3) {
            return false;
        }
        for (String w : words) {
            if (!w.matches("[\\p{L}][\\p{L}.'’-]*")) {
                return false;
            }
        }
        return !looksLikeHeadline(s) || s.equals(s.toUpperCase(Locale.ROOT));
    }

    private static boolean looksLikeLocation(String s) {
        return s.length() <= 60 && s.matches("^[\\p{L}][\\p{L}.' -]+,\\s*[\\p{L}][\\p{L}.' -]*$");
    }

    private static final Pattern TITLE_WORDS = Pattern.compile(
            "(?i)\\b(engineer|developer|manager|analyst|designer|architect|consultant|scientist|lead|director|"
                    + "administrator|specialist|officer|intern|programmer|tester|qa|devops|full[- ]?stack|"
                    + "backend|frontend|front-end|back-end|student|associate|coordinator|executive|researcher)\\b");

    private static boolean looksLikeHeadline(String s) {
        return s.split("\\s+").length <= 9 && TITLE_WORDS.matcher(s).find();
    }

    // ------------------------------------------------------------------ experience

    private List<Experience> parseExperience(List<String> lines) {
        List<Experience> result = new ArrayList<>();
        if (lines == null) {
            return result;
        }
        List<String> headerLines = new ArrayList<>();
        List<String> bullets = new ArrayList<>();
        boolean sawBullet = false;
        for (String raw : lines) {
            if (raw.isBlank()) {
                continue;
            }
            Matcher b = BULLET.matcher(raw);
            if (b.matches()) {
                bullets.add(b.group(1).trim());
                sawBullet = true;
                continue;
            }
            if (isEntryHeader(raw, !bullets.isEmpty())) {
                if (sawBullet) {
                    result.add(buildExperience(headerLines, bullets));
                    headerLines = new ArrayList<>();
                    bullets = new ArrayList<>();
                    sawBullet = false;
                }
                headerLines.add(raw);
            } else if (!bullets.isEmpty() && !endsSentence(bullets.get(bullets.size() - 1))) {
                // Wrapped continuation of the previous bullet.
                int last = bullets.size() - 1;
                bullets.set(last, bullets.get(last) + " " + raw);
            } else {
                bullets.add(raw);           // paragraph-style description line
                sawBullet = true;
            }
        }
        if (!headerLines.isEmpty() || !bullets.isEmpty()) {
            result.add(buildExperience(headerLines, bullets));
        }
        return result;
    }

    private static boolean endsSentence(String s) {
        return s.endsWith(".") || s.endsWith("!") || s.endsWith(";") || s.endsWith("%");
    }

    /** A short line without sentence punctuation (or any line carrying a date range) starts/extends an entry header. */
    private static boolean isEntryHeader(String line, boolean entryHasBullets) {
        if (DATE_RANGE.matcher(line).find()) {
            return true;
        }
        int words = line.split("\\s+").length;
        return words <= 10 && line.length() <= 90 && !line.endsWith(".") && !line.contains("  ")
                && (!entryHasBullets || Character.isUpperCase(line.charAt(0)));
    }

    private Experience buildExperience(List<String> headerLines, List<String> bullets) {
        String start = "";
        String end = "";
        List<String> pieces = new ArrayList<>();
        for (String h : headerLines) {
            Matcher m = DATE_RANGE.matcher(h);
            String rest = h;
            if (m.find()) {
                start = m.group(1);
                end = m.group(2);
                rest = (h.substring(0, m.start()) + " " + h.substring(m.end())).trim();
            }
            for (String p : rest.split("\\s+(?:at|@)\\s+|\\s*[|•·●]\\s*|\\s+[–—-]\\s+|,\\s+(?=[A-Z])")) {
                String t = p.replaceAll("^[\\s(),|–—-]+|[\\s(),|–—-]+$", "");
                if (!t.isEmpty()) {
                    pieces.add(t);
                }
            }
        }
        String title = "";
        String company = "";
        String location = "";
        for (String p : pieces) {
            if (company.isEmpty() && COMPANY_HINT.matcher(p).find()) {
                company = p;
            } else if (title.isEmpty() && TITLE_WORDS.matcher(p).find()) {
                title = p;
            } else if (location.isEmpty() && looksLikeLocation(p)) {
                location = p;
            }
        }
        for (String p : pieces) {          // fill remaining blanks positionally
            if (title.isEmpty() && !p.equals(company) && !p.equals(location)) {
                title = p;
            } else if (company.isEmpty() && !p.equals(title) && !p.equals(location)) {
                company = p;
            }
        }
        return new Experience(title, company, location, start, end, new ArrayList<>(bullets));
    }

    // ------------------------------------------------------------------ education

    private List<Education> parseEducation(List<String> lines) {
        List<Education> result = new ArrayList<>();
        if (lines == null) {
            return result;
        }
        String degree = "";
        String institution = "";
        String start = "";
        String end = "";
        StringBuilder details = new StringBuilder();
        for (String raw : lines) {
            if (raw.isBlank()) {
                continue;
            }
            String line = BULLET.matcher(raw).matches() ? BULLET.matcher(raw).replaceFirst("$1") : raw;
            Matcher m = DATE_RANGE.matcher(line);
            String rest = line;
            String s = "";
            String e = "";
            if (m.find()) {
                s = m.group(1);
                e = m.group(2);
                rest = (line.substring(0, m.start()) + " " + line.substring(m.end())).trim();
            } else {
                Matcher year = Pattern.compile("\\b((?:19|20)\\d{2})\\b").matcher(line);
                if (year.find()) {
                    e = year.group(1);
                    rest = line.replace(year.group(), "").trim();
                }
            }
            rest = rest.replaceAll("^[\\s(),|–—-]+|[\\s(),|–—-]+$", "");
            boolean hasDegree = DEGREE.matcher(rest).find();
            boolean hasInstitution = INSTITUTION.matcher(rest).find();
            if (hasDegree && !degree.isEmpty() || hasInstitution && !institution.isEmpty()) {
                result.add(new Education(degree, institution, start, end, details.toString().trim()));
                degree = institution = start = end = "";
                details.setLength(0);
            }
            if (hasDegree && hasInstitution) {
                String[] parts = rest.split("\\s*[|,–—-]\\s*");
                for (String part : parts) {
                    if (INSTITUTION.matcher(part).find() && institution.isEmpty()) {
                        institution = part.trim();
                    } else if (degree.isEmpty()) {
                        degree = part.trim();
                    }
                }
            } else if (hasDegree) {
                degree = rest;
            } else if (hasInstitution) {
                institution = rest;
            } else if (!rest.isEmpty()) {
                if (degree.isEmpty() && institution.isEmpty()) {
                    degree = rest;
                } else {
                    details.append(details.isEmpty() ? "" : " ").append(rest);
                }
            }
            if (!s.isEmpty()) {
                start = s;
            }
            if (!e.isEmpty()) {
                end = e;
            }
        }
        if (!degree.isEmpty() || !institution.isEmpty()) {
            result.add(new Education(degree, institution, start, end, details.toString().trim()));
        }
        return result;
    }

    // ------------------------------------------------------------------ skills, projects, lists

    private List<String> parseSkills(List<String> lines) {
        Set<String> skills = new LinkedHashSet<>();
        if (lines == null) {
            return new ArrayList<>();
        }
        for (String raw : lines) {
            String line = BULLET.matcher(raw).matches() ? BULLET.matcher(raw).replaceFirst("$1") : raw;
            int colon = line.indexOf(':');
            if (colon > 0 && colon < 40) {
                line = line.substring(colon + 1);        // drop the "Languages:" style label
            }
            for (String s : line.split("\\s*[,;|•·●]\\s*")) {
                String t = s.trim().replaceAll("^[\\s-–—]+|[.\\s]+$", "");
                if (!t.isEmpty() && t.length() <= 40) {
                    skills.add(t);
                }
            }
        }
        return new ArrayList<>(skills);
    }

    private List<Project> parseProjects(List<String> lines) {
        List<Project> result = new ArrayList<>();
        if (lines == null) {
            return result;
        }
        String name = null;
        String url = "";
        List<String> bullets = new ArrayList<>();
        List<String> tech = new ArrayList<>();
        StringBuilder description = new StringBuilder();
        for (String raw : lines) {
            if (raw.isBlank()) {
                continue;
            }
            Matcher b = BULLET.matcher(raw);
            Matcher t = TECH_LINE.matcher(raw);
            if (t.matches()) {
                for (String s : t.group(1).split("\\s*[,;|]\\s*")) {
                    if (!s.isBlank()) {
                        tech.add(s.trim());
                    }
                }
            } else if (b.matches()) {
                bullets.add(b.group(1).trim());
            } else if (raw.length() <= 90 && !raw.endsWith(".") && (name == null || !bullets.isEmpty())) {
                if (name != null) {
                    result.add(new Project(name, url, description.toString().trim(), bullets, tech));
                    bullets = new ArrayList<>();
                    tech = new ArrayList<>();
                    description.setLength(0);
                    url = "";
                }
                Matcher u = URL.matcher(raw);
                if (u.find()) {
                    url = u.group(1);
                }
                name = raw.replaceAll("\\s*[|–—-]\\s*.*$", "").trim();
                if (name.isEmpty()) {
                    name = raw;
                }
            } else {
                description.append(description.isEmpty() ? "" : " ").append(raw);
            }
        }
        if (name != null) {
            result.add(new Project(name, url, description.toString().trim(), bullets, tech));
        }
        return result;
    }

    private List<String> parseSimpleList(List<String> lines) {
        List<String> out = new ArrayList<>();
        if (lines == null) {
            return out;
        }
        for (String raw : lines) {
            if (raw.isBlank()) {
                continue;
            }
            Matcher b = BULLET.matcher(raw);
            out.add(b.matches() ? b.group(1).trim() : raw.trim());
        }
        return out;
    }

    private static String joinNonBlank(List<String> lines) {
        if (lines == null) {
            return "";
        }
        return String.join(" ", lines.stream().filter(l -> !l.isBlank()).toList()).trim();
    }
}
