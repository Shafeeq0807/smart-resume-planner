package com.smartresume.planner.matching.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Local, deterministic text similarity (log-TF cosine over unigrams + bigrams). No embedding model needed. */
final class TextSimilarity {

    private TextSimilarity() {
    }

    private static final Set<String> STOP = Set.of(
            "a", "an", "the", "and", "or", "of", "to", "in", "on", "for", "with", "at", "by", "from", "as", "is",
            "are", "was", "were", "be", "been", "being", "this", "that", "these", "those", "it", "its", "we", "you",
            "our", "your", "their", "they", "will", "can", "should", "must", "have", "has", "had", "do", "does",
            "not", "but", "if", "than", "then", "so", "such", "using", "use", "used", "etc", "including", "within",
            "across", "about", "into", "over", "per", "new", "role", "team", "work", "working", "ability", "able",
            "strong", "good", "great", "excellent", "years", "year", "experience", "required", "preferred",
            "responsibilities", "requirements", "job", "company", "candidate", "looking", "join", "us", "who",
            "what", "all", "any", "more", "other", "also", "well", "both", "may", "least");

    static List<String> tokens(String text) {
        List<String> out = new java.util.ArrayList<>();
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}+#.]+")) {
            String t = raw.replaceAll("^[.]+|[.]+$", "");
            if (t.length() >= 2 && !STOP.contains(t) && !t.matches("\\d+")) {
                out.add(t);
            }
        }
        return out;
    }

    /** Cosine similarity in [0,1]. */
    static double cosine(String a, String b) {
        Map<String, Double> va = vector(a);
        Map<String, Double> vb = vector(b);
        if (va.isEmpty() || vb.isEmpty()) {
            return 0;
        }
        double dot = 0;
        for (Map.Entry<String, Double> e : va.entrySet()) {
            Double w = vb.get(e.getKey());
            if (w != null) {
                dot += e.getValue() * w;
            }
        }
        return dot / (norm(va) * norm(vb));
    }

    private static Map<String, Double> vector(String text) {
        List<String> toks = tokens(text);
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < toks.size(); i++) {
            counts.merge(toks.get(i), 1, Integer::sum);
            if (i + 1 < toks.size()) {
                counts.merge(toks.get(i) + " " + toks.get(i + 1), 1, Integer::sum);
            }
        }
        Map<String, Double> v = new HashMap<>();
        counts.forEach((k, c) -> v.put(k, 1 + Math.log(c)));
        return v;
    }

    private static double norm(Map<String, Double> v) {
        double s = 0;
        for (double d : v.values()) {
            s += d * d;
        }
        return Math.sqrt(s);
    }

    static Set<String> tokenSet(String text) {
        return new HashSet<>(tokens(text));
    }
}
