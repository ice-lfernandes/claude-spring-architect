# Tool decision matrix — scheduling in the Java/Spring ecosystem

Reference for `jobs-architect`, read at step 4 of its procedure. Researched on 2026-09-30;
every status or version claim carries its source. **No version number here is to be copied
into a build file** — coordinates only, the version comes from the Boot parent or is resolved
at runtime (`@CLAUDE.md` invariant 8). Where a coordinate differs per Boot major, both are
named and the project's `pom.xml` parent decides.

---

## 1. The tools

### 1.1 `@Scheduled` (Spring Framework)

In-process scheduling on a `TaskScheduler`. `fixedDelay` (after completion), `fixedRate`
(regardless of completion), `cron` (with `zone`). Programmatic triggers through
`SchedulingConfigurer`.

| | |
|---|---|
| Single run across instances | **No** — every instance runs every job |
| Catch-up of a missed fire | No — the schedule lives in memory |
| Dynamic schedules | Only through `SchedulingConfigurer`, and it is clumsy |
| Retry | No |
| Ops surface | `/actuator/scheduledtasks`; observation `tasks.scheduled.execution` **only when** an `ObservationRegistry` is set on the `ScheduledTaskRegistrar` through a `SchedulingConfigurer` |
| Infra | None |
| Coordinates | none beyond `spring-boot-starter` |
| License | Apache-2.0 |

Traps: without virtual threads the default pool size is **1**, so two jobs serialize
(`spring.task.scheduling.pool.size`). With `spring.threads.virtual.enabled=true` Boot uses a
`SimpleAsyncTaskScheduler` on virtual threads and ignores the pool properties.

Sources: <https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html>,
<https://docs.spring.io/spring-framework/reference/integration/observability.html>

### 1.2 ShedLock

A **lock**, not a scheduler: wraps an existing `@Scheduled` method so one instance runs it per
period. `@EnableSchedulerLock(defaultLockAtMostFor = …)`, `@SchedulerLock(name, lockAtMostFor,
lockAtLeastFor)`, `LockAssert.assertLocked()` in the body. JDBC provider with `usingDbTime()`
reads the database clock instead of each node's.

| | |
|---|---|
| Single run across instances | **Yes**, best effort — bounded by `lockAtMostFor` |
| Catch-up | No |
| Dynamic schedules | No |
| Retry | No |
| Ops surface | None |
| Infra | One table (`shedlock`: `name`, `lock_until`, `locked_at`, `locked_by`) |
| Coordinates | `net.javacrumbs.shedlock:shedlock-spring`, `net.javacrumbs.shedlock:shedlock-provider-jdbc-template` |
| License | Apache-2.0 |

Source: <https://github.com/lukas-krecan/ShedLock>

### 1.3 Quartz

Full scheduler: persistent triggers, clustering over a shared JDBC store, runtime-created
schedules, misfire instructions. `@DisallowConcurrentExecution` stops concurrent runs of one
job across the cluster; `@PersistJobDataAfterExecution` keeps `JobDataMap` mutations.

| | |
|---|---|
| Single run across instances | **Yes** — `spring.quartz.job-store-type=jdbc` + `org.quartz.jobStore.isClustered=true` |
| Catch-up | **Yes** — misfire instruction per trigger (threshold 60 s by default on the persistent store) |
| Dynamic schedules | **Yes**, native |
| Retry | Limited — refire from the job, or reschedule |
| Ops surface | None built in |
| Infra | ~11 `QRTZ_*` tables; `spring.quartz.jdbc.initialize-schema` or a migration |
| Coordinates | `org.springframework.boot:spring-boot-starter-quartz` |
| License | Apache-2.0 |

In a project that versions its schema with migrations, the `QRTZ_*` DDL is a migration like any
other and `initialize-schema` stays `never` — the tables are a schema requirement for the
persistence partial.

### 1.4 Spring Batch

Chunk-oriented (read/process/write) or tasklet jobs over a `JobRepository`. Restart from the
last committed chunk; skip and retry policies per item; partitioning and remote chunking to
scale. A `JobInstance` is keyed by job name + identifying `JobParameters`: rerunning with the
same parameters is refused once complete.

