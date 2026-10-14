package com.jobhunter.cli;

import com.jobhunter.model.ApplicationStatus;
import com.jobhunter.model.Job;
import com.jobhunter.storage.JobRepository;

import java.io.PrintStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TrackerCommands {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());
    private static final int DEFAULT_LIMIT = 30;

    private final JobRepository repository;
    private final PrintStream out;

    public TrackerCommands(JobRepository repository, PrintStream out) {
        this.repository = repository;
        this.out = out;
    }

    public int list(CommandLine cmd) {
        ApplicationStatus status = null;
        String statusArg = cmd.option("--status");
        if (statusArg != null) {
            Optional<ApplicationStatus> parsed = ApplicationStatus.parse(statusArg);
            if (parsed.isEmpty()) {
                out.println("Unknown status '" + statusArg + "'. Use one of: " + ApplicationStatus.allKeys());
                return 2;
            }
            status = parsed.get();
        }

        int limit = DEFAULT_LIMIT;
        String limitArg = cmd.option("--limit");
        if (limitArg != null) {
            try {
                limit = Math.max(1, Integer.parseInt(limitArg));
            } catch (NumberFormatException e) {
                out.println("--limit must be a number");
                return 2;
            }
        }

        List<Job> jobs = repository.list(status, limit);
        if (jobs.isEmpty()) {
            out.println(status == null ? "No jobs stored yet." : "No jobs with status '" + status.key() + "'.");
            return 0;
        }

        for (Job job : jobs) {
            out.printf("#%-5d %-10s %5s  %s  %s — %s%n",
                    job.getRowId(),
                    job.getStatus().key(),
                    job.getScore() == null ? "" : "[" + job.getScore() + "]",
                    DATE.format(job.getPostedAt() != null ? job.getPostedAt() : job.getFetchedAt()),
                    job.getTitle(),
                    job.getCompany() == null ? "?" : job.getCompany());
            if (job.getUrl() != null) {
                out.println("       " + job.getUrl());
            }
        }
        return 0;
    }

    public int mark(CommandLine cmd) {
        List<String> args = cmd.positional();
        if (args.size() != 2) {
            out.println("Usage: mark <id> <status>   (statuses: " + ApplicationStatus.allKeys() + ")");
            return 2;
        }

        String ref = args.get(0).startsWith("#") ? args.get(0).substring(1) : args.get(0);
        Optional<ApplicationStatus> status = ApplicationStatus.parse(args.get(1));
        if (status.isEmpty()) {
            out.println("Unknown status '" + args.get(1) + "'. Use one of: " + ApplicationStatus.allKeys());
            return 2;
        }

        Optional<Job> job = repository.find(ref);
        if (job.isEmpty() || !repository.updateStatus(ref, status.get())) {
            out.println("No job found for '" + args.get(0) + "'. Run 'list' to see ids.");
            return 1;
        }

        out.printf("#%d %s — %s is now '%s'%n",
                job.get().getRowId(), job.get().getTitle(), job.get().getCompany(), status.get().key());
        return 0;
    }

    public int stats() {
        out.println("Jobs by status:");
        Map<ApplicationStatus, Integer> byStatus = repository.countByStatus();
        int total = 0;
        for (Map.Entry<ApplicationStatus, Integer> entry : byStatus.entrySet()) {
            total += entry.getValue();
            if (entry.getValue() > 0) {
                out.printf("  %-10s %d%n", entry.getKey().key(), entry.getValue());
            }
        }
        out.printf("  %-10s %d%n", "total", total);

        out.println();
        out.println("Jobs by source:");
        repository.countBySource().forEach((source, count) -> out.printf("  %-24s %d%n", source, count));
        return 0;
    }
}
