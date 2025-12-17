# vtracer

**Low-overhead JVM agent for runtime method tracing (Java 21+)**

Zero code change. Attach to any running Java application. Instantly see method execution times.

> This is a developer tool for backend engineers, SREs, and platform teams who need to understand request performance at the JVM level.

---

## 🎯 Current Status (Day 3 Complete – December 2025)

✅ Day 1: Premain agent with successful load message  
✅ Day 2: ByteBuddy instrumentation – method entry/exit timing  
✅ Day 3: Dynamic attach + real Spring Boot app tracing (Tomcat internals visible)  

Live demo: Attach to running Spring Boot app → see `Http11Processor.recycle()` took 16.81 ms

---

## 🚀 What vtracer Does Right Now

- **Static attach**: `-javaagent` argument se load
- **Dynamic attach**: Running JVM mein attach (`AttachTool <PID>`)
- Instruments **all methods** (including framework internals)
- Prints method execution time in milliseconds
- Works with platform threads and virtual threads
- Overhead < 2% (tested with k6 at 800 VUs)
- No restart, no code change, no configuration

Example log after dynamic attach::
<img width="1345" height="656" alt="image" src="https://github.com/user-attachments/assets/f0ce2952-7d56-4348-b4c8-fbc0a862568a" />

```
[vtracer] Agent loaded – starting ByteBuddy instrumentation
[vtracer] Instrumentation complete – method timing active!
[vtracer] Method public void org.apache.coyote.http11.Http11Processor.recycle() executed in 16.81 ms
[vtracer] Method public void org.apache.tomcat.util.http.MimeHeaders.recycle() executed in 0.00 ms
```

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
- http://localhost:8080/slow

Watch method timing logs in the Spring Boot console.

---

## 📊 Day-by-Day Progress

### Day 1 – Premain Agent Foundation
- Agent loads via `-javaagent`
- Prints loading message and Instrumentation object
- Verified with TestApp

### Day 2 – ByteBuddy Instrumentation
- Method entry/exit timing using nanoTime
- Fat JAR with shaded ByteBuddy
- Works with static attach
- Verified method duration logging

### Day 3 – Dynamic Attach Success
- `AttachTool` using VirtualMachine API
- Attach to running Spring Boot app
- Real Tomcat method tracing (Http11Processor, MimeHeaders, ByteChunk, etc.)
- Verified with k6 load test (800 VUs, 100% success)

### Day 4 – Virtual Thread Pinning Detection
- JFR RecordingStream with jdk.VirtualThreadPinned event
- Pinning detected in synchronized block
- Warning with thread name and duration
- Verified in Spring Boot with virtual threads enabled

  Example: <img width="1365" height="649" alt="image" src="https://github.com/user-attachments/assets/8e83f39c-3ae2-4db9-8f6e-9132d7099de2" />

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

---

## ⚡ What This Tool Will NOT Do (Deliberate Omissions)

| Feature                     | Why Not?                                      |
|-----------------------------|-----------------------------------------------|
| Distributed tracing         | Out of scope – focus is JVM internals         |
| Async context propagation   | Avoids ThreadLocal leaks with virtual threads |
| Deep stack traces           | Prevents allocation storms                    |
| UI dashboard                | CLI-first for production use                  |
| AI insights                 | We solve real problems, not hype              |

This keeps overhead low and design simple.

---

## 🔜 Next Steps (Week 2–3)

- Virtual thread pinning detection via JFR
- Sampling (10% requests)
- JSON report output
- Overhead circuit breaker (>2% auto-disable)
- Final release v0.1.0

---

**Built by Abhishek Mule**  

Learning JVM internals, one day at a time.```
