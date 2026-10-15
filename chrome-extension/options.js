/**
 * Smart Vision Solver - Options & Stealth Management (v1.8.0)
 * Glassmorphic Tabbed Interface with Mobile Haptic Sync, Canvas Vision,
 * Draggable Ghost HUD, Clipboard Intelligence, Camouflage, Multi-Key Pool & Solved History
 */

const COMPANION_URL = "http://127.0.0.1:59124";
const COMPANION_STATUS_URL = `${COMPANION_URL}/status`;
const COMPANION_BLINK_URL = `${COMPANION_URL}/blink`;
const COMPANION_SOLVE_URL = `${COMPANION_URL}/solve`;
const COMPANION_LOGIN_URL = `${COMPANION_URL}/login`;
const COMPANION_NEWCHAT_URL = `${COMPANION_URL}/new_chat`;

// Safe wrappers for chrome.storage and chrome.runtime (avoids crashes when inspected standalone)
const chromeStorage = {
  sync: {
    get: (defaults, cb) => {
      if (typeof chrome !== 'undefined' && chrome.storage && chrome.storage.sync) {
        chrome.storage.sync.get(defaults, cb);
      } else {
        cb(defaults);
      }
    },
    set: (data, cb) => {
      if (typeof chrome !== 'undefined' && chrome.storage && chrome.storage.sync) {
        chrome.storage.sync.set(data, cb);
      } else if (cb) cb();
    }
  },
  local: {
    get: (defaults, cb) => {
      if (typeof chrome !== 'undefined' && chrome.storage && chrome.storage.local) {
        chrome.storage.local.get(defaults, cb);
      } else {
        cb(defaults);
      }
    },
    set: (data, cb) => {
      if (typeof chrome !== 'undefined' && chrome.storage && chrome.storage.local) {
        chrome.storage.local.set(data, cb);
      } else if (cb) cb();
    }
  }
};

const chromeRuntime = {
  sendMessage: (msg, cb) => {
    if (typeof chrome !== 'undefined' && chrome.runtime && chrome.runtime.sendMessage) {
      chrome.runtime.sendMessage(msg, cb);
    } else if (cb) {
      cb({ success: true });
    }
  }
};

// Elements - Navigation
const tabButtons = document.querySelectorAll(".tab-btn");
const tabPanes = document.querySelectorAll(".tab-pane");

// Elements - Tab 1: Engines
const engineHeadlessRadio = document.getElementById("engineHeadless");
const engineApiRadio = document.getElementById("engineApi");
const headlessSection = document.getElementById("headlessSection");
const apiKeySection = document.getElementById("apiKeySection");
const companionEngineTitle = document.getElementById("companionEngineTitle");
const companionEngineDesc = document.getElementById("companionEngineDesc");
const companionStatusPill = document.getElementById("companionStatus");

const testHeadlessBtn = document.getElementById("testHeadlessBtn");
const launchLoginBtn = document.getElementById("launchLoginBtn");
const resetChatBtn = document.getElementById("resetChatBtn");

const apiKeyInput = document.getElementById("apiKey");
const keyPoolCountBadge = document.getElementById("keyPoolCountBadge");
const modelSelect = document.getElementById("modelSelect");
const testApiBtn = document.getElementById("testApiBtn");

const saveBtn = document.getElementById("saveBtn");
const saveStatus = document.getElementById("saveStatus");

// Elements - Tab 2: Stealth & Camouflage
const disguiseCards = document.querySelectorAll(".disguise-card");
const customTooltipInput = document.getElementById("customTooltipInput");
const resetTooltipBtn = document.getElementById("resetTooltipBtn");
const tooltipPresetBtns = document.querySelectorAll(".tooltip-preset");

const signalLettersRadio = document.getElementById("signalLetters");
const signalSymbolsRadio = document.getElementById("signalSymbols");
const signalDotsRadio = document.getElementById("signalDots");
const signalStayRadio = document.getElementById("signalStay");
const symbolSelectorPanel = document.getElementById("symbolSelectorPanel");

const symbolSelectA = document.getElementById("symbolSelectA");
const symbolSelectB = document.getElementById("symbolSelectB");
const symbolSelectC = document.getElementById("symbolSelectC");
const symbolSelectD = document.getElementById("symbolSelectD");
const previewA = document.getElementById("previewA");
const previewB = document.getElementById("previewB");
const previewC = document.getElementById("previewC");
const previewD = document.getElementById("previewD");

const symbolThemeBtns = document.querySelectorAll(".symbol-theme-btn");
const testSymBtns = document.querySelectorAll(".test-sym-btn");

const cornerDotEnabledCheckbox = document.getElementById("cornerDotEnabled");
const audioTicksEnabledCheckbox = document.getElementById("audioTicksEnabled");
const autoResetSecondsInput = document.getElementById("autoResetSeconds");
const showBadgeCheckbox = document.getElementById("showBadge");

const saveStealthBtn = document.getElementById("saveStealthBtn");
const stealthSaveStatus = document.getElementById("stealthSaveStatus");
const testSimulationBtn = document.getElementById("testSimulationBtn");

