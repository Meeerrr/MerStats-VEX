# 📊 MerStats | VEX Robotics Analytics Platform

<div align="center">
  <img src="src/main/resources/com/merstats/vex/logo.png" alt="MerStats Logo" width="150"/>
  
  <br/>
  <strong>Actionable insights, predictive matchmaking, and high-performance telemetry for the competitive VEX Robotics community.</strong>
  <br/><br/>
  
  <img src="https://img.shields.io/badge/version-2.3.0-blue.svg" alt="Version 2.3.0"/>
  <img src="https://img.shields.io/badge/build-passing-brightgreen.svg" alt="Build Status"/>
  <img src="https://img.shields.io/badge/Java-17%2B-ED8B00?logo=java&logoColor=white" alt="Java 17"/>
  <img src="https://img.shields.io/badge/Python-3.10%2B-3776AB?logo=python&logoColor=white" alt="Python 3.10"/>
  <img src="https://img.shields.io/badge/Database-Supabase-3ECF8E?logo=supabase&logoColor=white" alt="Supabase"/>
</div>

<br/>

## 🚀 Overview
**MerStats** is a comprehensive, full-stack analytics platform designed to bridge the gap between complex RobotEvents API endpoints and clean, actionable data visualization. Built for elite teams, coaches, and analysts, this platform provides real-time telemetry, historical skills tracking, and predictive event scouting.

Driven by a proprietary **Python & Supabase Data Lake** backend and visualized through a high-performance **JavaFX Client** (with an upcoming Web application), MerStats completely replaces standard descriptive statistics with a mathematically pure, chronologically strict predictive engine. 

---

## ✨ Core Features

### 🧠 TrueRank Predictive Engine (v2.3.0)
Replaces standard Win/Loss records with a dynamic, chronological 2v2 Elo algorithm. 
* **Single-Pass Timeline:** Strictly processes matches in chronological order to prevent multi-pass "time loop" Elo bleed.
* **The "Worlds Shield":** Asymmetric event weighting mathematically rewards teams for surviving the World Championship (`2.0x` win / `0.5x` loss multiplier) instead of punishing them for playing hard schedules.
* **Logarithmic MOV & OPR Synergy:** Integrates a capped `1.20x` Margin of Victory curve and a `1.10x` Rolling OPR synergy bonus to accurately reward offensive dominance without allowing stat-farming against empty fields.

### ⚡ Real-Time Telemetry & Smart Pipeline
* **Python ETL Workers:** Our backend `fast_worker.py` and `raw_data_builder.py` scripts autonomously scrape the RobotEvents v2 API, clean the data, and upsert it directly into our cloud database.
* **Supabase Integration:** Powered by a massive PostgreSQL database acting as a high-speed JSONB data lake, ensuring the client receives instant query responses for the global leaderboard without rate-limiting.

### 🌍 Global Event Leaderboards & Local Analytics
Instantly aggregate and rank every single team across massive tournaments. The client-side Java engine calculates localized Offensive Power Ratings (OPR) using Apache Commons Math and Singular Value Decomposition (SVD) to isolate true robot performance at specific events.

### 🎨 Premium UX/UI Client
The Java 17 desktop client features a custom-built routing engine, dynamic CSS styling, responsive floating cards, true native Dark Mode, and fluid asynchronous data-arrival animations to ensure zero UI freezing.

---

## 🗺️ Upcoming Modules (The v2.4+ Roadmap)
MerStats is currently expanding its feature set based on our active Agile development board:

* **🌐 MerStats Web:** A full frontend web application (React/Next.js) allowing teams to access the TrueRank leaderboards from their mobile browsers during tournaments.
* **📸 Social & Scouting Feed:** Unverified team ID claiming, robot reveal image boards, and CAD model sharing to build a unified VEX community hub.
* **📈 Advanced Event Analytics:** The "Schedule Luck" quantifier, Live Alliance Synergy predictors, and intra-day momentum tracking for ultimate weekend scouting.
* **🏆 The Hardware Archive:** A visual trophy case tracking Excellence, Tournament Champion, and Design awards, including custom platform-specific badges.

---

## 📥 Installation & Usage (Desktop Client)

The easiest way to use MerStats is to download the compiled executable.

1. Navigate to the **[Releases](../../releases)** tab on the right side of this repository.
2. Download the latest `MerStats-vX.X.X.zip` file.
3. Unzip the file to your desired folder.
4. Double-click the `MerStats.exe` file to launch the platform!

---

## 💻 Developer Setup

If you wish to clone this repository, run the Python backend, or contribute to the UI, follow these steps to set up your local environment:

### Prerequisites
* **Java Development Kit (JDK) 17+**
* **Apache Maven**
* **Python 3.10+** (For the backend ETL pipeline)
