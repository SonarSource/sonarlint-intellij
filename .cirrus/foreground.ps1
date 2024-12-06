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

function Print-AllProcessesWithMainWindowTitle {
    $processes = Get-Process | Select-Object Id, ProcessName, MainWindowTitle

    # Write the output to the console
    $processes | ForEach-Object { Write-Output "Id: $_.Id, ProcessName: $_.ProcessName, MainWindowTitle: $_.MainWindowTitle" }
}

function Bring-WindowToForegroundByTitle {
    param (
        [string]$windowTitle,
        [int]$maxRetries = 15,
        [int]$retryDelay = 1000
    )

	Write-Output "Starting to try bring window to foreground"

    for ($i = 0; $i -lt $maxRetries; $i++) {
        $process = Get-Process | Where-Object { $_.MainWindowTitle -eq $windowTitle } | Select-Object -First 1

        if ($null -ne $process) {
            if ([CustomUser32]::SetForegroundWindow($process.MainWindowHandle)) {
                Write-Output "Brought window with title '$windowTitle' to the foreground."
                return
            } else {
                Start-Sleep -Milliseconds $retryDelay
            }
        } else {
            Write-Output "No window with the title '$windowTitle' found."
            return
        }
    }

    Write-Output "Failed to bring window with title '$windowTitle' to the foreground after $maxRetries attempts."
}

Write-Output "Starting to print process"

# Print all processes with main window titles
Print-AllProcessesWithMainWindowTitle

# Example call to bring the window with title "Welcome to IntelliJ IDEA" to the foreground
Bring-WindowToForegroundByTitle -windowTitle "Welcome to IntelliJ IDEA"
