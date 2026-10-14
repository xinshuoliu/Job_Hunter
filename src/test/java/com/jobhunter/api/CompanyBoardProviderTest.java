package com.jobhunter.api;

import com.jobhunter.model.Job;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CompanyBoardProviderTest {

    @Test
    @DisplayName("Greenhouse postings are parsed with clean titles and plain-text descriptions")
    void parsesGreenhouse() {
        GreenhouseProvider provider = new GreenhouseProvider(Map.of("workleap", ""));

        List<Job> jobs = provider.parse(Fixtures.read("greenhouse.json"), "workleap", "");

        assertEquals(2, jobs.size());
        Job intern = jobs.get(0);
        assertEquals("greenhouse-workleap-5196800007", intern.getId());
        assertEquals("greenhouse", intern.getSource());
        assertEquals("Software Developer Intern - Winter 2027", intern.getTitle());
        assertEquals("Workleap", intern.getCompany());
        assertEquals("Montreal, Quebec, Canada", intern.getLocation());
        assertEquals("About Build Java & React services.", intern.getDescription());
        assertEquals(Instant.parse("2026-08-26T13:18:22Z"), intern.getPostedAt());
        assertEquals("https://job-boards.greenhouse.io/workleap/jobs/5196800007", intern.getUrl());

        assertEquals(Instant.parse("2026-10-01T12:00:00Z"), jobs.get(1).getPostedAt());
    }

    @Test
    @DisplayName("A configured display name overrides the Greenhouse company name")
    void greenhouseDisplayNameWins() {
        GreenhouseProvider provider = new GreenhouseProvider(Map.of("workleap", "Workleap Inc"));

        List<Job> jobs = provider.parse(Fixtures.read("greenhouse.json"), "workleap", "Workleap Inc");

        assertEquals("Workleap Inc", jobs.get(0).getCompany());
    }

    @Test
    @DisplayName("Lever postings carry all locations, remote flag and yearly salary only")
    void parsesLever() {
        LeverProvider provider = new LeverProvider(Map.of("mirego", ""));

        List<Job> jobs = provider.parse(Fixtures.read("lever.json"), "mirego", "");

        assertEquals(2, jobs.size());
        Job stage = jobs.get(0);
        assertEquals("lever-mirego-a2e76a1c-fda4-4ede-b99f-95b63e2bc785", stage.getId());
        assertEquals("Stagiaire en développement logiciel", stage.getTitle());
        assertEquals("Mirego", stage.getCompany());
        assertEquals("Montréal; Québec", stage.getLocation());
        assertEquals(45000.0, stage.getSalaryMin());
        assertEquals(55000.0, stage.getSalaryMax());
        assertEquals("CAD", stage.getCurrency());
        assertEquals(Instant.ofEpochMilli(1791220218982L), stage.getPostedAt());

        Job hourly = jobs.get(1);
        assertEquals("Toronto; Remote", hourly.getLocation());
        assertNull(hourly.getSalaryMax(), "hourly wages must not be compared against a yearly minimum");
    }

    @Test
    @DisplayName("Ashby skips unlisted postings and includes secondary and remote locations")
    void parsesAshby() {
        AshbyProvider provider = new AshbyProvider(Map.of("valence", "Valence Labs"));

        List<Job> jobs = provider.parse(Fixtures.read("ashby.json"), "valence", "Valence Labs");

        assertEquals(3, jobs.size());
        Job ml = jobs.get(0);
        assertEquals("ashby-valence-37c6d60e-1bd3-4b11-a8e4-25d06df752fc", ml.getId());
        assertEquals("Valence Labs", ml.getCompany());
        assertEquals("Montreal; Toronto", ml.getLocation());
        assertEquals("Train models with PyTorch.", ml.getDescription());
        assertEquals(Instant.parse("2026-04-28T19:01:33.578Z"), ml.getPostedAt());

        assertEquals("Canada; Remote", jobs.get(1).getLocation());
        assertEquals("Montreal", jobs.get(2).getLocation(), "null fields must not break parsing");
    }

    @Test
    @DisplayName("Without a display name the slug is turned into a company name")
    void slugFallsBackToTitleCase() {
        assertEquals("Valence Labs", CompanyBoardProvider.companyName("", null, "valence-labs"));
        assertEquals("From Api", CompanyBoardProvider.companyName("", " From Api ", "slug"));
        assertEquals("Configured", CompanyBoardProvider.companyName("Configured", "From Api", "slug"));
    }
}