| | |
|---|---|
| Single run across instances | Through the `JobRepository` — one execution per `JobInstance` |
| Catch-up | Restart of a failed execution; scheduling itself comes from another tool |
| Dynamic schedules | No — Batch runs jobs, it does not schedule them |
| Restart from point of failure | **Yes** — its purpose |
| Retry | **Yes**, per item |
| Ops surface | `spring.batch.job.enabled` (auto-run at startup — `false` when a scheduler launches it) |
| Infra | `BATCH_*` metadata tables, or none with the resourceless repository |
| Coordinates | `org.springframework.boot:spring-boot-starter-batch` |
| License | Apache-2.0 |

**Spring Batch 6 (GA 2025-11-19, with Boot 4):** `DefaultBatchConfiguration` defaults to a
resourceless `JobRepository` (no run history, no restart); `JobBuilderFactory` and
`StepBuilderFactory` are **removed** — build with `new JobBuilder(name, repository)` /
`new StepBuilder(name, repository)`; `JobExplorer` is deprecated (`JobRepository` extends it);
`JobOperator` extends `JobLauncher`. Restartability needs the JDBC repository explicitly.

Sources: <https://spring.io/blog/2025/11/19/spring-batch-6-0-0-ga/>,
<https://github.com/spring-projects/spring-batch/wiki/Spring-Batch-6.0-Migration-Guide>

### 1.5 db-scheduler

Persistent, cluster-safe scheduler on **one table** (`scheduled_tasks`). Recurring and one-time
tasks; one-time tasks can be created at runtime with data. Polling strategy
`fetch-and-lock-on-execute` (default, any database) or `lock-and-fetch` with `SELECT … FOR
UPDATE SKIP LOCKED` (PostgreSQL, SQL Server, MySQL 8+; for high throughput).

| | |
|---|---|
| Single run across instances | **Yes** — row lock per execution |
| Catch-up | Partial — an overdue execution runs on the next poll |
| Dynamic schedules | **Yes** (one-time tasks with data) |
| Retry | Limited — `FailureHandler` (e.g. backoff, max retries) per task |
| Ops surface | None built in |
| Infra | One table |
| Coordinates | `com.github.kagkarlsson:db-scheduler-spring-boot-starter` (Boot 3) · `com.github.kagkarlsson:db-scheduler-spring-boot-4-starter` (Boot 4) |
| License | Apache-2.0. Needs a recent JDK — check the minimum on the release resolved |

Source: <https://github.com/kagkarlsson/db-scheduler>

### 1.6 JobRunr

Background and recurring jobs as lambdas or annotated methods, persisted in the application's
database, retried with exponential backoff, with a web dashboard.

| | |
|---|---|
| Single run across instances | **Yes** |
| Catch-up | Partial — a recurring job re-evaluates on the next poll |
| Dynamic schedules | **Yes** — `enqueue`, `schedule`, `scheduleRecurrently` at runtime |
| Retry | **Yes**, exponential backoff by default |
| Ops surface | **Dashboard** (separate port) |
| Infra | Its own tables, auto-created |
| Coordinates | the JobRunr Spring Boot starter matching the Boot major (`org.jobrunr:jobrunr-spring-boot-3-starter` for Boot 3; check the Boot 4 artifact at runtime) |
| License | **LGPL-3.0** for OSS; **JobRunr Pro** is commercial. OSS limits recurring jobs (100) |

**The license is a question, not a footnote.** LGPL is acceptable in most organizations and
refused in some; the recurring-job cap and Pro-only features (priority queues, batches,
workflows) are what the choice buys into. Ask before choosing it.

Source: <https://www.jobrunr.io/en/documentation/getting-started/spring/>

### 1.7 Without a template — named so the interview can rule them out

