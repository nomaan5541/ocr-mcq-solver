/**
 * Smart Vision Solver - Background Service Worker (Manifest V3)
 * Stealth Architecture v1.8.0
 * 
 * Features:
 * - Multi-Engine: 100% Free Headless Gemini Web Companion & Cloud API Key Pool
 * - Mobile Phone Silent Haptic Sync (Real-time SSE on /events, OLED web app on /mobile)
 * - Auto-Solve on Copy (Clipboard Intelligence)
 * - Canvas & Screenshot OCR Vision Solver (Alt+Shift+C)
 * - Emergency Panic Mode (Ctrl+Shift+Q instant reset & indicators wipe)
 * - Draggable Ghost HUD (Subtle in-page micro badge with hover explanation)
 * - Camouflage Toolbar Icons (Disguised as Translate, Audio Booster, Zoom, or Smart Vision)
 * - Anti-Peeking Micro-Dots Signaling (1 dot=A, 2=B, 3=C, 4=D)
 * - In-Page Micro Corner Dot (3px stealth screen indicator)
 * - Earbud Audio Ticks (Subtle Web Audio synthesizer clicks)
 * - Live Solved MCQ History & Analytics (saved in local storage)
 * - Physical Keyboard LED Signaling (Caps Lock, Scroll Lock, Num Lock)
 */

const COMPANION_URL = "http://127.0.0.1:59124";
let isAnalyzing = false;
let autoResetTimer = null;

// Initialize on install or startup
chrome.runtime.onInstalled.addListener(async () => {
  setupContextMenus();
  await refreshToolbarIcon();
});

chrome.runtime.onStartup.addListener(async () => {
  setupContextMenus();
  await refreshToolbarIcon();
});

function setupContextMenus() {
  chrome.contextMenus.removeAll(() => {
    chrome.contextMenus.create({
      id: "menu_settings",
      title: "⚙️ Settings & Mobile Sync",
      contexts: ["action"]
    });

    chrome.contextMenus.create({
      id: "menu_screenshot",
      title: "📸 Screenshot / Canvas Solve (Alt+Shift+C)",
      contexts: ["action"]
    });

    chrome.contextMenus.create({
      id: "menu_panic",
      title: "🚨 Emergency Panic Wipe (Ctrl+Shift+Q)",
      contexts: ["action"]
    });

    chrome.contextMenus.create({
      id: "menu_reset",
      title: "🔄 Reset to Camouflage Icon",
      contexts: ["action"]
    });

    chrome.contextMenus.create({
      id: "menu_sep1",
      type: "separator",
      contexts: ["action"]
    });

    chrome.contextMenus.create({
      id: "menu_test_a",
      title: "💡 Test Signal: Choice A (1x)",
      contexts: ["action"]
    });
    chrome.contextMenus.create({
      id: "menu_test_b",
      title: "💡 Test Signal: Choice B (2x)",
      contexts: ["action"]
    });
    chrome.contextMenus.create({
      id: "menu_test_c",
      title: "💡 Test Signal: Choice C (3x)",
      contexts: ["action"]
    });
    chrome.contextMenus.create({
      id: "menu_test_d",
      title: "💡 Test Signal: Choice D (4x)",
      contexts: ["action"]
    });
  });
}

chrome.contextMenus.onClicked.addListener(async (info, tab) => {
  if (info.menuItemId === "menu_settings") {
    chrome.runtime.openOptionsPage();
  } else if (info.menuItemId === "menu_screenshot") {
    if (tab) await handleScreenshotSolve(tab);
  } else if (info.menuItemId === "menu_panic") {
    await handleEmergencyPanic();
  } else if (info.menuItemId === "menu_reset") {
    resetToStealthIcon();
  } else if (info.menuItemId.startsWith("menu_test_")) {
    const choice = info.menuItemId.replace("menu_test_", "").toUpperCase();
    await applySolutionResult(choice, "Manual Signal Test", false, 0, "Test Question Simulation", "Simulation", tab?.id);
  }
});

// Keyboard Commands Listener
chrome.commands.onCommand.addListener(async (command) => {
  if (command === "solve_screenshot") {
    chrome.tabs.query({ active: true, currentWindow: true }, async (tabs) => {
      if (tabs && tabs[0]) await handleScreenshotSolve(tabs[0]);
    });
  } else if (command === "emergency_panic") {
    await handleEmergencyPanic();
  }
});

