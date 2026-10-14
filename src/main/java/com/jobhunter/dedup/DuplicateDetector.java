package com.jobhunter.dedup;

import com.jobhunter.model.Job;
import com.jobhunter.util.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DuplicateDetector {

    public static final double DEFAULT_THRESHOLD = 0.8;

    private static final Pattern YEAR = Pattern.compile("^20\\d\\d$");
    private static final Pattern NUMBER = Pattern.compile("^\\d+$");

    private static final Map<String, String> SEASONS = Map.ofEntries(
            Map.entry("summer", "summer"), Map.entry("ete", "summer"),
            Map.entry("fall", "fall"), Map.entry("autumn", "fall"), Map.entry("automne", "fall"),
            Map.entry("winter", "winter"), Map.entry("hiver", "winter"),
            Map.entry("spring", "spring"), Map.entry("printemps", "spring"),
            Map.entry("january", "winter"), Map.entry("janvier", "winter"),
            Map.entry("september", "fall"), Map.entry("septembre", "fall"));

    private static final Set<String> INTERN_WORDS = Set.of(
            "intern", "interns", "internship", "internships", "coop", "coops", "stage", "stages",
            "stagiaire", "stagiaires", "student", "students", "etudiant", "etudiante");

    private static final Set<String> NOISE = Set.of(
            "at", "the", "a", "an", "for", "of", "and", "in", "to", "with", "on", "or", "en", "de", "des",
            "du", "la", "le", "les", "et", "ou", "au", "aux", "chez", "pour",
            "montreal", "quebec", "qc", "canada", "remote", "hybrid", "hybride", "onsite", "site",
            "teletravail", "presentiel", "position", "opportunity", "role", "poste", "h", "f", "e", "x");

    private static final Pattern CO_OP = Pattern.compile("\\bco op\\b");

    private final double threshold;

    public DuplicateDetector() {
        this(DEFAULT_THRESHOLD);
    }

    public DuplicateDetector(double threshold) {
        this.threshold = threshold;
    }

    public boolean isSameJob(Job a, Job b) {
        if (a.getId() != null && a.getId().equals(b.getId())) return true;
        if (!sameCompany(a.getCompany(), b.getCompany())) return false;
        if (a.deduplicationKey().equals(b.deduplicationKey())) return true;

        Fingerprint left = fingerprint(a.getTitle(), a.getCompany());
        Fingerprint right = fingerprint(b.getTitle(), b.getCompany());
        if (conflicts(left.years(), right.years()) || conflicts(left.seasons(), right.seasons())) return false;
        if (!left.code().isEmpty() && !right.code().isEmpty() && !left.code().equals(right.code())) return false;

        return jaccard(left.tokens(), right.tokens()) >= threshold;
    }

    public Optional<Job> findDuplicate(Job job, Collection<Job> candidates) {
        return candidates.stream().filter(other -> other != job && isSameJob(job, other)).findFirst();
    }

    public Index index(Collection<Job> jobs) {
        Index index = new Index();
        jobs.forEach(index::add);
        return index;
    }

    public final class Index {

        private final Map<String, List<Job>> byCompany = new HashMap<>();

        public void add(Job job) {
            byCompany.computeIfAbsent(companyBucket(job.getCompany()), key -> new ArrayList<>()).add(job);
        }

        public Optional<Job> find(Job job) {
            return findDuplicate(job, byCompany.getOrDefault(companyBucket(job.getCompany()), List.of()));
        }
    }

    private static String companyBucket(String company) {
        List<String> words = tokens(Text.companyKey(company));
        return words.isEmpty() ? "" : words.get(0);
    }

    static boolean sameCompany(String a, String b) {
        List<String> left = tokens(Text.companyKey(a));
        List<String> right = tokens(Text.companyKey(b));
        if (left.isEmpty() || right.isEmpty()) return left.equals(right);
        List<String> shorter = left.size() <= right.size() ? left : right;
        List<String> longer = shorter == left ? right : left;
        return longer.subList(0, shorter.size()).equals(shorter);
    }

    static Fingerprint fingerprint(String title, String company) {
        String normalized = CO_OP.matcher(Text.normalize(title)).replaceAll("coop");
        Set<String> companyWords = new HashSet<>(tokens(Text.normalize(company)));

        Set<String> years = new HashSet<>();
        Set<String> seasons = new HashSet<>();
        Set<String> words = new HashSet<>();
        StringBuilder code = new StringBuilder();

        for (String token : tokens(normalized)) {
            if (YEAR.matcher(token).matches()) {
                years.add(token);
            } else if (NUMBER.matcher(token).matches()) {
                code.append(code.isEmpty() ? "" : "-").append(token);
                words.add(token);
            } else if (SEASONS.containsKey(token)) {
                seasons.add(SEASONS.get(token));
            } else if (INTERN_WORDS.contains(token)) {
                words.add("intern");
            } else if (!NOISE.contains(token) && !companyWords.contains(token)) {
                words.add(token);
            }
        }
        return new Fingerprint(words, years, seasons, code.toString());
    }

    private static boolean conflicts(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) return false;
        return left.stream().noneMatch(right::contains);
    }

    private static double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) return left.equals(right) ? 1.0 : 0.0;
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        long shared = left.stream().filter(right::contains).count();
        return (double) shared / union.size();
    }

    private static List<String> tokens(String normalized) {
        if (normalized == null || normalized.isBlank()) return List.of();
        return Arrays.stream(normalized.split(" ")).filter(t -> !t.isEmpty()).toList();
    }

    record Fingerprint(Set<String> tokens, Set<String> years, Set<String> seasons, String code) {
        @Override
        public String toString() {
            return tokens.stream().sorted().collect(Collectors.joining(" ")) + " " + years + " " + seasons + " " + code;
        }
    }
}
