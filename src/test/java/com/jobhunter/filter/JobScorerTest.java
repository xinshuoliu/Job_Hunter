package com.jobhunter.filter;

import com.jobhunter.model.Job;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JobScorerTest {

    private final Map<String, Integer> weights = new LinkedHashMap<>(Map.of(
            "java", 3, "backend", 2, "technical writer", -10, "spring", 4));

    @Test
    @DisplayName("Title matches count fully, description-only matches count half")
    void weighsTitleOverDescription() {
        JobScorer scorer = new JobScorer(weights, null);

        assertEquals(5, scorer.score(job("Java Backend Intern", null)));
        assertEquals(3 + 2, scorer.score(job("Java Intern", "Spring Boot services")));
        assertEquals(-10 + 1, scorer.score(job("Technical Writer Intern", "Write Java docs")));
        assertEquals(0, scorer.score(job("Designer Intern", null)));
    }

    @Test
    @DisplayName("apply() sets scores and drops jobs under min_score")
    void appliesMinimum() {
        JobScorer scorer = new JobScorer(weights, 2);
        List<Job> kept = scorer.apply(List.of(job("Java Intern", null), job("Designer Intern", null)));

        assertEquals(1, kept.size());
        assertEquals(3, kept.get(0).getScore());
    }

    @Test
    @DisplayName("A scorer without weights leaves jobs and scores untouched")
    void disabledScorerIsPassThrough() {
        List<Job> jobs = List.of(job("Designer Intern", null));
        assertSame(jobs, JobScorer.disabled().apply(jobs));
        assertNull(jobs.get(0).getScore());
    }

    @Test
    @DisplayName("Jobs are ordered best first with unscored ones last")
    void sortsByScore() {
        Job low = job("a", null);
        low.setScore(1);
        Job high = job("b", null);
        high.setScore(9);
        Job none = job("c", null);

        List<Job> sorted = JobScorer.byScoreDescending(new ArrayList<>(List.of(none, low, high)));

        assertEquals(List.of(high, low, none), sorted);
    }

    private static Job job(String title, String description) {
        Job job = new Job();
        job.setId(title + description);
        job.setTitle(title);
        job.setDescription(description);
        return job;
    }
}