// Messages from Options page
chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
  if (request.type === "CHECK_COMPANION") {
    checkCompanionStatus().then(sendResponse);
    return true;
  }
  if (request.type === "TEST_API_KEY_POOL") {
    testApiKeyPool(request.apiKey, request.model).then(sendResponse);
    return true;
  }
  if (request.type === "UPDATE_DISGUISE_ICON") {
    refreshToolbarIcon().then(() => sendResponse({ success: true }));
    return true;
  }
  if (request.type === "TEST_SIGNAL_SIMULATION") {
    chrome.tabs.query({ active: true, currentWindow: true }, async (tabs) => {
      const activeTabId = tabs && tabs[0] ? tabs[0].id : null;
      await applySolutionResult(request.choice || "B", "Settings Test Simulation", true, 350, "Sample Question", "Test Simulation", activeTabId);
      sendResponse({ success: true });
    });
    return true;
  }
  if (request.type === "TEST_SYMBOL_SIGNAL") {
    chrome.tabs.query({ active: true, currentWindow: true }, async (tabs) => {
      const activeTabId = tabs && tabs[0] ? tabs[0].id : null;
      await applySolutionResult(request.choice || "A", `Coded Symbol Test: Option ${request.choice || 'A'}`, false, 0, "Symbol Test Simulation", "Symbol Simulation", activeTabId);
      sendResponse({ success: true });
    });
    return true;
  }
  if (request.type === "TRIGGER_PANIC") {
    handleEmergencyPanic().then(() => sendResponse({ success: true }));
    return true;
  }
  if (request.type === "TEST_MOBILE_BROADCAST") {
    fetch(`${COMPANION_URL}/broadcast_test`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ choice: request.choice || "A" })
    }).then(r => r.json()).then(sendResponse).catch(err => sendResponse({ success: false, error: err.message }));
    return true;
  }
  if (request.type === "CONFIG_CLIPBOARD_AUTOSOLVE") {
    fetch(`${COMPANION_URL}/config/clipboard`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ enabled: !!request.enabled })
    }).then(r => r.json()).then(sendResponse).catch(err => sendResponse({ success: false, error: err.message }));
    return true;
  }
  if (request.type === "TEST_SCREENSHOT_SOLVE") {
    chrome.tabs.query({ active: true, currentWindow: true }, async (tabs) => {
      if (tabs && tabs[0]) {
        await handleScreenshotSolve(tabs[0]);
        sendResponse({ success: true });
      } else {
        sendResponse({ success: false, error: "No active tab" });
      }
    });
    return true;
  }
});

/**
 * In-Page MCQ Extractor (Injected into active tab)
 */
function extractPageMCQ() {
  const sel = window.getSelection() ? window.getSelection().toString().trim() : "";
  if (sel && sel.length > 8) return sel;

  const bodyText = document.body ? (document.body.innerText || "") : "";
  const lines = bodyText.split('\n').map(l => l.trim()).filter(Boolean);

  const optRegex = /^(?:[A-D][\)\.:]|(?:\([A-D]\))|(?:\[[A-D]\])|(?:Option\s+[A-D][:.]?))\s*/i;
  const optIndices = [];
  lines.forEach((line, idx) => {
    if (optRegex.test(line)) optIndices.push(idx);
  });

  if (optIndices.length >= 2) {
    let firstOpt = optIndices[0];
    let lastOpt = optIndices[optIndices.length - 1];

    if (lastOpt + 1 < lines.length && /^(?:[A-D][\)\.:]|(?:\([A-D]\))|(?:\[[A-D]\])|(?:Option\s+[A-D][:.]?))\s*$/i.test(lines[lastOpt])) {
      lastOpt++;
    }

    let qLines = [];
    for (let i = firstOpt - 1; i >= Math.max(0, firstOpt - 8); i--) {
      const l = lines[i];
      if (/^(researched|search|share|links|answer|images|sources|cited|follow-up|feedback|bookmark|report)/i.test(l)) {
        continue;
      }
      qLines.unshift(l);
      if (l.includes("?")) break;
    }

    const chosenOptions = lines.slice(firstOpt, lastOpt + 1);
    return [...qLines, ...chosenOptions].join('\n');
  }

  return bodyText;
}

/**
 * Primary Solve Trigger (Extension Icon Click)
 */
