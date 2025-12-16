# vtracer

**Low-overhead JVM agent for runtime method tracing (Java 21+)**

Zero code change. Attach to any running Java application. Instantly see method execution times.

> This is a developer tool for backend engineers, SREs, and platform teams who need to understand request performance at the JVM level.

---

## 🎯 Current Status (Day 3 Complete – December 2025)

✅ Day 1: Premain agent with successful load message  
✅ Day 2: ByteBuddy instrumentation – method entry/exit timing  
✅ Day 3: Dynamic attach + real Spring Boot app tracing (Tomcat internals visible)  

<img width="1345" height="656" alt="image" src="https://github.com/user-attachments/assets/f0ce2952-7d56-4348-b4c8-fbc0a862568a" />



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

Example log after dynamic attach:

