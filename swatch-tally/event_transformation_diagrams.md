# EventConflictType & Event Transformation (Updated)

This document contains visual Mermaid diagrams showing how incoming events are transformed based on their conflict types in the RHSM Subscriptions system, including the recent batch processing optimizations and cascading deductions fix.

# EventConflictType Mermaid Diagrams

## Overview of EventConflictTypes

The system defines 5 different types of event conflicts that can occur during event processing:

1. **ORIGINAL** - First occurrence of an event
2. **IDENTICAL** - Duplicate event (ignored)
3. **CORRECTIVE** - Same descriptors, different measurements
4. **CONTEXTUAL** - Same measurements, different descriptors  
5. **COMPREHENSIVE** - Different measurements and descriptors

## Batch Processing Overview

```mermaid
flowchart TD
    A[Batch of Events Received] --> B[Group by Event Key]
    B --> C[For each Event Key Group]
    C --> D{Multiple Events with Same Conflict Key?}
    D -->|No| E[Process Single Event]
    D -->|Yes| F[Intra-batch Deduplication]
    F --> G[Remove Exact Duplicates]
    G --> H{Multiple Events Remain?}
    H -->|No| I[Process Single Event]
    H -->|Yes| J[Resolve to Final Event]
    J --> K[Select Highest Value]
    K --> L[Process Final Event]
    
    E --> M[Apply Conflict Resolution]
    I --> M
    L --> M
    
    style F fill:#FFE6E6
    style G fill:#FFE6E6
    style J fill:#FFE6E6
    style K fill:#FFE6E6
```

## Updated Event Processing Flow

```mermaid
flowchart TD
    A[Batch of Events] --> B[Group by Event Key]
    B --> C[For each Event Key Group]
    C --> D[Intra-batch Deduplication]
    D --> E[Remove Exact Duplicates]
    E --> F{Multiple Events with Same Conflict Key?}
    F -->|No| G[Process Single Event]
    F -->|Yes| H[Resolve to Final Event]
    H --> I[Select Event with Highest Value]
    I --> J[Process Final Event Against DB]
    
    G --> K[Check Database Conflicts]
    J --> K
    K --> L{Event Key Exists in DB?}
    L -->|No| M[ORIGINAL - Save Event]
    L -->|Yes| N[Determine Conflict Type]
    N --> O[Apply Conflict Resolution]
    
    style D fill:#FFE6E6
    style E fill:#FFE6E6
    style H fill:#FFE6E6
    style I fill:#FFE6E6
```

## 1. ORIGINAL Event Flow (Updated)

```mermaid
flowchart TD
    A[Event Received] --> B[Batch Processing]
    B --> C[Intra-batch Deduplication]
    C --> D[Final Event Selected]
    D --> E{Event Key Exists in DB?}
    E -->|No| F[ORIGINAL Conflict Type]
    F --> G[Process Event Normally]
    G --> H[Save Event to Database]
    H --> I[Event Processing Complete]
    
    E -->|Yes| J[Check for Conflicts]
    
    style C fill:#FFE6E6
    style F fill:#90EE90
    style G fill:#E6F3FF
    style H fill:#E6F3FF
```

## 2. IDENTICAL Event Flow (Updated)

```mermaid
flowchart TD
    A[Event Received] --> B[Batch Processing]
    B --> C[Intra-batch Deduplication]
    C --> D[Exact Duplicates Removed]
    D --> E{Event Key Exists in DB?}
    E -->|Yes| F[Compare Event Details]
    F --> G{UsageConflictKey Match?}
    G -->|Yes| H{Descriptors Match?}
    H -->|Yes| I{Measurements Match?}
    I -->|Yes| J[IDENTICAL Conflict Type]
    J --> K[Event Ignored - Idempotent]
    K --> L[No Database Changes]
    L --> M[Event Processing Complete]
    
    style C fill:#FFE6E6
    style D fill:#FFE6E6
    style J fill:#FFB6C1
    style K fill:#FFE6E6
    style L fill:#FFE6E6
```