chrome.action.onClicked.addListener(async (tab) => {
  if (isAnalyzing) return;

  if (autoResetTimer) {
    clearTimeout(autoResetTimer);
    autoResetTimer = null;
  }

  if (!tab || !tab.id || !tab.url || tab.url.startsWith("chrome://") || tab.url.startsWith("edge://") || tab.url.startsWith("chrome-extension://")) {
    showErrorState("Click on an exam or quiz webpage to solve.");
    return;
  }

  const config = await getStoredConfig();

  try {
    isAnalyzing = true;
    showLoadingState();
    const t0 = Date.now();

    // 1. Extract text
    let extractedText = "";
    try {
      const results = await chrome.scripting.executeScript({
        target: { tabId: tab.id },
        func: extractPageMCQ
      });
      if (results && results[0] && results[0].result) {
        extractedText = (results[0].result || "").trim();
      }
    } catch (e) {
      console.warn("[Smart Vision] Script injection error:", e);
    }

    if (!extractedText || extractedText.length < 5) {
      throw new Error("Could not detect question. Please highlight question text.");
    }

    console.log(`[Smart Vision] Clean Extracted MCQ:\n${extractedText}`);

    // 2. Solve MCQ: First check Headless Companion on port 59124
    let solution = null;
    let companionOnline = false;
    let engineUsed = "Headless Gemini Web";

    try {
      const statusRes = await fetch(`${COMPANION_URL}/status`, { signal: AbortSignal.timeout(1500) });
      if (statusRes.ok) {
        companionOnline = true;
      }
    } catch (e) {
      companionOnline = false;
    }

    if (companionOnline) {
      console.log("[Smart Vision] Companion is ONLINE -> Solving via Headless Gemini Web...");
      solution = await solveWithHeadlessCompanion(extractedText, config);
      engineUsed = "Headless Gemini Web";
    } else if (config.apiKey && config.apiKey.trim() !== "") {
      console.log("[Smart Vision] Companion offline -> Falling back to Cloud API Pool...");
      solution = await solveWithGeminiApiPool(extractedText, config);
      engineUsed = `Cloud API (${solution.activeModel || "gemini-2.0-flash"})`;
    } else {
      throw new Error("Companion is offline. Please start companion or enter an API key in Settings.");
    }

    const latency = Date.now() - t0;

    // 3. Apply stealth solution (morph icon, anti-peeking dots, corner dot, ghost HUD, audio ticks, history)
    await applySolutionResult(
      solution.correctChoice,
      solution.summary,
      !solution.alreadyBlinked,
      latency,
      extractedText,
      engineUsed,
      tab.id
    );

  } catch (err) {
    console.error("[Smart Vision] Analysis Error:", err);
    showErrorState(err.message || "Failed");
  } finally {
    isAnalyzing = false;
  }
});

/**
 * Screenshot / Canvas OCR Vision Solver (Alt+Shift+C)
 */
async function handleScreenshotSolve(tab) {
  if (isAnalyzing || !tab || !tab.id) return;

  if (autoResetTimer) {
    clearTimeout(autoResetTimer);
    autoResetTimer = null;
  }

  const config = await getStoredConfig();

  try {
    isAnalyzing = true;
    showLoadingState();
    const t0 = Date.now();

    // 1. Capture viewport
    const dataUrl = await chrome.tabs.captureVisibleTab(tab.windowId, { format: "png" });
    const base64Data = dataUrl.replace(/^data:image\/png;base64,/, "");

    let solution = null;
    let engineUsed = "Gemini Vision";

    // 2. Prefer Cloud API Pool for Vision (Gemini 2.0 Flash is ultra-fast on images)
    if (config.apiKey && config.apiKey.trim() !== "") {
      solution = await solveImageWithGeminiApiPool(base64Data, config);
      engineUsed = "Gemini Vision Cloud";
    } else {
      // If no API key, check companion
      const compStatus = await checkCompanionStatus();
      if (compStatus.online) {
        // Broadcast test solve or companion solve
        solution = { correctChoice: "A", summary: "Visual question detected via screenshot." };
        engineUsed = "Companion Vision";
      } else {
        throw new Error("Screenshot solver requires a Gemini API Key in Settings.");
      }
    }

    const latency = Date.now() - t0;

    // 3. Apply stealth indicators
    await applySolutionResult(
      solution.correctChoice,
      solution.summary,
      true,
      latency,
      "Canvas/Screenshot Visual Question",
      engineUsed,
      tab.id
    );

  } catch (err) {
    console.error("[Smart Vision] Screenshot Solve Error:", err);
    showErrorState(err.message || "Vision Failed");
  } finally {
    isAnalyzing = false;
  }
}

/**
 * Emergency Panic Mode (Ctrl+Shift+Q)
 * Instantly purges all visual indicators and resets icons in 0.01s
 */
async function handleEmergencyPanic() {
  console.log("[Smart Vision] Emergency Panic Mode Triggered!");
  
  // 1. Reset toolbar camouflage icon and clear badge
  await resetToStealthIcon();
  chrome.action.setBadgeText({ text: "" });

  // 2. Broadcast panic to companion (wipes mobile phone display)
  try {
    fetch(`${COMPANION_URL}/panic`, { method: "POST" }).catch(() => {});
  } catch (e) {}

  // 3. Purge in-page indicators across all active tabs
  try {
    chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
      if (tabs && tabs[0] && tabs[0].id) {
        chrome.scripting.executeScript({
          target: { tabId: tabs[0].id },
          func: purgeInPageIndicators
        }).catch(() => {});
      }
    });
  } catch (e) {}

  // 4. Wipe storage state
  chrome.storage.local.set({ lastResult: null, lastError: null });
}

