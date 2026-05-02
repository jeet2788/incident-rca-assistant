
Incident RCA Assistant - Detailed Architecture Diagrams
1. System Architecture Overview
┌─────────────────────────────────────────────────────────────────────────────┐
│                         INCIDENT RCA ASSISTANT                              │
│                          Spring Boot 3.2 + Java 17                          │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────┐          ┌──────────────────────┐      ┌─────────────┐
│ Grafana / CloudWatch│          │  Alert Management    │      │  Slack Bot  │
│      Webhooks       │          │    Dashboard API     │      │ Notifications
└──────────┬──────────┘          └──────────┬───────────┘      └──────┬──────┘
           │                                │                         │
           │ Send Alerts                    │ REST API                 │ Push
           ▼                                ▼                         ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                              │
│                          KAFKA CLUSTER (3.x)                               │
│                                                                              │
│  ┌─────────────────┐                                  ┌──────────────────┐ │
│  │  alerts.raw     │ (High/Critical Only)             │ rca.generated    │ │
│  │  Topic (Incoming)◄──────────────────────────────────Topic (Outgoing)  │ │
│  └─────────────────┘                                  └──────────────────┘ │
│         ▲                                                      ▲             │
│         │                                                      │             │
│         │ Concurrency: 3 Partitions                           │             │
│         │ Consumer Group: rca-consumer-group                  │             │
│         │                                                      │             │
└─────────────────────────────────────────────────────────────────────────────┘
           │                                                      │
           │ Consume & Filter                                    │ Produce
           ▼                                                      │
┌─────────────────────────────────────────────────────────────────┼──────────┐
│                    SPRING BOOT APPLICATION                      │          │
│                        (Port 8080)                              │          │
│                                                                 │          │
│  ┌──────────────────────────────────────────────────────────┐  │          │
│  │ Alert Kafka Consumer                                     │  │          │
│  │ • Validates severity (CRITICAL/HIGH)                    │  │          │
│  │ • Deserializes AlertEvent                               │  │          │
│  │ • Initiates RCA Pipeline                                │  │          │
│  └──────────────────┬───────────────────────────────────────┘  │          │
│                    │                                            │          │
│                    ▼                                            │          │
│  ┌──────────────────────────────────────────────────────────┐  │          │
│  │ RCA PIPELINE SERVICE (Orchestrator)                      │  │          │
│  │ ┌────────────────────────────────────────────────────┐   │  │          │
│  │ │ Step 1: Persist Alert (alerts table)              │   │  │          │
│  │ └────────────────────────────────────────────────────┘   │  │          │
│  │                   │                                       │  │          │
│  │ ┌────────────────▼────────────────────────────────────┐   │  │          │
│  │ │ Step 2: Generate & Store Embedding                │   │  │          │
│  │ │ • Embedding Service (OpenAI text-embedding-3-small)   │  │          │
│  │ │ • Vector Dim: 1536                                │   │  │          │
│  │ │ • Stores in incident_embeddings table             │   │  │          │
│  │ └────────────────┬───────────────────────────────────┘   │  │          │
│  │                 │                                         │  │          │
│  │ ┌───────────────▼───────────────────────────────────┐    │  │          │
│  │ │ Step 3: RAG Retrieval                             │    │  │          │
│  │ │ • Query pgvector (IVFFlat index)                  │    │  │          │
│  │ │ • Cosine similarity search                        │    │  │          │
│  │ │ • Retrieve Top-K=5 similar incidents & runbooks  │    │  │          │
│  │ │ • Apply threshold: 0.75                           │    │  │          │
│  │ └───────────────┬───────────────────────────────────┘    │  │          │
│  │                 │                                         │  │          │
│  │ ┌───────────────▼───────────────────────────────────┐    │  │          │
│  │ │ Step 4: Generate RCA via LLM                      │    │  │          │
│  │ │ • OpenAI GPT-4-Turbo-Preview                      │    │  │          │
│  │ │ • System Prompt: SRE Expert Role                  │    │  │          │
│  │ │ • User Prompt: Alert + RAG Context               │    │  │          │
│  │ │ • Max Tokens: 2000                                │    │  │          │
│  │ │ • Parses JSON: root_cause, impact, timeline, etc. │    │  │          │
│  │ └───────────────┬───────────────────────────────────┘    │  │          │
│  │                 │                                         │  │          │
│  │ ┌───────────────▼───────────────────────────────────┐    │  │          │
│  │ │ Step 5: Persist RCA Report                        │    │  │          │
│  │ │ • Stores in rca_reports table                     │    │  │          │
│  │ │ • Captures sources_used (runbooks/incidents)      │    │  │          │
│  │ │ • Sets status: DRAFT                              │    │  │          │
│  │ └───────────────┬───────────────────────────────────┘    │  │          │
│  │                 │                                         │  │          │
│  │ ┌───────────────▼───────────────────────────────────┐    │  │          │
│  │ │ Step 6: Notify Slack & Publish to Kafka           │    │  │          │
│  │ │ • Posts report to configured channel              │    │  │          │
│  │ │ • Publishes to rca.generated topic ───────────────┼──┼──┘           │
│  │ │ • Marks alert as RESOLVED                         │    │             │
│  │ └───────────────────────────────────────────────────┘    │             │
│  └──────────────────────────────────────────────────────────┘             │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
           │                              │
           │ Database Operations          │
           ▼                              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    PostgreSQL 15+ with pgvector                             │