| Tool | Why it is in the matrix | Why no template |
|---|---|---|
| **Kubernetes `CronJob`** / external scheduler (EventBridge Scheduler, Airflow, Temporal) | Scheduling leaves the process: a pod runs, does the job, exits. `concurrencyPolicy: Forbid` avoids overlaps | Not application code — the job is a use case with a CLI or HTTP entry point, and the schedule is platform configuration. Record the choice in the partial; nothing for the executor to generate here |
| **Spring Cloud Task** | Run history for short-lived tasks | Still maintained, but no case needs it until the schedule is external — then it records runs of a `CronJob`. Adds its own tables |
| **Spring Cloud Data Flow** | Orchestration UI over tasks | **OSS ended** — 2.11.x was the last open-source line, April 2025; commercial only since. Not an option for a generated project. <https://spring.io/blog/2025/04/21/spring-cloud-data-flow-commercial/> |
| **Spring Modulith event publication registry** | Replaces a hand-rolled outbox + relay for Spring application events, with resubmission of incomplete publications | A **relay form** decision, not a scheduling one — it belongs to the messaging partial. When chosen there, the relay job below does not exist |
| **Debezium CDC** | Reads the transaction log instead of polling the outbox | Same — a relay form decided in the messaging partial; it removes the polling job and adds a Kafka Connect service |

---

## 2. Decision matrix — scenario against tool

Read the rows the interview answered. **The first tool that satisfies every "must" row wins**;
tie → the one already in the project's `pom.xml`, then the one with less infrastructure.

| Scenario | `@Scheduled` | `@Scheduled` + ShedLock | Quartz (JDBC, clustered) | Spring Batch | db-scheduler | JobRunr |
|---|---|---|---|---|---|---|
| One instance, by written deployment constraint | ✅ | overkill | overkill | if volume | ✅ | ✅ |
| Many instances, pass must not run twice | ❌ | ✅ | ✅ | ✅ (per instance key) | ✅ | ✅ |
| Many instances, **work partitioned by row claim** (locked or leased claim) | ✅ — the claim coordinates | redundant | overkill | — | overkill | overkill |
| A missed fire must be caught up | ❌ | ❌ | ✅ | ✅ (restart) | partial | partial |
| Schedules created at runtime from data | ❌ | ❌ | ✅ | ❌ | ✅ | ✅ |
| Large volume, restart from point of failure | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| Per-execution retry with backoff | ❌ | ❌ | limited | ✅ (per item) | ✅ | ✅ |
| Operators need a UI (pause, trigger, inspect) | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| Fire-and-forget background task from a use case | weak | ❌ | possible | ❌ | ✅ | ✅ |
| Extra tables | 0 | 1 | ~11 | ~9 or 0 | 1 | several |

Composition, not competition: **Spring Batch runs a job, something else fires it.** A Batch job
on a schedule is Batch plus one of the other columns — usually `@Scheduled` + ShedLock, or
Quartz when the trigger itself must survive downtime.

## 3. The outbox's two jobs

| Job | Default shape | When it changes |
|---|---|---|
| **Relay** — claims pending rows, sends, marks | `@Scheduled(fixedDelay)` calling the relay's inbound port. Coordination comes from the claim: a locked or leased claim lets every instance run the pass; an unlocked claim needs a written single-instance constraint **or** ShedLock around the trigger | The messaging partial chose Modulith events or CDC — no relay job at all |
| **Prune** — deletes published rows older than the retention window | `@Scheduled(cron, zone)` off-peak, deleting in batches until a pass deletes fewer than the batch size. Idempotent by nature, so a double run on two instances only costs a query — still locked when a lock mechanism already exists in the project | Retention decided as "keep forever" in the persistence partial, with its reason — then no prune job, and the personal-data consequence is recorded there |

**Under another technology.** The relay stays on `@Scheduled` even when the project chose
Quartz or db-scheduler for its calendar jobs — its coordination is the claim, and a persistent
scheduler would write its store every second for nothing (`@.claude/rules/scheduling.md`
§ Boundary). The prune follows the project's technology: under Quartz or db-scheduler it is a
job of that scheduler, and its lock is that scheduler's, not ShedLock.

The relay's poll interval is this skill's; its batch size and attempt ceiling are the
persistence partial's (the claim query and the columns encode them). The retention window is
the persistence partial's; the prune job's cadence and batch are this skill's.
