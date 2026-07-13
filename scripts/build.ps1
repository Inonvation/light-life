param(
    [string]$Version,
    [switch]$SkipVersion
)

$versionFile = "$env:USERPROFILE\.devicecontrol-version"

if (-not $Version) {
    if (Test-Path $versionFile) {
        $lastVersion = Get-Content $versionFile -Raw
        $parts = $lastVersion.Trim().Split('.')
        $patch = [int]$parts[2] + 1
        $Version = "$($parts[0]).$($parts[1]).$patch"
    } else {
        $Version = "0.0.1"
    }
}

if (-not $SkipVersion) {
    Write-Host "[Build] Version: $Version"
    Set-Content -Path $versionFile -Value $Version
} else {
    if (Test-Path $versionFile) {
        $Version = Get-Content $versionFile -Raw
    }
    Write-Host "[Build] Version: $Version (no bump)"
}

Write-Host "[Build] Compiling..."
& .gradlew.bat :app:assembleDebug "-PbuildVersionCode=1" "-PbuildVersionName=$Version"

if ($LASTEXITCODE -ne 0) {
    Write-Host "[FAILED] Build error"
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host "[OK] APK: appuildoutputsapkdebugapp-debug.apk"
Write-Host "[OK] Version: $Version"
Read-Host "Press Enter to exit"