│                                                                              │
│  Database: rca_db                                                            │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │ alerts (Staging)                     │ incident_embeddings (RAG)     │   │
│  │ • id (UUID)                          │ • id (UUID)                   │   │
│  │ • alert_name, service, metric        │ • alert_id (FK)               │   │
│  │ • severity, threshold, current_val   │ • alert_text                  │   │
│  │ • status (PENDING/PROCESSING/RES)    │ • embedding (vector[1536])    │   │
│  │ • raw_payload (JSONB)                │ • service, severity           │   │
│  │ • created_at, updated_at             │ • rca_summary, feedback_score │   │
│  │                                      │ • idx_incident_embedding:     │   │
│  │ Indexes:                             │   IVFFlat(lists=100)          │   │
│  │ • idx_alerts_service                 │                               │   │
│  │ • idx_alerts_status                  │                               │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │ runbooks (Knowledge Base)               │ rca_reports (Output)       │   │
│  │ • id (UUID)                            │ • id (UUID)                │   │
│  │ • title, service, content              │ • alert_id (FK)            │   │
│  │ • embedding (vector[1536])             │ • root_cause, impact       │   │
│  │ • tags (TEXT[])                        │ • timeline, fix_applied    │   │
│  │ • created_at, updated_at               │ • prevention               │   │
│  │                                        │ • full_report, model_used  │   │
│  │ Indexes:                               │ • tokens_used              │   │
│  │ • idx_runbook_embedding:               │ • sources_used (JSONB)     │   │
│  │   IVFFlat(lists=100)                   │ • engineer_feedback, score │   │
│  │                                        │ • status (DRAFT/PUB/CLOSED)│   │
│  │                                        │ • slack_ts, created_at     │   │
│  │                                        │                            │   │
│  │                                        │ Indexes:                   │   │
│  │                                        │ • idx_rca_alert            │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
           │
           └── Connection String: jdbc:postgresql://localhost:5432/rca_db
               User: ${DB_USERNAME:postgres}
               Pass: ${DB_PASSWORD:postgres}


┌─────────────────────────────────────────────────────────────────────────────┐
│                          REST API ENDPOINTS                                 │
│                                                                              │
│  RCA Reports:                                                                │
│  • GET    /api/v1/rca              → List all RCA reports                   │
│  • GET    /api/v1/rca?status=DRAFT → Filter by status                       │
│  • GET    /api/v1/rca/{id}         → Get specific report                    │
│  • PATCH  /api/v1/rca/{id}/feedback → Submit engineer feedback              │
│  • PATCH  /api/v1/rca/{id}/close   → Close report                           │
│                                                                              │
│  Runbooks (Knowledge Base):                                                  │
│  • GET    /api/v1/runbooks         → List all runbooks                      │
│  • POST   /api/v1/runbooks         → Ingest new runbook                     │
│  • GET    /api/v1/runbooks/{id}    → Get runbook details                    │
│  • POST   /api/v1/runbooks/{id}/re-embed → Regenerate embedding             │
│  • DELETE /api/v1/runbooks/{id}    → Delete runbook                         │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                       EXTERNAL INTEGRATIONS                                 │
│                                                                              │
│  OpenAI API                       Slack API                                  │
│  • Base URL: api.openai.com/v1    • Bot Token: ${SLACK_BOT_TOKEN}           │
│  • Embedding Model:               • Channel: ${SLACK_ALERT_CHANNEL}         │
│    text-embedding-3-small         • Posts RCA reports in real-time          │
│  • Chat Model: gpt-4-turbo-preview│                                         │
│  • Max Tokens: 2000               │                                         │
│                                   │                                         │
└─────────────────────────────────────────────────────────────────────────────┘
2. Data Flow Diagram
ALERT INGESTION & RCA GENERATION FLOW
═════════════════════════════════════════════════════════════════════════════

