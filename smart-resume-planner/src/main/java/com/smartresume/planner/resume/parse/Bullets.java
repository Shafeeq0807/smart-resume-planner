package com.smartresume.planner.resume.parse;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Shared heuristics about bullet-point quality, used by the ATS analyzer and the AI optimizer. */
public final class Bullets {

    private Bullets() {
    }

    public static final Set<String> STRONG_VERBS = Set.of(
            "accelerated", "achieved", "architected", "automated", "built", "championed", "cut", "created",
            "delivered", "deployed", "designed", "developed", "directed", "drove", "eliminated", "engineered",
            "established", "expanded", "generated", "grew", "implemented", "improved", "increased", "initiated",
            "integrated", "introduced", "launched", "led", "managed", "migrated", "mentored", "modernized",
            "negotiated", "optimized", "orchestrated", "owned", "pioneered", "reduced", "refactored", "resolved",
            "restructured", "revamped", "saved", "scaled", "shipped", "simplified", "spearheaded", "streamlined",
            "strengthened", "transformed", "trained", "unified", "upgraded", "won", "authored", "boosted",
            "consolidated", "coordinated", "decreased", "enhanced", "executed", "formulated", "maximized",
            "overhauled", "produced", "secured", "standardized", "tested", "analyzed", "collaborated");

    private static final Pattern WEAK_START = Pattern.compile(
            "(?i)^(responsible for|duties (included|include)|tasked with|helped( to| with)?|assisted( with| in)?|"
                    + "worked (on|with)|participated in|involved in|handled|was part of|in charge of|"
                    + "contributed to)\\b");
    private static final Pattern METRIC = Pattern.compile(
            "(?i)(\\d+(?:[.,]\\d+)?\\s?(?:%|percent|x\\b|k\\b|m\\b|million|billion|users|customers|requests|ms\\b|"
                    + "hours|days|weeks|people|engineers|clients|services|apis|endpoints|tests)|[$€£₹]\\s?\\d|"
                    + "\\b\\d{2,}\\b)");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\[[^\\]]*(X|Y|Z|%|metric|number)[^\\]]*\\]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FIRST_PERSON = Pattern.compile("(?i)\\b(i|my|me|myself)\\b");

    public static String firstWord(String bullet) {
        String[] w = bullet.strip().split("[\\s,;:]+", 2);
        return w.length == 0 ? "" : w[0].toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    public static boolean startsWithStrongVerb(String bullet) {
        return STRONG_VERBS.contains(firstWord(bullet));
    }

    public static boolean startsWeak(String bullet) {
        return WEAK_START.matcher(bullet.strip()).find();
    }

    public static boolean hasMetric(String bullet) {
        return METRIC.matcher(bullet).find();
    }

    public static boolean hasPlaceholder(String bullet) {
        return PLACEHOLDER.matcher(bullet).find();
    }

    public static boolean usesFirstPerson(String text) {
        return FIRST_PERSON.matcher(text).find();
    }

    public static int words(String s) {
        return s == null || s.isBlank() ? 0 : s.trim().split("\\s+").length;
    }

    /** 0..100 impact score: metric, strong verb, sensible length, no filler opener. */
    public static int impactScore(String bullet) {
        int score = 0;
        if (hasMetric(bullet)) {
            score += 40;
        }
        if (startsWithStrongVerb(bullet)) {
            score += 25;
        }
        int w = words(bullet);
        if (w >= 10 && w <= 30) {
            score += 20;
        } else if (w >= 6) {
            score += 8;
        }
        if (!startsWeak(bullet)) {
            score += 15;
        }
        return Math.min(100, score);
    }

    /** A bullet worth sending to the AI rewriter: it is substantive but weak. */
    public static boolean isWeak(String bullet) {
        return words(bullet) >= 4 && impactScore(bullet) < 60;
    }
}
