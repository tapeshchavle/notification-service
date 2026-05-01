<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-4.0.6-brightgreen?style=for-the-badge&logo=springboot" alt="Spring Boot"/>
  <img src="https://img.shields.io/badge/Java-17+-orange?style=for-the-badge&logo=openjdk" alt="Java"/>
  <img src="https://img.shields.io/badge/Architecture-Event--Driven-blue?style=for-the-badge" alt="Architecture"/>
  <img src="https://img.shields.io/badge/Design%20Patterns-6-purple?style=for-the-badge" alt="Patterns"/>
</p>

# 🔔 Notification Service

> A **production-grade, scalable notification service** supporting Email, SMS, Push, and In-App channels with priority queuing, exponential backoff retry, scheduled delivery, and user preference management.

Built following **SOLID principles** and industry-standard design patterns to ensure extensibility — adding a new notification channel requires **zero changes** to existing code.

---

## 📑 Table of Contents

- [High-Level Architecture](#-high-level-architecture)
- [System Interaction Flow](#-system-interaction-flow)
- [Notification Lifecycle](#-notification-lifecycle)
- [Retry Mechanism](#-retry-mechanism--exponential-backoff-with-jitter)
- [Class Diagram](#-class-diagram)
- [Package Structure](#-package-structure)
- [Design Patterns](#-design-patterns)
- [SOLID Principles](#-solid-principles)
- [API Reference](#-api-reference)
- [Configuration](#%EF%B8%8F-configuration)
- [Getting Started](#-getting-started)
- [Adding a New Channel](#-adding-a-new-channel)
- [Tech Stack](#-tech-stack)

---

## 🏗 High-Level Architecture

```mermaid
graph TB
    subgraph External["🌐 External Services"]
        S1["Service 1<br/>(Order Service)"]
        S2["Service 2<br/>(Auth Service)"]
        S3["Service N<br/>(Any Microservice)"]
    end

    subgraph LB["⚖️ Load Balancer"]
        NX["Nginx / ALB"]
    end

    subgraph NS["📦 Notification Service"]
        direction TB

        subgraph API["🔌 API Layer"]
            NC["NotificationController<br/>REST API"]
        end

        subgraph Core["⚙️ Core Layer"]
            NO["NotificationOrchestrator<br/>(Facade)"]
            UPS["UserPreferenceService<br/>(Preference Store)"]
        end

        subgraph Queue["📬 Queue Layer"]
            NQ["InMemoryNotificationQueue<br/>(PriorityBlockingQueue)"]
            NQC["NotificationQueueConsumer<br/>(Multi-threaded)"]
        end

        subgraph Retry["🔁 Retry Layer"]
            RP["ExponentialBackoffRetryPolicy<br/>(Backoff + Jitter)"]
            DL["Dead Letter<br/>(Max retries exhausted)"]
        end

        subgraph Channel["📡 Channel Processors"]
            REG["ChannelProcessorRegistry<br/>(Auto-Discovery Factory)"]
            EP["📧 EmailProcessor"]
            SP["💬 SmsProcessor"]
            PP["📱 PushProcessor"]
            IP["🔔 InAppProcessor"]
        end

        subgraph Scheduler["⏰ Scheduler"]
            SCH["NotificationScheduler<br/>(Delayed Delivery)"]
        end
    end

    subgraph Delivery["🚀 External Delivery"]
        SMTP["SMTP / SES<br/>(Email)"]
        TWILIO["Twilio / SNS<br/>(SMS)"]
        FCM["FCM / APNs<br/>(Push)"]
        WS["WebSocket / SSE<br/>(In-App)"]
    end

    S1 & S2 & S3 --> NX
    NX --> NC
    NC --> NO
    NC --> SCH
    NO --> UPS
    NO --> NQ
    SCH --> NO
    NQ --> NQC
    NQC --> REG
    NQC --> RP
    RP -->|"retry"| NQ
    RP -->|"max retries"| DL
    REG --> EP & SP & PP & IP
    EP --> SMTP
    SP --> TWILIO
    PP --> FCM
    IP --> WS

    style External fill:#fff3cd,stroke:#ffc107,color:#000
    style NS fill:#e8f5e9,stroke:#4caf50,color:#000
    style API fill:#e3f2fd,stroke:#2196f3,color:#000
    style Core fill:#fff8e1,stroke:#ff9800,color:#000
    style Queue fill:#fce4ec,stroke:#e91e63,color:#000
    style Retry fill:#f3e5f5,stroke:#9c27b0,color:#000
    style Channel fill:#e0f2f1,stroke:#009688,color:#000
    style Scheduler fill:#e8eaf6,stroke:#3f51b5,color:#000
    style Delivery fill:#efebe9,stroke:#795548,color:#000
```

---

## 🔄 System Interaction Flow

### Immediate Notification Flow

```mermaid
sequenceDiagram
    autonumber
    participant Client as 🌐 External Service
    participant Ctrl as 📡 NotificationController
    participant Orch as ⚙️ NotificationOrchestrator
    participant Pref as 👤 UserPreferenceService
    participant Queue as 📬 NotificationQueue
    participant Consumer as 🔧 QueueConsumer
    participant Registry as 🗂️ ProcessorRegistry
    participant Processor as 📧 ChannelProcessor
    participant Retry as 🔁 RetryPolicy

    Client->>+Ctrl: POST /api/v1/notifications
    Ctrl->>Ctrl: Validate request (@Valid)
    Ctrl->>+Orch: sendNotification(request)

    Orch->>+Pref: isChannelEnabled(userId, type)
    Pref-->>-Orch: ✅ enabled

    Orch->>+Pref: isInQuietHours(userId)
    Pref-->>-Orch: ❌ not in quiet hours

    Orch->>Orch: Build Notification entity
    Orch->>+Queue: enqueue(notification)
    Queue->>Queue: Add to PriorityBlockingQueue
    Queue-->>-Orch: ✅ queued
    Orch-->>-Ctrl: notification (id, QUEUED)
    Ctrl-->>-Client: 202 Accepted

    Note over Consumer: Consumer threads continuously<br/>poll the queue

    Consumer->>+Queue: dequeue() [blocking]
    Queue-->>-Consumer: notification

    Consumer->>Consumer: markProcessing()
    Consumer->>+Registry: getProcessor(type)
    Registry-->>-Consumer: processor

    Consumer->>+Processor: process(notification)

    alt ✅ Success
        Processor-->>-Consumer: OK
        Consumer->>Consumer: markSent()
    else ❌ Failure (retryable)
        Processor-->>Consumer: ChannelDeliveryException
        Consumer->>+Retry: shouldRetry(notification)
        Retry-->>-Consumer: true

        Consumer->>+Retry: getNextDelay(notification)
        Retry-->>-Consumer: 2000ms (exponential + jitter)

        Consumer->>Consumer: markRetryScheduled()
        Consumer->>Queue: schedule re-enqueue after delay
    else 💀 Max retries exhausted
        Consumer->>Consumer: markDeadLettered()
    end
```

### Scheduled Notification Flow

```mermaid
sequenceDiagram
    autonumber
    participant Client as 🌐 External Service
    participant Ctrl as 📡 NotificationController
    participant Sched as ⏰ NotificationScheduler
    participant Orch as ⚙️ NotificationOrchestrator
    participant Queue as 📬 NotificationQueue

    Client->>+Ctrl: POST /api/v1/notifications/schedule
    Ctrl->>+Sched: scheduleNotification(..., scheduledAt)
    Sched->>Sched: Calculate delay = scheduledAt - now
    Sched->>Sched: Schedule future task
    Sched-->>-Ctrl: scheduleId
    Ctrl-->>-Client: 202 Accepted (PENDING)

    Note over Sched: ⏳ Waiting until scheduledAt...

    Sched->>+Orch: sendNotification(...)
    Orch->>Queue: enqueue(notification)
    Orch-->>-Sched: notification

    Note over Queue: Standard processing flow continues
```

---

## 🔄 Notification Lifecycle

```mermaid
stateDiagram-v2
    [*] --> PENDING : Created by Orchestrator

    PENDING --> QUEUED : Enqueued to NotificationQueue
    PENDING --> CANCELLED : Cancelled before queuing

    QUEUED --> PROCESSING : Dequeued by Consumer

    PROCESSING --> SENT : Delivery successful ✅
    PROCESSING --> FAILED : Delivery failed ❌

    FAILED --> RETRY_SCHEDULED : retryPolicy.shouldRetry() = true
    FAILED --> DEAD_LETTERED : Max retries exhausted 💀

    RETRY_SCHEDULED --> QUEUED : After backoff delay

    SENT --> [*]
    DEAD_LETTERED --> [*]
    CANCELLED --> [*]
```

---

## 🔁 Retry Mechanism — Exponential Backoff with Jitter

The retry mechanism uses **exponential backoff with jitter** to prevent the thundering herd problem.

### Formula

```
delay = min(baseDelay × multiplier^retryCount × (1 ± jitterFactor), maxDelay)
```

### Retry Timeline (with defaults)

```mermaid
gantt
    title Retry Timeline (baseDelay=1s, multiplier=2x, jitter=±20%)
    dateFormat X
    axisFormat %s sec

    section Attempt 1
    Send & Fail           :crit, 0, 100
    Backoff ~1.0s         :active, 100, 1100

    section Attempt 2
    Send & Fail           :crit, 1100, 1200
    Backoff ~2.0s         :active, 1200, 3200

    section Attempt 3
    Send & Fail           :crit, 3200, 3300
    Backoff ~4.0s         :active, 3300, 7300

    section Final Attempt
    Send & Fail           :crit, 7300, 7400
    DEAD LETTERED 💀      :done, 7400, 8000
```

### Configuration

| Parameter | Default | Description |
|-----------|---------|-------------|
| `max-retries` | `3` | Maximum retry attempts per notification |
| `base-delay-ms` | `1000` | Initial backoff delay (1 second) |
| `multiplier` | `2.0` | Exponential growth factor |
| `max-delay-ms` | `30000` | Maximum delay cap (30 seconds) |
| `jitter-factor` | `0.2` | ±20% randomization |

### Retryable vs Non-Retryable

| Failure Type | Example | Action |
|---|---|---|
| **Retryable** | SMTP timeout, FCM unavailable, network error | Retry with backoff |
| **Non-retryable** | Invalid email address, missing device token | Dead-letter immediately |

---

## 📐 Class Diagram

```mermaid
classDiagram
    direction TB

    %% ─── Enums ────────────────────────────────────
    class NotificationType {
        <<enumeration>>
        EMAIL
        SMS
        PUSH
        IN_APP
    }

    class NotificationStatus {
        <<enumeration>>
        PENDING
        QUEUED
        PROCESSING
        SENT
        FAILED
        RETRY_SCHEDULED
        DEAD_LETTERED
        CANCELLED
    }

    class NotificationPriority {
        <<enumeration>>
        LOW
        MEDIUM
        HIGH
        CRITICAL
        +int weight
    }

    %% ─── Domain Models ───────────────────────────
    class Notification {
        -String id
        -String userId
        -NotificationType type
        -String subject
        -String body
        -Map~String, String~ metadata
        -NotificationPriority priority
        -NotificationStatus status
        -int retryCount
        -int maxRetries
        -Instant nextRetryAt
        -Instant createdAt
        -String failureReason
        +markQueued()
        +markProcessing()
        +markSent()
        +markFailed(String reason)
        +markRetryScheduled(Instant nextRetry)
        +markDeadLettered()
        +compareTo(Notification) int
        +builder()$ Builder
    }

    class UserPreference {
        -String userId
        -Set~NotificationType~ enabledChannels
        -LocalTime quietHoursStart
        -LocalTime quietHoursEnd
        +isChannelEnabled(NotificationType) boolean
        +isInQuietHours() boolean
    }

    %% ─── Strategy Interface ──────────────────────
    class ChannelProcessor {
        <<interface>>
        +getType() NotificationType
        +supports(NotificationType) boolean
        +process(Notification)
    }

    class AbstractChannelProcessor {
        <<abstract>>
        #Logger log
        -NotificationType type
        +process(Notification) final
        #doSend(Notification)*
    }

    class EmailChannelProcessor {
        +doSend(Notification)
    }

    class SmsChannelProcessor {
        -int SMS_MAX_LENGTH
        +doSend(Notification)
    }

    class PushChannelProcessor {
        +doSend(Notification)
    }

    class InAppChannelProcessor {
        -Map~String, List~ userNotifications
        +doSend(Notification)
        +getNotificationsForUser(String) List
        +getUnreadCount(String) int
        +clearNotificationsForUser(String)
    }

    %% ─── Registry ────────────────────────────────
    class ChannelProcessorRegistry {
        -Map~NotificationType, ChannelProcessor~ processorMap
        +getProcessor(NotificationType) ChannelProcessor
        +hasProcessor(NotificationType) boolean
        +getRegisteredTypes() Set
    }

    %% ─── Queue ───────────────────────────────────
    class NotificationQueue {
        <<interface>>
        +enqueue(Notification)
        +dequeue() Notification
        +size() int
        +isEmpty() boolean
    }

    class InMemoryNotificationQueue {
        -PriorityBlockingQueue~Notification~ queue
        -int capacity
        -AtomicLong totalEnqueued
        -AtomicLong totalDequeued
        +enqueue(Notification)
        +dequeue() Notification
        +getStats() String
    }

    class NotificationQueueConsumer {
        -ExecutorService consumerExecutor
        -ScheduledExecutorService retryScheduler
        -AtomicLong totalProcessed
        -AtomicLong totalSucceeded
        -AtomicLong totalFailed
        -Map~String, Notification~ notificationTracker
        +start()
        +stop()
        +getNotification(String) Notification
        +getMetrics() Map
        -consumeLoop()
        -processNotification(Notification)
        -handleDeliveryFailure(Notification, Exception)
    }

    %% ─── Retry ───────────────────────────────────
    class RetryPolicy {
        <<interface>>
        +shouldRetry(Notification) boolean
        +getNextDelay(Notification) Duration
    }

    class ExponentialBackoffRetryPolicy {
        -int maxRetries
        -long baseDelayMs
        -double multiplier
        -long maxDelayMs
        -double jitterFactor
        +shouldRetry(Notification) boolean
        +getNextDelay(Notification) Duration
    }

    %% ─── Services ────────────────────────────────
    class NotificationOrchestrator {
        <<Facade>>
        +sendNotification(...) Notification
    }

    class UserPreferenceService {
        -Map~String, UserPreference~ preferenceStore
        +getPreference(String) UserPreference
        +isChannelEnabled(String, NotificationType) boolean
        +isInQuietHours(String) boolean
        +updatePreference(UserPreference)
    }

    class NotificationScheduler {
        -ScheduledExecutorService scheduler
        -Map~String, ScheduledFuture~ scheduledTasks
        +scheduleNotification(...) String
        +cancelScheduledNotification(String) boolean
        +getPendingCount() int
        +getMetrics() Map
    }

    %% ─── API Layer ───────────────────────────────
    class NotificationController {
        +sendNotification(NotificationRequest) ResponseEntity
        +scheduleNotification(ScheduleNotificationRequest) ResponseEntity
        +getNotificationStatus(String) ResponseEntity
        +getMetrics() ResponseEntity
    }

    class ChannelDeliveryException {
        -boolean retryable
        +isRetryable() boolean
    }

    %% ─── Relationships ──────────────────────────
    ChannelProcessor <|.. AbstractChannelProcessor : implements
    AbstractChannelProcessor <|-- EmailChannelProcessor : extends
    AbstractChannelProcessor <|-- SmsChannelProcessor : extends
    AbstractChannelProcessor <|-- PushChannelProcessor : extends
    AbstractChannelProcessor <|-- InAppChannelProcessor : extends

    NotificationQueue <|.. InMemoryNotificationQueue : implements
    RetryPolicy <|.. ExponentialBackoffRetryPolicy : implements

    Notification --> NotificationType : has
    Notification --> NotificationStatus : has
    Notification --> NotificationPriority : has
    UserPreference --> NotificationType : contains set of

    ChannelProcessorRegistry --> ChannelProcessor : manages *
    NotificationQueueConsumer --> NotificationQueue : polls
    NotificationQueueConsumer --> ChannelProcessorRegistry : uses
    NotificationQueueConsumer --> RetryPolicy : uses

    NotificationOrchestrator --> UserPreferenceService : checks
    NotificationOrchestrator --> NotificationQueue : enqueues to
    NotificationScheduler --> NotificationOrchestrator : delegates to

    NotificationController --> NotificationOrchestrator : uses
    NotificationController --> NotificationScheduler : uses
    NotificationController --> NotificationQueueConsumer : queries
```

---

## 📁 Package Structure

```
com.notification_service/
│
├── 🔌 api/                              ← REST API Layer
│   ├── controller/
│   │   └── NotificationController        4 REST endpoints
│   ├── dto/
│   │   ├── NotificationRequest           Input DTO (validated)
│   │   ├── NotificationResponse          Output DTO (factory methods)
│   │   └── ScheduleNotificationRequest   Schedule DTO
│   └── exception/
│       ├── GlobalExceptionHandler        Uniform error responses
│       └── NotificationException         Custom exception + error codes
│
├── ⚙️ core/                              ← Domain Layer
│   ├── model/
│   │   ├── Notification                  Domain entity (Builder + Comparable)
│   │   ├── NotificationType              EMAIL | SMS | PUSH | IN_APP
│   │   ├── NotificationStatus            PENDING → QUEUED → SENT / DEAD_LETTERED
│   │   ├── NotificationPriority          LOW | MEDIUM | HIGH | CRITICAL
│   │   └── UserPreference                Per-user channel + quiet hours config
│   └── service/
│       ├── NotificationOrchestrator      Facade — single entry point
│       └── UserPreferenceService         Preference store
│
├── 📡 channel/                            ← Channel Processors (Strategy Pattern)
│   ├── ChannelProcessor                  Strategy interface
│   ├── AbstractChannelProcessor          Template Method base (logging, timing)
│   ├── ChannelDeliveryException          Retryable vs non-retryable errors
│   ├── ChannelProcessorRegistry          Factory — auto-discovers processors
│   ├── email/EmailChannelProcessor       📧 Email delivery
│   ├── sms/SmsChannelProcessor           💬 SMS delivery
│   ├── push/PushChannelProcessor         📱 Push notifications
│   └── inapp/InAppChannelProcessor       🔔 In-app notifications
│
├── 📬 queue/                              ← Notification Queue
│   ├── NotificationQueue                 Interface (swappable for Kafka/RabbitMQ)
│   ├── InMemoryNotificationQueue         PriorityBlockingQueue implementation
│   └── NotificationQueueConsumer         Multi-threaded consumer + retry handler
│
├── 🔁 retry/                              ← Retry Mechanism
│   ├── RetryPolicy                       Interface
│   └── ExponentialBackoffRetryPolicy     Backoff + jitter implementation
│
└── ⏰ scheduler/                          ← Scheduler Service
    └── NotificationScheduler             Delayed/scheduled delivery
```

---

## 🎨 Design Patterns

| Pattern | Where | Why |
|---------|-------|-----|
| **Strategy** | `ChannelProcessor` interface + 4 implementations | Swap notification channels without modifying orchestrator |
| **Template Method** | `AbstractChannelProcessor.process()` calls `doSend()` | Shared logging/timing/error-handling; subclasses only define delivery logic |
| **Factory / Registry** | `ChannelProcessorRegistry` | Auto-discovers all channel beans at startup; O(1) lookup by type |
| **Facade** | `NotificationOrchestrator` | Single entry point hiding queue, preferences, validation complexity |
| **Builder** | `Notification.builder()` | Clean construction of complex domain objects |
| **Observer** | `NotificationQueueConsumer` polling `NotificationQueue` | Decouple notification production from consumption |

---

## ✅ SOLID Principles

| Principle | Implementation |
|-----------|---------------|
| **S** — Single Responsibility | Each class has exactly one job: `EmailChannelProcessor` sends emails, `ChannelProcessorRegistry` resolves processors, `ExponentialBackoffRetryPolicy` calculates delays |
| **O** — Open/Closed | Add a new channel by creating a new `ChannelProcessor` implementation — **zero changes to existing code** |
| **L** — Liskov Substitution | All processors are interchangeable via `ChannelProcessor` interface; all queues via `NotificationQueue` |
| **I** — Interface Segregation | Thin, focused interfaces: `ChannelProcessor` (3 methods), `NotificationQueue` (4 methods), `RetryPolicy` (2 methods) |
| **D** — Dependency Inversion | `NotificationOrchestrator` depends on `NotificationQueue` interface, not `InMemoryNotificationQueue` concrete class |

---

## 📡 API Reference

### 1. Send Notification

```http
POST /api/v1/notifications
Content-Type: application/json
```

**Request Body:**
```json
{
  "userId": "user-123",
  "type": "EMAIL",
  "subject": "Welcome!",
  "body": "Hello, welcome to our platform!",
  "metadata": {
    "email": "user@example.com"
  },
  "priority": "HIGH"
}
```

**Channel-specific `metadata`:**

| Channel | Required Fields |
|---------|----------------|
| `EMAIL` | `"email": "user@example.com"` |
| `SMS` | `"phoneNumber": "+919876543210"` |
| `PUSH` | `"deviceToken": "fcm_xyz"`, `"platform": "ANDROID"` |
| `IN_APP` | *(none required)* |

**Response: `202 Accepted`**
```json
{
  "notificationId": "bed8080e-d485-4048-a83d-d8a30d8c4fd8",
  "status": "QUEUED",
  "type": "EMAIL",
  "message": "Notification accepted and queued for delivery via EMAIL",
  "timestamp": "2026-05-01T19:23:36.814Z"
}
```

---

### 2. Schedule Notification

```http
POST /api/v1/notifications/schedule
Content-Type: application/json
```

**Request Body:**
```json
{
  "userId": "user-123",
  "type": "IN_APP",
  "subject": "Reminder",
  "body": "Your subscription expires tomorrow",
  "metadata": {},
  "priority": "MEDIUM",
  "scheduledAt": "2026-05-02T10:00:00Z"
}
```

**Response: `202 Accepted`**
```json
{
  "notificationId": "schedule-1542f5c0",
  "status": "PENDING",
  "type": "IN_APP",
  "message": "Notification scheduled for delivery at 2026-05-02T10:00:00Z via IN_APP",
  "timestamp": "2026-05-01T19:24:26.991Z"
}
```

---

### 3. Check Status

```http
GET /api/v1/notifications/{id}/status
```

**Response: `200 OK`**
```json
{
  "notificationId": "bed8080e-d485-4048-a83d-d8a30d8c4fd8",
  "status": "SENT",
  "type": "EMAIL",
  "message": "Notification status: SENT",
  "timestamp": "2026-05-01T19:23:55.476Z"
}
```

---

### 4. System Metrics

```http
GET /api/v1/notifications/metrics
```

**Response: `200 OK`**
```json
{
  "message": "Current system metrics",
  "metrics": {
    "totalProcessed": 8,
    "totalSucceeded": 4,
    "totalFailed": 4,
    "totalRetried": 3,
    "totalDeadLettered": 1,
    "queueSize": 0
  }
}
```

---

### Error Responses

**Validation Error: `400 Bad Request`**
```json
{
  "timestamp": "2026-05-01T19:24:32.608Z",
  "status": 400,
  "error": "Bad Request",
  "errorCode": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "details": "body: body is required, userId: userId is required"
}
```

**Queue Full: `503 Service Unavailable`**
```json
{
  "timestamp": "...",
  "status": 503,
  "error": "Service Unavailable",
  "errorCode": "QUEUE_FULL",
  "message": "Notification queue is full (capacity=10000). Notification xyz rejected."
}
```

---

## ⚙️ Configuration

All tunable parameters are externalized in `application.yml`:

```yaml
notification:
  queue:
    capacity: 10000               # Max items in the in-memory queue
    consumer-threads: 4           # Number of concurrent queue consumers

  retry:
    max-retries: 3                # Maximum retry attempts per notification
    base-delay-ms: 1000           # Initial backoff delay (milliseconds)
    multiplier: 2.0               # Exponential backoff multiplier
    max-delay-ms: 30000           # Maximum delay cap (milliseconds)
    jitter-factor: 0.2            # ±20% jitter to prevent thundering herd

  scheduler:
    pool-size: 2                  # Scheduler thread pool size

  async:
    core-pool-size: 4             # Async executor core threads
    max-pool-size: 8              # Async executor max threads
    queue-capacity: 500           # Async executor queue capacity
```

---

## 🚀 Getting Started

### Prerequisites
- Java 17+
- Maven 3.8+

### Build & Run

```bash
# Clone the repository
git clone <repo-url>
cd notification-service

# Build
./mvnw clean compile

# Run
./mvnw spring-boot:run
```

### Quick Test

```bash
# Send an email notification
curl -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-123",
    "type": "EMAIL",
    "subject": "Hello!",
    "body": "Welcome to the notification service!",
    "metadata": {"email": "test@example.com"},
    "priority": "HIGH"
  }'

# Check system metrics
curl http://localhost:8080/api/v1/notifications/metrics
```

---

## 🔌 Adding a New Channel

Adding a new notification channel (e.g., **WhatsApp**) requires **exactly 2 changes** and **zero modifications** to existing code:

### Step 1: Add enum constant

```java
// NotificationType.java
public enum NotificationType {
    EMAIL, SMS, PUSH, IN_APP,
    WHATSAPP  // ← Add this
}
```

### Step 2: Create the processor

```java
@Component
public class WhatsAppChannelProcessor extends AbstractChannelProcessor {

    public WhatsAppChannelProcessor() {
        super(NotificationType.WHATSAPP);
    }

    @Override
    protected void doSend(Notification notification) {
        String phoneNumber = notification.getMetadata().get("phoneNumber");
        // Call WhatsApp Business API here
        log.info("[WHATSAPP] Sent to: {}", phoneNumber);
    }
}
```

**That's it!** The `ChannelProcessorRegistry` auto-discovers it at startup via Spring's component scan. No factory changes, no if-else chains, no registry modifications.

---

## 🛠 Tech Stack

| Technology | Purpose |
|---|---|
| **Spring Boot 4.0.6** | Application framework |
| **Java 17** | Language |
| **Jakarta Validation** | Request validation (`@Valid`, `@NotBlank`) |
| **PriorityBlockingQueue** | In-memory priority queue |
| **ScheduledExecutorService** | Retry scheduling + delayed notifications |
| **SLF4J + Logback** | Structured logging |

---

## 📜 License

This project is open-sourced for educational purposes.

---

<p align="center">
  <b>Built with ❤️ following industry-standard system design principles</b>
</p>