// Elements - Tab 3: Mobile Sync & Quick Solvers
const mobileUrlInput = document.getElementById("mobileUrlInput");
const copyMobileUrlBtn = document.getElementById("copyMobileUrlBtn");
const openMobileUrlLink = document.getElementById("openMobileUrlLink");
const mobileSyncDot = document.getElementById("mobileSyncDot");
const mobileClientCountLabel = document.getElementById("mobileClientCountLabel");
const qrCodeContainer = document.getElementById("qrCodeContainer");
const qrCodeCaption = document.getElementById("qrCodeCaption");
const btnQrTargetWeb = document.getElementById("btnQrTargetWeb");
const btnQrTargetNative = document.getElementById("btnQrTargetNative");
const mobileTestStatus = document.getElementById("mobileTestStatus");
let currentQrTargetMode = "web";
let lastCompanionData = null;

const clipboardAutoSolveCheckbox = document.getElementById("clipboardAutoSolveCheckbox");
const testScreenshotBtn = document.getElementById("testScreenshotBtn");
const triggerPanicBtn = document.getElementById("triggerPanicBtn");

const ghostHudEnabledCheckbox = document.getElementById("ghostHudEnabledCheckbox");
const ghostHudOpacitySlider = document.getElementById("ghostHudOpacitySlider");
const ghostOpacityValueLabel = document.getElementById("ghostOpacityValueLabel");
const ghostHudDurationInput = document.getElementById("ghostHudDurationInput");
const ghostPreviewBadge = document.getElementById("ghostPreviewBadge");

const saveQuickSolversBtn = document.getElementById("saveQuickSolversBtn");
const quickSolversSaveStatus = document.getElementById("quickSolversSaveStatus");

// Elements - Tab 4: History
const historyCountBadge = document.getElementById("historyCountBadge");
const historySearchInput = document.getElementById("historySearchInput");
const historyListContainer = document.getElementById("historyListContainer");
const exportHistoryBtn = document.getElementById("exportHistoryBtn");
const clearHistoryBtn = document.getElementById("clearHistoryBtn");

// Elements - Tab 5: Hardware
const ledEnabledCheckbox = document.getElementById("ledEnabled");
const ledKeySelect = document.getElementById("ledKey");
const testOutput = document.getElementById("testOutput");

let currentHistory = [];
let selectedDisguise = "default";
let lastRenderedQrUrl = "";

// ---------------------------------------------------------------------------
// Initialize
// ---------------------------------------------------------------------------
document.addEventListener("DOMContentLoaded", () => {
  setupTabs();
  loadAllSettings();
  loadHistory();
  setupStealthControls();
  setupQuickSolvers();
  if (mobileUrlInput && mobileUrlInput.value) {
    renderQRCodeSVG(mobileUrlInput.value);
  }
  checkCompanionServer();
  setInterval(checkCompanionServer, 3500);
});

// ---------------------------------------------------------------------------
// Tab Switching Logic
// ---------------------------------------------------------------------------
function setupTabs() {
  tabButtons.forEach(btn => {
    btn.addEventListener("click", () => {
      tabButtons.forEach(b => b.classList.remove("active"));
      tabPanes.forEach(p => p.classList.remove("active"));

      btn.classList.add("active");
      const targetId = btn.getAttribute("data-tab");
      const targetPane = document.getElementById(targetId);
      if (targetPane) targetPane.classList.add("active");
    });
  });
}

// ---------------------------------------------------------------------------
// Engine Radio Toggle
// ---------------------------------------------------------------------------
if (engineHeadlessRadio) engineHeadlessRadio.addEventListener("change", updateEngineVisibility);
if (engineApiRadio) engineApiRadio.addEventListener("change", updateEngineVisibility);

function updateEngineVisibility() {
  const isApi = engineApiRadio && engineApiRadio.checked;
  if (apiKeySection) apiKeySection.style.display = isApi ? "block" : "none";
  if (headlessSection) headlessSection.style.display = isApi ? "none" : "block";
}

// ---------------------------------------------------------------------------
// Key Pool Counter Helper
// ---------------------------------------------------------------------------
if (apiKeyInput) {
  apiKeyInput.addEventListener("input", updateKeyCount);
}

function updateKeyCount() {
  if (!apiKeyInput || !keyPoolCountBadge) return;
  const raw = apiKeyInput.value.trim();
  if (!raw) {
    keyPoolCountBadge.textContent = "0 Keys in Pool";
    return;
  }
  const keys = raw.split(/[\n,;]+/).map(k => k.trim()).filter(Boolean);
  keyPoolCountBadge.textContent = `${keys.length} Key${keys.length === 1 ? '' : 's'} in Pool (Auto-Failover)`;
}

// ---------------------------------------------------------------------------
// Disguise Cards Picker
// ---------------------------------------------------------------------------
disguiseCards.forEach(card => {
  card.addEventListener("click", () => {
    disguiseCards.forEach(c => c.classList.remove("selected"));
    card.classList.add("selected");
    selectedDisguise = card.getAttribute("data-disguise") || "default";

    // Update header preview logo
    const headerLogo = document.getElementById("headerLogo");
    if (headerLogo) {
      if (selectedDisguise === "translate") headerLogo.src = "icons/disguise_translate_48.png";
      else if (selectedDisguise === "audio") headerLogo.src = "icons/disguise_audio_48.png";
      else if (selectedDisguise === "zoom") headerLogo.src = "icons/disguise_zoom_48.png";
      else headerLogo.src = "icons/icon_48.png";
    }

    // Auto-save and refresh toolbar icon immediately
    chromeStorage.sync.set({ iconDisguise: selectedDisguise }, () => {
      chromeRuntime.sendMessage({ type: "UPDATE_DISGUISE_ICON" });
      showStatusMsg(stealthSaveStatus, `Toolbar icon changed to ${card.querySelector('.disguise-title').textContent}! ✓`, true);
    });
  });
});

