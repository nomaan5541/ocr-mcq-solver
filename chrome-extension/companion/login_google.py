#!/usr/bin/env python3
"""
=============================================================================
Smart Vision Solver - Google Sign-In Helper (OPTIONAL)
=============================================================================
NOTE: Gemini Web already solves MCQs 100% WITHOUT signing in!
Signing in is completely optional if you want personalized history.
=============================================================================
"""

import os
import sys
import time
import subprocess
import urllib.request

profile_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "gemini_profile"))
os.makedirs(profile_dir, exist_ok=True)

print("=" * 65)
print(" SMART VISION SOLVER - GOOGLE SIGN-IN HELPER")
print("=" * 65)
print(" IMPORTANT: Gemini Web ALREADY works and solves MCQs WITHOUT logging in!")
print(" Signing into Google is 100% OPTIONAL.")
print("=" * 65)

# Check if companion server is running and holding the profile lock
is_companion_running = False
try:
    with urllib.request.urlopen("http://127.0.0.1:59124/status", timeout=1) as resp:
        if resp.status == 200:
            is_companion_running = True
except Exception:
    pass

if is_companion_running:
    print("\n[NOTE] The background companion server is currently active.")
    print("To open a visible browser window, we will launch Google Chrome directly.")

# Find official Google Chrome path
chrome_candidates = [
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
    os.path.expandvars(r"%LOCALAPPDATA%\Google\Chrome\Application\chrome.exe")
]

chrome_exe = None
for candidate in chrome_candidates:
    if os.path.isfile(candidate):
        chrome_exe = candidate
        break

if chrome_exe:
    print(f"\nLaunching Official Google Chrome: {chrome_exe}")
    print("This avoids the 'This browser may not be secure' error from Google.")
    print("\nWhen you are done, simply close the Chrome window.")
    print("=" * 65)
    
    # Launch real Google Chrome directly as a normal application
    proc = subprocess.Popen([
        chrome_exe,
        f"--user-data-dir={profile_dir}",
        "--no-first-run",
        "--no-default-browser-check",
        "https://gemini.google.com/app"
    ])
    proc.wait()
    print("\n[SUCCESS] Browser closed. Profile saved!")
else:
    # Fallback to Playwright
    print("\nLaunching Playwright Chromium...")
    from playwright.sync_api import sync_playwright
    with sync_playwright() as p:
        ctx = p.chromium.launch_persistent_context(
            user_data_dir=profile_dir,
            headless=False,
            args=["--disable-blink-features=AutomationControlled"]
        )
        page = ctx.pages[0] if ctx.pages else ctx.new_page()
        page.goto("https://gemini.google.com/app")
        try:
            page.wait_for_close(timeout=300000)
        except Exception:
            pass
        try:
            ctx.close()
        except Exception:
            pass
    print("\n[SUCCESS] Profile saved!")
