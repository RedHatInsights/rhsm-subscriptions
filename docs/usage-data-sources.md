# SWATCH Data Sources - Instance and Usage Information

```mermaid
graph TB
    SWATCH["SWATCH"]
    
    subgraph USAGE_SOURCES["Direct Usage Data Sources"]
        ANSIBLE["Ansible"]
        COST_MGMT["Cost Management"]
    end
    
    subgraph SCHEDULED_QUERIES["Scheduled Query Sources"]
        HBI["HBI<br/>(Host Based Inventory)"]
        OBSERVATORIUM["Observatorium<br/>(Prometheus/Telemeter)"]
    end

    DB[("rhsm-subscriptions<br/>Database")]

    %% Direct usage data sources submit usage information
    USAGE_SOURCES -->|"Submits usage information"| SWATCH
    
    %% Scheduled query sources provide usage data to SWATCH
    SCHEDULED_QUERIES -->|"Provides usage data<br/>(SWATCH queries on schedule)"| SWATCH
    
    %% SWATCH stores usage data in database
    SWATCH -->|"Stores usage data"| DB
    
    %% Styling
    classDef swatchStyle fill:#ffebee,stroke:#c62828,stroke-width:3px
    classDef ansibleStyle fill:#e0f7fa,stroke:#00838f,stroke-width:2px
    classDef costMgmtStyle fill:#fff3e0,stroke:#e65100,stroke-width:2px
    classDef queryStyle fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px
    classDef dbStyle fill:#f3e5f5,stroke:#6a1b9a,stroke-width:2px
    
    class SWATCH,DB swatchStyle
    class ANSIBLE ansibleStyle
    class COST_MGMT costMgmtStyle
    class HBI dbStyle
    class OBSERVATORIUM queryStyle
```

## Data Flow Summary

### 1. Direct Usage Submissions
- **Ansible** and **Cost Management** submit usage information messages directly to SWATCH
- These systems send actual usage data that SWATCH processes and aggregates

### 2. Scheduled Queries

#### HBI (Host Based Inventory)
- **SWATCH queries HBI** on a scheduled basis to:
  - Get host/instance information
  - Retrieve host metadata and relationships

#### Observatorium (Prometheus/Telemeter)
- **SWATCH queries Observatorium** on a scheduled basis to fetch:
  - OpenShift cluster metrics (via Telemeter)
  - RHEL telemetry metrics (via Rhelemeter)
  - Time-series usage data for various products

### 3. Data Storage
- **SWATCH stores all usage data** in the `rhsm-subscriptions` database
- This includes usage information from all sources (Ansible, Cost Management, HBI, and Observatorium)

---

## Technical Implementation Details

```mermaid
graph TB
    SWATCH["SWATCH"]
    
    ANSIBLE["Ansible"]
    COST_MGMT["Cost Management"]
    
    KAFKA_TOPIC[("platform.rhsm-subscriptions.metering-tasks<br/>Kafka Topic")]
    
    OBSERVATORIUM["Observatorium<br/>(Prometheus API)"]
    
    HBI_DB[("HBI Database<br/>(Readonly Clone)")]
    
    SWATCH_DB[("rhsm-subscriptions<br/>Database")]

    %% Ansible and Cost Management publish to Kafka topic
    ANSIBLE -->|"Publishes messages"| KAFKA_TOPIC
    COST_MGMT -->|"Publishes messages"| KAFKA_TOPIC
    
    %% SWATCH consumes from Kafka topic
    KAFKA_TOPIC -->|"Consumes messages"| SWATCH
    
    %% SWATCH makes REST calls to Observatorium
    SWATCH -->|"REST API calls<br/>(Prometheus API)"| OBSERVATORIUM
    OBSERVATORIUM -->|"Returns metrics data"| SWATCH
    
    %% SWATCH queries HBI readonly database
    SWATCH -->|"SQL queries"| HBI_DB
    HBI_DB -->|"Returns host data"| SWATCH
    
    %% SWATCH stores data
    SWATCH -->|"Stores usage data"| SWATCH_DB
    
    %% Styling
    classDef swatchStyle fill:#ffebee,stroke:#c62828,stroke-width:3px
    classDef ansibleStyle fill:#e0f7fa,stroke:#00838f,stroke-width:2px
    classDef costMgmtStyle fill:#fff3e0,stroke:#e65100,stroke-width:2px
    classDef kafkaStyle fill:#fce4ec,stroke:#c2185b,stroke-width:2px
    classDef apiStyle fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px
    classDef dbStyle fill:#f3e5f5,stroke:#6a1b9a,stroke-width:2px
    
    class SWATCH,SWATCH_DB swatchStyle
    class ANSIBLE ansibleStyle
    class COST_MGMT costMgmtStyle
    class KAFKA_TOPIC kafkaStyle
    class OBSERVATORIUM apiStyle
    class HBI_DB dbStyle
```