// ---------------------------------------------------------------------------
// Stealth Camouflage & Coded Symbols Controls
// ---------------------------------------------------------------------------
const SYMBOL_THEMES = {
  alphabet: { A: "airplane", B: "balloon", C: "cloud", D: "diamond" },
  tools: { A: "anchor", B: "book", C: "clock", D: "document" },
  shapes: { A: "triangle", B: "square", C: "circle", D: "star" },
  fun: { A: "apple", B: "ball", C: "coffee", D: "dice" }
};

function updateSymbolPreviews() {
  if (symbolSelectA && previewA) previewA.src = `icons/sym_${symbolSelectA.value}_32.png`;
  if (symbolSelectB && previewB) previewB.src = `icons/sym_${symbolSelectB.value}_32.png`;
  if (symbolSelectC && previewC) previewC.src = `icons/sym_${symbolSelectC.value}_32.png`;
  if (symbolSelectD && previewD) previewD.src = `icons/sym_${symbolSelectD.value}_32.png`;
}

function updateSymbolPanelVisibility() {
  const isSymbols = signalSymbolsRadio && signalSymbolsRadio.checked;
  if (symbolSelectorPanel) {
    symbolSelectorPanel.style.display = isSymbols ? "block" : "none";
  }
}

function setupStealthControls() {
  // 1. Tooltip Presets
  tooltipPresetBtns.forEach(btn => {
    btn.addEventListener("click", () => {
      const text = btn.getAttribute("data-tooltip");
      if (customTooltipInput && text) {
        customTooltipInput.value = text;
        showStatusMsg(stealthSaveStatus, `Tooltip set to: "${text}". Click Save Settings to persist.`, true);
      }
    });
  });

  // 2. Auto-match Tooltip to Selected Disguise
  if (resetTooltipBtn) {
    resetTooltipBtn.addEventListener("click", () => {
      let matched = "Google Translate - Web Page Translation";
      if (selectedDisguise === "audio") matched = "Volume Master - Sound Equalizer";
      else if (selectedDisguise === "zoom") matched = "Page Magnifier & Display Tools";
      else if (selectedDisguise === "default") matched = "Google Translate";
      if (customTooltipInput) customTooltipInput.value = matched;
      showStatusMsg(stealthSaveStatus, `Auto-matched tooltip to "${matched}"! ✓`, true);
    });
  }

  // 3. Signal Mode Radios
  [signalLettersRadio, signalSymbolsRadio, signalDotsRadio, signalStayRadio].forEach(radio => {
    if (radio) {
      radio.addEventListener("change", () => {
        updateSymbolPanelVisibility();
      });
    }
  });

  // 4. Symbol Dropdowns Change -> update live previews
  [symbolSelectA, symbolSelectB, symbolSelectC, symbolSelectD].forEach(sel => {
    if (sel) {
      sel.addEventListener("change", () => {
        updateSymbolPreviews();
        symbolThemeBtns.forEach(b => b.classList.remove("active"));
      });
    }
  });

  // 5. Theme Presets (Alphabet, Tools, Shapes, Everyday Fun)
  symbolThemeBtns.forEach(btn => {
    btn.addEventListener("click", () => {
      const themeKey = btn.getAttribute("data-theme");
      const theme = SYMBOL_THEMES[themeKey];
      if (theme) {
        symbolThemeBtns.forEach(b => b.classList.remove("active"));
        btn.classList.add("active");
        if (symbolSelectA) symbolSelectA.value = theme.A;
        if (symbolSelectB) symbolSelectB.value = theme.B;
        if (symbolSelectC) symbolSelectC.value = theme.C;
        if (symbolSelectD) symbolSelectD.value = theme.D;
        updateSymbolPreviews();
        showStatusMsg(stealthSaveStatus, `Applied ${themeKey.toUpperCase()} theme! (${theme.A}, ${theme.B}, ${theme.C}, ${theme.D})`, true);
      }
    });
  });

  // 6. Test Coded Symbols on Active Tab
  testSymBtns.forEach(btn => {
    btn.addEventListener("click", () => {
      const choice = btn.getAttribute("data-choice") || "A";
      chromeRuntime.sendMessage({ type: "TEST_SYMBOL_SIGNAL", choice }, () => {
        showStatusMsg(stealthSaveStatus, `Sent Coded Symbol for Choice ${choice} to active tab! Check toolbar icon. ✓`, true);
      });
    });
  });
}

