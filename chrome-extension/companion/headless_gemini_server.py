#!/usr/bin/env python3
"""
=============================================================================
Smart Vision Solver - Unified Headless Gemini Companion Server v1.8 (Windows)
=============================================================================
Features:
  1. Headless Gemini Web Engine (Zero browser tabs, zero desktop windows).
  2. Mobile Phone Silent Sync:
     - Real-time Server-Sent Events (SSE) on /events
     - Pitch-black OLED Mobile Web App on /mobile
     - Silent Web Vibration API (1 buzz=A, 2=B, 3=C, 4=D)
     - Tap-to-peek stealth overlay & Screen Wake Lock
  3. Auto-Solve on Copy (Clipboard Intelligence):
     - Background Windows clipboard listener (Ctrl+C auto-solve)
  4. Physical Keyboard LED Controller (Caps Lock, Scroll Lock, Num Lock).
  5. Emergency Panic Mode (POST /panic wipes mobile & desktop indicators).
  6. HTTP API on port 59124 (bound to 0.0.0.0 for LAN/Wi-Fi phone sync).
=============================================================================
"""

import os
import re
import sys
import time
import queue
import socket
import threading
import subprocess
from urllib.parse import urlparse, parse_qs
from http.server import HTTPServer, BaseHTTPRequestHandler
from socketserver import ThreadingMixIn
import json
import ctypes
from ctypes import wintypes

# ---------------------------------------------------------------------------
# Global State & Configuration
# ---------------------------------------------------------------------------
PORT = 59124
VK_CAPITAL = 0x14  # Caps Lock
VK_NUMLOCK = 0x90  # Num Lock
VK_SCROLL  = 0x91  # Scroll Lock

KEY_MAP = {
    "caps_lock": VK_CAPITAL,
    "scroll_lock": VK_SCROLL,
    "num_lock": VK_NUMLOCK
}

BLINK_COUNTS = {
    "A": 1, "B": 2, "C": 3, "D": 4,
    "1": 1, "2": 2, "3": 3, "4": 4
}

# SSE clients queue management
sse_lock = threading.Lock()
sse_clients = []
latest_solution = {
    "choice": None,
    "summary": None,
    "timestamp": None,
    "blinks": 0,
    "id": 0
}

# Auto-solve clipboard config
clipboard_auto_solve_enabled = False
last_clipboard_text = ""