### Technical Details

#### 1. Kafka Integration
- **Ansible** and **Cost Management** publish usage messages to the Kafka topic `platform.rhsm-subscriptions.metering-tasks`
- **SWATCH** consumes messages from this topic to process usage data

#### 2. Observatorium Integration
- **SWATCH** makes **REST API calls** to Observatorium using the Prometheus API
- Queries use PromQL (Prometheus Query Language) to fetch metrics
- Returns time-series usage data for processing

#### 3. HBI Database Integration
- **SWATCH** queries a **readonly clone** of the HBI database using SQL
- This provides direct access to host inventory data without impacting the primary HBI database
- Returns host/instance information and metadata

---

## Timing and Scheduling Patterns

```mermaid
sequenceDiagram
    participant ANSIBLE as Ansible/Cost Mgmt
    participant KAFKA as Kafka Topic<br/>(metering-tasks)
    participant SWATCH as SWATCH
    participant OBSERVATORIUM as Observatorium<br/>(Prometheus API)
    participant HBI_DB as HBI Database<br/>(Readonly Clone)
    participant SWATCH_DB as rhsm-subscriptions<br/>Database

    Note over ANSIBLE,KAFKA: Async Messages (Continuous)
    loop Continuously
        ANSIBLE->>KAFKA: Publish usage messages
        KAFKA->>SWATCH: Consume messages (async)
        SWATCH->>SWATCH_DB: Store usage data
    end

    Note over SWATCH,OBSERVATORIUM: Scheduled: Every hour at :30
    loop Every hour at :30
        SWATCH->>OBSERVATORIUM: REST API query (PromQL)
        OBSERVATORIUM-->>SWATCH: Return metrics data
        SWATCH->>SWATCH_DB: Store metrics data
    end

    Note over SWATCH,HBI_DB: Scheduled: Nightly
    loop Nightly
        SWATCH->>HBI_DB: SQL query host data
        HBI_DB-->>SWATCH: Return host inventory
        SWATCH->>SWATCH_DB: Store host data
    end
```

### Scheduling Details

- **Kafka Messages**: Processed **asynchronously** as they arrive from Ansible/Cost Management
- **Observatorium Queries**: Executed **every hour at the 30-minute mark** (e.g., 1:30, 2:30, 3:30)
- **HBI Database Queries**: Executed **nightly** during scheduled maintenance window

---

## Post-Ingestion Processing Flow

```mermaid
graph LR
    INGESTION["Usage Data<br/>Ingestion"]
    NORMALIZE["Normalize &<br/>Adjust Usage"]
    EVENTS_TABLE[("Events Table<br/>(rhsm-subscriptions DB)")]
    TALLY["Batch Process:<br/>Do a Tally"]
    
    INGESTION -->|"Raw usage data"| NORMALIZE
    NORMALIZE -->|"Normalized & adjusted events"| EVENTS_TABLE
    EVENTS_TABLE -->|"Batch processing"| TALLY
    
    %% Styling
    classDef processStyle fill:#fff3e0,stroke:#e65100,stroke-width:2px
    classDef tableStyle fill:#ffebee,stroke:#c62828,stroke-width:2px
    classDef batchStyle fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px
    
    class INGESTION,NORMALIZE processStyle
    class EVENTS_TABLE tableStyle
    class TALLY batchStyle
```

### Processing Pipeline

1. **Usage Ingestion**
   - Raw usage data is received from all sources (Ansible, Cost Management, HBI, Observatorium)

2. **Normalization & Adjustment**
   - Usage values are **normalized** according to product-specific rules
   - Usage may be **adjusted or amended** based on business logic (e.g., hypervisor relationships, guest mappings)
   - Metadata is enriched (e.g., display names, relationships)

3. **Events Table (Staging Area)**
   - Normalized and adjusted events are persisted to the **Events table** in the `rhsm-subscriptions` database
   - This table serves as a **staging area** where events accumulate before batch processing
   - Events remain in this table until the tally process consumes them

4. **Batch Tally Process**
   - A scheduled batch process ("do a tally") reads events from the Events table
   - Events are aggregated by organization, product, metric, and time period
   - Aggregated tallies are stored in snapshot tables for reporting and billing

