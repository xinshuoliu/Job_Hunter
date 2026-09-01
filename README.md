# 🔍 Job Hunter

A multi-source job posting aggregator that fetches listings from multiple APIs, filters by your criteria, deduplicates across sources, and notifies you instantly via Discord, email, or console.

![CI](https://github.com/xinshuoliu/Job_Hunter/actions/workflows/ci.yml/badge.svg)

## Architecture

```
┌─────────────┐  ┌──────────────┐
│   Adzuna    │  │  Arbeitnow   │   ← API Providers (add more by implementing JobProvider)
│   API       │  │  API         │
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
| `filters` | Keyword matching, exclusions, salary, location |
| `notifications` | Enable/configure Console, Discord, or Email alerts |
| `database` | SQLite path and `retention_days` for pruning old postings |

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