## 3. CORRECTIVE Event Flow (Updated - Batch Processing)

```mermaid
flowchart TD
    A[Multiple Events with Same Conflict Key] --> B[Batch Processing]
    B --> C[Intra-batch Deduplication]
    C --> D[Remove Exact Duplicates]
    D --> E{Multiple Events Remain?}
    E -->|Yes| F[Resolve to Final Event]
    F --> G[Select Event with Highest Value]
    G --> H[Process Final Event Against DB]
    H --> I{Event Key Exists in DB?}
    I -->|Yes| J[Compare Event Details]
    J --> K{UsageConflictKey Match?}
    K -->|Yes| L{Descriptors Match?}
    L -->|Yes| M{Measurements Match?}
    M -->|No| N[CORRECTIVE Conflict Type]
    N --> O[Create Deduction Event]
    O --> P[Save New Event]
    P --> Q[Event Processing Complete]
    
    E -->|No| R[Process Single Event]
    R --> I
    
    style B fill:#FFE6E6
    style C fill:#FFE6E6
    style D fill:#FFE6E6
    style F fill:#FFE6E6
    style G fill:#FFE6E6
    style N fill:#FFD700
    style O fill:#FFFACD
    style P fill:#E6F3FF
```

## 4. CONTEXTUAL Event Flow (Updated)

```mermaid
flowchart TD
    A[Event Received] --> B[Batch Processing]
    B --> C[Intra-batch Deduplication]
    C --> D[Final Event Selected]
    D --> E{Event Key Exists in DB?}
    E -->|Yes| F[Compare Event Details]
    F --> G{UsageConflictKey Match?}
    G -->|Yes| H{Descriptors Match?}
    H -->|No| I{Measurements Match?}
    I -->|Yes| J[CONTEXTUAL Conflict Type]
    J --> K[Create Deduction Event]
    K --> L[Save New Event]
    L --> M[Event Processing Complete]
    
    style C fill:#FFE6E6
    style J fill:#FFA500
    style K fill:#FFFACD
    style L fill:#E6F3FF
```

## 5. COMPREHENSIVE Event Flow (Updated)

```mermaid
flowchart TD
    A[Event Received] --> B[Batch Processing]
    B --> C[Intra-batch Deduplication]
    C --> D[Final Event Selected]
    D --> E{Event Key Exists in DB?}
    E -->|Yes| F[Compare Event Details]
    F --> G{UsageConflictKey Match?}
    G -->|Yes| H{Descriptors Match?}
    H -->|No| I{Measurements Match?}
    I -->|No| J[COMPREHENSIVE Conflict Type]
    J --> K[Create Deduction Event]
    K --> L[Save New Event]
    L --> M[Event Processing Complete]
    
    style C fill:#FFE6E6
    style J fill:#FF6347
    style K fill:#FFFACD
    style L fill:#E6F3FF
```

## Complete Decision Tree (Updated)

```mermaid
flowchart TD
    A[Batch of Events] --> B[Group by Event Key]
    B --> C[Intra-batch Deduplication]
    C --> D[Remove Exact Duplicates]
    D --> E{Multiple Events with Same Conflict Key?}
    E -->|Yes| F[Resolve to Final Event]
    F --> G[Select Highest Value]
    G --> H[Process Final Event]
    E -->|No| H
    
    H --> I{Event Key Exists in DB?}
    I -->|No| J[ORIGINAL]
    I -->|Yes| K[Compare UsageConflictKey]
    
    K -->|No Match| J
    K -->|Match| L{Descriptors Match?}
    
    L -->|No| M{Measurements Match?}
    L -->|Yes| N{Measurements Match?}
    
    M -->|Yes| O[CONTEXTUAL]
    M -->|No| P[COMPREHENSIVE]
    N -->|Yes| Q[IDENTICAL]
    N -->|No| R[CORRECTIVE]
    
    J --> S[Process & Save]
    O --> T[Create Deduction + Save New]
    P --> T
    Q --> U[Ignore Event]
    R --> T
    
    style C fill:#FFE6E6
    style D fill:#FFE6E6
    style F fill:#FFE6E6
    style G fill:#FFE6E6
    style J fill:#90EE90
    style O fill:#FFA500
    style P fill:#FF6347
    style Q fill:#FFB6C1
    style R fill:#FFD700
```