def get_local_ip():
    """Detects primary local IPv4 address."""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(('8.8.8.8', 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"

LOCAL_IP = get_local_ip()

# ---------------------------------------------------------------------------
# Physical Keyboard LED Control
# ---------------------------------------------------------------------------
blink_lock = threading.Lock()

def get_key_state(vk_code):
    if sys.platform == "win32":
        return (ctypes.windll.user32.GetKeyState(vk_code) & 1) != 0
    return False

def press_key(vk_code):
    if sys.platform == "win32":
        KEYEVENTF_KEYUP = 0x0002
        ctypes.windll.user32.keybd_event(vk_code, 0x45, 0, 0)
        time.sleep(0.02)
        ctypes.windll.user32.keybd_event(vk_code, 0x45, KEYEVENTF_KEYUP, 0)

def blink_led_worker(vk_code, count, key_name):
    with blink_lock:
        print(f"[LED Signal] Blinking {key_name.upper()} {count} time(s)...")
        initial_state = get_key_state(vk_code)
        for i in range(count):
            press_key(vk_code)
            time.sleep(0.20)
            press_key(vk_code)
            time.sleep(0.20)
        final_state = get_key_state(vk_code)
        if final_state != initial_state:
            press_key(vk_code)
        print(f"[LED Signal] Completed. {key_name.upper()} restored.")

def trigger_led_blink(choice, key_name="caps_lock"):
    count = BLINK_COUNTS.get(choice.upper(), 1)
    vk_code = KEY_MAP.get(key_name.lower(), VK_CAPITAL)
    t = threading.Thread(target=blink_led_worker, args=(vk_code, count, key_name), daemon=True)
    t.start()
    return count

# ---------------------------------------------------------------------------
# 64-bit Safe Windows Clipboard Reader
# ---------------------------------------------------------------------------
def get_windows_clipboard_text():
    if sys.platform != "win32":
        return None
    try:
        CF_UNICODETEXT = 13
        u = ctypes.windll.user32
        k = ctypes.windll.kernel32
        
        u.OpenClipboard.argtypes = [wintypes.HWND]
        u.OpenClipboard.restype = wintypes.BOOL
        u.CloseClipboard.argtypes = []
        u.CloseClipboard.restype = wintypes.BOOL
        u.GetClipboardData.argtypes = [wintypes.UINT]
        u.GetClipboardData.restype = wintypes.HANDLE
        
        k.GlobalLock.argtypes = [wintypes.HGLOBAL]
        k.GlobalLock.restype = ctypes.c_void_p
        k.GlobalUnlock.argtypes = [wintypes.HGLOBAL]
        k.GlobalUnlock.restype = wintypes.BOOL

        if not u.OpenClipboard(None):
            return None
        try:
            h = u.GetClipboardData(CF_UNICODETEXT)
            if not h:
                return None
            p = k.GlobalLock(h)
            if not p:
                return None
            text = ctypes.c_wchar_p(p).value
            k.GlobalUnlock(h)
            return text
        finally:
            u.CloseClipboard()
    except Exception:
        return None

def looks_like_mcq(text):
    if not text or len(text.strip()) < 15 or len(text) > 4000:
        return False
    # Check for options pattern: A), B), C) or 1., 2. or (A) or Option A
    has_options = bool(re.search(r'(?:^[A-D][\)\.:]|[ \n][A-D][\)\.:]|\([A-D]\)|Option\s+[A-D])', text, re.IGNORECASE | re.MULTILINE))
    has_question = "?" in text or bool(re.search(r'\b(which|what|where|who|how|why|select|choose|find)\b', text, re.IGNORECASE))
    return has_options and has_question

# ---------------------------------------------------------------------------
# Real-Time SSE Broadcast Engine
# ---------------------------------------------------------------------------
def broadcast_event(event_type, payload):
    global latest_solution
    if event_type == "solve":
        latest_solution = {
            "type": "solve",
            "choice": payload.get("choice", "A"),
            "summary": payload.get("summary", ""),
            "timestamp": time.strftime("%H:%M:%S"),
            "blinks": payload.get("blinks", 1),
            "id": latest_solution.get("id", 0) + 1
        }
        msg = f"event: solve\ndata: {json.dumps(latest_solution)}\n\n"
    elif event_type == "panic":
        msg = f"event: panic\ndata: {json.dumps({'action': 'panic', 'timestamp': time.strftime('%H:%M:%S')})}\n\n"
    else:
        msg = f"event: {event_type}\ndata: {json.dumps(payload)}\n\n"

    with sse_lock:
        dead = []
        for q in sse_clients:
            try:
                q.put_nowait(msg)
            except Exception:
                dead.append(q)
        for d in dead:
            if d in sse_clients:
                sse_clients.remove(d)

# ---------------------------------------------------------------------------
# Dedicated Playwright Worker Thread
# ---------------------------------------------------------------------------
class PlaywrightGeminiWorker(threading.Thread):
    def __init__(self, profile_dir):
        super().__init__(daemon=True)
        self.profile_dir = profile_dir
        self.queue = queue.Queue()
        self.is_ready = False
        self.page = None
        self.context = None
        self.playwright = None
        self.last_error = None

    def run(self):
        from playwright.sync_api import sync_playwright

        print(f"[Headless Engine] Initializing Playwright Chromium...")
        print(f"[Headless Engine] Profile: {self.profile_dir}")
        
        try:
            self.playwright = sync_playwright().start()
            self._launch_browser()
        except Exception as e:
            self.last_error = str(e)
            print(f"[Headless Engine] Launch error: {e}")

        # Process work loop
        while True:
            task = self.queue.get()
            if not task:
                break
            
            action = task.get("action")
            result_holder = task.get("result_holder")
            done_event = task.get("done_event")
            
            try:
                if action == "solve":
                    res = self._do_solve(task.get("question"))
                    result_holder["result"] = res
                elif action == "new_chat":
                    res = self._do_new_chat()
                    result_holder["result"] = res
                elif action == "status":
                    result_holder["result"] = {
                        "is_ready": self.is_ready,
                        "url": self.page.url if self.page else None,
                        "error": self.last_error
                    }
            except Exception as e:
                print(f"[Headless Engine] Task execution error: {e}")
                result_holder["error"] = str(e)
                try:
                    self._launch_browser()
                except Exception:
                    pass
            finally:
                done_event.set()
                self.queue.task_done()

    def _launch_browser(self):
        self.is_ready = False
        os.makedirs(self.profile_dir, exist_ok=True)
        
        try:
            if self.context:
                self.context.close()
        except Exception:
            pass

        chrome_exe = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
        launch_kwargs = {
            "user_data_dir": self.profile_dir,
            "headless": True,
            "args": [
                "--disable-blink-features=AutomationControlled",
                "--no-first-run",
                "--no-default-browser-check"
            ]
        }
        if os.path.isfile(chrome_exe):
            launch_kwargs["executable_path"] = chrome_exe
            print(f"[Headless Engine] Using installed Google Chrome: {chrome_exe}")
        else:
            print("[Headless Engine] Using bundled Playwright Chromium")

        self.context = self.playwright.chromium.launch_persistent_context(**launch_kwargs)
        self.page = self.context.pages[0] if self.context.pages else self.context.new_page()
        print("[Headless Engine] Navigating to https://gemini.google.com/app...")
        self.page.goto("https://gemini.google.com/app", wait_until="networkidle", timeout=30000)
        self.page.wait_for_timeout(1000)
        self.is_ready = True
        print("[Headless Engine] Ready! Standing by for exam questions.")

    def _do_new_chat(self):
        if not self.page:
            return False
        try:
            btn = self.page.query_selector('a[aria-label*="New chat"], button[aria-label*="New chat"], a[href="/app"]')
            if btn:
                btn.click()
                self.page.wait_for_timeout(600)
                return True
            else:
                self.page.goto("https://gemini.google.com/app", wait_until="networkidle", timeout=15000)
                return True
        except Exception as e:
            print(f"[Headless Engine] new_chat error: {e}")
            return False

    def _do_solve(self, question_text):
        if not self.page:
            raise RuntimeError("Browser not initialized")

        prompt = (
            "CRITICAL INSTRUCTION:\n"
            "You are an expert Multiple Choice Question (MCQ) Solver.\n"
            "Solve the following question and select the single correct choice (A, B, C, or D).\n"
            "Reply strictly in this format:\n"
            "CORRECT_OPTION: X\n"
            "Where X is strictly one letter: A, B, C, or D.\n"
            "Followed by a concise 1-sentence explanation.\n\n"
            f"--- QUESTION START ---\n{question_text}\n--- QUESTION END ---"
        )

        existing_responses = len(self.page.query_selector_all('message-content, .model-response, [data-test-id="model-response-text"]'))
        if existing_responses > 20:
            self._do_new_chat()
            existing_responses = 0

        inserted = self.page.evaluate("""text => {
            const el = document.querySelector('div.ql-editor, div[contenteditable="true"]');
            if (el) {
                el.focus();
                document.execCommand('selectAll', false, null);
                document.execCommand('insertText', false, text);
                el.dispatchEvent(new Event('input', { bubbles: true }));
                return true;
            }
            return false;
        }""", prompt)

        if not inserted:
            raise RuntimeError("Could not find Gemini prompt editor on page.")

        self.page.wait_for_timeout(150)
        send_btn = self.page.query_selector('button[aria-label*="Send"]:not([aria-disabled="true"]), button.send-button:not([disabled])')
        if send_btn:
            send_btn.click()
        else:
            self.page.keyboard.press("Enter")

        start_time = time.time()
        answer_text = ""
        while time.time() - start_time < 22:
            stop_btn = self.page.query_selector('button[aria-label*="Stop"], button[aria-label*="Pause"]')
            responses = self.page.query_selector_all('message-content, .model-response, [data-test-id="model-response-text"]')
            if len(responses) > existing_responses:
                latest = responses[-1].inner_text().strip()
                if latest and ("CORRECT_OPTION" in latest or "Option" in latest or len(latest) > 6):
                    if not stop_btn:
                        answer_text = latest
                        break
            self.page.wait_for_timeout(250)

        if not answer_text:
            raise RuntimeError("Timeout waiting for Gemini response.")

        match = re.search(r'CORRECT_OPTION:\s*([A-D])', answer_text, re.IGNORECASE)
        if not match:
            match = re.search(r'\b(?:Option|Choice)\s*([A-D])\b', answer_text, re.IGNORECASE)
        if not match:
            match = re.search(r'\b([A-D])\b', answer_text)

        choice = match.group(1).upper() if match else "A"

        summary = ""
        lines = [l.strip() for l in answer_text.split("\n") if l.strip() and not l.startswith("CORRECT_OPTION")]
        if lines:
            summary = lines[0]
            if len(summary) > 120:
                summary = summary[:117] + "..."

        return {
            "correctChoice": choice,
            "summary": summary,
            "rawText": answer_text
        }

    def execute_solve(self, question, timeout=30):
        holder = {}
        event = threading.Event()
        self.queue.put({
            "action": "solve",
            "question": question,
            "result_holder": holder,
            "done_event": event
        })
        if not event.wait(timeout=timeout):
            raise TimeoutError("Gemini engine response timeout.")
        if "error" in holder:
            raise RuntimeError(holder["error"])
        return holder.get("result", {})

    def execute_new_chat(self, timeout=10):
        holder = {}
        event = threading.Event()
        self.queue.put({
            "action": "new_chat",
            "result_holder": holder,
            "done_event": event
        })
        event.wait(timeout=timeout)
        return holder.get("result", False)

# ---------------------------------------------------------------------------
# Background Clipboard Intelligence Thread (Auto-Solve on Ctrl+C)
# ---------------------------------------------------------------------------
def clipboard_monitor_loop():
    global last_clipboard_text, clipboard_auto_solve_enabled
    while True:
        try:
            if clipboard_auto_solve_enabled:
                text = get_windows_clipboard_text()
                if text and text != last_clipboard_text:
                    last_clipboard_text = text
                    if looks_like_mcq(text):
                        print(f"\n[Clipboard Intelligence] MCQ detected on clipboard! Auto-solving...")
                        print(f"[Clipboard Intelligence] Snippet: {text[:80].replace(chr(10), ' ')}...")
                        try:
                            solution = gemini_worker.execute_solve(text)
                            choice = solution.get("correctChoice", "A")
                            summary = solution.get("summary", "")
                            blinks = trigger_led_blink(choice, "caps_lock")
                            broadcast_event("solve", {
                                "choice": choice,
                                "summary": summary,
                                "blinks": blinks
                            })
                            print(f"[Clipboard Intelligence] Solved: Choice {choice} -> Broadcast to Phone & LED")
                        except Exception as e:
                            print(f"[Clipboard Intelligence] Auto-solve failed: {e}")
        except Exception:
            pass
        time.sleep(0.5)

# ---------------------------------------------------------------------------
# Pitch-Black Stealth Mobile Web App HTML
# ---------------------------------------------------------------------------
MOBILE_APP_HTML = """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
  <title>System Monitor</title>
  <style>
    * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
    html, body {
      margin: 0; padding: 0;
      width: 100%; height: 100%;
      background: #000000;
      color: #333333;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      overflow: hidden;
      user-select: none;
      -webkit-user-select: none;
    }
    #screen {
      width: 100%; height: 100%;
      display: flex; flex-direction: column;
      justify-content: center; align-items: center;
      background: #000000;
      position: relative;
    }
    /* Hidden stealth peek display */
    #peekDisplay {
      opacity: 0;
      transition: opacity 0.08s ease;
      text-align: center;
      padding: 1.5rem;
      pointer-events: none;
    }
    #peekChoice {
      font-size: 8rem;
      font-weight: 800;
      color: #555555;
      line-height: 1;
      margin-bottom: 0.5rem;
      letter-spacing: -2px;
    }
    #peekSummary {
      font-size: 1rem;
      color: #3a3a3a;
      max-width: 280px;
      line-height: 1.3;
      word-break: break-word;
    }
    /* Status dots */
    #connDot {
      position: absolute;
      bottom: 8px; right: 8px;
      width: 4px; height: 4px;
      border-radius: 50%;
      background: #115e2e;
      opacity: 0.6;
    }
    #pulseDot {
      position: absolute;
      top: 8px; left: 8px;
      width: 6px; height: 6px;
      border-radius: 50%;
      background: transparent;
      transition: all 0.2s ease;
    }
    /* Quick setup panel - hidden after tap */
    #setupPanel {
      position: absolute;
      top: 0; left: 0; right: 0; bottom: 0;
      background: rgba(0,0,0,0.95);
      display: flex; flex-direction: column;
      justify-content: center; align-items: center;
      padding: 2rem;
      z-index: 100;
      transition: opacity 0.3s ease;
    }
    .panel-card {
      background: #0a0a0a;
      border: 1px solid #1a1a1a;
      border-radius: 16px;
      padding: 1.5rem;
      width: 100%; max-width: 320px;
      text-align: center;
    }
    .panel-title { color: #888888; font-size: 1.1rem; font-weight: 600; margin-bottom: 0.5rem; }
    .panel-desc { color: #555555; font-size: 0.8rem; margin-bottom: 1.25rem; line-height: 1.4; }
    .btn {
      display: block; width: 100%;
      padding: 0.75rem;
      margin: 0.35rem 0;
      border: 1px solid #222222;
      border-radius: 8px;
      background: #111111;
      color: #888888;
      font-size: 0.85rem;
      font-weight: 500;
      cursor: pointer;
    }
    .btn-primary { background: #1e3a5f; color: #90caf9; border-color: #2b4c7e; }
    .btn-test-row { display: flex; gap: 0.5rem; margin-top: 0.75rem; }
    .btn-test { flex: 1; padding: 0.5rem; font-size: 0.75rem; }
    .armed-msg { color: #22c55e; font-size: 0.75rem; margin-top: 0.75rem; display: none; }
  </style>
</head>
<body>
  <div id="screen">
    <div id="pulseDot"></div>
    <div id="connDot" title="Sync Status"></div>

    <div id="peekDisplay">
      <div id="peekChoice">--</div>
      <div id="peekSummary">Waiting for question...</div>
    </div>
  </div>

  <div id="setupPanel">
    <div class="panel-card">
      <div class="panel-title">Silent Haptic Sync</div>
      <div class="panel-desc">
        Screen stays pure pitch black.<br>
        Vibrates silently in your pocket:<br>
        <strong>1 Buzz=A &bull; 2=B &bull; 3=C &bull; 4=D</strong><br>
        Hold screen to peek answer letter.
      </div>
      
      <button id="armBtn" class="btn btn-primary">⚡ Arm Haptics & Go Black</button>
      
      <div style="font-size:0.75rem; color:#444; margin-top:0.75rem;">Test Phone Motor:</div>
      <div class="btn-test-row">
        <button class="btn btn-test" onclick="vibrateChoice('A')">A (1x)</button>
        <button class="btn btn-test" onclick="vibrateChoice('B')">B (2x)</button>
        <button class="btn btn-test" onclick="vibrateChoice('C')">C (3x)</button>
        <button class="btn btn-test" onclick="vibrateChoice('D')">D (4x)</button>
      </div>
      <div id="armedMsg" class="armed-msg">✓ Silent Mode Armed & WakeLock Active</div>
    </div>
  </div>

  <script>
    let wakeLock = null;
    let lastSolutionId = 0;
    const VIB_PATTERNS = {
      'A': [350],
      'B': [350, 180, 350],
      'C': [350, 180, 350, 180, 350],
      'D': [350, 180, 350, 180, 350, 180, 350]
    };

    function vibrateChoice(choice) {
      const pattern = VIB_PATTERNS[choice.toUpperCase()] || [300];
      if ('vibrate' in navigator) {
        navigator.vibrate(pattern);
      }
    }

    async function requestWakeLock() {
      try {
        if ('wakeLock' in navigator) {
          wakeLock = await navigator.wakeLock.request('screen');
        }
      } catch (err) {}
    }

    document.getElementById('armBtn').addEventListener('click', async () => {
      await requestWakeLock();
      vibrateChoice('A'); // Quick feedback buzz
      try {
        if (document.documentElement.requestFullscreen) {
          document.documentElement.requestFullscreen();
        }
      } catch (e) {}
      document.getElementById('setupPanel').style.opacity = '0';
      setTimeout(() => {
        document.getElementById('setupPanel').style.display = 'none';
      }, 350);
    });

    // Double tap anywhere on screen to show setup panel again
    let lastTap = 0;
    document.addEventListener('touchend', (e) => {
      const currentTime = new Date().getTime();
      const tapLength = currentTime - lastTap;
      if (tapLength < 400 && tapLength > 50) {
        document.getElementById('setupPanel').style.display = 'flex';
        setTimeout(() => document.getElementById('setupPanel').style.opacity = '1', 10);
      }
      lastTap = currentTime;
    });

    // Hold to peek answer
    const screenEl = document.getElementById('screen');
    const peekDisplay = document.getElementById('peekDisplay');

    function showPeek() {
      peekDisplay.style.opacity = '0.9';
    }
    function hidePeek() {
      peekDisplay.style.opacity = '0';
    }

    screenEl.addEventListener('touchstart', showPeek);
    screenEl.addEventListener('touchend', hidePeek);
    screenEl.addEventListener('mousedown', showPeek);
    screenEl.addEventListener('mouseup', hidePeek);

    function applySolution(choice, summary) {
      document.getElementById('peekChoice').textContent = choice;
      document.getElementById('peekSummary').textContent = summary || 'Answer detected';
      vibrateChoice(choice);
      
      const pulseDot = document.getElementById('pulseDot');
      const colors = { A: '#15803d', B: '#1d4ed8', C: '#7e22ce', D: '#b45309' };
      pulseDot.style.background = colors[choice] || '#15803d';
      pulseDot.style.boxShadow = `0 0 8px ${pulseDot.style.background}`;
      setTimeout(() => {
        pulseDot.style.background = 'transparent';
        pulseDot.style.boxShadow = 'none';
      }, 3000);
    }

    function triggerPanic() {
      document.getElementById('peekChoice').textContent = '--';
      document.getElementById('peekSummary').textContent = 'Standing by';
      hidePeek();
      if ('vibrate' in navigator) navigator.vibrate(0);
    }

    // Connect to Server-Sent Events
    function connectSSE() {
      const connDot = document.getElementById('connDot');
      const evtSource = new EventSource('/events');

      evtSource.addEventListener('open', () => {
        connDot.style.background = '#15803d'; // Green
      });

      evtSource.addEventListener('solve', (e) => {
        try {
          const data = JSON.parse(e.data);
          if (data && data.choice) {
            lastSolutionId = data.id || (lastSolutionId + 1);
            applySolution(data.choice, data.summary);
          }
        } catch (err) {}
      });

      evtSource.addEventListener('panic', () => {
        triggerPanic();
      });

      evtSource.addEventListener('error', () => {
        connDot.style.background = '#b45309'; // Amber fallback
        evtSource.close();
        setTimeout(connectSSE, 3000);
      });
    }

    // Fallback polling if SSE drops
    setInterval(async () => {
      try {
        const res = await fetch('/latest_solution');
        if (res.ok) {
          const data = await res.json();
          if (data.id && data.id > lastSolutionId && data.choice) {
            lastSolutionId = data.id;
            applySolution(data.choice, data.summary);
          }
        }
      } catch (e) {}
    }, 2500);

    connectSSE();
  </script>
</body>
</html>
"""

# ---------------------------------------------------------------------------
# HTTP API Server
# ---------------------------------------------------------------------------
profile_path = os.path.abspath(os.path.join(os.path.dirname(__file__), "gemini_profile"))
gemini_worker = PlaywrightGeminiWorker(profile_path)

class ThreadedHTTPServer(ThreadingMixIn, HTTPServer):
    daemon_threads = True

class UnifiedCompanionHandler(BaseHTTPRequestHandler):
    def end_headers(self):
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        super().end_headers()

    def send_json(self, status_code, data):
        try:
            body = json.dumps(data).encode('utf-8')
            self.send_response(status_code)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        except (ConnectionResetError, ConnectionAbortedError, BrokenPipeError):
            pass

    def do_OPTIONS(self):
        self.send_response(200)
        self.end_headers()

    def do_GET(self):
        global clipboard_auto_solve_enabled
        parsed = urlparse(self.path)
        params = parse_qs(parsed.query)

        # 1. Health check & status
        if parsed.path == "/status":
            resp_data = {
                "status": "online",
                "engine": "headless_gemini",
                "browser_ready": gemini_worker.is_ready,
                "profile": gemini_worker.profile_dir,
                "platform": sys.platform,
                "local_ip": LOCAL_IP,
                "mobile_url": f"http://{LOCAL_IP}:{PORT}/mobile",
                "clipboard_auto_solve": clipboard_auto_solve_enabled,
                "active_mobile_clients": len(sse_clients)
            }
            self.send_json(200, resp_data)
            return

        # 2. Pitch-Black Mobile App
        if parsed.path == "/mobile":
            body = MOBILE_APP_HTML.encode('utf-8')
            self.send_response(200)
            self.send_header('Content-Type', 'text/html; charset=utf-8')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return

        # 3. Real-Time Server-Sent Events (SSE) Stream
        if parsed.path == "/events":
            self.send_response(200)
            self.send_header('Content-Type', 'text/event-stream')
            self.send_header('Cache-Control', 'no-cache')
            self.send_header('Connection', 'keep-alive')
            self.end_headers()

            q = queue.Queue()
            with sse_lock:
                sse_clients.append(q)
            print(f"[SSE Sync] Mobile phone connected. Active subscribers: {len(sse_clients)}")

            try:
                # Send connection welcome
                init_msg = f"event: connect\ndata: {json.dumps({'status': 'connected', 'id': latest_solution.get('id', 0)})}\n\n"
                self.wfile.write(init_msg.encode('utf-8'))
                self.wfile.flush()

                while True:
                    try:
                        msg = q.get(timeout=20)
                        self.wfile.write(msg.encode('utf-8'))
                        self.wfile.flush()
                    except queue.Empty:
                        # Keep-alive heartbeat ping
                        self.wfile.write(b": ping\n\n")
                        self.wfile.flush()
            except (ConnectionResetError, ConnectionAbortedError, BrokenPipeError):
                pass
            finally:
                with sse_lock:
                    if q in sse_clients:
                        sse_clients.remove(q)
                print(f"[SSE Sync] Mobile subscriber disconnected. Remaining: {len(sse_clients)}")
            return

        # 4. Fallback latest solution poll
        if parsed.path == "/latest_solution":
            self.send_json(200, latest_solution)
            return

        # 5. Direct LED blink trigger
        if parsed.path == "/blink":
            answer = params.get("answer", ["A"])[0].upper()
            key_name = params.get("key", ["caps_lock"])[0].lower()
            blinks = trigger_led_blink(answer, key_name)
            
            resp = {
                "success": True,
                "answer": answer,
                "blinks": blinks,
                "key": key_name
            }
            self.send_json(200, resp)
            return

        # 6. One-time Google login helper
        if parsed.path == "/login":
            login_script = os.path.join(os.path.dirname(__file__), "login_google.py")
            subprocess.Popen([sys.executable, login_script], creationflags=subprocess.CREATE_NEW_CONSOLE)
            resp = {"success": True, "message": "Google Login window launched."}
            self.send_json(200, resp)
            return

        self.send_response(404)
        self.end_headers()

    def do_POST(self):
        global clipboard_auto_solve_enabled
        parsed = urlparse(self.path)
        content_len = int(self.headers.get('Content-Length', 0))
        post_body = self.rfile.read(content_len).decode('utf-8') if content_len > 0 else "{}"

        try:
            data = json.loads(post_body)
        except Exception:
            data = {}

        # 1. Primary MCQ Solver
        if parsed.path == "/solve":
            question = data.get("question", "").strip()
            key_name = data.get("key", "caps_lock").lower()

            if not question:
                self.send_json(400, {"success": False, "error": "No question provided"})
                return

            print(f"\n[Companion] >>> Received Question ({len(question)} chars)")
            try:
                solution = gemini_worker.execute_solve(question)
                choice = solution.get("correctChoice", "A")
                summary = solution.get("summary", "")

                print(f"[Companion] >>> Answer: Choice {choice} | Summary: {summary}")

                # Trigger physical LED blink
                blinks = trigger_led_blink(choice, key_name)

                # Broadcast to connected Mobile Phones
                broadcast_event("solve", {
                    "choice": choice,
                    "summary": summary,
                    "blinks": blinks
                })

                resp = {
                    "success": True,
                    "correctChoice": choice,
                    "summary": summary,
                    "blinks": blinks,
                    "engine": "headless_gemini"
                }
                self.send_json(200, resp)
            except Exception as e:
                print(f"[Companion] Solve error: {e}")
                self.send_json(500, {"success": False, "error": str(e)})
            return

        # 2. Emergency Panic Mode
        if parsed.path == "/panic":
            print("[Companion] !!! EMERGENCY PANIC TRIGGERED !!!")
            broadcast_event("panic", {})
            self.send_json(200, {"success": True, "message": "Panic broadcast sent."})
            return

        # 3. Test Mobile Vibration Broadcast
        if parsed.path == "/broadcast_test":
            choice = data.get("choice", "A").upper()
            blinks = BLINK_COUNTS.get(choice, 1)
            broadcast_event("solve", {
                "choice": choice,
                "summary": f"Signal Test for Choice {choice}",
                "blinks": blinks
            })
            self.send_json(200, {"success": True, "choice": choice, "blinks": blinks})
            return

        # 4. Clipboard Auto-Solve Toggle
        if parsed.path == "/config/clipboard":
            enabled = bool(data.get("enabled", False))
            clipboard_auto_solve_enabled = enabled
            print(f"[Companion] Clipboard Auto-Solve set to: {enabled}")
            self.send_json(200, {"success": True, "clipboard_auto_solve": clipboard_auto_solve_enabled})
            return

        # 5. Reset conversation
        if parsed.path == "/new_chat":
            success = gemini_worker.execute_new_chat()
            self.send_json(200, {"success": success})
            return

        self.send_response(404)
        self.end_headers()

    def log_message(self, format, *args):
        pass

def main():
    print("=" * 65)
    print(" SMART VISION SOLVER - UNIFIED COMPANION SERVER v1.8")
    print("=" * 65)
    print(f" Local Machine: http://127.0.0.1:{PORT}")
    print(f" Mobile Phone:  http://{LOCAL_IP}:{PORT}/mobile")
    print(f" Local Wi-Fi:   {LOCAL_IP}")
    print(" Features: Headless Gemini, Mobile Haptics, Clipboard AI, LED")
    print("=" * 65)

    # Start headless browser worker thread
    gemini_worker.start()

    # Start clipboard intelligence thread
    clip_thread = threading.Thread(target=clipboard_monitor_loop, daemon=True)
    clip_thread.start()

    # Bind to 0.0.0.0 so phone on same Wi-Fi can connect
    server = ThreadedHTTPServer(('0.0.0.0', PORT), UnifiedCompanionHandler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping server...")
        server.server_close()

if __name__ == "__main__":
    main()
