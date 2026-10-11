package com.smartresume.planner.matching.service;

import com.smartresume.planner.matching.domain.SkillType;
import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Built-in vocabulary of skills (name, type, family, aliases) loaded from {@code skills/catalog.csv}.
 * Used to normalise skill names ("ReactJS" -> "React"), find skills in free text with word-boundary-safe
 * matching, and find "related" skills in the same family for partial credit.
 */
@Slf4j
@Component
public class SkillCatalog {

    public record Skill(String name, SkillType type, String group, List<Pattern> patterns) {

        public boolean appearsIn(String text) {
            return firstMatchStart(text) >= 0;
        }

        int firstMatchStart(String text) {
            int best = -1;
            for (Pattern p : patterns) {
                Matcher m = p.matcher(text);
                if (m.find() && (best < 0 || m.start() < best)) {
                    best = m.start();
                }
            }
            return best;
        }
    }

    private static final String LEFT = "(?<![\\p{L}\\p{N}+#])";
    private static final String RIGHT = "(?![\\p{L}\\p{N}+#])";

    private final List<Skill> skills = new ArrayList<>();
    private final Map<String, Skill> byAlias = new HashMap<>();

    @PostConstruct
    void load() throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("skills/catalog.csv").getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] cols = line.split("\\|", -1);
                if (cols.length < 3) {
                    continue;
                }
                String name = cols[0].trim();
                SkillType type = SkillType.valueOf(cols[1].trim());
                String group = cols[2].trim();
                List<String> aliases = new ArrayList<>();
                if (cols.length > 3 && !cols[3].isBlank()) {
                    for (String a : cols[3].split(";")) {
                        if (!a.isBlank()) {
                            aliases.add(a.trim());
                        }
                    }
                }
                if (!aliases.contains("!" + name)) {
                    aliases.add(name);
                }
                Skill skill = new Skill(name, type, group, buildPatterns(aliases));
                skills.add(skill);
                for (String a : aliases) {
                    byAlias.putIfAbsent(a.replace("!", "").replace("*", "").toLowerCase(Locale.ROOT), skill);
                }
                byAlias.putIfAbsent(name.toLowerCase(Locale.ROOT), skill);
            }
        }
        log.info("Skill catalog loaded: {} skills, {} aliases", skills.size(), byAlias.size());
    }

    private static List<Pattern> buildPatterns(List<String> aliases) {
        Set<String> insensitive = new LinkedHashSet<>();
        Set<String> sensitive = new LinkedHashSet<>();
        for (String raw : aliases) {
            boolean cs = raw.startsWith("!");
            String a = cs ? raw.substring(1) : raw;
            boolean wildcard = a.endsWith("*");
            if (wildcard) {
                a = a.substring(0, a.length() - 1);
            }
            String body = Pattern.quote(a) + (wildcard ? "\\p{L}*" : "");
            (cs ? sensitive : insensitive).add(body);
        }
        List<Pattern> patterns = new ArrayList<>();
        if (!insensitive.isEmpty()) {
            patterns.add(Pattern.compile(LEFT + "(?:" + String.join("|", insensitive) + ")" + RIGHT,
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
        }
        if (!sensitive.isEmpty()) {
            patterns.add(Pattern.compile(LEFT + "(?:" + String.join("|", sensitive) + ")" + RIGHT));
        }
        return patterns;
    }

    public Optional<Skill> resolve(String rawName) {
        if (rawName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byAlias.get(rawName.trim().toLowerCase(Locale.ROOT)));
    }

    /** Canonical catalog name when known, otherwise the cleaned input. */
    public String normalize(String rawName) {
        return resolve(rawName).map(Skill::name).orElseGet(() -> rawName == null ? "" : rawName.trim());
    }

    public List<Skill> findIn(String text) {
        List<Skill> found = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return found;
        }
        for (Skill s : skills) {
            if (s.appearsIn(text)) {
                found.add(s);
            }
        }
        return found;
    }

    /** Skills in the same family (e.g. MySQL <-> PostgreSQL), excluding the skill itself. */
    public List<Skill> relatedTo(Skill skill) {
        return skills.stream().filter(s -> s.group().equals(skill.group()) && !s.name().equals(skill.name())).toList();
    }

    /** The first line of {@code text} that mentions the skill, cleaned for display (max 300 chars). */
    public Optional<String> evidenceLine(String text, Skill skill) {
        for (String line : text.split("\n")) {
            for (Pattern p : skill.patterns()) {
                if (p.matcher(line).find()) {
                    String cleaned = line.strip().replaceFirst("^[•●▪■◦○·►➢✓✔*\\-–—]\\s*", "");
                    return Optional.of(cleaned.length() > 300 ? cleaned.substring(0, 297) + "..." : cleaned);
                }
            }
        }
        return Optional.empty();
    }

    public List<Skill> all() {
        return List.copyOf(skills);
    }
}
