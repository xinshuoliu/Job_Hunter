package com.jobhunter.storage;

import com.jobhunter.model.Job;
import com.jobhunter.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class JobRepository {

    private static final Logger log = LoggerFactory.getLogger(JobRepository.class);

    private static final int SCHEMA_VERSION = 1;

    private final String dbPath;

    public JobRepository(String dbPath) {
        this.dbPath = dbPath;
        initDatabase();
    }

    private void initDatabase() {
        String sql = """
                CREATE TABLE IF NOT EXISTS provider_runs (
                    provider_name TEXT PRIMARY KEY,
                    last_run_at TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS jobs (
                    id TEXT PRIMARY KEY,
                    source TEXT NOT NULL,
                    title TEXT,
                    company TEXT,
                    location TEXT,
                    description TEXT,
                    url TEXT,
                    salary_min REAL,
                    salary_max REAL,
                    currency TEXT,
                    posted_at TEXT,
                    fetched_at TEXT,
                    dedup_key TEXT,
                    notified INTEGER DEFAULT 0
                );
                CREATE INDEX IF NOT EXISTS idx_fetched_at ON jobs(fetched_at);
                """;

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            migrate(conn, stmt);
            log.info("Database initialized at {}", dbPath);
        } catch (SQLException e) {
            log.error("Failed to initialize database: {}", e.getMessage());
            throw new RuntimeException("Database init failed", e);
        }
    }

    private void migrate(Connection conn, Statement stmt) throws SQLException {
        int version;
        try (ResultSet rs = stmt.executeQuery("PRAGMA user_version")) {
            version = rs.next() ? rs.getInt(1) : 0;
        }

        if (version >= SCHEMA_VERSION) {
            return;
        }

        log.info("Migrating database schema from version {} to {}", version, SCHEMA_VERSION);

        boolean autoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            rebuildDedupKeys(conn);

            stmt.executeUpdate("""
                    UPDATE jobs SET notified = 1
                    WHERE notified = 0
                      AND dedup_key IN (SELECT dedup_key FROM jobs WHERE notified = 1)
                    """);

            int removed = stmt.executeUpdate("""
                    DELETE FROM jobs
                    WHERE rowid NOT IN (SELECT MIN(rowid) FROM jobs GROUP BY dedup_key)
                    """);

            stmt.executeUpdate("DROP INDEX IF EXISTS idx_dedup_key");
            stmt.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS idx_dedup_key ON jobs(dedup_key)");
            stmt.executeUpdate("PRAGMA user_version = " + SCHEMA_VERSION);

            conn.commit();
            if (removed > 0) {
                log.info("Migration collapsed {} duplicate job rows", removed);
            }
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(autoCommit);
        }
    }

    private void rebuildDedupKeys(Connection conn) throws SQLException {
        String select = "SELECT id, title, company FROM jobs";
        String update = "UPDATE jobs SET dedup_key = ? WHERE id = ?";

        try (Statement read = conn.createStatement();
             ResultSet rs = read.executeQuery(select);
             PreparedStatement ps = conn.prepareStatement(update)) {

            while (rs.next()) {
                String key = Text.normalize(rs.getString("title")) + "|" + Text.normalize(rs.getString("company"));
                ps.setString(1, key);
                ps.setString(2, rs.getString("id"));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public boolean save(Job job) {
        return !saveAll(List.of(job)).isEmpty();
    }

    public List<Job> saveAll(List<Job> jobs) {
        List<Job> newJobs = new ArrayList<>();

        String sql = """
                INSERT OR IGNORE INTO jobs
                (id, source, title, company, location, description, url,
                 salary_min, salary_max, currency, posted_at, fetched_at, dedup_key)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Job job : jobs) {
                    ps.setString(1, job.getId());
                    ps.setString(2, job.getSource());
                    ps.setString(3, job.getTitle());
                    ps.setString(4, job.getCompany());
                    ps.setString(5, job.getLocation());
                    ps.setString(6, job.getDescription());
                    ps.setString(7, job.getUrl());
                    ps.setObject(8, job.getSalaryMin());
                    ps.setObject(9, job.getSalaryMax());
                    ps.setString(10, job.getCurrency());
                    ps.setString(11, job.getPostedAt() != null ? job.getPostedAt().toString() : null);
                    ps.setString(12, job.getFetchedAt().toString());
                    ps.setString(13, job.deduplicationKey());

                    if (ps.executeUpdate() > 0) {
                        newJobs.add(job);
                        log.info("Saved new job: {}", job);
                    } else {
                        log.debug("Duplicate skipped: {}", job);
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            log.error("Failed to save jobs: {}", e.getMessage());
            return List.of();
        }

        log.info("Saved {} new jobs out of {} total", newJobs.size(), jobs.size());
        return newJobs;
    }

    public List<Job> getUnnotifiedJobs() {
        String sql = "SELECT * FROM jobs WHERE notified = 0 ORDER BY fetched_at DESC";
        List<Job> jobs = new ArrayList<>();

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                jobs.add(mapRow(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to fetch unnotified jobs: {}", e.getMessage());
        }
        return jobs;
    }

    public void markAsNotified(List<Job> jobs) {
        if (jobs.isEmpty()) return;

        String sql = "UPDATE jobs SET notified = 1 WHERE id = ?";

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Job job : jobs) {
                ps.setString(1, job.getId());
                ps.addBatch();
            }
            ps.executeBatch();
            log.info("Marked {} jobs as notified", jobs.size());
        } catch (SQLException e) {
            log.error("Failed to mark jobs as notified: {}", e.getMessage());
        }
    }

    public int pruneOlderThan(int retentionDays) {
        if (retentionDays <= 0) return 0;

        String sql = "DELETE FROM jobs WHERE notified = 1 AND fetched_at < ?";
        String cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS).toString();

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cutoff);
            int removed = ps.executeUpdate();
            if (removed > 0) {
                log.info("Pruned {} jobs older than {} days", removed, retentionDays);
            }
            return removed;
        } catch (SQLException e) {
            log.error("Failed to prune old jobs: {}", e.getMessage());
            return 0;
        }
    }

    public int count() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM jobs")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    private Job mapRow(ResultSet rs) throws SQLException {
        Job job = new Job();
        job.setId(rs.getString("id"));
        job.setSource(rs.getString("source"));
        job.setTitle(rs.getString("title"));
        job.setCompany(rs.getString("company"));
        job.setLocation(rs.getString("location"));
        job.setDescription(rs.getString("description"));
        job.setUrl(rs.getString("url"));
        job.setSalaryMin(rs.getObject("salary_min") != null ? rs.getDouble("salary_min") : null);
        job.setSalaryMax(rs.getObject("salary_max") != null ? rs.getDouble("salary_max") : null);
        job.setCurrency(rs.getString("currency"));
        String postedAt = rs.getString("posted_at");
        if (postedAt != null) job.setPostedAt(Instant.parse(postedAt));
        job.setFetchedAt(Instant.parse(rs.getString("fetched_at")));
        return job;
    }

    public boolean shouldProviderRun(String providerName, int intervalHours) {
        String sql = "SELECT last_run_at FROM provider_runs WHERE provider_name = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, providerName);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return true;
            Instant lastRun = Instant.parse(rs.getString("last_run_at"));
            return Instant.now().isAfter(lastRun.plusSeconds(intervalHours * 3600L));
        } catch (SQLException e) {
            return true;
        }
    }

    public void markProviderRan(String providerName) {
        String sql = "INSERT OR REPLACE INTO provider_runs (provider_name, last_run_at) VALUES (?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, providerName);
            ps.setString(2, Instant.now().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to mark provider run: {}", e.getMessage());
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }
}
