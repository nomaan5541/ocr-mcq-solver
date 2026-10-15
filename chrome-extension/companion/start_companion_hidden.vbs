' Silently launches the Headless Gemini Companion with zero window visible
Set WshShell = CreateObject("WScript.Shell")
WshShell.CurrentDirectory = CreateObject("Scripting.FileSystemObject").GetParentFolderName(WScript.ScriptFullName)
WshShell.Run "python headless_gemini_server.py", 0, False