## Cascading Deductions Fix - Before vs After

### Before Fix (Individual Processing)

```mermaid
graph LR
    subgraph "Batch of Events"
        A1[Event 1: 2.0 vCPUs]
        A2[Event 2: 3.0 vCPUs]
        A3[Event 3: 4.0 vCPUs]
    end
    
    subgraph "Individual Processing (BEFORE)"
        B1[Process Event 1]
        B2[Process Event 2]
        B3[Process Event 3]
    end
    
    subgraph "Database Result (BEFORE)"
        C1[Event: 2.0 vCPUs]
        C2[Deduction: -2.0 vCPUs]
        C3[Event: 3.0 vCPUs]
        C4[Deduction: -3.0 vCPUs]
        C5[Event: 4.0 vCPUs]
        C6[Total: 5 events]
    end
    
    A1 --> B1
    A2 --> B2
    A3 --> B3
    
    B1 --> C1
    B1 --> C2
    B2 --> C3
    B2 --> C4
    B3 --> C5
    
    style C2 fill:#FF6347
    style C4 fill:#FF6347
    style C6 fill:#FF6347
```

### After Fix (Batch Processing)

```mermaid
graph LR
    subgraph "Batch of Events"
        A1[Event 1: 2.0 vCPUs]
        A2[Event 2: 3.0 vCPUs]
        A3[Event 3: 4.0 vCPUs]
    end
    
    subgraph "Batch Processing (AFTER)"
        B1[Group by Conflict Key]
        B2[Intra-batch Deduplication]
        B3[Select Highest Value: 4.0]
        B4[Process Single Event]
    end
    
    subgraph "Database Result (AFTER)"
        C1[Event: 4.0 vCPUs]
        C2[Total: 1 event]
    end
    
    A1 --> B1
    A2 --> B1
    A3 --> B1
    
    B1 --> B2
    B2 --> B3
    B3 --> B4
    B4 --> C1
    
    style B1 fill:#FFE6E6
    style B2 fill:#FFE6E6
    style B3 fill:#FFE6E6
    style B4 fill:#FFE6E6
    style C1 fill:#90EE90
    style C2 fill:#90EE90
```

## Conflict Type Summary Table (Updated)

```mermaid
graph LR
    subgraph "Conflict Types"
        A[ORIGINAL<br/>First occurrence]
        B[IDENTICAL<br/>Exact duplicate]
        C[CORRECTIVE<br/>Same descriptors,<br/>different measurements]
        D[CONTEXTUAL<br/>Same measurements,<br/>different descriptors]
        E[COMPREHENSIVE<br/>Different measurements<br/>and descriptors]
    end
    
    subgraph "Actions"
        F[Process & Save]
        G[Ignore Event]
        H[Create Deduction +<br/>Save New Event]
    end
    
    subgraph "Batch Processing"
        I[Intra-batch<br/>Deduplication]
        J[Highest Value<br/>Selection]
    end
    
    A --> F
    B --> G
    C --> H
    D --> H
    E --> H
    
    C -.-> I
    C -.-> J
    
    style A fill:#90EE90
    style B fill:#FFB6C1
    style C fill:#FFD700
    style D fill:#FFA500
    style E fill:#FF6347
    style F fill:#E6F3FF
    style G fill:#FFE6E6
    style H fill:#FFFACD
    style I fill:#FFE6E6
    style J fill:#FFE6E6
```

## Key Methods Behavior (Updated)

