@echo off
title Smart Vision - Headless Gemini Companion
cd /d "%~dp0"
echo ============================================================
echo  Starting Headless Gemini Web Companion on Port 59124
echo ============================================================
python headless_gemini_server.py
pause
