# 🔍 Job Hunter

A multi-source job posting aggregator that fetches listings from multiple APIs, filters by your criteria, deduplicates across sources, and notifies you instantly via Discord, email, or console.

![CI](https://github.com/xinshuoliu/Job_Hunter/actions/workflows/ci.yml/badge.svg)

## Architecture

```
┌─────────────┐  ┌──────────────┐
│ Adzuna,     │  │ Greenhouse,  │   ← Providers (add more by implementing JobProvider)
│ JSearch ... │  │ Lever, Ashby,│
│ (search)    │  │ Simplify     │
└──────┬──────┘  └──────┬───────┘
       │                │
       └───────┬────────┘
               ▼
        ┌──────────────┐
        │  JobFilter   │   ← Keywords, location, salary, exclusions
        └──────┬───────┘
               ▼
        ┌──────────────┐
        │ JobRepository│   ← SQLite storage + deduplication
        └──────┬───────┘
               ▼
     ┌─────────┼──────────┐
     ▼         ▼          ▼
 ┌────────┐ ┌────────┐ ┌────────┐
 │Console │ │Discord │ │ Email  │   ← Notification channels
 └────────┘ └────────┘ └────────┘
```

## Quick Start

### Prerequisites
- Java 21+
- Maven 3.8+
- (Optional) Docker

### 1. Clone & Configure

```bash
git clone https://github.com/xinshuoliu/Job_Hunter.git
cd job-hunter
cp config.example.yml config.yml
```

Edit `config.yml` with your API keys and preferences. `config.yml` is gitignored.

Any string value may reference an environment variable with `${VAR}` or `${VAR:-default}`,
so credentials can stay out of the file entirely:

```yaml
apis:
  adzuna:
    app_key: "${JOBHUNTER_ADZUNA_APP_KEY}"
```

### 2. Get an Adzuna API Key (Free)

1. Go to [developer.adzuna.com](https://developer.adzuna.com/)
2. Sign up and create an application
3. Copy your `app_id` and `app_key` into the config

### 3. Run

```bash
# With Maven
mvn clean package
java -jar target/job-hunter-1.0-SNAPSHOT.jar config.yml

# With Docker
docker compose up --build
```

The process runs continuously, polling every `search.poll_interval_minutes`. Each provider
additionally has its own minimum interval, tracked in SQLite, so restarts never re-hammer the
APIs. The image does not bake in `config.yml` — mount it (docker compose already does).

To run from cron, Windows Task Scheduler or a CI job instead, do a single cycle and exit:

```bash
java -jar target/job-hunter-1.0-SNAPSHOT.jar --config config.yml once
```

### 4. Track applications

Every stored job has an application status. The CLI reads the same database:

```bash
java -jar target/job-hunter-1.0-SNAPSHOT.jar list                    # newest 30 jobs
java -jar target/job-hunter-1.0-SNAPSHOT.jar list --status applied   # filter by status
java -jar target/job-hunter-1.0-SNAPSHOT.jar mark 42 applied         # 42 = number shown by list
java -jar target/job-hunter-1.0-SNAPSHOT.jar stats
```

Statuses are `new`, `interested`, `applied`, `interview`, `offer`, `rejected` and `ignored`.
Jobs marked `interested`, `applied`, `interview` or `offer` are never pruned by `retention_days`.

## Configuration

See `config.yml` for all options. Key sections:

| Section | What it does |
|---------|-------------|
| `apis` | API credentials for each provider |
| `search` | What to search for, where, and how often |
| `providers` | Which job sources to enable |
| `boards` | Company career boards and community internship lists to watch |
| `filters` | Keyword matching, exclusions, salary, location, posting age |
| `scoring` | Keyword weights that rank matches and an optional `min_score` cutoff |
| `dedup` | How far back to look for reworded reposts of the same job |
| `notifications` | Instant or daily-digest delivery to Console, Discord, or Email |
| `database` | SQLite path and `retention_days` for pruning old postings |

### Company boards and internship lists

Besides the search APIs, Job Hunter can read company career boards directly. These public
endpoints need no API key and often list postings before the aggregators pick them up:

```yaml
boards:
  greenhouse: [workleap]
  lever: [mirego, plusgrade]
  ashby:
    - cohere
    - valence: "Valence Labs"
```

The slug is the company's segment in its careers URL (`job-boards.greenhouse.io/<slug>`,
`jobs.lever.co/<slug>`, `jobs.ashbyhq.com/<slug>`). An entry can optionally map the slug to a
display name, which keeps cross-source deduplication accurate when the slug differs from the
company name other providers report.

The `simplify` provider reads the community-maintained
[SimplifyJobs](https://github.com/SimplifyJobs) internship list. Only active listings posted in
the last `max_age_days` days are considered, and your normal filters still apply.

Unlike the search APIs, boards and the Simplify list are not scoped to `search.location`, so a
broad `filters.locations` entry such as `Canada` or `Remote` would let through postings from
across the country or the US. `boards.locations` narrows only these sources before the regular
filters run:

```yaml
boards:
  locations: ["Montreal", "Remote in Canada", "Canada - Remote"]
```

Keywords and locations match whole words, ignoring case and accents, so `intern` matches
"Software Intern" but not "Internal Tools", and `Montreal` matches "Montréal, QC". Multi-word
terms such as `co-op` or `new grad` work too. `required_keywords` and, by default,
`exclude_keywords` only look at the job title; set `exclude_scope: "all"` to also scan the
description and company.

### Cutting noise

| Option | Effect |
|--------|--------|
| `filters.max_age_days` | Drop postings published more than N days ago (undated postings pass) |
| `filters.reject_past_years` | Drop titles that only mention past years, e.g. "Summer 2025" (default `true`) |
| `filters.exclude_locations` | Drop jobs whose **title** names one of these places, unless it also names one of your `locations`. Catches aggregators that tag an Ottawa job as "Montreal" |
| `filters.exclude_companies` | Drop reposts from job boards and other unwanted employers |

### Ranking with scores

`scoring.weights` assigns points to terms. A term found in the title earns its full weight; one
found only in the description earns half. Negative weights push unwanted roles down, and jobs
scoring below `scoring.min_score` are dropped. Notifications list the best matches first and show
each score. Leave `weights` empty to turn scoring off.

```yaml
scoring:
  min_score: 0
  weights: { "java": 3, "backend": 2, "technical writer": -10 }
```

### Duplicates

The same posting often appears on several sources, and reposts are reworded. Two jobs are
treated as one when their companies match after dropping legal suffixes ("Kinaxis Inc." =
"Kinaxis") and their titles share at least 80% of their meaningful words, ignoring seasons,
locations, work mode and the company name. Titles naming different terms ("Fall 2026" vs
"Winter 2027") or different requisition numbers always stay separate. New postings are compared
with everything stored in the last `dedup.window_days` days (default 120).

### Delivery

With `notifications.mode: "instant"` (the default) new jobs are sent as soon as they are found.
With `mode: "digest"`, jobs are collected and sent once a day, in one message, on the first cycle
after `digest_hour` (local time, default 18).

When there is nothing new, a short status message is sent at most once every
`notifications.status_interval_hours` (default 24, `0` disables it). A posting is only marked as
notified once every channel confirms delivery; anything that failed is retried next cycle.

Upgrading from an older version migrates `jobs.db` automatically; a copy of the previous file
is kept next to it as `jobs.db.v<old-version>.bak`.

## Running Tests

```bash
mvn test
```

## Adding a New Job Source

1. Create a class implementing `JobProvider` in `src/main/java/com/jobhunter/api/`
2. Implement `getName()` and `fetchJobs()`
3. Register it in `App.buildProviders()` and add a toggle under `providers:` in the config

That's it — the filter, storage, and notification layers handle the rest.

## Tech Stack

- **Java 21** — Language
- **OkHttp** — HTTP client for API calls
- **Gson** — JSON parsing
- **SQLite** — Local persistence
- **JUnit 5 + Mockito** — Testing
- **Docker** — Containerization
- **GitHub Actions** — CI/CD

## License

MIT
