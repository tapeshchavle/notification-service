<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-4.0.6-brightgreen?style=for-the-badge&logo=springboot" alt="Spring Boot"/>
  <img src="https://img.shields.io/badge/Java-17+-orange?style=for-the-badge&logo=openjdk" alt="Java"/>
  <img src="https://img.shields.io/badge/Architecture-Event--Driven-blue?style=for-the-badge" alt="Architecture"/>
  <img src="https://img.shields.io/badge/Design%20Patterns-6-purple?style=for-the-badge" alt="Patterns"/>
</p>

# 🔔 Notification Service

> A **production-grade, scalable notification service** supporting Email, SMS, Push, and In-App channels with priority queuing, exponential backoff retry, scheduled delivery, and user preference management.

Built following **SOLID principles** and industry-standard design patterns — adding a new channel requires **zero changes** to existing code.

---

## 📑 Table of Contents

- [High-Level Architecture](#-high-level-architecture)
- [System Interaction Flow](#-system-interaction-flow)
- [Notification Lifecycle](#-notification-lifecycle)
- [Retry Mechanism](#-retry-mechanism)
- [Class Diagram](#-class-diagram)
- [Package Structure](#-package-structure)
- [Design Patterns & SOLID](#-design-patterns--solid-principles)
- [Demo API Reference](#-demo-api-reference)
- [Configuration](#%EF%B8%8F-configuration)
- [Getting Started](#-getting-started)
- [Adding a New Channel](#-adding-a-new-channel)

---

## 🏗 High-Level Architecture

```mermaid
graph TB
    subgraph External["🌐 External Services"]
        S1["Service 1<br/>(Order Service)"]
        S2["Service 2<br/>(Auth Service)"]
        S3["Service N"]
    end

    subgraph NS["📦 Notification Service"]
        direction TB
        NC["🔌 NotificationController<br/>REST API"]
        NO["⚙️ NotificationOrchestrator<br/>(Facade)"]
        UPS["👤 UserPreferenceService"]
        NQ["📬 NotificationQueue<br/>(PriorityBlockingQueue)"]
        NQC["🔧 QueueConsumer<br/>(Multi-threaded)"]
        REG["🗂️ ProcessorRegistry<br/>(Auto-Discovery)"]
        RP["🔁 RetryPolicy<br/>(Exponential Backoff)"]
        DL["💀 Dead Letter"]
        SCH["⏰ Scheduler"]
    end

    subgraph Channels["📡 Channel Processors"]
        EP["📧 Email"]
        SP["💬 SMS"]
        PP["📱 Push"]
        IP["🔔 In-App"]
    end

    S1 & S2 & S3 --> NC
    NC --> NO
    NC --> SCH
    NO --> UPS
    NO --> NQ
    SCH --> NO
    NQ --> NQC
    NQC --> REG
    NQC --> RP
    RP -->|"retry"| NQ
    RP -->|"exhausted"| DL
    REG --> EP & SP & PP & IP

    style NS fill:#e8f5e9,stroke:#4caf50,color:#000
    style Channels fill:#e0f2f1,stroke:#009688,color:#000
    style External fill:#fff3cd,stroke:#ffc107,color:#000
```

---

## 🔄 System Interaction Flow

```mermaid
sequenceDiagram
    autonumber
    participant C as 🌐 Client
    participant Ctrl as 📡 Controller
    participant Orch as ⚙️ Orchestrator
    participant Pref as 👤 Preferences
    participant Q as 📬 Queue
    participant W as 🔧 Worker
    participant Reg as 🗂️ Registry
    participant P as 📧 Processor
    participant R as 🔁 Retry

    C->>+Ctrl: POST /api/v1/notifications
    Ctrl->>+Orch: sendNotification(request)
    Orch->>Pref: isChannelEnabled? isInQuietHours?
    Pref-->>Orch: ✅ allowed
    Orch->>Q: enqueue(notification)
    Orch-->>-Ctrl: QUEUED
    Ctrl-->>-C: 202 Accepted

    W->>+Q: dequeue() [blocking]
    Q-->>-W: notification
    W->>Reg: getProcessor(type)
    Reg-->>W: processor
    W->>+P: process(notification)

    alt ✅ Success
        P-->>-W: OK → markSent()
    else ❌ Retryable Failure
        P-->>W: ChannelDeliveryException
        W->>R: shouldRetry? → getNextDelay()
        W->>Q: re-enqueue after backoff delay
    else 💀 Max Retries Exhausted
        W->>W: markDeadLettered()
    end
```

---

## 🔄 Notification Lifecycle

```mermaid
stateDiagram-v2
    [*] --> PENDING : Created
    PENDING --> QUEUED : Enqueued
    PENDING --> CANCELLED : Cancelled
    QUEUED --> PROCESSING : Dequeued by worker
    PROCESSING --> SENT : ✅ Delivered
    PROCESSING --> FAILED : ❌ Error
    FAILED --> RETRY_SCHEDULED : shouldRetry = true
    FAILED --> DEAD_LETTERED : 💀 Max retries
    RETRY_SCHEDULED --> QUEUED : After backoff
    SENT --> [*]
    DEAD_LETTERED --> [*]
    CANCELLED --> [*]
```

---

## 🔁 Retry Mechanism

**Formula:** `delay = baseDelay × multiplier^retryCount × (1 ± jitterFactor)`

| Attempt | Delay (approx.) | Status |
|---------|-----------------|--------|
| 1st fail | ~1000ms | RETRY_SCHEDULED |
| 2nd fail | ~2000ms | RETRY_SCHEDULED |
| 3rd fail | ~4000ms | RETRY_SCHEDULED |
| 4th fail | — | 💀 DEAD_LETTERED |

| Config | Default | Description |
|--------|---------|-------------|
| `max-retries` | `3` | Max retry attempts |
| `base-delay-ms` | `1000` | Initial backoff (1s) |
| `multiplier` | `2.0` | Exponential factor |
| `max-delay-ms` | `30000` | Max delay cap (30s) |
| `jitter-factor` | `0.2` | ±20% randomization |

---

## 📐 Class Diagram

```mermaid
classDiagram
    direction TB

    class NotificationType {
        <<enum>>
        EMAIL
        SMS
        PUSH
        IN_APP
    }

    class NotificationStatus {
        <<enum>>
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
        <<enum>>
        LOW
        MEDIUM
        HIGH
        CRITICAL
    }

    class Notification {
        -String id
        -String userId
        -NotificationType type
        -NotificationPriority priority
        -NotificationStatus status
        -int retryCount
        -int maxRetries
        +markQueued()
        +markSent()
        +markFailed(reason)
        +markRetryScheduled(nextRetry)
        +markDeadLettered()
        +compareTo(other) int
    }

    class NotificationRequest {
        <<record>>
        +String userId
        +NotificationType type
        +String subject
        +String body
        +Map metadata
        +NotificationPriority priority
    }

    class NotificationResponse {
        <<record>>
        +String notificationId
        +NotificationStatus status
        +NotificationType type
        +String message
        +success()$ NotificationResponse
        +scheduled()$ NotificationResponse
        +withMetrics()$ NotificationResponse
    }

    class UserPreference {
        <<record>>
        +String userId
        +Set enabledChannels
        +LocalTime quietHoursStart
        +LocalTime quietHoursEnd
        +isChannelEnabled(type) boolean
        +isInQuietHours() boolean
    }

    class ChannelProcessor {
        <<interface>>
        +getType() NotificationType
        +supports(type) boolean
        +process(notification)
    }

    class AbstractChannelProcessor {
        <<abstract>>
        +process(notification) final
        #doSend(notification)*
    }

    class EmailChannelProcessor { +doSend(notification) }
    class SmsChannelProcessor { +doSend(notification) }
    class PushChannelProcessor { +doSend(notification) }
    class InAppChannelProcessor { +doSend(notification) }

    class ChannelProcessorRegistry {
        -Map processorMap
        +getProcessor(type) ChannelProcessor
        +hasProcessor(type) boolean
    }

    class NotificationQueue {
        <<interface>>
        +enqueue(notification)
        +dequeue() Notification
        +size() int
    }

    class InMemoryNotificationQueue {
        -PriorityBlockingQueue queue
        +enqueue(notification)
        +dequeue() Notification
    }

    class RetryPolicy {
        <<interface>>
        +shouldRetry(notification) boolean
        +getNextDelay(notification) Duration
    }

    class ExponentialBackoffRetryPolicy {
        -int maxRetries
        -long baseDelayMs
        -double multiplier
        -double jitterFactor
        +shouldRetry(notification) boolean
        +getNextDelay(notification) Duration
    }

    class NotificationQueueConsumer {
        +start()
        +stop()
        +getNotification(id) Notification
        +getMetrics() Map
    }

    class NotificationOrchestrator {
        <<Facade>>
        +sendNotification(...) Notification
    }

    class NotificationController {
        +sendNotification(req) ResponseEntity
        +scheduleNotification(req) ResponseEntity
        +getNotificationStatus(id) ResponseEntity
        +getMetrics() ResponseEntity
    }

    ChannelProcessor <|.. AbstractChannelProcessor
    AbstractChannelProcessor <|-- EmailChannelProcessor
    AbstractChannelProcessor <|-- SmsChannelProcessor
    AbstractChannelProcessor <|-- PushChannelProcessor
    AbstractChannelProcessor <|-- InAppChannelProcessor
    NotificationQueue <|.. InMemoryNotificationQueue
    RetryPolicy <|.. ExponentialBackoffRetryPolicy
    ChannelProcessorRegistry --> ChannelProcessor
    NotificationQueueConsumer --> NotificationQueue
    NotificationQueueConsumer --> ChannelProcessorRegistry
    NotificationQueueConsumer --> RetryPolicy
    NotificationOrchestrator --> NotificationQueue
    NotificationOrchestrator --> UserPreference
    NotificationController --> NotificationOrchestrator
```

---

## 📁 Package Structure

```
com.notification_service/
├── api/
│   ├── controller/NotificationController.java    ← 4 REST endpoints
│   ├── dto/
│   │   ├── NotificationRequest.java              ← record (validated)
│   │   ├── NotificationResponse.java             ← record (factory methods)
│   │   └── ScheduleNotificationRequest.java      ← record
│   └── exception/
│       ├── GlobalExceptionHandler.java            ← Uniform error responses
│       └── NotificationException.java
├── channel/
│   ├── ChannelProcessor.java                      ← Strategy interface
│   ├── AbstractChannelProcessor.java              ← Template Method
│   ├── ChannelProcessorRegistry.java              ← Factory/Registry
│   ├── ChannelDeliveryException.java
│   ├── email/EmailChannelProcessor.java
│   ├── sms/SmsChannelProcessor.java
│   ├── push/PushChannelProcessor.java
│   └── inapp/InAppChannelProcessor.java
├── core/
│   ├── model/
│   │   ├── Notification.java                      ← Domain entity (Builder)
│   │   ├── NotificationType.java                  ← EMAIL|SMS|PUSH|IN_APP
│   │   ├── NotificationStatus.java                ← Full lifecycle
│   │   ├── NotificationPriority.java              ← LOW→CRITICAL
│   │   └── UserPreference.java                    ← record (immutable)
│   └── service/
│       ├── NotificationOrchestrator.java           ← Facade
│       └── UserPreferenceService.java
├── queue/
│   ├── NotificationQueue.java                     ← Interface
│   ├── InMemoryNotificationQueue.java             ← PriorityBlockingQueue
│   └── NotificationQueueConsumer.java             ← Multi-threaded worker
├── retry/
│   ├── RetryPolicy.java                           ← Interface
│   └── ExponentialBackoffRetryPolicy.java         ← Backoff + jitter
└── scheduler/
    └── NotificationScheduler.java                 ← Delayed delivery
```

---

## 🎨 Design Patterns & SOLID Principles

| Pattern | Where | Purpose |
|---------|-------|---------|
| **Strategy** | `ChannelProcessor` | Pluggable channel implementations |
| **Template Method** | `AbstractChannelProcessor` | Shared logging/timing; subclasses define `doSend()` |
| **Factory/Registry** | `ChannelProcessorRegistry` | Auto-discovers processors via Spring DI |
| **Facade** | `NotificationOrchestrator` | Single entry point hiding complexity |
| **Builder** | `Notification.builder()` | Clean entity construction |

| SOLID | How |
|-------|-----|
| **S** – Single Responsibility | Each class has one job |
| **O** – Open/Closed | New channel = new class, zero existing changes |
| **L** – Liskov Substitution | All processors interchangeable via interface |
| **I** – Interface Segregation | Thin interfaces: `ChannelProcessor`(3), `RetryPolicy`(2) |
| **D** – Dependency Inversion | Depend on `NotificationQueue` interface, not concrete class |

---

## 📡 Demo API Reference

> **Base URL:** `http://localhost:8080/api/v1/notifications`

### 1. 📧 Send Email Notification

```bash
curl -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-001",
    "type": "EMAIL",
    "subject": "Welcome to Our Platform!",
    "body": "Hi John, welcome aboard! Your account has been successfully created.",
    "metadata": {
      "email": "john.doe@example.com"
    },
    "priority": "HIGH"
  }'
```

**Response `202 Accepted`:**
```json
{
    "notificationId": "039976ad-1091-4b3b-8595-6d55b762e453",
    "status": "QUEUED",
    "type": "EMAIL",
    "message": "Notification accepted and queued for delivery via EMAIL",
    "timestamp": "2026-05-01T20:23:04.371309Z",
    "metrics": null
}
```

---

### 2. 💬 Send SMS Notification

```bash
curl -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-002",
    "type": "SMS",
    "subject": "OTP Verification",
    "body": "Your OTP is 847293. Valid for 5 minutes. Do not share this code.",
    "metadata": {
      "phoneNumber": "+919876543210"
    },
    "priority": "CRITICAL"
  }'
```

**Response `202 Accepted`:**
```json
{
    "notificationId": "a400e42b-442f-45d6-91a5-3afb40eb553c",
    "status": "QUEUED",
    "type": "SMS",
    "message": "Notification accepted and queued for delivery via SMS",
    "timestamp": "2026-05-01T20:23:07.848124Z",
    "metrics": null
}
```

---

### 3. 📱 Send Push Notification

```bash
curl -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-003",
    "type": "PUSH",
    "subject": "New Message from Sarah",
    "body": "Hey! Are you coming to the meeting at 3 PM today?",
    "metadata": {
      "deviceToken": "dGhpcyBpcyBhIHRlc3QgZGV2aWNlIHRva2Vu",
      "platform": "ANDROID"
    },
    "priority": "MEDIUM"
  }'
```

**Response `202 Accepted`:**
```json
{
    "notificationId": "a44ead7b-d532-401d-866c-cf4e8aaea054",
    "status": "QUEUED",
    "type": "PUSH",
    "message": "Notification accepted and queued for delivery via PUSH",
    "timestamp": "2026-05-01T20:23:10.185252Z",
    "metrics": null
}
```

---

### 4. 🔔 Send In-App Notification

```bash
curl -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-004",
    "type": "IN_APP",
    "subject": "Achievement Unlocked 🏆",
    "body": "Congratulations! You earned the Gold Contributor badge for 100 contributions.",
    "metadata": {},
    "priority": "LOW"
  }'
```

**Response `202 Accepted`:**
```json
{
    "notificationId": "6f243be2-06db-4091-97ee-70bbdd4f23f7",
    "status": "QUEUED",
    "type": "IN_APP",
    "message": "Notification accepted and queued for delivery via IN_APP",
    "timestamp": "2026-05-01T20:23:10.239269Z",
    "metrics": null
}
```

---

### 5. ⏰ Schedule Email for Later

```bash
curl -X POST http://localhost:8080/api/v1/notifications/schedule \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-005",
    "type": "EMAIL",
    "subject": "Payment Reminder",
    "body": "Your subscription payment of $9.99 is due tomorrow.",
    "metadata": {
      "email": "billing@example.com"
    },
    "priority": "HIGH",
    "scheduledAt": "2026-05-02T10:00:00Z"
  }'
```

**Response `202 Accepted`:**
```json
{
    "notificationId": "schedule-853c2d46",
    "status": "PENDING",
    "type": "EMAIL",
    "message": "Notification scheduled for delivery at 2026-05-02T10:00:00Z via EMAIL",
    "timestamp": "2026-05-01T20:23:23.111432Z",
    "metrics": null
}
```

---

### 6. ⏰ Schedule SMS for Later

```bash
curl -X POST http://localhost:8080/api/v1/notifications/schedule \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-006",
    "type": "SMS",
    "subject": "Appointment",
    "body": "Reminder: You have a dentist appointment at 4:30 PM today.",
    "metadata": {
      "phoneNumber": "+14155551234"
    },
    "priority": "MEDIUM",
    "scheduledAt": "2026-05-02T16:00:00Z"
  }'
```

**Response `202 Accepted`:**
```json
{
    "notificationId": "schedule-38207fb1",
    "status": "PENDING",
    "type": "SMS",
    "message": "Notification scheduled for delivery at 2026-05-02T16:00:00Z via SMS",
    "timestamp": "2026-05-01T20:23:23.128766Z",
    "metrics": null
}
```

---

### 7. 🔍 Check Notification Status

```bash
curl http://localhost:8080/api/v1/notifications/039976ad-1091-4b3b-8595-6d55b762e453/status
```

**Response `200 OK`:**
```json
{
    "notificationId": "039976ad-1091-4b3b-8595-6d55b762e453",
    "status": "SENT",
    "type": "EMAIL",
    "message": "Notification status: SENT",
    "timestamp": "2026-05-01T20:23:34.005403Z",
    "metrics": null
}
```

---

### 8. 📊 System Metrics

```bash
curl http://localhost:8080/api/v1/notifications/metrics
```

**Response `200 OK`:**
```json
{
    "notificationId": null,
    "status": null,
    "type": null,
    "message": "Current system metrics",
    "timestamp": "2026-05-01T20:23:53.008070Z",
    "metrics": {
        "totalProcessed": 10,
        "totalSucceeded": 6,
        "totalFailed": 4,
        "totalRetried": 3,
        "totalDeadLettered": 1,
        "queueSize": 0
    }
}
```

---

### 9. 🔁 Retry Demo (Simulated Failure → Dead Letter)

```bash
curl -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-retry",
    "type": "EMAIL",
    "subject": "Retry Demo",
    "body": "This notification will fail and trigger exponential backoff retry",
    "metadata": {
      "email": "retry@demo.com",
      "simulateFailure": "true"
    },
    "priority": "HIGH"
  }'
```

**Server logs show exponential backoff:**
```
Attempt 1 → FAILED → Retry scheduled (delay: ~1041ms)
Attempt 2 → FAILED → Retry scheduled (delay: ~1924ms)
Attempt 3 → FAILED → Retry scheduled (delay: ~4268ms)
Attempt 4 → FAILED → Max retries exhausted → DEAD_LETTERED 💀
```

**Status check after retries:**
```json
{
    "notificationId": "ccd3b309-d2d2-48c7-b954-dba224711ec3",
    "status": "DEAD_LETTERED",
    "type": "EMAIL",
    "message": "Notification status: DEAD_LETTERED",
    "timestamp": "2026-05-01T20:23:53.070437Z",
    "metrics": null
}
```

---

### 10. ❌ Validation Error

```bash
curl -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{"userId": "", "body": ""}'
```

**Response `400 Bad Request`:**
```json
{
    "timestamp": "2026-05-01T20:23:34.102040Z",
    "status": 400,
    "error": "Bad Request",
    "errorCode": "VALIDATION_FAILED",
    "message": "Request validation failed",
    "details": "body: body is required, type: type is required (EMAIL, SMS, PUSH, IN_APP), userId: userId is required"
}
```

---

### 11. 🔍 Not Found

```bash
curl http://localhost:8080/api/v1/notifications/nonexistent-id/status
```

**Response `404 Not Found`:**
```json
{
    "notificationId": null,
    "status": null,
    "type": null,
    "message": "Notification not found: nonexistent-id",
    "timestamp": "2026-05-01T20:23:34.126873Z",
    "metrics": null
}
```

---

### Channel-Specific Metadata Reference

| Channel | Required `metadata` Fields | Example |
|---------|---------------------------|---------|
| `EMAIL` | `email` | `{"email": "user@example.com"}` |
| `SMS` | `phoneNumber` | `{"phoneNumber": "+919876543210"}` |
| `PUSH` | `deviceToken`, `platform` | `{"deviceToken": "abc123", "platform": "ANDROID"}` |
| `IN_APP` | *(none required)* | `{}` |

### Priority Levels

| Priority | Weight | Use Case |
|----------|--------|----------|
| `CRITICAL` | 4 | OTPs, security alerts |
| `HIGH` | 3 | Transactional emails, payment reminders |
| `MEDIUM` | 2 | Messages, updates |
| `LOW` | 1 | Achievements, marketing |

---

## ⚙️ Configuration

All parameters in `application.yml`:

```yaml
notification:
  queue:
    capacity: 10000
    consumer-threads: 4
  retry:
    max-retries: 3
    base-delay-ms: 1000
    multiplier: 2.0
    max-delay-ms: 30000
    jitter-factor: 0.2
  scheduler:
    pool-size: 2
```

---

## 🚀 Getting Started

```bash
# Prerequisites: Java 17+, Maven 3.8+

# Build
./mvnw clean compile

# Run
./mvnw spring-boot:run

# Quick test
curl -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{"userId":"test","type":"EMAIL","body":"Hello!","metadata":{"email":"test@example.com"}}'
```

---

## 🔌 Adding a New Channel

**Step 1:** Add enum constant
```java
public enum NotificationType {
    EMAIL, SMS, PUSH, IN_APP,
    WHATSAPP  // ← new
}
```

**Step 2:** Create processor (done!)
```java
@Component
public class WhatsAppChannelProcessor extends AbstractChannelProcessor {
    public WhatsAppChannelProcessor() { super(NotificationType.WHATSAPP); }

    @Override
    protected void doSend(Notification notification) {
        String phone = notification.getMetadata().get("phoneNumber");
        // WhatsApp Business API call here
    }
}
```

**Zero changes** to any existing class. Auto-discovered by the Registry at startup.

---

## 🛠 Tech Stack

| Technology | Purpose |
|---|---|
| Spring Boot 4.0.6 | Application framework |
| Java 17 (Records) | Language + modern features |
| Jakarta Validation | Request validation |
| PriorityBlockingQueue | Priority-aware async queue |
| ScheduledExecutorService | Retry scheduling + delayed delivery |

---

<p align="center"><b>Built with ❤️ following industry-standard system design principles</b></p>