┌─────────────────────────────────────────────────────────────────────────────┐
│                         STEP 1: ALERT INGESTION                             │
└─────────────────────────────────────────────────────────────────────────────┘

  Grafana/CloudWatch
  ↓ Alert JSON
  ┌───────────────────────────────┐
  │ {                             │
  │   "alertName": "High P99",    │
  │   "service": "order-service", │
  │   "metricName": "p99_latency" │
  │   "threshold": "2000ms",      │
  │   "currentValue": "4800ms",   │
  │   "severity": "CRITICAL",     │
  │   "environment": "production" │
  │ }                             │
  └───────────────────────────────┘
  ↓ Publish to Kafka
  ┌─────────────────────────────────────┐
  │ Kafka Topic: alerts.raw             │
  │ Partition: 0-2 (Concurrency: 3)     │
  └─────────────────────────────────────┘
  ↓ Consume & Validate
  ┌─────────────────────────────────────┐
  │ AlertKafkaConsumer                  │
  │ ✓ Deserialize AlertEvent            │
  │ ✓ Filter: CRITICAL or HIGH only     │
  │ ✓ Drop LOW/MEDIUM severity          │
  └─────────────────────────────────────┘
  ↓ Pass to Pipeline
  ┌─────────────────────────────────────┐
  │ Alert Status: PENDING → PROCESSING  │
  │ Persisted to alerts table           │
  └─────────────────────────────────────┘


┌─────────────────────────────────────────────────────────────────────────────┐
│                 STEP 2: EMBEDDING GENERATION & STORAGE                      │
└─────────────────────────────────────────────────────────────────────────────┘

  Alert Record (In PostgreSQL)
  ↓
  ┌─────────────────────────────────────┐
  │ EmbeddingService.buildAlertText()   │
  │ Converts Alert to Natural Language: │
  │ "High P99 latency in order-service" │
  │ "P99: 4800ms (threshold: 2000ms)"   │
  │ "Environment: production"            │
  └─────────────────────────────────────┘
  ↓ Call OpenAI Embedding API
  ┌─────────────────────────────────────┐
  │ OpenAI text-embedding-3-small       │
  │ Input: Alert text description       │
  │ Output: Vector[1536] dimensions     │
  │ Cost: ~$0.02 per 1M tokens          │
  └─────────────────────────────────────┘
  ↓ Store Embedding
  ┌──────────────────────────────────────────┐
  │ incident_embeddings Table                │
  │ • embedding: vector[1536]                │
  │ • service: "order-service"               │
  │ • alert_text: "High P99 latency..."      │
  │ • alert_id: FK to alerts table           │
  │ • Indexed with IVFFlat (lists=100)       │
  └──────────────────────────────────────────┘