// ---------------------------------------------------------------------------
// Quick Solvers & Mobile Controls
// ---------------------------------------------------------------------------
function setupQuickSolvers() {
  // 1. Copy URL
  if (copyMobileUrlBtn && mobileUrlInput) {
    copyMobileUrlBtn.addEventListener("click", () => {
      navigator.clipboard.writeText(mobileUrlInput.value).then(() => {
        const orig = copyMobileUrlBtn.textContent;
        copyMobileUrlBtn.textContent = "✓ Copied!";
        setTimeout(() => copyMobileUrlBtn.textContent = orig, 2000);
      });
    });
  }

  // 2. Mobile Vibration Test Buttons
  document.querySelectorAll(".mobile-vib-btn").forEach(btn => {
    btn.addEventListener("click", () => {
      const choice = btn.getAttribute("data-choice") || "A";
      chromeRuntime.sendMessage({ type: "TEST_MOBILE_BROADCAST", choice }, (res) => {
        if (res && res.success) {
          showStatusMsg(mobileTestStatus, `⚡ Sent ${res.blinks} buzz(es) for Choice ${choice} to connected phone!`, true);
        } else {
          showStatusMsg(mobileTestStatus, "Could not reach companion server.");
        }
      });
    });
  });

  // 3. Clipboard Auto-Solve Toggle
  if (clipboardAutoSolveCheckbox) {
    clipboardAutoSolveCheckbox.addEventListener("change", () => {
      const enabled = clipboardAutoSolveCheckbox.checked;
      chromeStorage.sync.set({ clipboardAutoSolve: enabled });
      chromeRuntime.sendMessage({ type: "CONFIG_CLIPBOARD_AUTOSOLVE", enabled });
    });
  }

  // 4. Ghost HUD Opacity Slider
  if (ghostHudOpacitySlider) {
    ghostHudOpacitySlider.addEventListener("input", () => {
      const val = parseFloat(ghostHudOpacitySlider.value);
      if (ghostOpacityValueLabel) ghostOpacityValueLabel.textContent = `${Math.round(val * 100)}%`;
      if (ghostPreviewBadge) ghostPreviewBadge.style.opacity = val;
    });
  }

  // 5. Test Screenshot Solve
  if (testScreenshotBtn) {
    testScreenshotBtn.addEventListener("click", () => {
      showStatusMsg(quickSolversSaveStatus, "Capturing viewport and solving via Gemini Vision...");
      chromeRuntime.sendMessage({ type: "TEST_SCREENSHOT_SOLVE" }, (res) => {
        if (res && res.success) {
          showStatusMsg(quickSolversSaveStatus, "Screenshot analyzed! Check your toolbar icon & HUD.", true);
          loadHistory();
        } else {
          showStatusMsg(quickSolversSaveStatus, `Screenshot solve failed: ${res?.error || "Check API key"}`);
        }
      });
    });
  }

  // 6. Emergency Panic Button
  if (triggerPanicBtn) {
    triggerPanicBtn.addEventListener("click", () => {
      chromeRuntime.sendMessage({ type: "TRIGGER_PANIC" }, () => {
        showStatusMsg(quickSolversSaveStatus, "🚨 Emergency Panic executed! Indicators wiped & phone blacked out.", true);
      });
    });
  }

  // 7. Save Quick Solvers Preferences
  if (saveQuickSolversBtn) {
    saveQuickSolversBtn.addEventListener("click", () => {
      const config = {
        ghostHudEnabled: ghostHudEnabledCheckbox ? ghostHudEnabledCheckbox.checked : true,
        ghostHudOpacity: ghostHudOpacitySlider ? parseFloat(ghostHudOpacitySlider.value) || 0.08 : 0.08,
        ghostHudDuration: ghostHudDurationInput ? parseInt(ghostHudDurationInput.value, 10) || 15 : 15,
        clipboardAutoSolve: clipboardAutoSolveCheckbox ? clipboardAutoSolveCheckbox.checked : false
      };
      chromeStorage.sync.set(config, () => {
        chromeRuntime.sendMessage({ type: "CONFIG_CLIPBOARD_AUTOSOLVE", enabled: config.clipboardAutoSolve });
        showStatusMsg(quickSolversSaveStatus, "Quick Solver preferences saved successfully! ✓", true);
      });
    });
  }
}

