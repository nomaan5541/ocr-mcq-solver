# =============================================================================
# Smart Vision Solver - Windows PowerShell Keyboard LED Companion
# =============================================================================
# Listens on http://localhost:59124 and toggles keyboard LEDs (ScrollLock, CapsLock, NumLock)
# Run with: powershell -ExecutionPolicy Bypass -File companion\keyboard_led_blinker.ps1
# =============================================================================

Add-Type -AssemblyName System.Web

# Define Win32 Keyboard Controller
$Win32Keyboard = Add-Type -MemberDefinition @"
[DllImport("user32.dll")]
public static extern void keybd_event(byte bVk, byte bScan, uint dwFlags, int dwExtraInfo);

[DllImport("user32.dll")]
public static extern short GetKeyState(int nVirtKey);
"@ -Name "Win32KeyController" -Namespace "KeyboardHardware" -PassThru

$VK_CAPITAL = 0x14
$VK_NUMLOCK = 0x90
$VK_SCROLL  = 0x91
$KEYEVENTF_KEYUP = 0x0002

function Toggle-Key([byte]$vkCode) {
    [KeyboardHardware.Win32KeyController]::keybd_event($vkCode, 0x45, 0, 0)
    Start-Sleep -Milliseconds 25
    [KeyboardHardware.Win32KeyController]::keybd_event($vkCode, 0x45, $KEYEVENTF_KEYUP, 0)
}

function Get-Key-State([int]$vkCode) {
    return (([KeyboardHardware.Win32KeyController]::GetKeyState($vkCode) -band 1) -ne 0)
}

function Blink-LED([string]$choice, [string]$keyName) {
    $vkCode = $VK_SCROLL
    if ($keyName -eq "caps_lock") { $vkCode = $VK_CAPITAL }
    elseif ($keyName -eq "num_lock") { $vkCode = $VK_NUMLOCK }

    $counts = @{ "A" = 1; "B" = 2; "C" = 3; "D" = 4; "1" = 1; "2" = 2; "3" = 3; "4" = 4 }
    $blinkCount = if ($counts.ContainsKey($choice)) { $counts[$choice] } else { 1 }

    Write-Host "[LED Signal] Blinking $keyName $blinkCount time(s) for Choice $choice..." -ForegroundColor Cyan

    $initialState = Get-Key-State($vkCode)

    for ($i = 0; $i -lt $blinkCount; $i++) {
        Toggle-Key($vkCode)
        Start-Sleep -Milliseconds 180
        Toggle-Key($vkCode)
        Start-Sleep -Milliseconds 180
    }

    $finalState = Get-Key-State($vkCode)
    if ($finalState -ne $initialState) {
        Toggle-Key($vkCode)
    }
    Write-Host "[LED Signal] Completed. Initial key state restored." -ForegroundColor Green
}

$port = 59124
$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add("http://127.0.0.1:$port/")

try {
    $listener.Start()
} catch {
    Write-Host "Port $port already in use or permission denied." -ForegroundColor Red
    exit
}

Write-Host "============================================================" -ForegroundColor Yellow
Write-Host " SMART VISION SOLVER - POWERSHELL KEYBOARD LED COMPANION" -ForegroundColor Yellow
Write-Host "============================================================" -ForegroundColor Yellow
Write-Host " Listening on: http://127.0.0.1:$port/" -ForegroundColor White
Write-Host " Signals: A=1x, B=2x, C=3x, D=4x" -ForegroundColor Gray
Write-Host " Press Ctrl+C in this window to stop.`n" -ForegroundColor Gray

while ($listener.IsListening) {
    try {
        $context = $listener.GetContext()
        $request = $context.Request
        $response = $context.Response

        # CORS Headers
        $response.AddHeader("Access-Control-Allow-Origin", "*")
        $response.AddHeader("Access-Control-Allow-Methods", "GET, OPTIONS")
        $response.AddHeader("Access-Control-Allow-Headers", "Content-Type")

        if ($request.HttpMethod -eq "OPTIONS") {
            $response.StatusCode = 200
            $response.Close()
            continue
        }

        $path = $request.Url.AbsolutePath

        if ($path -eq "/status") {
            $json = '{"status":"online","platform":"windows_powershell"}'
            $buffer = [System.Text.Encoding]::UTF8.GetBytes($json)
            $response.ContentType = "application/json"
            $response.ContentLength64 = $buffer.Length
            $response.OutputStream.Write($buffer, 0, $buffer.Length)
            $response.Close()
        }
        elseif ($path -eq "/blink") {
            $query = [System.Web.HttpUtility]::ParseQueryString($request.Url.Query)
            $answer = if ($query["answer"]) { $query["answer"].ToUpper() } else { "A" }
            $key = if ($query["key"]) { $query["key"].ToLower() } else { "caps_lock" }

            $json = "{`"success`":true,`"answer`":`"$answer`",`"key`":`"$key`"}"
            $buffer = [System.Text.Encoding]::UTF8.GetBytes($json)
            $response.ContentType = "application/json"
            $response.ContentLength64 = $buffer.Length
            $response.OutputStream.Write($buffer, 0, $buffer.Length)
            $response.Close()

            try {
                Blink-LED $answer $key
            } catch {
                Write-Host "[LED Error] $_" -ForegroundColor Red
            }
        }
        else {
            $response.StatusCode = 404
            $response.Close()
        }
    } catch {
        Write-Host "[Listener Error] $_" -ForegroundColor DarkGray
    }
}