┌─────────────────────────────────────────────────────────────────────────────┐
│             STEP 3: RAG RETRIEVAL (Context Augmented Generation)             │
└─────────────────────────────────────────────────────────────────────────────┘

  Generated Embedding (vector[1536])
  ↓
  ┌───────────────────────────────────────────────────────────┐
  │ RagRetrievalService.retrieve(embedding, service)          │
  │ Performs VECTOR SIMILARITY SEARCH on 2 tables:            │
  │                                                            │
  │ Query 1: incident_embeddings                             │
  │ ├─ Cosine Similarity: <embedding, stored_embedding>      │
  │ ├─ Filter: service = "order-service"                     │
  │ ├─ Limit: Top-5 most similar incidents                   │
  │ └─ Threshold: 0.75                                       │
  │                                                            │
  │ Query 2: runbooks                                        │
  │ ├─ Cosine Similarity: <embedding, runbook_embedding>     │
  │ ├─ Filter: (optional) service = "order-service"          │
  │ ├─ Limit: Top-5 most similar runbooks                    │
  │ └─ Threshold: 0.75                                       │
  └───────────────────────────────────────────────────────────┘
  ↓ Retrieved Context
  ┌────────────────────────────────────────────────────────┐
  │ RetrievalResult Object:                                │
  │ • past_incident_ids: [id1, id2, id3, ...]             │
  │ • past_incident_summaries: [RCA summaries]            │
  │ • runbook_ids: [rb1, rb2, rb3, ...]                   │
  │ • runbook_content: [procedures, solutions]            │
  │ • context: Formatted string for LLM prompt            │
  │   "Similar incident on 2024-01-10:                    │
  │    Root cause was connection pool exhaustion.         │
  │    Runbook: Scale up database replicas..."            │
  └────────────────────────────────────────────────────────┘


┌─────────────────────────────────────────────────────────────────────────────┐
│           STEP 4: RCA GENERATION VIA GPT-4 (LLM Inference)                   │
└─────────────────────────────────────────────────────────────────────────────┘

  ┌──────────────────────────────────────────────────────────────┐
  │ OpenAI GPT-4-Turbo-Preview                                   │
  │                                                               │
  │ SYSTEM PROMPT:                                               │
  │ "You are an expert SRE specializing in RCA. Generate a      │
  │  structured report with: root_cause, impact, timeline,      │
  │  fix_applied, prevention. Respond in JSON."                 │
  │                                                               │
  │ USER PROMPT:                                                 │
  │ "## CURRENT ALERT                                            │
  │  Service: order-service                                      │
  │  Alert: High P99 Latency                                     │
  │  Severity: CRITICAL                                          │
  │                                                               │
  │  ## CONTEXT FROM KNOWLEDGE BASE                              │
  │  [Retrieved incidents and runbooks]                          │
  │                                                               │
  │  Generate RCA in JSON format."                              │
  │                                                               │
  │ Configuration:                                               │
  │ • Max Tokens: 2000                                           │
  │ • Temperature: Default (0.7)                                 │
  │ • Cost: ~$0.03 per 1M input + ~$0.06 per 1M output tokens  │
  └──────────────────────────────────────────────────────────────┘
  ↓ Response
  ┌──────────────────────────────────────────────────────────────┐
  │ LLM JSON Response:                                           │
  │ {                                                            │
  │   "rootCause": "Connection pool exhaustion due to leak",    │
  │   "impact": "50% of requests timing out, SLA violated",     │
  │   "timeline": "14:30 - pool created, 14:45 - leak noticed",│
  │   "fixApplied": "Restart service, add connection limit",    │
  │   "prevention": "Implement pool monitoring, add tests"      │
  │ }                                                            │
  └──────────────────────────────────────────────────────────────┘


┌─────────────────────────────────────────────────────────────────────────────┐
│               STEP 5: PERSIST RCA REPORT & MARK COMPLETE                     │
└─────────────────────────────────────────────────────────────────────────────┘

  RCA Generation Result
  ↓ Parse & Structure
  ┌──────────────────────────────────────────────────────────┐
  │ RcaReport Entity:                                         │
  │ • id: UUID                                               │
  │ • alert_id: FK to alerts                                 │
  │ • root_cause: "Connection pool exhaustion..."            │
  │ • impact: "50% of requests timing out..."                │
  │ • timeline: "Timeline of events..."                      │
  │ • fix_applied: "Restart service..."                      │
  │ • prevention: "Implement pool monitoring..."             │
  │ • full_report: Formatted markdown version                │
  │ • model_used: "gpt-4-turbo-preview"                      │
  │ • tokens_used: 1250                                      │
  │ • sources_used: {                                         │
  │     "incidents": ["id1", "id2"],                         │
  │     "runbooks": ["rb1", "rb2"]                           │
  │   }                                                       │
  │ • status: "DRAFT"                                        │
  │ • created_at: timestamp                                  │
  └──────────────────────────────────────────────────────────┘
  ↓ Store in PostgreSQL
  ┌──────────────────────────────────────────────────────────┐
  │ rca_reports Table                                         │
  └──────────────────────────────────────────────────────────┘
  ↓ Update Alert Status
  ┌──────────────────────────────────────────────────────────┐
  │ alerts.status: PROCESSING → RESOLVED                     │
  │ Stored in alerts table                                   │
  └──────────────────────────────────────────────────────────┘


