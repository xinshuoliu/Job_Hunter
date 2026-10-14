package com.jobhunter.filter;

import com.jobhunter.model.Job;
import com.jobhunter.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JobScorer {

    private static final Logger log = LoggerFactory.getLogger(JobScorer.class);

    private final Map<String, Integer> weights;
    private final Integer minScore;

    public JobScorer(Map<String, Integer> weights, Integer minScore) {
        this.weights = new LinkedHashMap<>(weights);
        this.minScore = minScore;
    }

    public static JobScorer disabled() {
        return new JobScorer(Map.of(), null);
    }

    public boolean isEnabled() {
        return !weights.isEmpty();
    }

    public int score(Job job) {
        String title = Text.normalize(job.getTitle());
        String description = Text.normalize(job.getDescription());
        int total = 0;
        for (Map.Entry<String, Integer> weight : weights.entrySet()) {
            if (Text.containsTerm(title, weight.getKey())) {
                total += weight.getValue();
            } else if (Text.containsTerm(description, weight.getKey())) {
                total += weight.getValue() / 2;
            }
        }
        return total;
    }

    public List<Job> apply(List<Job> jobs) {
        if (!isEnabled()) return jobs;

        List<Job> kept = new ArrayList<>();
        for (Job job : jobs) {
            job.setScore(score(job));
            if (minScore == null || job.getScore() >= minScore) {
                kept.add(job);
            } else {
                log.debug("Dropped below min_score ({} < {}): {}", job.getScore(), minScore, job);
            }
        }
        if (kept.size() < jobs.size()) {
            log.info("Scoring dropped {} of {} matches below min_score {}", jobs.size() - kept.size(), jobs.size(), minScore);
        }
        return kept;
    }

    public static List<Job> byScoreDescending(List<Job> jobs) {
        List<Job> sorted = new ArrayList<>(jobs);
        sorted.sort(Comparator.comparing(Job::getScore, Comparator.nullsLast(Comparator.reverseOrder())));
        return sorted;
    }
}
