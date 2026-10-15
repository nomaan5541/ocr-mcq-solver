/**
 * Smart Vision Solver - Offscreen Engine Controller
 * Manages the completely invisible Gemini iframe with ZERO browser tabs and ZERO windows!
 */

console.log("[Smart Vision Offscreen] Offscreen engine running...");

const iframe = document.getElementById("geminiFrame");
let isFrameLoaded = false;
let isContentScriptReady = false;

if (iframe) {
  iframe.addEventListener("load", () => {
    console.log("[Smart Vision Offscreen] Gemini iframe loaded.");
    isFrameLoaded = true;
  });
}

// Track ready state from iframe
window.addEventListener("message", (event) => {
  if (event.data && event.data.type === "GEMINI_CONTENT_SCRIPT_READY") {
    console.log("[Smart Vision Offscreen] Content script in iframe reported ready.");
    isContentScriptReady = true;
  }
});

function waitForFrameReady(timeoutMs = 10000) {
  return new Promise((resolve) => {
    if (isContentScriptReady || isFrameLoaded) {
      return resolve(true);
    }
    const timer = setTimeout(() => resolve(false), timeoutMs);
    const check = () => {
      if (isContentScriptReady || isFrameLoaded) {
        clearTimeout(timer);
        resolve(true);
      } else {
        setTimeout(check, 250);
      }
    };
    check();
  });
}

chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
  if (request.type === "OFFSCREEN_PING") {
    sendResponse({ status: "alive", frameLoaded: isFrameLoaded, ready: isContentScriptReady });
    return true;
  }

  if (request.type === "OFFSCREEN_RELOAD_FRAME") {
    if (iframe) {
      isFrameLoaded = false;
      isContentScriptReady = false;
      iframe.src = "https://gemini.google.com/app";
    }
    sendResponse({ status: "reloading" });
    return true;
  }

  if (request.type === "INJECT_AND_SOLVE") {
    console.log("[Smart Vision Offscreen] Received INJECT_AND_SOLVE prompt from background.js");

    waitForFrameReady(8000).then(() => {
      if (!iframe || !iframe.contentWindow) {
        sendResponse({ success: false, error: "Gemini iframe not available in offscreen document." });
        return;
      }

      const timeoutTimer = setTimeout(() => {
        window.removeEventListener("message", onSolveResult);
        sendResponse({
          success: false,
          error: "Timeout waiting for Gemini response. Please open Settings -> 'In-Page Google Sign-In' to verify your session."
        });
      }, 28000);

      function onSolveResult(event) {
        if (event.data && event.data.type === "SOLVE_MCQ_RESULT") {
          clearTimeout(timeoutTimer);
          window.removeEventListener("message", onSolveResult);
          console.log("[Smart Vision Offscreen] Received solve result from iframe:", event.data.payload);
          sendResponse(event.data.payload);
        }
      }
      window.addEventListener("message", onSolveResult);

      try {
        iframe.contentWindow.postMessage({
          type: "INJECT_AND_SOLVE",
          prompt: request.prompt
        }, "*");
      } catch (postErr) {
        clearTimeout(timeoutTimer);
        window.removeEventListener("message", onSolveResult);
        sendResponse({ success: false, error: "Failed to dispatch prompt to iframe: " + postErr.message });
      }
    });

    return true; // Keep message channel open for async sendResponse!
  }
});
