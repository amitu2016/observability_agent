# Step 1: Async Kafka Investigation Setup

## Date: 2026-06-08

## Completed Tasks

### 1. Updated `triage-agent/build.gradle`
Added dependencies:
- `implementation 'org.springframework.boot:spring-boot-starter-data-jpa'`
- `implementation 'org.springframework.kafka:spring-kafka'`
- `runtimeOnly 'org.postgresql:postgresql'`
- `testImplementation 'org.springframework.kafka:spring-kafka-test'`

### 2. Created Directory Structure
- `triage-agent/src/main/java/com/example/triageagent/model/`
- `triage-agent/src/main/java/com/example/triageagent/repository/`

### 3. Created `InvestigationJob.java`
- JPA Entity with UUID primary key
- Fields: id, question, status (PENDING/RUNNING/COMPLETED/FAILED), createdAt, completedAt, reportJson
- Maps to `investigation_jobs` table

### 4. Created `InvestigationJobRepository.java`
- Extends `JpaRepository<InvestigationJob, UUID>`
- Provides CRUD operations for InvestigationJob entities

### 5. Updated `application-test.yml`
- Added `spring.kafka.bootstrap-servers: localhost:9092`
- Added `spring.kafka.listener.auto-startup: false` (listeners disabled during unit tests)

## Verification
```
./gradlew :triage-agent:compileJava
BUILD SUCCESSFUL
```

## Next Steps
- Step 2: Create Kafka producer/consumer configuration and REST API for job submission