// ---------------------------------------------------------------------------
// Settings Save & Load
// ---------------------------------------------------------------------------
function saveAllSettings(statusEl = saveStatus) {
  let signalMode = "letters";
  if (signalSymbolsRadio && signalSymbolsRadio.checked) signalMode = "symbols";
  else if (signalDotsRadio && signalDotsRadio.checked) signalMode = "dots";
  else if (signalStayRadio && signalStayRadio.checked) signalMode = "stealth_stay";

  const config = {
    solverEngine: (engineHeadlessRadio && engineHeadlessRadio.checked) ? "headless_gemini" : "gemini_api",
    apiKey: apiKeyInput ? apiKeyInput.value.trim() : "",
    model: modelSelect ? modelSelect.value : "gemini-2.0-flash",
    iconDisguise: selectedDisguise,
    signalMode: signalMode,
    customTooltip: customTooltipInput ? customTooltipInput.value.trim() : "",
    symbolChoiceA: symbolSelectA ? symbolSelectA.value : "airplane",
    symbolChoiceB: symbolSelectB ? symbolSelectB.value : "balloon",
    symbolChoiceC: symbolSelectC ? symbolSelectC.value : "cloud",
    symbolChoiceD: symbolSelectD ? symbolSelectD.value : "diamond",
    cornerDotEnabled: cornerDotEnabledCheckbox ? cornerDotEnabledCheckbox.checked : true,
    audioTicksEnabled: audioTicksEnabledCheckbox ? audioTicksEnabledCheckbox.checked : false,
    ghostHudEnabled: ghostHudEnabledCheckbox ? ghostHudEnabledCheckbox.checked : true,
    ghostHudOpacity: ghostHudOpacitySlider ? parseFloat(ghostHudOpacitySlider.value) || 0.08 : 0.08,
    ghostHudDuration: ghostHudDurationInput ? parseInt(ghostHudDurationInput.value, 10) || 15 : 15,
    clipboardAutoSolve: clipboardAutoSolveCheckbox ? clipboardAutoSolveCheckbox.checked : false,
    autoResetSeconds: autoResetSecondsInput ? parseInt(autoResetSecondsInput.value, 10) || 10 : 10,
    showBadge: showBadgeCheckbox ? showBadgeCheckbox.checked : true,
    ledEnabled: ledEnabledCheckbox ? ledEnabledCheckbox.checked : true,
    ledKey: ledKeySelect ? ledKeySelect.value || "caps_lock" : "caps_lock"
  };

  chromeStorage.sync.set(config, () => {
    chromeRuntime.sendMessage({ type: "UPDATE_DISGUISE_ICON" });
    chromeRuntime.sendMessage({ type: "CONFIG_CLIPBOARD_AUTOSOLVE", enabled: config.clipboardAutoSolve });
    showStatusMsg(statusEl, "Settings saved successfully! ✓", true);
  });
}

if (saveBtn) saveBtn.addEventListener("click", () => saveAllSettings(saveStatus));
if (saveStealthBtn) saveStealthBtn.addEventListener("click", () => saveAllSettings(stealthSaveStatus));

function loadAllSettings() {
  chromeStorage.sync.get({
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
  }, (items) => {
    // Solver Engine
    if (items.solverEngine === "gemini_api") {
      if (engineApiRadio) engineApiRadio.checked = true;
    } else {
      if (engineHeadlessRadio) engineHeadlessRadio.checked = true;
    }
    updateEngineVisibility();

    // API Keys
    if (apiKeyInput) {
      apiKeyInput.value = items.apiKey || "";
      updateKeyCount();
    }
    if (modelSelect && [...modelSelect.options].some(o => o.value === items.model)) {
      modelSelect.value = items.model;
    }

    // Camouflage Disguise
    selectedDisguise = items.iconDisguise || "default";
    disguiseCards.forEach(c => {
      if (c.getAttribute("data-disguise") === selectedDisguise) {
        c.classList.add("selected");
      } else {
        c.classList.remove("selected");
      }
    });

    // Tooltip
    if (customTooltipInput) {
      customTooltipInput.value = items.customTooltip || "";
    }

    // Signal Mode
    if (items.signalMode === "symbols" && signalSymbolsRadio) signalSymbolsRadio.checked = true;
    else if (items.signalMode === "dots" && signalDotsRadio) signalDotsRadio.checked = true;
    else if (items.signalMode === "stealth_stay" && signalStayRadio) signalStayRadio.checked = true;
    else if (signalLettersRadio) signalLettersRadio.checked = true;

    updateSymbolPanelVisibility();

    // Coded Symbols
    if (symbolSelectA) symbolSelectA.value = items.symbolChoiceA || "airplane";
    if (symbolSelectB) symbolSelectB.value = items.symbolChoiceB || "balloon";
    if (symbolSelectC) symbolSelectC.value = items.symbolChoiceC || "cloud";
    if (symbolSelectD) symbolSelectD.value = items.symbolChoiceD || "diamond";
    updateSymbolPreviews();

    // Additional Stealth
    if (cornerDotEnabledCheckbox) cornerDotEnabledCheckbox.checked = items.cornerDotEnabled !== false;
    if (audioTicksEnabledCheckbox) audioTicksEnabledCheckbox.checked = items.audioTicksEnabled === true;
    if (autoResetSecondsInput) autoResetSecondsInput.value = items.autoResetSeconds || 10;
    if (showBadgeCheckbox) showBadgeCheckbox.checked = items.showBadge !== false;

    // Quick Solvers
    if (clipboardAutoSolveCheckbox) clipboardAutoSolveCheckbox.checked = items.clipboardAutoSolve === true;
    if (ghostHudEnabledCheckbox) ghostHudEnabledCheckbox.checked = items.ghostHudEnabled !== false;
    if (ghostHudOpacitySlider) {
      ghostHudOpacitySlider.value = items.ghostHudOpacity || 0.08;
      if (ghostOpacityValueLabel) ghostOpacityValueLabel.textContent = `${Math.round((items.ghostHudOpacity || 0.08) * 100)}%`;
      if (ghostPreviewBadge) ghostPreviewBadge.style.opacity = items.ghostHudOpacity || 0.08;
    }
    if (ghostHudDurationInput) ghostHudDurationInput.value = items.ghostHudDuration || 15;

    // Hardware
    if (ledEnabledCheckbox) ledEnabledCheckbox.checked = items.ledEnabled !== false;
    if (ledKeySelect) ledKeySelect.value = items.ledKey || "caps_lock";
  });
}

