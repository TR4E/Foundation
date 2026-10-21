<h1 align="center">Injector-Scheduler-Extension</h1>

<p align="center">Repeating component methods with configurable synchronous and asynchronous execution.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Injector extension" src="https://img.shields.io/badge/Injector-Extension-C71A36?logo=apachemaven&logoColor=white"></a>
</p>

Scheduler discovers `@Scheduler` methods on Injector components and dispatches them at a fixed rate. Applications choose the executors through `SchedulerCallback`, allowing the same component to run in a service, desktop application, or platform with a dedicated main thread.

## Contents

<details>
<summary>On this page</summary>

- [Quick start](#quick-start)
- [Schedule settings](#schedule-settings)
- [Execution and threading](#execution-and-threading)
- [Application executors](#application-executors)
- [Lifecycle and failures](#lifecycle-and-failures)
- [Installation and build](#installation-and-build)

</details>

## Quick start

```java
@Singleton
public final class ReportService {

    @Scheduler(period = 30, unit = TimeUnit.SECONDS, asynchronous = true)
    private void refresh() {
        // Refresh the application's report data.
    }

    @Scheduler(period = 1, unit = TimeUnit.HOURS, clock = true)
    private void summarize() {
        // Publish the periodic summary.
    }
}
```

Import `me.trae.foundation.injector.extensions.scheduler.annotation.Scheduler`. The example uses Lombok and omits imports. The class must be discovered as a component or supplied explicitly; a scheduled method alone does not register its declaring class.

## Schedule settings

| Attribute | Declared default | Effective behavior |
| --- | --- | --- |
| `period` | Required | Interval between fixed-rate dispatches |
| `unit` | Required | `TimeUnit` for the period and initial delay |
| `initialDelay` | `0` | A positive value is used; otherwise the first run waits one period |
| `clock` | `false` | Align the first run to the next epoch-based period boundary |
| `asynchronous` | `false` | Choose the application's asynchronous executor when true |

Periods are converted to milliseconds and must remain positive after conversion. Sub-millisecond periods are rejected with `SchedulerException`. Scheduled methods must take no parameters; private methods are supported. Methods on superclasses are scanned too.

With `clock = true`, clock alignment determines the initial delay and takes precedence over `initialDelay`. An hourly period aligns to an epoch-hour boundary. This is interval alignment using the system clock, rather than a cron expression or a timezone-aware calendar schedule.

## Execution and threading

One lazily created daemon platform thread named `injector-scheduler` keeps time for the extension and submits each execution to the chosen executor.

| Execution mode | Default executor | Consequence |
| --- | --- | --- |
| Synchronous | `Runnable::run` | The method runs on the scheduler thread and blocks other dispatches until it finishes |
| Asynchronous | A new virtual thread per run | Dispatch continues while earlier executions are still working |

Fixed-rate dispatch does not wait for work submitted to an asynchronous executor. Executions of the same method can overlap when they take longer than their period. Use component-level coordination or an executor with the required serialization when overlap is unsuitable.

## Application executors

Implement both `ApplicationCallback` and `SchedulerCallback` on the application class to route executions to application-owned executors:

```java
@AllArgsConstructor
@Application
public final class DesktopApplication implements ApplicationCallback, SchedulerCallback {

    private final Executor mainExecutor;
    private final Executor backgroundExecutor;

    @Override
    public Executor getSynchronousExecutor() {
        return this.mainExecutor;
    }

    @Override
    public Executor getAsynchronousExecutor() {
        return this.backgroundExecutor;
    }
}
```

Pass the platform's main-thread dispatcher and a suitable background executor when constructing this application. The application owns their lifecycle. The extension does not shut down externally supplied executors.

## Lifecycle and failures

Methods are collected as components are created and scheduled after the application initializes. Attaching components schedules their new methods after initialization. Removing a component cancels its future dispatches with `cancel(false)`, allowing an already running execution to finish. The timing executor shuts down when no scheduled components remain.

Exceptions thrown by a scheduled method are reported through the current thread's uncaught exception handler; later runs continue. Executor submission failures occur outside that method wrapper, so custom executors should remain available while their application is running.

This module supplies scheduling only. Use [Commons](../Injector-Commons-Extension/README.md) if a component also needs annotated startup or shutdown methods.

## Installation and build

Add this dependency alongside Injector-API and runtime Injector-Core:

```xml
<dependency>
    <groupId>me.trae.foundation.injector.extensions</groupId>
    <artifactId>Injector-Scheduler-Extension</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

From the repository root with JDK 25 and Maven:

```shell
mvn -pl Injector/Injector-Extensions/Injector-Scheduler-Extension -am install
```

Tests need no external services. Return to the [extensions overview](../README.md) or [Injector guide](../../README.md).