┌─────────────────────────────────────────────────────────────────────────────┐
│        STEP 6: NOTIFY SLACK & PUBLISH RESULT TO KAFKA                        │
└─────────────────────────────────────────────────────────────────────────────┘

  Completed RCA Report
  ├─→ Slack Notification
  │   ├─ Post to configured channel (default: #incidents)
  │   ├─ Format: Formatted message with RCA sections
  │   ├─ Slack Timestamp stored in rca_reports.slack_ts
  │   └─ Engineers can reply/react for feedback
  │
  └─→ Kafka Publication
      ├─ Topic: rca.generated
      ├─ Message: Structured RCA event
      ├─ Partition: Based on service name (key)
      └─ Available for downstream systems (dashboards, webhooks)

  Example Slack Message:
  ┌────────────────────────────────────────────┐
  │ 🚨 *RCA Generated: High P99 Latency*       │
  │                                            │
  │ Service: order-service                     │
  │ Severity: CRITICAL                         │
  │                                            │
  │ *Root Cause:*                              │
  │ Connection pool exhaustion                 │
  │                                            │
  │ *Impact:*                                  │
  │ 50% of requests timing out                 │
  │                                            │
  │ *Fix Applied:*                             │
  │ Restart service, add connection limit      │
  │                                            │
  │ [View Full Report] [Provide Feedback]      │
  └────────────────────────────────────────────┘
3. Component Interaction Diagram
╔═════════════════════════════════════════════════════════════════════════════╗
║                      COMPONENT INTERACTION MATRIX                           ║
╚═════════════════════════════════════════════════════════════════════════════╝

┌─────────────────────────────────────────────────────────────────────────────┐
│                          ENTRY POINT                                         │
└─────────────────────────────────────────────────────────────────────────────┘

   AlertKafkaConsumer (Kafka Listener)
   │
   ├─ Dependencies:
   │  ├─ ObjectMapper (JSON deserialization)
   │  └─ RcaPipelineService (orchestration)
   │
   └─ Responsibilities:
      ├─ @KafkaListener on alerts.raw topic
      ├─ Filter by severity (CRITICAL | HIGH)
      ├─ Deserialize AlertEvent
      └─ Initiate RCA pipeline on valid alerts


┌─────────────────────────────────────────────────────────────────────────────┐
│                     ORCHESTRATION LAYER                                      │
└─────────────────────────────────────────────────────────────────────────────┘

   RcaPipelineService (@Service, @Transactional)
   │
   ├─ Step 1: persistAlert()
   │  └─ Uses: AlertRepository.save()
   │     Stores in: alerts table (status=PROCESSING)
   │
   ├─ Step 2: embedAndStore()
   │  └─ Uses: EmbeddingService
   │     ├─ buildAlertText() → Natural language description
   │     ├─ OpenAiClient.embed() → Get vector[1536]
   │     └─ Stores in: incident_embeddings table
   │
   ├─ Step 3: retrieve()
   │  └─ Uses: RagRetrievalService
   │     ├─ Vector similarity search on incident_embeddings
   │     ├─ Vector similarity search on runbooks
   │     └─ Returns: RetrievalResult with context
   │
   ├─ Step 4: generate()
   │  └─ Uses: RcaGenerationService
   │     ├─ buildUserPrompt() → Combines alert + RAG context
   │     ├─ OpenAiClient.chat() → LLM inference
   │     └─ parseAndBuildReport() → Structure LLM response
   │
   ├─ Step 5: save()
   │  └─ Uses: RcaReportRepository.save()
   │     Stores in: rca_reports table (status=DRAFT)
   │
   └─ Step 6: postRca() & notify()
      ├─ Uses: SlackNotificationService.postRca()
      │  └─ Posts structured message to Slack channel
      └─ Updates: alert.status = RESOLVED


┌─────────────────────────────────────────────────────────────────────────────┐
│                    BUSINESS LOGIC LAYER                                      │
└─────────────────────────────────────────────────────────────────────────────┘

   EmbeddingService
   │
   ├─ buildAlertText(event)
   │  └─ Converts AlertEvent to natural language
   │     Input: AlertEvent
   │     Output: "High P99 latency in order-service..."
   │
   ├─ embedAndStore(alert, event)
   │  ├─ Calls: OpenAiClient.embed(alertText)
   │  │  └─ HTTP POST to api.openai.com/v1/embeddings
   │  │     Model: text-embedding-3-small
   │  │     Response: float[] (1536 dimensions)
   │  │
   │  └─ Calls: IncidentEmbeddingRepository.save()
   │     └─ Persists to incident_embeddings
   │
   └─ Dependencies:
      ├─ OpenAiClient (HTTP client wrapper)
      ├─ IncidentEmbeddingRepository (data access)
      └─ Configuration: ${openai.embedding-model}, ${rca.embedding.dimensions}


   RagRetrievalService
   │
   ├─ retrieve(embedding, service)
   │  │
   │  ├─ Query 1: Search incident_embeddings
   │  │  └─ SQL: SELECT * FROM incident_embeddings
   │  │        WHERE embedding <-> input_embedding < 0.25  [cosine distance]
   │  │        AND (service = ? OR service IS NULL)
   │  │        LIMIT 5
   │  │        INDEX: idx_incident_embedding (IVFFlat)
   │  │
   │  └─ Query 2: Search runbooks
   │     └─ SQL: SELECT * FROM runbooks
   │           WHERE embedding <-> input_embedding < 0.25  [cosine distance]
   │           LIMIT 5
   │           INDEX: idx_runbook_embedding (IVFFlat)
   │
   ├─ Builds: RetrievalResult
   │  ├─ incidentIds: List<UUID>
   │  ├─ runbookIds: List<UUID>
   │  └─ context: Formatted string for LLM
   │
   └─ Dependencies:
      ├─ IncidentEmbeddingRepository (query incidents)
      ├─ RunbookRepository (query runbooks)
      └─ Configuration: ${rca.rag.top-k}, ${rca.rag.similarity-threshold}


   RcaGenerationService
   │
   ├─ generate(alert, event, retrieval)
   │  │
   │  ├─ buildUserPrompt(event, ragContext)
   │  │  └─ Structures prompt:
   │  │     "## CURRENT ALERT\n{alert details}\n\n
   │  │      ## CONTEXT FROM KNOWLEDGE BASE\n{rag context}\n\n
   │  │      Generate RCA in JSON..."
   │  │
   │  ├─ openAiClient.chat(SYSTEM_PROMPT, userPrompt)
   │  │  └─ HTTP POST to api.openai.com/v1/chat/completions
   │  │     Model: gpt-4-turbo-preview
   │  │     Max Tokens: 2000
   │  │     System Prompt: Expert SRE instructions
   │  │
   │  └─ parseAndBuildReport(response, alert, retrieval)
   │     ├─ Extract JSON from LLM response
   │     ├─ Parse: rootCause, impact, timeline, fixApplied, prevention
   │     ├─ Handle fallback (if JSON parsing fails)
   │     └─ Build RcaReport entity
   │
   └─ Dependencies:
      ├─ OpenAiClient (HTTP client wrapper)
      ├─ ObjectMapper (JSON parsing)
      └─ Configuration: ${openai.chat-model}, ${openai.max-tokens}


   SlackNotificationService
   │
   ├─ postRca(report, alert)
   │  │
   │  ├─ Format RCA into Slack-friendly message
   │  │  └─ Sections: Root Cause, Impact, Fix Applied, Prevention
   │  │
   │  ├─ HTTP POST to Slack API (slack.com/api/chat.postMessage)
   │  │  Headers: Authorization: Bearer ${SLACK_BOT_TOKEN}
   │  │  Body: {channel, text, blocks[], ...}
   │  │
   │  └─ Store slack_ts in rca_reports.slack_ts
   │     (Used for threaded updates/reactions)
   │
   └─ Dependencies:
      ├─ Slack SDK (com.slack.api:slack-api-client)
      └─ Configuration: ${slack.bot-token}, ${slack.channel}


┌─────────────────────────────────────────────────────────────────────────────┐
│                    EXTERNAL INTEGRATION LAYER                                │
└─────────────────────────────────────────────────────────────────────────────┘

   OpenAiClient (HTTP Wrapper)
   │
   ├─ embed(text) → float[] (1536 dimensions)
   │  └─ POST /v1/embeddings
   │     Model: text-embedding-3-small
   │
   ├─ chat(systemPrompt, userPrompt) → String (LLM response)
   │  └─ POST /v1/chat/completions
   │     Model: gpt-4-turbo-preview
   │
   └─ Configuration:
      ├─ Base URL: https://api.openai.com/v1
      ├─ API Key: ${OPENAI_API_KEY}
      ├─ Uses: WebClient (Spring WebFlux)
      └─ Error handling: Retries, timeouts, rate limiting


┌─────────────────────────────────────────────────────────────────────────────┐
│                    DATA ACCESS LAYER (JPA Repositories)                      │
└─────────────────────────────────────────────────────────────────────────────┘

   AlertRepository
   ├─ JpaRepository<Alert, UUID>
   ├─ save(), findById(), findAll()
   ├─ findByStatus(status)
   ├─ findByService(service)
   └─ Database: alerts table

   IncidentEmbeddingRepository
   ├─ JpaRepository<IncidentEmbedding, UUID>
   ├─ save(), findById(), findAll()
   ├─ Custom: Vector similarity search
   │  └─ @Query("... embedding <-> ? < ?")
   └─ Database: incident_embeddings table (with IVFFlat index)

   RcaReportRepository
   ├─ JpaRepository<RcaReport, UUID>
   ├─ save(), findById(), findAll()
   ├─ findByStatus(status)
   ├─ findByAlertId(alertId)
   └─ Database: rca_reports table

   RunbookRepository
   ├─ JpaRepository<Runbook, UUID>
   ├─ save(), findById(), findAll(), delete()
   ├─ Custom: Vector similarity search
   │  └─ @Query("... embedding <-> ? < ?")
   └─ Database: runbooks table (with IVFFlat index)


┌─────────────────────────────────────────────────────────────────────────────┐
│                    REST API LAYER (Controllers)                              │
└─────────────────────────────────────────────────────────────────────────────┘

   RcaController
   │
   ├─ GET /api/v1/rca
   │  └─ Calls: RcaReportRepository.findAll()
   │     Returns: List<RcaReport>
   │
   ├─ GET /api/v1/rca?status=DRAFT
   │  └─ Calls: RcaReportRepository.findByStatus(status)
   │     Returns: List<RcaReport> (filtered)
   │
   ├─ GET /api/v1/rca/{id}
   │  └─ Calls: RcaReportRepository.findById(id)
   │     Returns: RcaReport
   │
   ├─ PATCH /api/v1/rca/{id}/feedback
   │  └─ Accepts: {feedback_score, engineer_feedback}
   │     Stores feedback in rca_reports
   │
   └─ PATCH /api/v1/rca/{id}/close
      └─ Updates: rca_reports.status = CLOSED


   RunbookController
   │
   ├─ GET /api/v1/runbooks
   │  └─ Returns: List<Runbook>
   │
   ├─ POST /api/v1/runbooks
   │  └─ Accepts: {title, service, content, tags}
   │     Calls: EmbeddingService.embedAndStore()
   │     Stores in: runbooks table
   │
   ├─ GET /api/v1/runbooks/{id}
   │  └─ Returns: Runbook details
   │
   ├─ POST /api/v1/runbooks/{id}/re-embed
   │  └─ Re-generates embedding for runbook content
   │
   └─ DELETE /api/v1/runbooks/{id}
      └─ Removes runbook from system


╔═════════════════════════════════════════════════════════════════════════════╗
║                         DATA MODEL RELATIONSHIPS                            ║
╚═════════════════════════════════════════════════════════════════════════════╝

   Alerts (1) ──────→ (N) Incident Embeddings
   │                    │
   │                    └─ Embedding vector stored
   │                    └─ Used for future RAG queries
   │
   Alerts (1) ──────→ (N) RCA Reports
   │                    │
   │                    └─ RCA generated for alert
   │                    └─ status: DRAFT → PUBLISHED → CLOSED
   │
   Runbooks (N) ◄────── (1) Incident Embeddings
   │                    │
   │                    └─ Retrieved during RAG
   │                    └─ Same embedding dimension (1536)
   │
   RCA Reports ◄────── Sources Used (JSONB)
                        │
                        └─ References incident_ids & runbook_ids
                        └─ Audit trail of knowledge base usage
4. Technology Stack
Layer	Technology	Version	Purpose
Framework	Spring Boot	3.2.0	Application framework
Language	Java	17+	Programming language
Message Queue	Apache Kafka	3.x	Event streaming
Database	PostgreSQL	15+	Relational DB
Vector DB	pgvector	0.1.4	Vector indexing & search
Embedding Model	OpenAI text-embedding-3-small	Latest	Text to vector conversion
LLM	OpenAI GPT-4-Turbo-Preview	Latest	RCA generation
Notifications	Slack API SDK	1.36.1	Slack messaging
HTTP Client	Spring WebFlux	3.2.0	Async HTTP calls to OpenAI
ORM	Spring Data JPA / Hibernate	Latest	Database abstraction
Build Tool	Maven	3.9+	Project build
Utilities	Lombok, Jackson	Latest	Boilerplate reduction, JSON
5. Environment Configuration
# Application Configuration
spring.application.name: incident-rca-assistant
server.port: 8080

# Database
spring.datasource.url: jdbc:postgresql://localhost:5432/rca_db
spring.datasource.username: ${DB_USERNAME:postgres}
spring.datasource.password: ${DB_PASSWORD:postgres}
spring.jpa.hibernate.ddl-auto: validate

# Kafka
spring.kafka.bootstrap-servers: ${KAFKA_BROKERS:localhost:9092}
spring.kafka.consumer.group-id: rca-consumer-group
spring.kafka.consumer.concurrency: 3

# OpenAI
openai.api-key: ${OPENAI_API_KEY}
openai.embedding-model: text-embedding-3-small
openai.chat-model: gpt-4-turbo-preview
openai.max-tokens: 2000

# Slack
slack.bot-token: ${SLACK_BOT_TOKEN}
slack.channel: ${SLACK_ALERT_CHANNEL:#incidents}

# RCA Configuration
rca.kafka.alert-topic: alerts.raw
rca.kafka.rca-topic: rca.generated
rca.rag.top-k: 5
rca.rag.similarity-threshold: 0.75
rca.embedding.dimensions: 1536
6. Deployment Architecture
┌─────────────────────────────────────────────────────────────────────────────┐
│                      PRODUCTION DEPLOYMENT                                  │
└─────────────────────────────────────────────────────────────────────────────┘

  ┌──────────────────────┐
  │   Load Balancer      │
  │   (API Requests)     │
  └──────────┬───────────┘
             │
   ┌─────────┴─────────┬──────────────────┐
   ▼                   ▼                  ▼
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│  Spring App │  │  Spring App │  │  Spring App │
│  Instance 1 │  │  Instance 2 │  │  Instance 3 │
│  (Port 8080)│  │  (Port 8080)│  │  (Port 8080)│
└─────────────┘  └─────────────┘  └─────────────┘
   │                   │                  │
   │        Kafka Consumer Group: rca-consumer-group
   │        Concurrency: 3 (one per instance)
   │
   └─────────────────────┬──────────────────┘
                         │
         ┌───────────────┴───────────────┐
         ▼                               ▼
   ┌──────────────────┐        ┌──────────────────┐
   │  Kafka Cluster   │        │ PostgreSQL 15+   │
   │  (3 Brokers)     │        │ with pgvector    │
   └──────────────────┘        └──────────────────┘
         ▲                              ▲
         │                              │
   ┌─────┴──────────┐            ┌─────┴──────────┐
   │ alerts.raw     │            │ Connection     │
   │ rca.generated  │            │ Pool: HikariCP │
   └────────────────┘            └────────────────┘

  Horizontal Scaling:
  • Add more Spring App instances
  • Kafka partitions: max 3 (concurrency=3)
  • Database: Primary-Replica setup
  • External: OpenAI API (auto-scales), Slack API