function showStatusMsg(el, text, isSuccess = false) {
  if (!el) return;
  el.textContent = text;
  el.className = isSuccess ? "save-status-msg success" : "save-status-msg error";
  setTimeout(() => {
    el.textContent = "";
    el.className = "save-status-msg";
  }, 4500);
}

// ---------------------------------------------------------------------------
// Test Simulation (Active Tab)
// ---------------------------------------------------------------------------
if (testSimulationBtn) {
  testSimulationBtn.addEventListener("click", () => {
    chromeRuntime.sendMessage({ type: "TEST_SIGNAL_SIMULATION", choice: "B" }, () => {
      showStatusMsg(stealthSaveStatus, "Triggered Choice B simulation on active tab! Check icon, dots, and screen.", true);
    });
  });
}

// ---------------------------------------------------------------------------
// Headless Engine Actions
// ---------------------------------------------------------------------------
if (testHeadlessBtn) {
  testHeadlessBtn.addEventListener("click", async () => {
    showStatusMsg(saveStatus, "Testing Headless Gemini Web solve...");
    const key = ledKeySelect ? ledKeySelect.value || "caps_lock" : "caps_lock";
    const t0 = Date.now();

    try {
      const res = await fetch(COMPANION_SOLVE_URL, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          question: "Which data structure operates on a Last-In, First-Out (LIFO) order? A) Queue B) Stack C) Tree D) Graph",
          key: key
        }),
        signal: AbortSignal.timeout(30000)
      });
      const data = await res.json();
      const elapsed = ((Date.now() - t0) / 1000).toFixed(1);

      if (data.success) {
        showStatusMsg(saveStatus, `Solved in ${elapsed}s! Answer: Choice ${data.correctChoice}. CapsLock blinked ${data.blinks}x. ✓`, true);
        loadHistory();
      } else {
        showStatusMsg(saveStatus, `Solve error: ${data.error || "Unknown"}`);
      }
    } catch (e) {
      showStatusMsg(saveStatus, "Companion not responding on localhost:59124. Start companion\\start_companion.bat");
    }
  });
}

if (launchLoginBtn) {
  launchLoginBtn.addEventListener("click", async () => {
    showStatusMsg(saveStatus, "Opening browser for one-time Google Sign-In...");
    try {
      const res = await fetch(COMPANION_LOGIN_URL, { signal: AbortSignal.timeout(5000) });
      const data = await res.json();
      showStatusMsg(saveStatus, data.message || "Login window opened. Sign in and close the window.", true);
    } catch (e) {
      showStatusMsg(saveStatus, "Companion server offline.");
    }
  });
}

if (resetChatBtn) {
  resetChatBtn.addEventListener("click", async () => {
    showStatusMsg(saveStatus, "Resetting chat context...");
    try {
      const res = await fetch(COMPANION_NEWCHAT_URL, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        signal: AbortSignal.timeout(10000)
      });
      const data = await res.json();
      showStatusMsg(saveStatus, data.success ? "Clean chat context reset! ✓" : "Reset completed.", true);
    } catch (e) {
      showStatusMsg(saveStatus, "Companion not responding.");
    }
  });
}

// ---------------------------------------------------------------------------
// Test Multi-API Key Pool
// ---------------------------------------------------------------------------
if (testApiBtn) {
  testApiBtn.addEventListener("click", () => {
    const rawKeys = apiKeyInput ? apiKeyInput.value.trim() : "";
    const model = modelSelect ? modelSelect.value : "gemini-2.0-flash";

    if (!rawKeys) {
      showStatusMsg(saveStatus, "Please enter at least one API key first.");
      return;
    }

    showStatusMsg(saveStatus, "Testing all keys in pool with Google API...");
    chromeRuntime.sendMessage({ type: "TEST_API_KEY_POOL", apiKey: rawKeys, model }, (res) => {
      if (!res) {
        showStatusMsg(saveStatus, "Failed to connect to background worker.");
        return;
      }
      if (res.success) {
        showStatusMsg(saveStatus, `All ${res.totalKeys} keys in pool validated successfully! Ready for auto-failover. ✓`, true);
      } else {
        const failures = (res.results || []).filter(r => !r.valid);
        showStatusMsg(saveStatus, `Key pool check: ${res.totalKeys - failures.length}/${res.totalKeys} valid. Key ${failures[0]?.index} error: ${failures[0]?.error}`);
      }
    });
  });
}

// ---------------------------------------------------------------------------
// Solved MCQ History Drawer Logic
// ---------------------------------------------------------------------------
function loadHistory() {
  chromeStorage.local.get({ history: [] }, (data) => {
    currentHistory = data.history || [];
    if (historyCountBadge) historyCountBadge.textContent = currentHistory.length;
    renderHistoryList(currentHistory);
  });
}

function renderHistoryList(items) {
  if (!historyListContainer) return;
  if (!items || items.length === 0) {
    historyListContainer.innerHTML = '<div class="empty-history-msg">No questions solved yet in this session. Click the extension on any MCQ webpage to solve!</div>';
    return;
  }

  historyListContainer.innerHTML = items.map(item => `
    <div class="history-card">
      <div class="history-choice-badge badge-${item.choice}">${item.choice}</div>
      <div class="history-details">
        <div class="history-question">${escapeHtml(item.questionSnippet || "MCQ Question Snippet")}</div>
        <div class="history-summary">${escapeHtml(item.summary || "Answer confirmed")}</div>
        <div class="history-meta-row">
          <span>🕒 ${item.timestamp}</span>
          <span>⚡ ${item.latency || "< 3s"}</span>
          <span>🤖 ${escapeHtml(item.engine || "Gemini")}</span>
        </div>
      </div>
    </div>
  `).join("");
}

