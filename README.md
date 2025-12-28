# vtracer

**Low-overhead JVM agent for runtime method tracing and virtual thread pinning detection (Java 21+)**

Zero code change. Attach to any running Java application. Get structured insights into method execution times and virtual thread pinning.

> This is a developer tool for backend engineers, SREs, and platform teams who need to understand request performance at the JVM level.
> 
[![CI](https://github.com/abhishek-mule/vtracer/actions/workflows/ci.yml/badge.svg)](https://github.com/abhishek-mule/vtracer/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-blue)
![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)
![GitHub release](https://img.shields.io/github/v/release/abhishek-mule/vtracer)
![GitHub stars](https://img.shields.io/github/stars/abhishek-mule/vtracer?style=social)

---

## 🎯 Current Status (v0.1.0 Released – December 2025)

✅ Day 1: Premain agent foundation  
✅ Day 2: ByteBuddy instrumentation – method timing  
✅ Day 3: Dynamic attach + Tomcat internals tracing  
✅ Day 4: Virtual thread pinning detection via JFR  
✅ Day 5: 10% sampling + circuit breaker  
✅ Day 6: Structured JSON report on shutdown  
✅ Day 7: Final release with configuration support

Live demo: Attach to running Spring Boot app → see pinning warnings and method timings in JSON report.

---

## 🚀 Features

- **Static & dynamic attach** support
- **Configurable sampling** (YAML file)
- **Selective instrumentation** (include/exclude packages)
- **Virtual thread pinning detection** via JFR
- **Method execution timing**
- **Circuit breaker** (high latency pe tracing disable)
- **Structured JSON report** on JVM shutdown
- Low overhead (< 2% in load tests)

---

## 🛠️ Quick Start (5 Minutes)

### 1. Build the agent
```bash
mvn clean package
```

### 2. Run test Spring Boot app
```bash
cd spring-test-app/springtest/springtest
mvnw spring-boot:run
```

### 3. Find PID
```bash
jps -l
```
Note the PID of SpringtestApplication

### 4. Attach agent
```bash
cd ../../../vtracer
java -cp target/vtracer-1.0.jar com.example.vtracer.AttachTool <PID>
```

### 5. Hit endpoints
- http://localhost:8080/fast
- http://localhost:8080/slow (with synchronized block for pinning)

Watch console for sampled timings and pinning warnings.

Graceful shutdown (Ctrl+C) → check `vtracer-report-*.json`

---

## 📊 Usage Examples with Expected Output

### Static Attach
```bash
java -javaagent:target/vtracer-1.0.jar -jar your-app.jar
```

Expected console output:
```
[vtracer] Agent loaded – sampling rate: 10%, JFR pinning detection enabled
[vtracer] [sampled] Method public String com.example.DemoController.slow() executed in 2005.34 ms
[vtracer] ⚠️ PINNING DETECTED! Thread: virtual-123, Duration: 2000123456 ns
```

### Dynamic Attach
```bash
jps -l
java -cp target/vtracer-1.0.jar com.example.vtracer.AttachTool <PID>
```

Expected output after hitting /slow:
```
[vtracer] Agent successfully attached
[vtracer] ⚠️ PINNING DETECTED! Thread: tomcat-handler-0, Duration: 2005 ns
```

### JSON Report (on graceful shutdown)
File: `vtracer-report-2025-12-17T22-35-24.json`
```json
[
  {
    "type": "method_timing",
    "name": "public String com.example.springtest.DemoController.slow()",
    "durationMs": 2005.34,
    "timestamp": "2025-12-17T22:35:24.227Z"
  },
  {
    "type": "virtual_thread_pinning",
    "name": "tomcat-handler-0",
    "durationMs": 2005.66,
    "timestamp": "2025-12-17T22:35:26.232Z"
  }
]
```

<<<<<<< Updated upstream
### Day 5 – Sampling + Pinning Detection
- 10% sampling implemented (Random decision at method entry)
- JFR VirtualThreadPinned event captured
- Pinning warning with thread name and duration
- Verified with synchronized block in Spring Boot app

Example:
<img width="1318" height="683" alt="image" src="https://github.com/user-attachments/assets/7a8531a9-53da-4253-a96b-204ad10a9b71" />


### Day 6 – JSON Report Generation
- Structured JSON report on JVM shutdown
- Includes sampled method timings and pinning events
- Shutdown hook ensures report is written
- Verified with graceful shutdown
Example:
<img width="1321" height="643" alt="image" src="https://github.com/user-attachments/assets/30b3f141-c0c6-4304-b0ae-7254fdb73534" />

=======
>>>>>>> Stashed changes
---

## 📋 CLI Reference

| Command                        | Description                          | Example                              |
|--------------------------------|--------------------------------------|--------------------------------------|
| `-javaagent:vtracer.jar`       | Static attach at startup            | `java -javaagent:vtracer-1.0.jar -jar app.jar` |
| `AttachTool <PID>`             | Dynamic attach to running JVM       | `java -cp vtracer.jar AttachTool 12345` |

---

## 📄 JSON Report Schema

```json
[
  {
    "type": "method_timing" | "virtual_thread_pinning",
    "name": "method signature or thread name",
    "durationMs": 2005.34,
    "timestamp": "ISO-8601 timestamp"
  }
]
```

---

## 📊 Benchmarks (k6 Load Test – 800 VUs)

| Scenario                  | Baseline p95 | With vtracer p95 | Overhead |
|---------------------------|--------------|------------------|----------|
| /fast endpoint            | 520ms        | 535ms            | ~2.88%   |
| /slow endpoint (2s sleep) | 2010ms       | 2025ms           | ~0.75%   |

Sampling (10%) keeps overhead minimal.

---

## ⚡ Deliberate Omissions

| Feature                     | Why Not?                                      |
|-----------------------------|-----------------------------------------------|
| Distributed tracing         | Out of scope – focus is JVM internals         |
| Async context propagation   | Avoids ThreadLocal leaks with virtual threads |
| Deep stack traces           | Prevents allocation storms                    |
| UI dashboard                | CLI-first for production use                  |
| AI insights                 | We solve real problems, not hype              |

---

## 🔜 Roadmap

- Adaptive sampling
- Flame graph export
- Prometheus metrics
- OpenTelemetry integration
- GraalVM native image support

---

**Built by Abhishek Mule**

Learning JVM internals, one day at a time.

⭐ Star if you're into JVM magic!

<<<<<<< Updated upstream
**Built by Abhishek Mule**  

Learning JVM internals, one day at a time.```
=======
Last updated: v0.1.0 Released (December 2025)
>>>>>>> Stashed changes

