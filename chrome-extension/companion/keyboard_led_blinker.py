#!/usr/bin/env python3
"""
=============================================================================
Smart Vision Solver - Physical Keyboard LED & Headless Gemini Companion
=============================================================================
This file executes the full Unified Headless Gemini Companion.
"""
import sys
import os

# Delegate directly to headless_gemini_server
import headless_gemini_server

if __name__ == "__main__":
    headless_gemini_server.main()
