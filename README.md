# 📊 MerStats | VEX Robotics Analytics Platform

<div align="center">
  <img src="src/main/resources/com/merstats/vex/logo.png" alt="MerStats Logo" width="150"/>
  
  <br/>
  <strong>Actionable insights and high-performance telemetry for the competitive VEX Robotics community.</strong>
</div>

<br/>

## 🚀 Overview
**MerStats** is a modern, standalone desktop application designed to bridge the gap between complex RobotEvents API endpoints and clean, actionable UI design. Built for teams, coaches, and analysts, this platform provides instant, real-time data on VEX Robotics teams, encompassing historical skills runs, global rankings, and advanced metrics.

Developed from the ground up with **Java** and **JavaFX**, MerStats features a proprietary asynchronous fetching engine that guarantees a frictionless, fluid user experience with zero UI freezing.

## ✨ Core Features
- **⚡ Real-Time Telemetry:** Instantly query any VEX Team ID (e.g., `11017Y`) to pull live, sorted data directly from the RobotEvents servers.
- **📈 Advanced Skills Sorting:** Automatically categorizes and ranks a team's highest-performing Driver and Programming skills runs across multiple seasons and events.
- **🎨 Premium UX/UI:** Features a custom-built routing engine, dynamic CSS styling, responsive floating cards, Mac-style scrollbars, and fluid data-arrival animations.
- **🧠 Smart Caching:** Includes an integrated session-memory system that tracks recent searches via an intuitive contextual dropdown menu.
- **🔒 Secure Architecture:** Built using robust `CompletableFuture` background threading to ensure safe, reliable API transactions and graceful error handling for invalid IDs or network drops.

## 🛠️ Upcoming Modules (In Development)
- **⚔️ Head-to-Head Simulator:** Predictive matchup analytics and historical win-rate comparisons.
- **🏆 Awards Archive:** A visual trophy case tracking Excellence, Champion, and Design awards.
- **🌍 Global Leaderboards:** Dynamic tracking of the highest-rated teams worldwide.

---

## 📥 Installation & Usage

The easiest way to use MerStats is to download the compiled executable.

1. Navigate to the [Releases](../../releases) tab on the right side of this repository.
2. Download the latest `MerStats.exe` file.
3. Double-click to run! *(Note: Requires Java 17 or higher installed on your machine).*

---

## 💻 Developer Setup

If you wish to clone this repository, explore the codebase, or contribute, follow these steps:

### Prerequisites
* **Java Development Kit (JDK) 17+**
* **Apache Maven**
* **IntelliJ IDEA** (Recommended) or VS Code