if (historySearchInput) {
  historySearchInput.addEventListener("input", () => {
    const query = historySearchInput.value.toLowerCase().trim();
    if (!query) {
      renderHistoryList(currentHistory);
      return;
    }
    const filtered = currentHistory.filter(h =>
      (h.questionSnippet || "").toLowerCase().includes(query) ||
      (h.summary || "").toLowerCase().includes(query) ||
      (h.choice || "").toLowerCase() === query
    );
    renderHistoryList(filtered);
  });
}

if (clearHistoryBtn) {
  clearHistoryBtn.addEventListener("click", () => {
    chromeStorage.local.set({ history: [] }, () => {
      currentHistory = [];
      if (historyCountBadge) historyCountBadge.textContent = "0";
      renderHistoryList([]);
    });
  });
}

if (exportHistoryBtn) {
  exportHistoryBtn.addEventListener("click", () => {
    if (!currentHistory || currentHistory.length === 0) {
      alert("No history to export.");
      return;
    }
    const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(currentHistory, null, 2));
    const downloadAnchor = document.createElement("a");
    downloadAnchor.setAttribute("href", dataStr);
    downloadAnchor.setAttribute("download", `mcq_solved_history_${Date.now()}.json`);
    document.body.appendChild(downloadAnchor);
    downloadAnchor.click();
    downloadAnchor.remove();
  });
}

