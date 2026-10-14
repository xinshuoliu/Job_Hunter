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

## Configuration

See `config.yml` for all options. Key sections:

| Section | What it does |
|---------|-------------|
| `apis` | API credentials for each provider |
| `search` | What to search for, where, and how often |
| `providers` | Which job sources to enable |
| `boards` | Company career boards and community internship lists to watch |
| `filters` | Keyword matching, exclusions, salary, location |
| `notifications` | Enable/configure Console, Discord, or Email alerts |
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

When a cycle finds nothing new, a short status message is sent at most once every
`notifications.status_interval_hours` (default 24, `0` disables it).

Jobs are deduplicated on normalized title + company, so the same posting reported by two
providers with different location spellings is only announced once. A posting is only marked
as notified once every channel confirms delivery; anything that failed is retried next cycle.

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
