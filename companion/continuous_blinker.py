#!/usr/bin/env python3
"""
Continuous Keyboard LED Blinker for Hardware Verification
Blinks Caps Lock (and Num Lock / Scroll Lock) continuously until stopped.
"""
import sys
import time
import signal
import ctypes

VK_CAPITAL = 0x14  # Caps Lock
VK_NUMLOCK = 0x90  # Num Lock
VK_SCROLL  = 0x91  # Scroll Lock

KEYEVENTF_KEYUP = 0x0002

def press_key(vk_code):
    ctypes.windll.user32.keybd_event(vk_code, 0x45, 0, 0)
    time.sleep(0.03)
    ctypes.windll.user32.keybd_event(vk_code, 0x45, KEYEVENTF_KEYUP, 0)

def get_key_state(vk_code):
    return (ctypes.windll.user32.GetKeyState(vk_code) & 1) != 0

initial_caps = get_key_state(VK_CAPITAL)
initial_num  = get_key_state(VK_NUMLOCK)
initial_scroll = get_key_state(VK_SCROLL)

def cleanup(sig=None, frame=None):
    print("\n[Blinker] Stopping... Restoring original LED states.")
    if get_key_state(VK_CAPITAL) != initial_caps:
        press_key(VK_CAPITAL)
    if get_key_state(VK_NUMLOCK) != initial_num:
        press_key(VK_NUMLOCK)
    if get_key_state(VK_SCROLL) != initial_scroll:
        press_key(VK_SCROLL)
    print("[Blinker] Done.")
    sys.exit(0)

signal.signal(signal.SIGINT, cleanup)
signal.signal(signal.SIGTERM, cleanup)

print("=" * 60)
print(" CONTINUOUS KEYBOARD LED BLINKER ACTIVE")
print(" Look at your keyboard's CAPS LOCK (and Num Lock) light!")
print(" Blinking continuously every 0.35 seconds...")
print("=" * 60)

count = 0
try:
    while True:
        count += 1
        # Toggle ON
        press_key(VK_CAPITAL)
        press_key(VK_NUMLOCK)
        time.sleep(0.35)
        
        # Toggle OFF
        press_key(VK_CAPITAL)
        press_key(VK_NUMLOCK)
        time.sleep(0.35)
        
        if count % 5 == 0:
            print(f"[Blinker] Pulse cycle {count} running... Look at your keyboard light!")
except KeyboardInterrupt:
    cleanup()
except Exception as e:
    cleanup()
