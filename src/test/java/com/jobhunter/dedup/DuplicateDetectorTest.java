package com.jobhunter.dedup;

import com.jobhunter.model.Job;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DuplicateDetectorTest {

    private final DuplicateDetector detector = new DuplicateDetector();

    @ParameterizedTest(name = "{0} @ {1}  ==  {2} @ {3}")
    @DisplayName("Reworded titles at the same company are recognised as one job")
    @CsvSource(delimiter = '|', value = {
            "Software Engineer Intern, Backend (Summer 2027 - Montreal) | Lyft | Software Engineer Intern - Backend Development at Lyft | Lyft, Inc.",
            "Software Engineer Intern, Backend | Lyft | Software Engineer Intern, Backend (Summer 2027) | Lyft",
            "Core Algorithms Software Developer Intern | Kinaxis | Intern Software Developer for Core Algorithms | Kinaxis Inc.",
            "Core Algorithms Software Developer Intern | Kinaxis | Co-op/Intern Software Developer, Core Algorithms | Kinaxis Inc.",
            "Software Developer Intern (Professional Services team), Winter 2027 | Coveo | Software Developer Intern; Professional Services team), Winter | Coveo Solutions Inc.",
            "Intern Software Developer - Montreal - 2027 | Squarepoint Capital LLP | Intern Software Developer - 2027 | Squarepoint Capital",
            "BACK-END WEB DEVELOPER – INTERNSHIP (2026-2027) | Workland | BACK-END WEB DEVELOPER – INTERNSHIP - S | Workland",
            "Internship in Software Development at SAP | SAP | Software Development Internship | SAP SE"
    })
    void matchesRewordedTitles(String titleA, String companyA, String titleB, String companyB) {
        assertTrue(detector.isSameJob(job("a", titleA, companyA), job("b", titleB, companyB)));
    }

    @ParameterizedTest(name = "{0} @ {1}  !=  {2} @ {3}")
    @DisplayName("Different roles, terms or companies stay separate")
    @CsvSource(delimiter = '|', value = {
            "Intern, Software (S-02-08) Fall 2026 | Matrox | Intern, Software (S-02-08) Winter 2027 | Matrox",
            "Intern, Software (S-02-08) Fall 2026 | Matrox | Intern, Software (S-01-16) Fall 2026 | Matrox",
            "Intern, Software Quality Assurance (S-01-04) Fall 2026 | Matrox | Intern, Software Quality Assurance (S-01-03) Fall 2026 | Matrox",
            "Software Design Internship | Ciena | Internship in Software Platform Design | Ciena Corporation",
            "Immersive Software Developer Intern – Montréal (Hybrid) | CAE | Software Developer Intern - CAE Montreal | CAE",
            "Winter 2027 Co-op: AI Developer Intern (4 months) | CGI | Winter 2027 Co-op: AI Developer Intern (8 months) | CGI",
            "Software Developer Intern (Professional Services team), Fall 2026 | Coveo | Software Developer Intern (Professional Services team), Winter 2027 | Coveo",
            "Frontend Developer Intern | Coveo | Backend Developer Intern | Coveo",
            "Software Developer Intern | Coveo | Software Developer Intern | Mirego",
            "Software Engineer Intern | General Dynamics | Software Engineer Intern | General Motors",
            "Machine Learning Intern | Lyft | Data Science Intern - Algorithms | Lyft"
    })
    void keepsDistinctJobsApart(String titleA, String companyA, String titleB, String companyB) {
        assertFalse(detector.isSameJob(job("a", titleA, companyA), job("b", titleB, companyB)));
    }

    @Test
    @DisplayName("Company names match on their leading words after dropping legal suffixes")
    void comparesCompanies() {
        assertTrue(DuplicateDetector.sameCompany("Bombardier", "Bombardier Transportation GmbH"));
        assertTrue(DuplicateDetector.sameCompany("0000050007 Royal Bank of Canada", "Royal Bank of Canada"));
        assertTrue(DuplicateDetector.sameCompany("Grass-Valley", "Grass Valley"));
        assertFalse(DuplicateDetector.sameCompany("General Dynamics", "General Motors"));
    }

    @Test
    @DisplayName("The index only compares jobs within the same company")
    void indexFindsWithinCompany() {
        Job stored = job("lyft-1", "Software Engineer Intern, Backend", "Lyft");
        DuplicateDetector.Index index = detector.index(List.of(
                stored, job("coveo-1", "Software Engineer Intern, Backend", "Coveo")));

        assertSame(stored, index.find(job("new", "Software Engineer Intern - Backend (Summer 2027)", "Lyft, Inc.")).orElseThrow());
        assertTrue(index.find(job("new", "Software Engineer Intern, Backend", "Shopify")).isEmpty());
    }

    private static Job job(String id, String title, String company) {
        Job job = new Job();
        job.setId(id);
        job.setTitle(title);
        job.setCompany(company);
        return job;
    }
}
