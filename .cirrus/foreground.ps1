Add-Type @"
using System;
using System.Runtime.InteropServices;
public class CustomUser32 {
    [DllImport("user32.dll")]
    public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")]
    public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll", SetLastError = true)]
    public static extern int GetWindowText(IntPtr hWnd, System.Text.StringBuilder lpString, int nMaxCount);
    public static string GetActiveWindowTitle() {
        IntPtr handle = GetForegroundWindow();
        System.Text.StringBuilder sb = new System.Text.StringBuilder(256);
        if (GetWindowText(handle, sb, sb.Capacity) > 0) {
            return sb.ToString();
        }
        return null;
    }
}
"@

function Bring-WindowToForeground {
    param (
        [string]$processName,
        [int]$maxRetries = 5,
        [int]$retryDelay = 1000
    )

    $process = Get-Process -Name $processName -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle } | Select-Object -First 1

    if ($null -eq $process) {
        Write-Output "No process with the name '$processName' found."
        return
    }

    for ($i = 0; $i -lt $maxRetries; $i++) {
        if ([CustomUser32]::SetForegroundWindow($process.MainWindowHandle)) {
            Write-Output "Brought process '$processName' to the foreground."
            return
        } else {
            Start-Sleep -Milliseconds $retryDelay
        }
    }

    Write-Output "Failed to bring process '$processName' to the foreground after $maxRetries attempts."
}

# Bring the "java" process to the foreground
Bring-WindowToForeground -processName "java"