```mermaid
graph TD
    subgraph "EventConflictType Methods"
        A[requiresDeduction]
        B[saveIncomingEvent]
    end
    
    subgraph "Return Values"
        C[ORIGINAL: false, true]
        D[IDENTICAL: false, false]
        E[CORRECTIVE: true, true]
        F[CONTEXTUAL: true, true]
        G[COMPREHENSIVE: true, true]
    end
    
    subgraph "Batch Processing Methods"
        H[deduplicateIntraBatchEvents]
        I[resolveIntraBatchConflicts]
        J[resolveToFinalEvent]
    end
    
    A --> C
    A --> D
    A --> E
    A --> F
    A --> G
    
    B --> C
    B --> D
    B --> E
    B --> F
    B --> G
    
    style C fill:#90EE90
    style D fill:#FFB6C1
    style E fill:#FFD700
    style F fill:#FFA500
    style G fill:#FF6347
    style H fill:#FFE6E6
    style I fill:#FFE6E6
    style J fill:#FFE6E6
```

## Event Processing State Machine (Updated)

```mermaid
stateDiagram-v2
    [*] --> BatchReceived
    
    BatchReceived --> GroupByEventKey
    GroupByEventKey --> IntraBatchDeduplication
    IntraBatchDeduplication --> RemoveExactDuplicates
    RemoveExactDuplicates --> MultipleEventsRemain
    
    MultipleEventsRemain --> ResolveToFinalEvent : Yes
    MultipleEventsRemain --> ProcessSingleEvent : No
    
    ResolveToFinalEvent --> SelectHighestValue
    SelectHighestValue --> ProcessFinalEvent
    ProcessFinalEvent --> CheckExistingEvent
    
    ProcessSingleEvent --> CheckExistingEvent
    
    CheckExistingEvent --> NoExistingEvent : Event Key Not Found
    CheckExistingEvent --> CompareEventDetails : Event Key Found
    
    NoExistingEvent --> ProcessOriginal : ORIGINAL
    ProcessOriginal --> EventSaved
    EventSaved --> [*]
    
    CompareEventDetails --> IdenticalEvent : All Fields Match
    CompareEventDetails --> CorrectiveEvent : Descriptors Match, Measurements Differ
    CompareEventDetails --> ContextualEvent : Measurements Match, Descriptors Differ
    CompareEventDetails --> ComprehensiveEvent : Both Differ
    
    IdenticalEvent --> EventIgnored
    EventIgnored --> [*]
    
    CorrectiveEvent --> CreateDeduction
    ContextualEvent --> CreateDeduction
    ComprehensiveEvent --> CreateDeduction
    
    CreateDeduction --> SaveNewEvent
    SaveNewEvent --> EventSaved
    
    note right of ProcessOriginal
        No conflicts, first occurrence
    end note
    
    note right of IdenticalEvent
        Idempotent behavior
    end note
    
    note right of CreateDeduction
        Deduction + New Event
    end note
    
    note right of ResolveToFinalEvent
        Batch processing optimization
    end note
```

## Visual Event Transformation Diagrams (Updated)

### 1. ORIGINAL Event - No Transformation (Updated)

```mermaid
graph LR
    subgraph "Incoming Event"
        A1[Event Key: ABC123]
        A2[Descriptors: CPU Usage]
        A3[Measurements: 4 cores]
        A4[Timestamp: 2024-01-01]
    end
    
    subgraph "Batch Processing"
        B1[Intra-batch Deduplication]
        B2[No Conflicts Found]
    end
    
    subgraph "Database State"
        C1[No existing event]
    end
    
    subgraph "Result"
        D1[Event Key: ABC123]
        D2[Descriptors: CPU Usage]
        D3[Measurements: 4 cores]
        D4[Timestamp: 2024-01-01]
        D5[Status: SAVED]
    end
    
    A1 --> B1
    B1 --> B2
    B2 --> D1
    A2 --> D2
    A3 --> D3
    A4 --> D4
    
    style B1 fill:#FFE6E6
    style B2 fill:#FFE6E6
    style D1 fill:#90EE90
    style D2 fill:#90EE90
    style D3 fill:#90EE90
    style D4 fill:#90EE90
    style D5 fill:#90EE90
```