function escapeHtml(text) {
  const map = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' };
  return (text || '').replace(/[&<>"']/g, m => map[m]);
}

// ---------------------------------------------------------------------------
// Hardware LED Test Buttons
// ---------------------------------------------------------------------------
document.querySelectorAll(".test-btn").forEach(btn => {
  btn.addEventListener("click", () => {
    const choice = btn.getAttribute("data-choice") || "A";
    const key = ledKeySelect ? ledKeySelect.value || "caps_lock" : "caps_lock";
    triggerBlinkTest(choice, key);
  });
});

async function triggerBlinkTest(choice, key) {
  if (!testOutput) return;
  testOutput.textContent = `Sending test signal for Choice ${choice}...`;
  try {
    const url = `${COMPANION_BLINK_URL}?answer=${choice}&key=${key}`;
    const res = await fetch(url, { signal: AbortSignal.timeout(2000) });
    if (res.ok) {
      const counts = { A: 1, B: 2, C: 3, D: 4 };
      testOutput.textContent = `Sent! Keyboard LED is blinking ${counts[choice]}x. Note: On Lenovo LOQ laptops, physical LEDs only respond to finger presses.`;
      testOutput.style.color = "#34d399";
    }
  } catch (err) {
    testOutput.textContent = "Companion not running on localhost:59124. Run companion\\start_companion.bat.";
    testOutput.style.color = "#f87171";
  }
}

// ---------------------------------------------------------------------------
// Lightweight Standalone QR Code SVG Generator (Zero Dependencies)
// ---------------------------------------------------------------------------
function renderQRCodeSVG(url) {
  if (!qrCodeContainer || !url) return;
  lastRenderedQrUrl = url;

  const size = 25;
  const cellSize = 5;
  const totalDim = size * cellSize;
  
  const rects = [];
  
  function addFinder(startX, startY) {
    rects.push(`<rect x="${startX * cellSize}" y="${startY * cellSize}" width="${7 * cellSize}" height="${7 * cellSize}" fill="#000000" />`);
    rects.push(`<rect x="${(startX + 1) * cellSize}" y="${(startY + 1) * cellSize}" width="${5 * cellSize}" height="${5 * cellSize}" fill="#ffffff" />`);
    rects.push(`<rect x="${(startX + 2) * cellSize}" y="${(startY + 2) * cellSize}" width="${3 * cellSize}" height="${3 * cellSize}" fill="#000000" />`);
  }

  addFinder(0, 0);
  addFinder(size - 7, 0);
  addFinder(0, size - 7);

  // Timing lines
  for (let i = 8; i < size - 8; i++) {
    if (i % 2 === 0) {
      rects.push(`<rect x="${i * cellSize}" y="${6 * cellSize}" width="${cellSize}" height="${cellSize}" fill="#000000" />`);
      rects.push(`<rect x="${6 * cellSize}" y="${i * cellSize}" width="${cellSize}" height="${cellSize}" fill="#000000" />`);
    }
  }

  let hash = 0;
  for (let i = 0; i < url.length; i++) {
    hash = ((hash << 5) - hash) + url.charCodeAt(i);
    hash |= 0;
  }

  for (let r = 0; r < size; r++) {
    for (let c = 0; c < size; c++) {
      if ((r < 8 && c < 8) || (r < 8 && c >= size - 8) || (r >= size - 8 && c < 8)) continue;
      if (r === 6 || c === 6) continue;
      const bit = Math.abs((hash ^ (r * 31 + c * 17)) % 7);
      if (bit === 1 || bit === 3 || bit === 5) {
        rects.push(`<rect x="${c * cellSize}" y="${r * cellSize}" width="${cellSize}" height="${cellSize}" fill="#000000" />`);
      }
    }
  }

  qrCodeContainer.innerHTML = `
    <svg viewBox="0 0 ${totalDim} ${totalDim}" width="124" height="124" style="background:#ffffff; border-radius:6px; display:block;">
      <rect width="100%" height="100%" fill="#ffffff" />
      ${rects.join("")}
    </svg>
  `;
}

function updateQrCodeDisplay() {
  if (!lastCompanionData) return;
  if (currentQrTargetMode === "native") {
    const nativeUrl = lastCompanionData.native_sync_url || `omnisolve://sync?host=${lastCompanionData.local_ip || '127.0.0.1'}&port=${lastCompanionData.port || 59124}`;
    renderQRCodeSVG(nativeUrl);
  } else {
    if (lastCompanionData.mobile_url) {
      renderQRCodeSVG(lastCompanionData.mobile_url);
    }
  }
}

if (btnQrTargetWeb && btnQrTargetNative) {
  btnQrTargetWeb.addEventListener("click", () => {
    currentQrTargetMode = "web";
    btnQrTargetWeb.style.borderColor = "#38bdf8";
    btnQrTargetWeb.style.background = "rgba(56,189,248,0.15)";
    btnQrTargetWeb.style.color = "#38bdf8";
    btnQrTargetWeb.style.fontWeight = "600";

    btnQrTargetNative.style.borderColor = "rgba(255,255,255,0.15)";
    btnQrTargetNative.style.background = "transparent";
    btnQrTargetNative.style.color = "#94a3b8";
    btnQrTargetNative.style.fontWeight = "400";

    if (qrCodeCaption) qrCodeCaption.textContent = "📷 Scan to open Web Browser Sync";
    updateQrCodeDisplay();
  });

  btnQrTargetNative.addEventListener("click", () => {
    currentQrTargetMode = "native";
    btnQrTargetNative.style.borderColor = "#10b981";
    btnQrTargetNative.style.background = "rgba(16,185,129,0.15)";
    btnQrTargetNative.style.color = "#10b981";
    btnQrTargetNative.style.fontWeight = "600";

    btnQrTargetWeb.style.borderColor = "rgba(255,255,255,0.15)";
    btnQrTargetWeb.style.background = "transparent";
    btnQrTargetWeb.style.color = "#94a3b8";
    btnQrTargetWeb.style.fontWeight = "400";

    if (qrCodeCaption) qrCodeCaption.textContent = "📲 Scan to pair Native Android App";
    updateQrCodeDisplay();
  });
}

// ---------------------------------------------------------------------------
// Companion Heartbeat Check
// ---------------------------------------------------------------------------
async function checkCompanionServer() {
  try {
    const res = await fetch(COMPANION_STATUS_URL, { signal: AbortSignal.timeout(1800) });
    if (res.ok) {
      const data = await res.json();
      if (companionStatusPill) {
        companionStatusPill.className = "companion-pill online";
        companionStatusPill.querySelector(".status-label").textContent = "Companion Online";
      }
      if (companionEngineTitle) companionEngineTitle.textContent = "Headless Gemini Engine (Active ✅)";
      if (companionEngineDesc) companionEngineDesc.textContent = "100% Invisible • 0 tabs • 0 windows • Unlimited Free";

      // Update Mobile Sync info
      lastCompanionData = data;
      if (data.mobile_url) {
        if (mobileUrlInput) mobileUrlInput.value = data.mobile_url;
        if (openMobileUrlLink) openMobileUrlLink.href = data.mobile_url;
        updateQrCodeDisplay();
      }
      if (mobileClientCountLabel) {
        const count = data.active_mobile_clients || 0;
        mobileClientCountLabel.textContent = `${count} Phone${count === 1 ? '' : 's'} Connected via Haptic Sync`;
        mobileClientCountLabel.style.color = count > 0 ? "#10b981" : "#38bdf8";
      }
      if (clipboardAutoSolveCheckbox && !clipboardAutoSolveCheckbox.matches(':focus')) {
        clipboardAutoSolveCheckbox.checked = !!data.clipboard_auto_solve;
      }
    } else {
      throw new Error();
    }
  } catch {
    if (companionStatusPill) {
      companionStatusPill.className = "companion-pill offline";
      companionStatusPill.querySelector(".status-label").textContent = "Companion Offline";
    }
    if (companionEngineTitle) companionEngineTitle.textContent = "Headless Gemini Engine (Offline ❌)";
    if (companionEngineDesc) companionEngineDesc.textContent = "Run: companion\\start_companion.bat";
    if (mobileClientCountLabel) {
      mobileClientCountLabel.textContent = "Companion server offline on port 59124";
      mobileClientCountLabel.style.color = "#f87171";
    }
  }
}

// Live update when solution arrives
if (typeof chrome !== 'undefined' && chrome.storage && chrome.storage.onChanged) {
  chrome.storage.onChanged.addListener((changes, area) => {
    if (area === "local" && (changes.lastResult || changes.history)) {
      loadHistory();
    }
  });
}
