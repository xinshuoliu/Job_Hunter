package com.jobhunter.api;

import com.jobhunter.model.Job;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JobDeduplicationTest {

    @Test
    @DisplayName("Same title/company/location produces same dedup key")
    void sameDedupKey() {
        Job job1 = new Job();
        job1.setTitle("Java Developer");
        job1.setCompany("Acme Corp");
        job1.setLocation("Montreal");

        Job job2 = new Job();
        job2.setTitle("Java Developer");
        job2.setCompany("Acme Corp");
        job2.setLocation("Montreal");

        assertEquals(job1.deduplicationKey(), job2.deduplicationKey());
    }

    @Test
    @DisplayName("Dedup key is case-insensitive")
    void caseInsensitiveDedupKey() {
        Job job1 = new Job();
        job1.setTitle("Java Developer");
        job1.setCompany("ACME CORP");
        job1.setLocation("MONTREAL");

        Job job2 = new Job();
        job2.setTitle("java developer");
        job2.setCompany("acme corp");
        job2.setLocation("montreal");

        assertEquals(job1.deduplicationKey(), job2.deduplicationKey());
    }

    @Test
    @DisplayName("Providers spelling the same city differently share a dedup key")
    void locationSpellingDoesNotAffectDedupKey() {
        Job fromJSearch = new Job();
        fromJSearch.setTitle("Software Developer Intern");
        fromJSearch.setCompany("Coveo");
        fromJSearch.setLocation("Montreal, CA");

        Job fromAdzuna = new Job();
        fromAdzuna.setTitle("Software Developer Intern");
        fromAdzuna.setCompany("Coveo");
        fromAdzuna.setLocation("Montréal, Québec");

        assertEquals(fromJSearch.deduplicationKey(), fromAdzuna.deduplicationKey());
    }

    @Test
    @DisplayName("Accents and punctuation are ignored in the dedup key")
    void accentsAndPunctuationAreIgnored() {
        Job accented = new Job();
        accented.setTitle("Stagiaire en ingénierie");
        accented.setCompany("Société Générale");

        Job plain = new Job();
        plain.setTitle("stagiaire en ingenierie");
        plain.setCompany("societe generale");

        assertEquals(accented.deduplicationKey(), plain.deduplicationKey());
    }

    @Test
    @DisplayName("Different jobs produce different dedup keys")
    void differentDedupKey() {
        Job job1 = new Job();
        job1.setTitle("Java Developer");
        job1.setCompany("Acme");
        job1.setLocation("Montreal");

        Job job2 = new Job();
        job2.setTitle("Python Developer");
        job2.setCompany("Acme");
        job2.setLocation("Montreal");

        assertNotEquals(job1.deduplicationKey(), job2.deduplicationKey());
    }

    @Test
    @DisplayName("Different companies produce different dedup keys")
    void differentCompanyDedupKey() {
        Job job1 = new Job();
        job1.setTitle("Software Developer Intern");
        job1.setCompany("Coveo");

        Job job2 = new Job();
        job2.setTitle("Software Developer Intern");
        job2.setCompany("Shopify");

        assertNotEquals(job1.deduplicationKey(), job2.deduplicationKey());
    }

    @Test
    @DisplayName("Null fields don't break dedup key")
    void nullFieldsDedupKey() {
        Job job = new Job();
        job.setTitle("Developer");

        assertDoesNotThrow(() -> job.deduplicationKey());
        assertNotNull(job.deduplicationKey());
    }
}