### 2. IDENTICAL Event - Ignored (Updated)

```mermaid
graph LR
    subgraph "Incoming Event"
        A1[Event Key: ABC123]
        A2[Descriptors: CPU Usage]
        A3[Measurements: 4 cores]
        A4[Timestamp: 2024-01-01]
    end
    
    subgraph "Batch Processing"
        B1[Intra-batch Deduplication]
        B2[No Exact Duplicates]
    end
    
    subgraph "Existing Event in DB"
        C1[Event Key: ABC123]
        C2[Descriptors: CPU Usage]
        C3[Measurements: 4 cores]
        C4[Timestamp: 2024-01-01]
    end
    
    subgraph "Result"
        D1[Event Key: ABC123]
        D2[Descriptors: CPU Usage]
        D3[Measurements: 4 cores]
        D4[Timestamp: 2024-01-01]
        D5[Status: IGNORED]
        D6[No Changes]
    end
    
    A1 --> B1
    B1 --> B2
    B2 --> D5
    
    style B1 fill:#FFE6E6
    style B2 fill:#FFE6E6
    style D5 fill:#FFB6C1
    style D6 fill:#FFB6C1
```

### 3. CORRECTIVE Event - Batch Processing (Updated)

```mermaid
graph LR
    subgraph "Batch of Events"
        A1[Event 1: 2.0 vCPUs]
        A2[Event 2: 3.0 vCPUs]
        A3[Event 3: 4.0 vCPUs]
    end
    
    subgraph "Batch Processing"
        B1[Group by Conflict Key]
        B2[Intra-batch Deduplication]
        B3[Select Highest Value: 4.0]
    end
    
    subgraph "Existing Event in DB"
        C1[Event Key: ABC123]
        C2[Descriptors: CPU Usage]
        C3[Measurements: 1.0 cores]
        C4[Timestamp: 2024-01-01]
    end
    
    subgraph "Deduction Event Created"
        D1[Event Key: ABC123-DED]
        D2[Descriptors: CPU Usage]
        D3[Measurements: -1.0 cores]
        D4[Timestamp: 2024-01-01]
    end
    
    subgraph "New Event Saved"
        E1[Event Key: ABC123]
        E2[Descriptors: CPU Usage]
        E3[Measurements: 4.0 cores]
        E4[Timestamp: 2024-01-01]
        E5[Status: AMENDED]
    end
    
    A1 --> B1
    A2 --> B1
    A3 --> B1
    B1 --> B2
    B2 --> B3
    B3 --> E3
    
    C3 --> D3
    
    style B1 fill:#FFE6E6
    style B2 fill:#FFE6E6
    style B3 fill:#FFE6E6
    style D1 fill:#FF6347
    style D2 fill:#FF6347
    style D3 fill:#FF6347
    style D4 fill:#FF6347
    style E1 fill:#FFD700
    style E2 fill:#FFD700
    style E3 fill:#FFD700
    style E4 fill:#FFD700
    style E5 fill:#FFD700
```

### 4. CONTEXTUAL Event - Descriptor Amendment (Updated)