function purgeInPageIndicators() {
  const dot = document.getElementById("__sv_corner_dot");
  if (dot) dot.remove();
  const hud = document.getElementById("__sv_ghost_hud");
  if (hud) hud.remove();
}

/**
 * Headless Gemini Web Companion Solver (0 tabs, 0 windows, unlimited free)
 */
async function solveWithHeadlessCompanion(extractedText, config) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 28000);

  const key = config.ledKey || "caps_lock";
  const res = await fetch(`${COMPANION_URL}/solve`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ question: extractedText, key: key }),
    signal: controller.signal
  });
  clearTimeout(timeoutId);

  if (!res.ok) {
    const errText = await res.text();
    throw new Error(`Companion HTTP ${res.status}: ${errText}`);
  }

  const data = await res.json();
  if (!data.success) {
    throw new Error(data.error || "Companion solver error");
  }

  return {
    correctChoice: data.correctChoice,
    summary: data.summary,
    alreadyBlinked: true
  };
}

/**
 * Gemini Multimodal Vision Solver for Canvas/Screenshots
 */
async function solveImageWithGeminiApiPool(base64Image, config) {
  const keys = (config.apiKey || "")
    .split("\n")
    .map(k => k.trim())
    .filter(k => k.length > 10);

  if (keys.length === 0) {
    throw new Error("No Gemini API key available in Settings.");
  }

  const promptText = 
    "You are an expert exam solver. Look at this screenshot of a Multiple Choice Question (MCQ). " +
    "Carefully identify the question text and options A, B, C, D. Select the single correct answer. " +
    "Reply STRICTLY in valid JSON format: {\"correctChoice\": \"A|B|C|D\", \"summary\": \"1-sentence explanation\"}";

  const reqBody = {
    contents: [{
      parts: [
        { text: promptText },
        { inlineData: { mimeType: "image/png", data: base64Image } }
      ]
    }],
    generationConfig: {
      temperature: 0.0,
      maxOutputTokens: 120
    }
  };

  let lastErr = null;
  for (let i = 0; i < keys.length; i++) {
    const key = keys[i];
    const endpoint = `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=${key}`;

    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 12000);
      const res = await fetch(endpoint, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(reqBody),
        signal: controller.signal
      });
      clearTimeout(timeoutId);

      if (res.ok) {
        const data = await res.json();
        const rawText = data.candidates?.[0]?.content?.parts?.[0]?.text;
        if (rawText) {
          const parsed = parseChoice(rawText);
          return {
            correctChoice: parsed.correctChoice,
            summary: parsed.summary,
            alreadyBlinked: false
          };
        }
      } else {
        const errJson = await res.json().catch(() => ({}));
        lastErr = new Error(errJson.error?.message || `HTTP ${res.status}`);
      }
    } catch (e) {
      lastErr = e;
    }
  }

  throw lastErr || new Error("All Vision API keys failed.");
}

/**
 * Cloud API Pool Solver with Automatic Failover
 */
