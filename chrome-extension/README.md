# 🛡️ Smart Vision Solver — Stealth MCQ Chrome Extension

A specialized, zero-footprint browser extension designed to analyze multiple-choice questions (MCQs) directly from your screen with **maximum stealth**.

---

## ⚡ Architecture v1.3: Dedicated In-Extension Gemini Window

This version provides the **exact separate browser window architecture as your Android mobile application**:
1. **Dedicated Standalone Window (Zero Tabs in Your Browser)**:
   - Does **NOT** open, pin, or touch any tabs in your main browser tab strip.
   - Operates in its own dedicated standalone popup window (`type: "popup"`).
   - Because it runs in its own window, **Chrome never throttles or suspends its background execution** (fixing the issue where you had to switch tabs to make it proceed).
2. **Direct Google Login**:
   - You log in to your personal Gmail account inside the dedicated window once.
   - All questions are solved for free with unlimited capacity through your Gemini session.
3. **Precision MCQ Extractor**:
   - Accurately captures only the question sentence and options (A, B, C, D), stripping out search queries, AI chat prompts, and site navigation.
4. **Hardware Caps Lock Signaling**:
   - Blinks your physical **Caps Lock** LED according to the answer:
     - **Choice A** ➔ 1 blink
     - **Choice B** ➔ 2 blinks
     - **Choice C** ➔ 3 blinks
     - **Choice D** ➔ 4 blinks
   - Automatically restores Caps Lock to OFF after blinking.

---

## 📂 Folder Structure

```
chrome-extension/
  ├── manifest.json              # Chrome Manifest V3 configuration
  ├── background.js             # Manages dedicated standalone window & LED signaling
  ├── gemini_content_script.js  # Runs on gemini.google.com with ProseMirror-safe text insertion
  ├── extract_text_script.js    # Precision line extractor targeting question & options A-D
  ├── options.html              # Settings interface with dedicated window status & controls
  ├── options.css               # Glassmorphic dark styling
  ├── options.js                # Dedicated window manager & settings controller
  ├── generate_icons.py         # Icon asset generator
  ├── icons/                    # Crisp 16/32/48/128px stealth & letter icons
  └── companion/                # Physical Keyboard LED Blinker Servers (Caps Lock default)
        ├── keyboard_led_blinker.py   # Pure Python (Zero pip dependencies)
        └── keyboard_led_blinker.ps1  # Zero-install PowerShell script for Windows
```

---

## 🚀 Setup & Usage

1. Open `chrome://extensions/` and click **↻ (Reload)** on **Smart Vision Solver**.
2. Right-click the extension icon and select **🪟 Show Dedicated Gemini Window**.
3. Sign into your Gmail account inside that window once. You can keep this window behind your main window or minimized.
4. On any page with an MCQ (like Perplexity or an exam site), **click the toolbar icon** (or press <kbd>Alt</kbd> + <kbd>Shift</kbd> + <kbd>S</kbd>):
   - The question will be solved inside the dedicated window.
   - The toolbar icon will morph into **A, B, C, or D**.
   - Your **Caps Lock** key light will blink the corresponding count.