```mermaid
graph LR
    subgraph "Incoming Event"
        A1[Event Key: ABC123]
        A2[Descriptors: GPU Usage]
        A3[Measurements: 4 cores]
        A4[Timestamp: 2024-01-01]
    end
    
    subgraph "Batch Processing"
        B1[Intra-batch Deduplication]
        B2[No Conflicts in Batch]
    end
    
    subgraph "Existing Event in DB"
        C1[Event Key: ABC123]
        C2[Descriptors: CPU Usage]
        C3[Measurements: 4 cores]
        C4[Timestamp: 2024-01-01]
    end
    
    subgraph "Deduction Event Created"
        D1[Event Key: ABC123-DED]
        D2[Descriptors: CPU Usage]
        D3[Measurements: -4 cores]
        D4[Timestamp: 2024-01-01]
    end
    
    subgraph "New Event Saved"
        E1[Event Key: ABC123]
        E2[Descriptors: GPU Usage]
        E3[Measurements: 4 cores]
        E4[Timestamp: 2024-01-01]
        E5[Status: AMENDED]
    end
    
    A1 --> B1
    B1 --> B2
    B2 --> E1
    A2 --> E2
    A3 --> E3
    A4 --> E4
    
    C2 --> D2
    C3 --> D3
    
    style B1 fill:#FFE6E6
    style B2 fill:#FFE6E6
    style D1 fill:#FF6347
    style D2 fill:#FF6347
    style D3 fill:#FF6347
    style D4 fill:#FF6347
    style E1 fill:#FFA500
    style E2 fill:#FFA500
    style E3 fill:#FFA500
    style E4 fill:#FFA500
    style E5 fill:#FFA500
```

### 5. COMPREHENSIVE Event - Full Amendment (Updated)

```mermaid
graph LR
    subgraph "Incoming Event"
        A1[Event Key: ABC123]
        A2[Descriptors: GPU Usage]
        A3[Measurements: 8 cores]
        A4[Timestamp: 2024-01-01]
    end
    
    subgraph "Batch Processing"
        B1[Intra-batch Deduplication]
        B2[No Conflicts in Batch]
    end
    
    subgraph "Existing Event in DB"
        C1[Event Key: ABC123]
        C2[Descriptors: CPU Usage]
        C3[Measurements: 4 cores]
        C4[Timestamp: 2024-01-01]
    end
    
    subgraph "Deduction Event Created"
        D1[Event Key: ABC123-DED]
        D2[Descriptors: CPU Usage]
        D3[Measurements: -4 cores]
        D4[Timestamp: 2024-01-01]
    end
    
    subgraph "New Event Saved"
        E1[Event Key: ABC123]
        E2[Descriptors: GPU Usage]
        E3[Measurements: 8 cores]
        E4[Timestamp: 2024-01-01]
        E5[Status: AMENDED]
    end
    
    A1 --> B1
    B1 --> B2
    B2 --> E1
    A2 --> E2
    A3 --> E3
    A4 --> E4
    
    C2 --> D2
    C3 --> D3
    
    style B1 fill:#FFE6E6
    style B2 fill:#FFE6E6
    style D1 fill:#FF6347
    style D2 fill:#FF6347
    style D3 fill:#FF6347
    style D4 fill:#FF6347
    style E1 fill:#FF6347
    style E2 fill:#FF6347
    style E3 fill:#FF6347
    style E4 fill:#FF6347
    style E5 fill:#FF6347
```

## Event Transformation Summary (Updated)

```mermaid
graph TB
    subgraph "Event Conflict Types"
        A[ORIGINAL<br/>First occurrence]
        B[IDENTICAL<br/>Exact duplicate]
        C[CORRECTIVE<br/>Same descriptors,<br/>different measurements]
        D[CONTEXTUAL<br/>Same measurements,<br/>different descriptors]
        E[COMPREHENSIVE<br/>Different measurements<br/>and descriptors]
    end
    
    subgraph "Batch Processing"
        F[Intra-batch Deduplication]
        G[Highest Value Selection]
        H[Single Event Processing]
    end
    
    subgraph "Database Operations"
        I[Save Event]
        J[Ignore Event]
        K[Create Deduction +<br/>Save New Event]
    end
    
    subgraph "Visual Indicators"
        L[Green: No conflicts]
        M[Pink: Ignored]
        N[Gold: Measurement change]
        O[Orange: Descriptor change]
        P[Red: Full change]
        Q[Light Red: Batch processing]
    end
    
    A --> F
    B --> F
    C --> F
    D --> F
    E --> F
    
    F --> G
    G --> H
    H --> I
    H --> J
    H --> K
    
    A -.-> L
    B -.-> M
    C -.-> N
    D -.-> O
    E -.-> P
    F -.-> Q
    G -.-> Q
    
    style A fill:#90EE90
    style B fill:#FFB6C1
    style C fill:#FFD700
    style D fill:#FFA500
    style E fill:#FF6347
    style F fill:#FFE6E6
    style G fill:#FFE6E6
    style H fill:#FFE6E6
    style I fill:#90EE90
    style J fill:#FFB6C1
    style K fill:#FFFACD
    style L fill:#90EE90
    style M fill:#FFB6C1
    style N fill:#FFD700
    style O fill:#FFA500
    style P fill:#FF6347
    style Q fill:#FFE6E6
```

