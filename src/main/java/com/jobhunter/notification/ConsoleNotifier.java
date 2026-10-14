package com.jobhunter.notification;

import com.jobhunter.model.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ConsoleNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(ConsoleNotifier.class);

    @Override
    public boolean notify(List<Job> jobs) {
        if (jobs.isEmpty()) {
            log.info("No new jobs to report.");
            return true;
        }

        System.out.println("\n========================================");
        System.out.printf("  🔔 %d NEW JOB(S) FOUND!%n", jobs.size());
        System.out.println("========================================\n");

        for (int i = 0; i < jobs.size(); i++) {
            Job job = jobs.get(i);
            System.out.printf("%d. %s%n", i + 1, job.getTitle());
            System.out.printf("   Company:  %s%n", job.getCompany() != null ? job.getCompany() : "N/A");
            System.out.printf("   Location: %s%n", job.getLocation() != null ? job.getLocation() : "N/A");

            if (job.getSalaryMin() != null || job.getSalaryMax() != null) {
                System.out.printf("   Salary:   %s - %s%n",
                        job.getSalaryMin() != null ? String.format("$%.0f", job.getSalaryMin()) : "?",
                        job.getSalaryMax() != null ? String.format("$%.0f", job.getSalaryMax()) : "?");
            }

            if (job.getScore() != null) {
                System.out.printf("   Score:    %d%n", job.getScore());
            }

            System.out.printf("   Link:     %s%n",job.getUrl() != null ? job.getUrl() : "N/A");
            System.out.printf("   Source:   %s%n", job.getSource());
            System.out.println();
        }

        System.out.println("========================================\n");
        return true;
    }
}