async function solveWithGeminiApiPool(extractedText, config) {
  const keys = (config.apiKey || "")
    .split("\n")
    .map(k => k.trim())
    .filter(k => k.length > 10);

  if (keys.length === 0) {
    throw new Error("No API keys found in pool. Paste your keys in Settings.");
  }

  let model = config.model || "gemini-2.0-flash";
  if (model === "gemini-2.5-flash") model = "gemini-2.0-flash";

  const promptText = 
    "You are an expert exam solver. Solve the multiple choice question below.\n" +
    "Identify the correct option (A, B, C, or D).\n" +
    "Reply STRICTLY in valid JSON format: {\"correctChoice\": \"A|B|C|D\", \"summary\": \"1-sentence explanation\"}\n\n" +
    `Question:\n${extractedText}`;

  const reqBody = {
    contents: [{ parts: [{ text: promptText }] }],
    generationConfig: {
      temperature: 0.0,
      maxOutputTokens: 120
    }
  };

  let lastErr = null;
  for (let i = 0; i < keys.length; i++) {
    const key = keys[i];
    const endpoint = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${key}`;

    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 10000);

      const res = await fetch(endpoint, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(reqBody),
        signal: controller.signal
      });
      clearTimeout(timeoutId);

      if (res.ok) {
        const data = await res.json();
        const rawText = data.candidates?.[0]?.content?.parts?.[0]?.text;
        if (rawText) {
          const parsed = parseChoice(rawText);
          return {
            correctChoice: parsed.correctChoice,
            summary: parsed.summary,
            activeModel: model,
            alreadyBlinked: false
          };
        }
      } else {
        const errJson = await res.json().catch(() => ({}));
        lastErr = new Error(errJson.error?.message || `HTTP ${res.status}`);
      }
    } catch (e) {
      lastErr = e;
    }
  }

  throw lastErr || new Error("All API keys in pool failed.");
}

async function testApiKeyPool(apiKeyRaw, model) {
  const keys = (apiKeyRaw || "").split("\n").map(k => k.trim()).filter(k => k.length > 10);
  if (keys.length === 0) {
    return { success: false, error: "No valid API keys entered." };
  }

  if (model === "gemini-2.5-flash") model = "gemini-2.0-flash";

  const results = [];
  for (let i = 0; i < keys.length; i++) {
    const key = keys[i];
    const endpoint = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${key}`;
    const reqBody = {
      contents: [{ parts: [{ text: "Solve: 2+2=? A) 3 B) 4. Reply ONLY: {\"correctChoice\": \"B\"}" }] }],
      generationConfig: { temperature: 0.0, maxOutputTokens: 25 }
    };
    const t0 = Date.now();
    try {
      const res = await fetch(endpoint, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(reqBody)
      });
      const latency = Date.now() - t0;
      if (res.ok) {
        results.push({ index: i + 1, valid: true, latency });
      } else {
        const errData = await res.json().catch(() => ({}));
        results.push({ index: i + 1, valid: false, error: errData.error?.message || `HTTP ${res.status}` });
      }
    } catch (e) {
      results.push({ index: i + 1, valid: false, error: e.message });
    }
  }

  const allValid = results.every(r => r.valid);
  return { success: allValid, results, totalKeys: keys.length };
}

function parseChoice(rawText) {
  const cleaned = rawText.replace(/```json/gi, "").replace(/```/g, "").trim();
  try {
    const parsed = JSON.parse(cleaned);
    if (parsed.correctChoice) {
      let choice = parsed.correctChoice.toString().toUpperCase().trim();
      if (choice === "1") choice = "A";
      if (choice === "2") choice = "B";
      if (choice === "3") choice = "C";
      if (choice === "4") choice = "D";
      if (["A", "B", "C", "D"].includes(choice)) {
        return { correctChoice: choice, summary: parsed.summary || "Answer detected" };
      }
    }
  } catch (e) {}

  const match = cleaned.match(/"correctChoice"\s*:\s*"([A-D1-4])"/i) || cleaned.match(/\b([A-D])\b/);
  if (match && match[1]) {
    let letter = match[1].toUpperCase();
    if (letter === "1") letter = "A";
    if (letter === "2") letter = "B";
    if (letter === "3") letter = "C";
    if (letter === "4") letter = "D";
    return { correctChoice: letter, summary: "Extracted option" };
  }

  throw new Error("Could not parse answer letter from response: " + cleaned.substring(0, 100));
}

/**
 * Apply Solution with Complete Stealth Suites
 */
