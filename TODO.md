# 🚀 Project Roadmap & Graph Memory TODO List

This document outlines active development goals, planned architecture improvements, and feature roadmap items for the **OCR MCQ Solver & Smart Vision Stealth Suite**.

---

## 📌 Active Development Priorities (High Priority)

### 📲 1. QR Code Direct Launch into Native OmniSolve Android App
- **Goal:** Enable the QR code displayed in the Chrome Extension Options dashboard (`Tab 3: Mobile Sync`) to directly launch and pair the native installed **OmniSolve Android Application** (`com.omnisolve.overlay`), continuing the exact same seamless solve and vibration path natively without requiring a mobile browser.
- **Why this is critical:**
  - **Background Pocket Vibrations:** Mobile browsers (Chrome/Safari) throttle or disable the Web Vibration API when the screen locks or when the tab is backgrounded. A native Android service (`FOREGROUND_SERVICE` + `Vibrator` / `VibrationEffect.Composition`) can vibrate silently in the user's pocket even with the screen fully turned off.
  - **Zero-Latency WebSocket Bridge:** The Android app connects directly via a persistent WebSocket (`ws://<LAN_IP>:59124/ws/client`), receiving answer packets in under 15ms.
  - **Dual Display Modes:** User can choose between:
    1. **Pocket Haptics Mode:** Screen black/off, silent discrete vibration pulses.
    2. **Dynamic Island Mode:** Floating Apple-inspired pill widget overlay displaying answer and confidence.
- **Architecture & Implementation Steps:**
  - [x] **Deep Link Intent Filter in AndroidManifest:** Registered `omnisolve://sync` scheme in [app/src/main/AndroidManifest.xml](file:///c:/Users/VIRUS/Downloads/mcq%20bypass/app/src/main/AndroidManifest.xml).
  - [ ] **Intent Receiver in MainActivity:** Parse `intent.data` inside `MainActivity.kt` on `onResume()` / `onNewIntent()` to extract companion server IP and WebSocket port.
  - [ ] **Background Sync Service:** Forward the connection parameters to `OverlayService.kt` to maintain persistent WebSocket sync.
  - [ ] **Extension Options Selector:** Add a toggle in [chrome-extension/options.html](file:///c:/Users/VIRUS/Downloads/mcq%20bypass/chrome-extension/options.html) to let user generate either:
    - `Browser QR Code` (`http://<LAN_IP>:59124/mobile`)
    - `Native OmniSolve App QR Code` (`omnisolve://sync?host=<LAN_IP>&port=59124`)

---

## 🍏 2. iOS Native Companion & Safari Web Extension (Coming Soon)
- **Status:** In active development! 🚀
- **Target Platform:** iOS 16.0+ (iPhone, iPad).
- **Core Features:**
  - **CoreHaptics Ultra-Crisp Tactile Feedback:** Custom haptic waveforms mimicking exact taps (1 tap for A, 2 for B, 3 for C, 4 for D).
  - **Native Safari Web Extension:** Complete feature parity with the Chrome Manifest v3 extension, including Coded Symbol Icons, Innocent Hover Tooltips, and Canvas Vision.
  - **Deep-Link Pairing:** `omnisolve-ios://sync?host=...` QR code integration for instant iOS pairing.

---

## 🎨 3. Stealth Suite Enhancements (Completed in v1.9)
- [x] **Innocent Hover Camouflage Tooltip:**
  - Fixed toolbar mouseover tooltip that previously leaked "Smart Vision (Click to solve MCQ)".
  - Replaced with customizable, innocent disguises (e.g., `"Google Translate - Web Page Translation"`, `"Volume Master - Sound Equalizer"`, `"Page Magnifier & Display Tools"`, etc.).
  - Added One-Click Auto-Match and quick preset pills.
- [x] **Coded Symbol Icons System:**
  - Replaced letter badges with innocent everyday symbol icons:
    - **Choice A:** ✈️ Airplane, 🍎 Apple, ⚓ Anchor, 🔺 Triangle
    - **Choice B:** 🎈 Balloon, ⚽ Ball, 📖 Book, 🟦 Square
    - **Choice C:** ☁️ Cloud, ⏰ Clock, ☕ Coffee Cup, 🟢 Circle
    - **Choice D:** 💎 Diamond, 📄 Document, 🎲 Dice, ⭐ Star
  - Full customization dropdowns with live image previews in the Options UI.
  - One-click Theme Presets (Alphabet, Tools, Shapes, Everyday).
  - Active Tab Test buttons.
- [x] **Full 64-Icon Asset Matrix:** Generated 16 symbol sets across 16x16, 32x32, 48x48, and 128x128 in [chrome-extension/icons/](file:///c:/Users/VIRUS/Downloads/mcq%20bypass/chrome-extension/icons/).

---

## 💾 4. Packaging & Backup Archives
- Latest versioned archive: [backups/mcq_bypass_v1.9_coded_symbols_and_camouflage_tooltips.zip](file:///c:/Users/VIRUS/Downloads/mcq%20bypass/backups/mcq_bypass_v1.9_coded_symbols_and_camouflage_tooltips.zip)
- Rolling latest release: [backups/mcq_bypass_latest_release.zip](file:///c:/Users/VIRUS/Downloads/mcq%20bypass/backups/mcq_bypass_latest_release.zip)
