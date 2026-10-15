/**
 * Smart Vision Solver - Gemini Web Content Script (v2.1)
 * Completely Invisible Background Solver with native MutationObserver
 * Zero GUI / Zero Skin / Multi-Turn Continuous Solving without reload churn
 */

if (window.__SMART_VISION_GEMINI_SCRIPT_LOADED__) {
  console.log("[Smart Vision] Content script already active.");
} else {
  window.__SMART_VISION_GEMINI_SCRIPT_LOADED__ = true;
  console.log("[Smart Vision] Advanced Gemini Content Script v2.1 Active");

  // Keep page active even when window is minimized by overriding visibilityState
  try {
    Object.defineProperty(document, 'hidden', { value: false, writable: false });
    Object.defineProperty(document, 'visibilityState', { value: 'visible', writable: false });
  } catch (_) {}

  chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
    if (request.type === "PING_GEMINI") {
      const isReady = checkInputReady();
      const loggedIn = isUserLoggedIn();
      sendResponse({ ready: isReady, url: window.location.href, loggedIn });
      return true;
    }

    if (request.type === "INJECT_AND_SOLVE") {
      console.log("[Smart Vision] Executing INJECT_AND_SOLVE for question...");
      injectAndSolvePrompt(request.prompt)
        .then(result => {
          console.log("[Smart Vision] Solved successfully:", result);
          sendResponse(result);
        })
        .catch(err => {
          console.error("[Smart Vision] Solve error:", err);
          sendResponse({ success: false, error: err.message });
        });
      return true; // Keep message channel open for async response
    }
  });

  // Listen for postMessage from parent offscreen window
  window.addEventListener("message", (event) => {
    if (event.data && event.data.type === "INJECT_AND_SOLVE") {
      console.log("[Smart Vision] Executing INJECT_AND_SOLVE via postMessage...");
      injectAndSolvePrompt(event.data.prompt)
        .then(result => {
          window.parent.postMessage({ type: "SOLVE_MCQ_RESULT", payload: result }, "*");
        })
        .catch(err => {
          window.parent.postMessage({ type: "SOLVE_MCQ_RESULT", payload: { success: false, error: err.message } }, "*");
        });
    }
  });

  // Announce ready state to parent offscreen window
  try {
    window.parent.postMessage({ type: "GEMINI_CONTENT_SCRIPT_READY" }, "*");
  } catch (_) {}

  function isUserLoggedIn() {
    if (window.location.href.includes("accounts.google.com")) {
      return false;
    }
    const signInBtn = document.querySelector('a[href*="accounts.google.com/ServiceLogin"], a[href*="accounts.google.com/signin"], button[aria-label*="Sign in" i]');
    if (signInBtn && !document.querySelector('rich-textarea, div[contenteditable="true"]')) {
      return false;
    }
    return true;
  }

  function findInputElement() {
    return document.querySelector('rich-textarea div[contenteditable="true"]') ||
           document.querySelector('div[contenteditable="true"][role="textbox"]') ||
           document.querySelector('div[contenteditable="true"]') ||
           document.querySelector('div[role="textbox"]') ||
           document.querySelector('rich-textarea p') ||
           document.querySelector('div.ql-editor') ||
           document.querySelector('textarea');
  }

  function checkInputReady() {
    return !!findInputElement();
  }

  async function waitForInputElement(timeoutMs = 8000) {
    const start = Date.now();
    while (Date.now() - start < timeoutMs) {
      const input = findInputElement();
      if (input) return input;
      await new Promise(r => setTimeout(r, 200));
    }
    return null;
  }

  function simulateClick(el) {
    if (!el) return;
    ['pointerdown', 'mousedown', 'pointerup', 'mouseup', 'click'].forEach(type => {
      const evt = new MouseEvent(type, { bubbles: true, cancelable: true, view: window });
      el.dispatchEvent(evt);
    });
    try { el.focus(); } catch (_) {}
  }

  /**
   * Resets chat only if turns exceed 25 to avoid DOM bloat,
   * otherwise preserves conversation for maximum speed and zero reload churn.
   */
  async function maybeCleanLongSession() {
    const turns = document.querySelectorAll(
      '.model-response-text, message-content, [data-test-id="model-turn"], model-response'
    );
    if (turns.length > 25) {
      console.log("[Smart Vision] Session exceeds 25 turns, initiating fresh chat...");
      const newChatBtn = document.querySelector(
        'a[href="/app"], [data-test-id="new-chat-button"], button[aria-label*="New chat" i], a[aria-label*="New chat" i]'
      );
      if (newChatBtn) {
        simulateClick(newChatBtn);
        await new Promise(r => setTimeout(r, 600));
      }
    }
  }

  async function injectAndSolvePrompt(prompt) {
    return new Promise(async (resolve, reject) => {
      try {
        if (!isUserLoggedIn()) {
          return reject(new Error("Please log into your Google Account in Gemini first."));
        }

        // Clean if too many turns accumulated
        await maybeCleanLongSession();

        // 1. Tag all existing response elements so we only listen for the NEW response
        const oldElements = document.querySelectorAll(
          '.model-response-text, .markdown, message-content, [data-test-id="model-turn"], model-response, .response-container'
        );
        oldElements.forEach(el => el.setAttribute('data-omni-old', 'true'));

        // 2. Locate input element with retry wait
        const richTextarea = document.querySelector('rich-textarea');
        if (richTextarea) simulateClick(richTextarea);

        const input = await waitForInputElement(8000);
        if (!input) {
          return reject(new Error("Gemini input box not ready. Ensure you are signed in."));
        }

        simulateClick(input);
        input.focus();

        // 3. Clear existing text in input box
        try {
          const sel = window.getSelection();
          const range = document.createRange();
          range.selectNodeContents(input);
          sel.removeAllRanges();
          sel.addRange(range);
          document.execCommand('delete', false, null);
        } catch (_) {}

        // 4. Insert text via DataTransfer paste event (reliable ProseMirror trigger)
        try {
          const dt = new DataTransfer();
          dt.setData('text/plain', prompt);
          const pasteEvt = new ClipboardEvent('paste', {
            bubbles: true,
            cancelable: true,
            clipboardData: dt
          });
          input.dispatchEvent(pasteEvt);
        } catch (_) {}

        // Fallback insertion
        try {
          document.execCommand('insertText', false, prompt);
        } catch (_) {}

        if (!input.textContent || input.textContent.trim().length === 0) {
          input.innerText = prompt;
        }

        input.dispatchEvent(new InputEvent('beforeinput', { bubbles: true, inputType: 'insertText', data: prompt }));
        input.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertText', data: prompt }));
        input.dispatchEvent(new Event('input', { bubbles: true }));
        input.dispatchEvent(new Event('change', { bubbles: true }));

        // 5. Send button trigger with retry
        let attempts = 0;
        const sendTimer = setInterval(() => {
          attempts++;
          const sendBtn = document.querySelector('button[aria-label*="Send" i]') ||
                          document.querySelector('button[aria-label*="Submit" i]') ||
                          document.querySelector('button[data-test-id="send-button"]') ||
                          document.querySelector('.send-button') ||
                          document.querySelector('button.send-button-container') ||
                          document.querySelector('mat-icon[fonticon="send"]')?.closest('button') ||
                          document.querySelector('mat-icon[data-mat-icon-name="send"]')?.closest('button') ||
                          document.querySelector('button:has(svg path[d*="M2.01 21L23 12"])');

          if (sendBtn && !sendBtn.disabled && sendBtn.getAttribute('aria-disabled') !== 'true') {
            clearInterval(sendTimer);
            simulateClick(sendBtn);
            startMutationObserver(resolve, reject);
          } else if (attempts >= 8) {
            clearInterval(sendTimer);
            // Keyboard Enter fallback
            try {
              ['keydown', 'keypress', 'keyup'].forEach(type => {
                input.dispatchEvent(new KeyboardEvent(type, {
                  key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true, cancelable: true
                }));
              });
            } catch (_) {}
            if (sendBtn) simulateClick(sendBtn);
            startMutationObserver(resolve, reject);
          }
        }, 120);

      } catch (err) {
        reject(err);
      }
    });
  }

  /**
   * Native MutationObserver: Instant callback upon DOM insertion.
   * Never throttled by background tab or minimized window throttling!
   */
  function startMutationObserver(resolve, reject) {
    let isResolved = false;

    function cleanup() {
      if (observer) observer.disconnect();
      if (backupInterval) clearInterval(backupInterval);
      if (timeoutTimer) clearTimeout(timeoutTimer);
    }

    function checkResponse() {
      if (isResolved) return;

      const allResponses = document.querySelectorAll(
        '.model-response-text, .markdown, message-content, [data-test-id="model-turn"], model-response, .response-container'
      );
      const newResponses = [];
      for (let i = 0; i < allResponses.length; i++) {
        const el = allResponses[i];
        if (el.getAttribute('data-omni-old') !== 'true' && !el.closest('[data-omni-old="true"]')) {
          newResponses.push(el);
        }
      }

      if (newResponses.length === 0) return;

      const targetElement = newResponses[newResponses.length - 1];
      const text = (targetElement.textContent || "").trim();
      if (!text || text.length === 0) return;

      // Check if error message from Gemini
      if (text.includes("something went wrong") && text.length < 100) {
        isResolved = true;
        cleanup();
        reject(new Error("Gemini reported a temporary error. Try again."));
        return;
      }

      // Check if still streaming
      const isStreaming = !!document.querySelector(
        'button[aria-label*="Stop" i], [data-test-id="stop-button"], mat-icon[fonticon="stop"]'
      );

      // Answer parsing regex
      let match = text.match(/CORRECT_OPTION:\s*\(?([A-D])\)?/i);
      if (!match) match = text.match(/ANSWER:\s*\(?([A-D])\)?/i);
      if (!match) match = text.match(/(?:correct\s*(?:answer|option)|answer\s*is)[:\s]*\(?([A-D])\)?/i);
      if (!match) match = text.match(/Option\s*\(?([A-D])\)?\s*(?:is\s*(?:the\s*)?correct|is\s*right)/i);
      if (!match) match = text.match(/\b([A-D])\s+is\s+(?:the\s+)?correct\s+(?:option|answer|choice)\b/i);

      if (!match && !isStreaming) {
        match = text.match(/(?:therefore|hence|so|answer is|option)\s*\(?([A-D])\)?/i) ||
                text.match(/^\s*\(?([A-D])\)?(?:\.|\:|\s|$)/m) ||
                text.match(/\b([A-D])\b(?=[^A-D]*$)/);
      }

      if (match && match[1]) {
        const letter = match[1].toUpperCase();
        if (['A', 'B', 'C', 'D'].includes(letter)) {
          isResolved = true;
          cleanup();
          resolve({
            success: true,
            correctChoice: letter,
            summary: text.substring(0, 160)
          });
        }
      }
    }

    // 1. Attach native MutationObserver
    const observer = new MutationObserver(() => {
      checkResponse();
    });

    observer.observe(document.body, {
      childList: true,
      subtree: true,
      characterData: true
    });

    // 2. Also check immediately and periodically as backup
    const backupInterval = setInterval(checkResponse, 400);

    // 3. Safety timeout (28s)
    const timeoutTimer = setTimeout(() => {
      if (!isResolved) {
        cleanup();
        reject(new Error("Timeout waiting for answer from Gemini Web."));
      }
    }, 28000);
  }
}