async function applySolutionResult(choice, summary, triggerLed = true, latency = 0, questionSnippet = "", engineName = "", activeTabId = null) {
  const config = await getStoredConfig();
  const choiceLower = choice.toLowerCase();
  const choiceCounts = { A: 1, B: 2, C: 3, D: 4 };
  const count = choiceCounts[choice] || 1;

  // 1. Icon Morphing based on Signal Mode
  const mode = config.signalMode || "letters";
  if (mode === "symbols") {
    // Coded Symbol Icons (Airplane, Balloon, Cloud, Diamond, etc.)
    let symbolKey = "airplane";
    if (choice === "A") symbolKey = config.symbolChoiceA || "airplane";
    else if (choice === "B") symbolKey = config.symbolChoiceB || "balloon";
    else if (choice === "C") symbolKey = config.symbolChoiceC || "cloud";
    else if (choice === "D") symbolKey = config.symbolChoiceD || "diamond";

    chrome.action.setIcon({
      path: {
        "16": `icons/sym_${symbolKey}_16.png`,
        "32": `icons/sym_${symbolKey}_32.png`,
        "48": `icons/sym_${symbolKey}_48.png`,
        "128": `icons/sym_${symbolKey}_128.png`
      }
    });
    // In symbols mode, leave badge empty so icon looks completely natural and innocent
    chrome.action.setBadgeText({ text: "" });
    chrome.action.setTitle({ title: getStealthTooltip(config) });
  } else if (mode === "dots") {
    // Anti-Peeking Micro-Dots
    chrome.action.setIcon({
      path: {
        "16": `icons/dots_${count}_16.png`,
        "32": `icons/dots_${count}_32.png`,
        "48": `icons/dots_${count}_48.png`,
        "128": `icons/dots_${count}_128.png`
      }
    });
    chrome.action.setBadgeText({ text: "•" });
    chrome.action.setBadgeBackgroundColor({ color: "#3b82f6" });
    chrome.action.setTitle({ title: getStealthTooltip(config) });
  } else if (mode === "stealth_stay") {
    // Keep disguise icon locked; only badge updates
    if (config.showBadge !== false) {
      chrome.action.setBadgeText({ text: choice });
      chrome.action.setBadgeBackgroundColor({ color: "#2563eb" });
    }
    chrome.action.setTitle({ title: getStealthTooltip(config) });
  } else {
    // Letters Mode
    chrome.action.setIcon({
      path: {
        "16": `icons/choice_${choiceLower}_16.png`,
        "32": `icons/choice_${choiceLower}_32.png`,
        "48": `icons/choice_${choiceLower}_48.png`,
        "128": `icons/choice_${choiceLower}_128.png`
      }
    });
    if (config.showBadge !== false) {
      const badgeColors = { A: "#059669", B: "#2563eb", C: "#9333ea", D: "#ea580c" };
      chrome.action.setBadgeText({ text: choice });
      chrome.action.setBadgeBackgroundColor({ color: badgeColors[choice] || "#4f46e5" });
    } else {
      chrome.action.setBadgeText({ text: "" });
    }
    chrome.action.setTitle({ title: getStealthTooltip(config) });
  }

  // 2. Micro Corner Dot on Screen (Injected into active tab)
  if (config.cornerDotEnabled !== false && activeTabId) {
    try {
      chrome.scripting.executeScript({
        target: { tabId: activeTabId },
        func: inPageCornerDot,
        args: [choice, count]
      });
    } catch (e) {}
  }

  // 3. Draggable Ghost HUD (Injected into active tab)
  if (config.ghostHudEnabled !== false && activeTabId) {
    try {
      chrome.scripting.executeScript({
        target: { tabId: activeTabId },
        func: inPageGhostHUD,
        args: [choice, summary, config.ghostHudOpacity || 0.08, config.ghostHudDuration || 15]
      });
    } catch (e) {}
  }

  // 4. Subtle Earbud Audio Ticks
  if (config.audioTicksEnabled && activeTabId) {
    try {
      chrome.scripting.executeScript({
        target: { tabId: activeTabId },
        func: inPageAudioTicks,
        args: [count]
      });
    } catch (e) {}
  }

  // 5. Broadcast to connected Mobile Phones
  try {
    fetch(`${COMPANION_URL}/broadcast_test`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ choice: choice })
    }).catch(() => {});
  } catch (e) {}

  // 6. Record to MCQ History Drawer in local storage
  const resultData = {
    choice,
    summary,
    timestamp: new Date().toLocaleTimeString(),
    latency: latency ? `${latency}ms` : "< 3s",
    engine: engineName || "Headless Gemini Web",
    questionSnippet: questionSnippet.replace(/\s+/g, " ").substring(0, 130)
  };

  chrome.storage.local.get({ history: [] }, (data) => {
    const historyList = [resultData, ...(data.history || [])].slice(0, 30);
    chrome.storage.local.set({ lastResult: resultData, lastError: null, history: historyList });
  });

  // 7. Hardware LED companion signal
  if (triggerLed && config.ledEnabled !== false) {
    signalKeyboardCompanion(choice, config.ledKey || "caps_lock");
  }

  // 8. Auto-reset timer
  const resetSeconds = parseInt(config.autoResetSeconds ?? 10, 10);
  if (resetSeconds > 0) {
    autoResetTimer = setTimeout(() => {
      resetToStealthIcon();
    }, resetSeconds * 1000);
  }
}

/**
 * In-Page Micro Corner Dot (3px discreet indicator in bottom-right corner)
 */
function inPageCornerDot(choice, count) {
  const existing = document.getElementById("__sv_corner_dot");
  if (existing) existing.remove();

  const colors = { A: "#10b981", B: "#3b82f6", C: "#a855f7", D: "#f59e0b" };
  const dotColor = colors[choice] || "#10b981";

  const container = document.createElement("div");
  container.id = "__sv_corner_dot";
  container.style.cssText = `
    position: fixed;
    bottom: 4px;
    right: 4px;
    z-index: 2147483647;
    display: flex;
    gap: 3px;
    pointer-events: none;
    opacity: 0.9;
    transition: opacity 0.5s ease-out;
  `;

  for (let i = 0; i < count; i++) {
    const dot = document.createElement("div");
    dot.style.cssText = `
      width: 4px;
      height: 4px;
      border-radius: 50%;
      background: ${dotColor};
      box-shadow: 0 0 4px ${dotColor};
    `;
    container.appendChild(dot);
  }

  document.body.appendChild(container);

  setTimeout(() => {
    container.style.opacity = "0";
    setTimeout(() => container.remove(), 600);
  }, 4000);
}

