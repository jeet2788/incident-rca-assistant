# Incident RCA Assistant

A Spring Boot service that automatically generates Root Cause Analysis (RCA) reports for production incidents using Kafka, pgvector (RAG), and OpenAI GPT-4.

## Architecture

```
Grafana/CloudWatch Webhook
        │
        ▼
  Kafka (alerts.raw)
        │
        ▼
 AlertKafkaConsumer
        │
        ▼
 RcaPipelineService
   ├── EmbeddingService  ──► pgvector (incident_embeddings)
   ├── RagRetrievalService ◄─ pgvector (incident_embeddings + runbooks)
   ├── RcaGenerationService ──► OpenAI GPT-4
   └── SlackNotificationService ──► Slack
        │
        ▼
 rca_reports (PostgreSQL)
```

## Prerequisites

- Java 17+
- Maven 3.9+
- PostgreSQL 15+ with pgvector extension
- Kafka 3.x
- OpenAI API key
- Slack bot token

## Environment Variables

| Variable | Description |
|----------|-------------|
| `OPENAI_API_KEY` | OpenAI API key |
| `SLACK_BOT_TOKEN` | Slack bot OAuth token |
| `SLACK_ALERT_CHANNEL` | Slack channel (default: `#incidents`) |
| `DB_USERNAME` | PostgreSQL username (default: `postgres`) |
| `DB_PASSWORD` | PostgreSQL password (default: `postgres`) |
| `KAFKA_BROKERS` | Kafka bootstrap servers (default: `localhost:9092`) |

## Database Setup

```sql
-- Run schema.sql against your PostgreSQL instance
psql -U postgres -d rca_db -f src/main/resources/schema.sql
```

## Running Locally

```bash
export OPENAI_API_KEY=sk-...
export SLACK_BOT_TOKEN=xoxb-...

mvn spring-boot:run
```

## API Endpoints

### RCA Reports
| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/rca` | List all RCA reports |
| `GET` | `/api/v1/rca?status=DRAFT` | Filter by status |
| `GET` | `/api/v1/rca/{id}` | Get a specific report |
| `PATCH` | `/api/v1/rca/{id}/feedback` | Submit engineer feedback |
| `PATCH` | `/api/v1/rca/{id}/close` | Close a report |

### Runbooks (Knowledge Base)
| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/runbooks` | List all runbooks |
| `POST` | `/api/v1/runbooks` | Ingest a new runbook |
| `GET` | `/api/v1/runbooks/{id}` | Get a runbook |
| `POST` | `/api/v1/runbooks/{id}/re-embed` | Re-generate embedding |
| `DELETE` | `/api/v1/runbooks/{id}` | Delete a runbook |

## Kafka Topics

| Topic | Direction | Description |
|-------|-----------|-------------|
| `alerts.raw` | Consumed | Incoming alert events |
| `rca.generated` | Produced | Generated RCA reports |

### Alert Event Schema (JSON)

```json
{
  "alertName": "High P99 Latency",
  "service": "order-service",
  "metricName": "p99_latency_ms",
  "threshold": "2000ms",
  "currentValue": "4800ms",
  "severity": "CRITICAL",
  "environment": "production",
  "timestamp": "2024-01-15T10:30:00Z",
  "rawPayload": "{...}"
}
```

Only `CRITICAL` and `HIGH` severity alerts trigger automatic RCA generation.

## How RAG Works

1. Each incoming alert is converted to a natural-language description and embedded via `text-embedding-3-small`.
2. The embedding is used to query `pgvector` for the top-5 most similar past incidents and runbooks using cosine similarity.
3. Retrieved context is injected into the GPT-4 prompt to ground the RCA in real historical data.
4. The generated RCA is stored and the embedding is saved for future retrieval.

## Building

```bash
mvn clean package -DskipTests
java -jar target/incident-rca-assistant-1.0.0.jar
```
