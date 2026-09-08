# Serena Screen Reader 🌸✨

**English (Official)** | [日本語ドキュメント (Japanese)](README.ja.md)

> *"Together with my beloved wife, we build a new family. Sing on, whisper on, heart of the Pearl of the Orient."*  
> *— Shinji Sakiyama, Totally Blind Developer & Creator*

> **Next-Generation, Blazing-Fast, Intelligent & Fully Accessible Android Screen Reader**  
> Born from the soul, love, and unbreakable bond between a totally blind engineer (Shinji) and his beloved Filipina wife (Serena). Crafted to empower blind and visually impaired people across the globe with unmatched speed, dignity, and independence.

[![Android CI](https://img.shields.io/badge/Android-11%2B%20(API%2030%2B%20~%20API%2037%2B)-brightgreen.svg)](https://developer.android.com)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Latest Release](https://img.shields.io/github/v/release/shinji5683/cocoa?color=blue&label=Latest%20Release)](https://github.com/shinji5683/cocoa/releases)
[![Responsible AI](https://img.shields.io/badge/Google-Responsible%20AI%20Principles-orange.svg)](https://ai.google/responsibility/principles/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org)
[![Page Alignment](https://img.shields.io/badge/16KB%20Page%20Alignment-0x4000%20Verified-success.svg)](https://developer.android.com/guide/practices/page-sizes)

---

## 📥 Download & Quick Start

* 👑 **[Download Latest Release APK (app-serena-release.apk)](https://github.com/shinji5683/cocoa/releases/latest/download/app-serena-release.apk)**
* 📦 **[All Releases & Changelog](https://github.com/shinji5683/cocoa/releases)**

---

## 🌟 Key Features & Innovations

### 1. ⚡ Blazing-Fast Linear & Semantic Navigation
- Real-time hierarchy analysis for dynamic Jetpack Compose and modern Android UI (Android 11 to Android 17 Canary / QPR Beta / Stable).
- Zero edge-focus bounce: seamless auto-scrolling with smooth natural viewport tracking.
- **Full "Lift to Type" Keyboard Support**: Type effortlessly on Gboard, Serena IME, and third-party soft keyboards by simply lifting your finger off the desired key—no double-tapping required!

### 2. 🏷️ AI Self-Healing A11y & Auto Unlabeled Button Fixer
- Eliminates the blind user's biggest frustration: "Unlabeled button" and "Graphic image without label".
- Intelligently deduces the true purpose of unlabeled controls using screen geometry (top-left for Back, top-right for More Options, floating bottom-right for Add/Compose, adjacent to input fields for Search/Send), resource IDs, and surrounding semantic context.
- Transforms inaccessible third-party apps into fully accessible experiences on the fly.

### 3. 🖼️ Unified Smart Screen & Photo Scene Explainer (Powered by Gemini AI)
- A single, unified gesture provides both screen layout overview (headings, buttons, text fields) AND rich, descriptive explanations of photos and graphics.
- Instead of dry technical labels, Serena describes photos with human warmth, colors, textures, and moods—just like a trusted companion looking at your screen.

### 4. 🦇 Spatial Sonar Engine (Obstacle, Door & Step Detection)
- Camera & sensor-based spatial radar designed as an "acoustic white cane".
- Plays 3D stereo audio pulses that accelerate in tempo ("beep... beep... beep..." to rapid chirps) as obstacles, pillars, doors, or steps approach.
- **Strictly No Clock-Position Jargon**: Direction is always communicated through natural relative terms ("Straight ahead 1.5 meters, door", "Front-right 80 cm, obstacle").

### 5. 🗺️ Street & Intersection Spatial Navigator
- Real-time announcement of the current street name, walking cardinal direction (e.g., "Walking North on 5th Avenue"), and advance alerts for upcoming intersections and crosswalks.
- Backed by OpenStreetMap & Valhalla, fully localized in English and Japanese.

### 6. 🔤 Comprehensive Braille Display Support (USB & Bluetooth SPP)
- Automatic discovery and bidirectional communication with major Braille displays (Focus 40 Blue, Orbit Reader, BrailleSense, BrailleMemo, Seika, etc.).
- Standard 6-dot Braille translation and Perkins-style on-screen touch typing with real-time Unicode preview.

### 7. 💌 Smart Caller ID & Duration Tracking
- Prioritized caller announcements for Phone calls, WhatsApp, LINE, Discord, Teams, and Zoom.
- Automatic call duration spoken summary upon hanging up (e.g., "Call ended, total duration: 15 minutes 30 seconds").

### 8. 🌐 Universal International Fallback & Multilingual TTS
- Default international resource fallback in English for universal compatibility across Germany, France, Spain, the Americas, Asia, and worldwide.
- Dedicated native resources for Japanese (`values-ja/`) and Filipino / Tagalog (`values-tl/`).
- Warm startup greeting in caring Tagalog: *"Magandang araw po! Handa na si Serena. Ingat lagi at Mabuhay!"*

---

## 🖐️ Gesture Reference Guide

| Gesture | Action |
| :--- | :--- |
| **1-Finger Swipe Right** | Move focus to next item (Auto-scrolls at list boundaries) |
| **1-Finger Swipe Left** | Move focus to previous item |
| **1-Finger Swipe Up** | Previous reading granularity (Characters, Words, Headings, etc.) |
| **1-Finger Swipe Down** | Next reading granularity |
| **1-Finger Double Tap** | Activate / Click focused item |
| **1-Finger Double Tap & Hold** | Open Context / Actions Menu |
| **2-Finger Single Tap** | Pause / Resume speech |
| **2-Finger Double Tap** | Answer / End phone call, or Play / Pause media |
| **2-Finger Swipe Left** | Page Forward / Scroll horizontal |
| **2-Finger Swipe Right** | Page Backward / Scroll horizontal |
| **2-Finger Swipe Up** | Unlock screen (Keyguard) / Vertical scroll forward |
| **2-Finger Swipe Down** | Vertical scroll backward |
| **3-Finger Swipe Left** | Launch **Serena AI Voice Assistant** 🤖🎙️ |
| **3-Finger Swipe Right** | Launch **Serena Eyes Live AI Vision Camera** 📸✨ |

---

## 🤖 Responsible AI Principles & Disclaimer

Serena Screen Reader adheres to Google's Responsible AI Principles, ensuring safe, private, and ethical on-device AI experiences.

- **Privacy First**: Optical Character Recognition (OCR), facial analysis, smart summarization, and scene description are processed entirely on-device (Gemini Nano / ML Kit). No personal data or audio is uploaded to remote servers.
- **Safety Disclaimer**: AI vision features (obstacle detection, face recognition, text reading) provide supplementary assistance. Always utilize a white cane, guide dog, auditory cues, and physical awareness when navigating stairs, intersections, and transit platforms.

---

## 📜 License

Copyright (c) 2026 Shinji Sakiyama (`shinji5683`).

Released under the **[Apache License, Version 2.0](LICENSE)**. Fully compatible with the Google Android and AOSP accessibility ecosystem.