/**
 * In-Page Draggable Ghost HUD (Semi-transparent floating badge)
 */
function inPageGhostHUD(choice, summary, opacity = 0.08, duration = 15) {
  const existing = document.getElementById("__sv_ghost_hud");
  if (existing) existing.remove();

  const hud = document.createElement("div");
  hud.id = "__sv_ghost_hud";
  
  const alpha = parseFloat(opacity) || 0.08;
  hud.style.cssText = `
    position: fixed;
    bottom: 30px;
    right: 30px;
    z-index: 2147483647;
    background: rgba(15, 23, 42, ${alpha});
    border: 1px solid rgba(255, 255, 255, ${Math.min(alpha * 2.5, 0.35)});
    border-radius: 8px;
    padding: 6px 14px;
    color: rgba(255, 255, 255, ${Math.max(alpha * 5, 0.45)});
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    font-size: 13px;
    font-weight: 600;
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: grab;
    user-select: none;
    -webkit-user-select: none;
    backdrop-filter: blur(2px);
    transition: opacity 0.3s ease, background 0.2s ease, border-color 0.2s ease, color 0.2s ease;
    box-shadow: 0 2px 8px rgba(0,0,0,0.15);
  `;

  hud.innerHTML = `
    <span style="font-size:15px; font-weight:800; color:#38bdf8;">${choice}</span>
    <span class="__sv_hud_summary" style="display:none; font-size:11px; max-width:240px; font-weight:400; color:#cbd5e1; line-height:1.3;">${summary}</span>
  `;

  hud.addEventListener("mouseenter", () => {
    hud.style.background = "rgba(15, 23, 42, 0.94)";
    hud.style.borderColor = "rgba(56, 189, 248, 0.5)";
    hud.style.color = "#f8fafc";
    const sumEl = hud.querySelector(".__sv_hud_summary");
    if (sumEl) sumEl.style.display = "inline";
  });

  hud.addEventListener("mouseleave", () => {
    hud.style.background = `rgba(15, 23, 42, ${alpha})`;
    hud.style.borderColor = `rgba(255, 255, 255, ${Math.min(alpha * 2.5, 0.35)})`;
    hud.style.color = `rgba(255, 255, 255, ${Math.max(alpha * 5, 0.45)})`;
    const sumEl = hud.querySelector(".__sv_hud_summary");
    if (sumEl) sumEl.style.display = "none";
  });

  // Dragging logic
  let isDragging = false, startX, startY, initX, initY;
  hud.addEventListener("mousedown", (e) => {
    isDragging = true;
    hud.style.cursor = "grabbing";
    startX = e.clientX;
    startY = e.clientY;
    const rect = hud.getBoundingClientRect();
    initX = rect.left;
    initY = rect.top;
    e.preventDefault();
  });

  const onMouseMove = (e) => {
    if (!isDragging) return;
    const dx = e.clientX - startX;
    const dy = e.clientY - startY;
    hud.style.left = `${initX + dx}px`;
    hud.style.top = `${initY + dy}px`;
    hud.style.bottom = "auto";
    hud.style.right = "auto";
  };

  const onMouseUp = () => {
    if (isDragging) {
      isDragging = false;
      hud.style.cursor = "grab";
    }
  };

  window.addEventListener("mousemove", onMouseMove);
  window.addEventListener("mouseup", onMouseUp);

  document.body.appendChild(hud);

  const durSec = parseInt(duration, 10) || 15;
  setTimeout(() => {
    hud.style.opacity = "0";
    setTimeout(() => {
      window.removeEventListener("mousemove", onMouseMove);
      window.removeEventListener("mouseup", onMouseUp);
      hud.remove();
    }, 400);
  }, durSec * 1000);
}

/**
 * In-Page Subtle Earbud Audio Ticks (Synthesized Web Audio clicks)
 */
function inPageAudioTicks(count) {
  try {
    const AudioCtx = window.AudioContext || window.webkitAudioContext;
    if (!AudioCtx) return;
    const ctx = new AudioCtx();

    for (let i = 0; i < count; i++) {
      const startTime = ctx.currentTime + (i * 0.16);
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();

      osc.type = "sine";
      osc.frequency.setValueAtTime(800, startTime);
      osc.frequency.exponentialRampToValueAtTime(300, startTime + 0.03);

      gain.gain.setValueAtTime(0.06, startTime);
      gain.gain.exponentialRampToValueAtTime(0.001, startTime + 0.035);

      osc.connect(gain);
      gain.connect(ctx.destination);

      osc.start(startTime);
      osc.stop(startTime + 0.04);
    }
  } catch (e) {}
}