## Data Flow Visualization (Updated)

```mermaid
graph LR
    subgraph "Before Processing"
        A1[Batch of Events]
        A2[Existing Events<br/>in Database]
    end
    
    subgraph "Batch Processing"
        B1[Group by Event Key]
        B2[Intra-batch Deduplication]
        B3[Highest Value Selection]
        B4[Single Event per Conflict Key]
    end
    
    subgraph "Conflict Detection"
        C1[Event Key Match?]
        C2[Descriptor Match?]
        C3[Measurement Match?]
    end
    
    subgraph "After Processing"
        D1[New Event Saved]
        D2[Deduction Event<br/>Created]
        D3[Event Ignored]
        D4[Database State<br/>Updated]
    end
    
    A1 --> B1
    A2 --> C1
    B1 --> B2
    B2 --> B3
    B3 --> B4
    B4 --> C1
    C1 --> C2
    C2 --> C3
    C3 --> D1
    C3 --> D2
    C3 --> D3
    D1 --> D4
    D2 --> D4
    
    style A1 fill:#E6F3FF
    style A2 fill:#E6F3FF
    style B1 fill:#FFE6E6
    style B2 fill:#FFE6E6
    style B3 fill:#FFE6E6
    style B4 fill:#FFE6E6
    style D1 fill:#90EE90
    style D2 fill:#FF6347
    style D3 fill:#FFB6C1
    style D4 fill:#E6F3FF
```

## Color Legend (Updated)

- **🟢 Green (#90EE90)**: ORIGINAL events - no conflicts, processed normally
- **🩷 Pink (#FFB6C1)**: IDENTICAL events - ignored, no changes
- **🟡 Gold (#FFD700)**: CORRECTIVE events - measurement changes
- **🟠 Orange (#FFA500)**: CONTEXTUAL events - descriptor changes  
- **🔴 Red (#FF6347)**: COMPREHENSIVE events - full changes and deduction events
- **🔵 Blue (#E6F3FF)**: Neutral/process elements
- **🟡 Light Yellow (#FFFACD)**: Deduction-related operations
- **🟥 Light Red (#FFE6E6)**: Batch processing operations (NEW)

## Key Changes Summary

### What's New:
1. **Batch Processing**: Events are now processed in batches, not individually
2. **Intra-batch Deduplication**: Exact duplicates are removed within a batch
3. **Highest Value Selection**: When multiple events have the same conflict key, only the highest value is processed
4. **Cascading Deductions Prevention**: No more incorrect deduction chains

### What's Improved:
1. **Performance**: Reduced database operations by processing events together
2. **Data Integrity**: Eliminated cascading deductions bug
3. **Consistency**: Predictable "highest value wins" behavior
4. **Efficiency**: Single event processing per conflict key instead of multiple

### What's Preserved:
1. **All 5 Conflict Types**: ORIGINAL, IDENTICAL, CORRECTIVE, CONTEXTUAL, COMPREHENSIVE
2. **Conflict Resolution Logic**: Individual conflict type behavior remains the same
3. **Deduction Creation**: Still creates deductions when appropriate
4. **Database Schema**: No changes to existing data structures 