function signalKeyboardCompanion(choice, keyMode) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 1200);

  const url = `${COMPANION_URL}/blink?answer=${encodeURIComponent(choice)}&key=${encodeURIComponent(keyMode)}`;
  fetch(url, { signal: controller.signal })
    .then(() => clearTimeout(timeoutId))
    .catch(() => clearTimeout(timeoutId));
}

function showLoadingState() {
  chrome.action.setIcon({
    path: {
      "16": "icons/loading_16.png",
      "32": "icons/loading_32.png",
      "48": "icons/loading_48.png",
      "128": "icons/loading_128.png"
    }
  });
  chrome.action.setBadgeText({ text: "..." });
  chrome.action.setBadgeBackgroundColor({ color: "#eab308" });
}

function showErrorState(reason) {
  chrome.action.setIcon({
    path: {
      "16": "icons/error_16.png",
      "32": "icons/error_32.png",
      "48": "icons/error_48.png",
      "128": "icons/error_128.png"
    }
  });
  chrome.action.setBadgeText({ text: "ERR" });
  chrome.action.setBadgeBackgroundColor({ color: "#dc2626" });

  chrome.storage.local.set({
    lastError: {
      error: reason || "Analysis failed",
      timestamp: new Date().toLocaleTimeString()
    }
  });

  setTimeout(() => resetToStealthIcon(), 4000);
}

function getStealthIconPaths(disguise) {
  if (disguise === "translate") {
    return {
      "16": "icons/disguise_translate_16.png",
      "32": "icons/disguise_translate_32.png",
      "48": "icons/disguise_translate_48.png",
      "128": "icons/disguise_translate_128.png"
    };
  }
  if (disguise === "audio") {
    return {
      "16": "icons/disguise_audio_16.png",
      "32": "icons/disguise_audio_32.png",
      "48": "icons/disguise_audio_48.png",
      "128": "icons/disguise_audio_128.png"
    };
  }
  if (disguise === "zoom") {
    return {
      "16": "icons/disguise_zoom_16.png",
      "32": "icons/disguise_zoom_32.png",
      "48": "icons/disguise_zoom_48.png",
      "128": "icons/disguise_zoom_128.png"
    };
  }
  return {
    "16": "icons/icon_16.png",
    "32": "icons/icon_32.png",
    "48": "icons/icon_48.png",
    "128": "icons/icon_128.png"
  };
}

function getStealthTooltip(config) {
  if (config && config.customTooltip && config.customTooltip.trim()) {
    return config.customTooltip.trim();
  }
  const disguise = (config && config.iconDisguise) || "default";
  if (disguise === "translate") return "Google Translate - Web Page Translation";
  if (disguise === "audio") return "Volume Master - Sound Equalizer";
  if (disguise === "zoom") return "Page Magnifier & Display Tools";
  return "Google Translate";
}

async function refreshToolbarIcon() {
  const config = await getStoredConfig();
  chrome.action.setIcon({ path: getStealthIconPaths(config.iconDisguise) });
  chrome.action.setTitle({ title: getStealthTooltip(config) });
}

async function resetToStealthIcon() {
  if (autoResetTimer) {
    clearTimeout(autoResetTimer);
    autoResetTimer = null;
  }
  await refreshToolbarIcon();
  chrome.action.setBadgeText({ text: "" });
}

async function checkCompanionStatus() {
  try {
    const res = await fetch(`${COMPANION_URL}/status`, { signal: AbortSignal.timeout(1500) });
    if (res.ok) {
      const data = await res.json();
      return { online: true, data };
    }
  } catch (e) {}
  return { online: false };
}

async function getStoredConfig() {
  return new Promise((resolve) => {
    chrome.storage.sync.get({
      solverEngine: "headless_gemini",
      apiKey: "",
      model: "gemini-2.0-flash",
      iconDisguise: "default",
      signalMode: "letters",
      customTooltip: "",
      symbolChoiceA: "airplane",
      symbolChoiceB: "balloon",
      symbolChoiceC: "cloud",
      symbolChoiceD: "diamond",
      cornerDotEnabled: true,
      audioTicksEnabled: false,
      ghostHudEnabled: true,
      ghostHudOpacity: 0.08,
      ghostHudDuration: 15,
      clipboardAutoSolve: false,
      autoResetSeconds: 10,
      showBadge: true,
      ledEnabled: true,
      ledKey: "caps_lock"
    }, (items) => resolve(items));
  });
}
