# Guided Windows installer. Requires Windows PowerShell 5.1 or PowerShell 7+.
[CmdletBinding()]
param(
    [string]$IP,
    [string]$Apk,
    [string]$Checksum,
    [switch]$DryRun,
    [switch]$NoPrompt,
    [switch]$DisableRedirect
)
$ErrorActionPreference = 'Stop'
$Release = 'v0.1.0'
$Repository = 'ibraking09/streaming-home'
$Package = 'local.firetv.home'
$Service = "$Package/.HomeRedirect"
$FullService = "$Package/local.firetv.home.HomeRedirect"
$Scratch = $null

function Invoke-Adb {
    $Output = & adb @args 2>&1
    if ($LASTEXITCODE -ne 0) { throw "ADB failed. $Output" }
    return ($Output -join "`n").Trim()
}
function Invoke-TV { Invoke-Adb -s $Target @args }
function Read-Services { Invoke-TV shell settings --user 0 get secure enabled_accessibility_services }
function Write-Services([string]$Value) {
    if ($Value -notmatch '^[A-Za-z0-9_./:$-]+$') { throw 'Unexpected accessibility service list.' }
    Invoke-TV shell "settings --user 0 put secure enabled_accessibility_services '$Value'" | Out-Null
}
function Test-Apk {
    if (!(Test-Path -LiteralPath $Apk -PathType Leaf) -or
        !(Test-Path -LiteralPath $Checksum -PathType Leaf)) { throw 'APK or checksum file is missing.' }
    $Name = [IO.Path]::GetFileName($Apk)
    $Pattern = '^([a-fA-F0-9]{64})\s+\*?' + [regex]::Escape($Name) + '$'
    $MatchesForApk = @(Get-Content -LiteralPath $Checksum | Where-Object { $_ -match $Pattern })
    if ($MatchesForApk.Count -ne 1) { throw 'Expected one SHA-256 entry for the APK filename.' }
    $Expected = [regex]::Match($MatchesForApk[0], $Pattern).Groups[1].Value
    if ((Get-FileHash -LiteralPath $Apk -Algorithm SHA256).Hash -ine $Expected) {
        throw 'APK checksum mismatch.'
    }
    Write-Host "Verified SHA-256 for $Name"
}

try {
    if (!(Get-Command adb -ErrorAction SilentlyContinue)) {
        throw 'Install Android SDK Platform-Tools and add adb to PATH.'
    }
    if (!$IP -and !$NoPrompt -and ![Console]::IsInputRedirected) { $IP = Read-Host 'Fire TV IPv4 address' }
    if ($IP -notmatch '^(0|[1-9][0-9]{0,2})(\.(0|[1-9][0-9]{0,2})){3}$' -or
        @($IP.Split('.') | Where-Object { [int]$_ -gt 255 }).Count) {
        throw 'Specify a valid Fire TV IPv4 address with -IP.'
    }
    $Target = "${IP}:5555"
    if ($Apk -and !$Checksum) {
        $Checksum = Join-Path ([IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($Apk))) 'SHA256SUMS'
    }
    if (!$DisableRedirect -and $Apk) { Test-Apk }
    if ($DryRun) {
        if ($DisableRedirect) { Write-Host "Would remove only the Streaming Home service from $Target." }
        else { Write-Host "Would check Fire OS on $Target and verify and install $Release. Redirection is opt-in." }
        exit 0
    }
    if (!$DisableRedirect -and !$Apk) {
        $Scratch = Join-Path ([IO.Path]::GetTempPath()) ("streaming-home-" + [Guid]::NewGuid())
        New-Item -Path $Scratch -ItemType Directory | Out-Null
        $Asset = "streaming-home-$Release.apk"
        $Base = "https://github.com/$Repository/releases/download/$Release"
        $Apk = Join-Path $Scratch $Asset
        Invoke-WebRequest -UseBasicParsing -Uri "$Base/$Asset" -OutFile $Apk
        if (!$Checksum) {
            $Checksum = Join-Path $Scratch 'SHA256SUMS'
            Invoke-WebRequest -UseBasicParsing -Uri "$Base/SHA256SUMS" -OutFile $Checksum
        }
        Test-Apk
    }

    Invoke-Adb connect $Target | Write-Host
    if ((Invoke-TV get-state) -ne 'device') { throw 'Authorize ADB on the Fire TV and retry.' }
    $Maker = Invoke-TV shell getprop ro.product.manufacturer
    $Model = Invoke-TV shell getprop ro.product.model
    $Firmware = Invoke-TV shell getprop ro.build.version.name
    $Sdk = Invoke-TV shell getprop ro.build.version.sdk
    if ($Maker -ne 'Amazon' -or $Sdk -notmatch '^\d+$' -or [int]$Sdk -lt 30) {
        throw 'Expected an Amazon Android-based Fire TV with API 30 or later.'
    }
    Write-Host "Detected $Model, $Firmware (Android API $Sdk)"
    if ($DisableRedirect) {
        $Current = @( (Read-Services).Split(':') | Where-Object { $_ -and $_ -ne 'null' } )
        $Next = @($Current | Where-Object { $_ -notin @($Service, $FullService) })
        if ($Next.Count -ne $Current.Count) {
            if ($Next.Count) { Write-Services ($Next -join ':') }
            else { Invoke-TV shell settings --user 0 delete secure enabled_accessibility_services | Out-Null }
        }
        if (@((Read-Services).Split(':') | Where-Object { $_ -in @($Service, $FullService) }).Count) {
            throw 'The redirect service is still listed.'
        }
        Write-Host 'Streaming Home redirection is disabled.'
        exit 0
    }
    Invoke-TV install -r (Resolve-Path -LiteralPath $Apk).Path | Write-Host
    if ((Invoke-TV shell pm path --user 0 $Package) -notmatch '^package:') {
        throw 'Android did not report the Streaming Home package after install.'
    }
    Write-Host 'Streaming Home installed. Open it from the Amazon Apps library.'
    if ($Model -ne 'AFTMA08C15' -or $Firmware -ne 'Fire OS 8.1.8.5 (RS8185/3898)' -or $Sdk -ne 30) {
        Write-Host 'This firmware is unverified. The installer leaves Home redirection alone.'
        exit 0
    }
    if ($NoPrompt -or [Console]::IsInputRedirected) { exit 0 }
    Write-Host 'Optional redirection observes Amazon launcher window metadata only. Amazon Home can briefly appear.'
    Write-Host 'In an Appstore or Amazon Home session, reopen Streaming Home to resume redirection.'
    if ((Read-Host 'Enable optional Home redirection? [y/N]') -notmatch '^(y|yes)$') { exit 0 }
    $Current = @((Read-Services).Split(':') | Where-Object { $_ -and $_ -ne 'null' })
    if ($Service -in $Current -or $FullService -in $Current) {
        Write-Host 'Home redirection is already configured.'
        exit 0
    }
    if ($Current.Count -and (Invoke-TV shell settings --user 0 get secure accessibility_enabled) -ne '1') {
        throw 'Existing services are off. Use Fire OS Accessibility to enable Streaming Home.'
    }
    Write-Services (($Current + $Service) -join ':')
    if (!@((Read-Services).Split(':') | Where-Object { $_ -in @($Service, $FullService) }).Count) {
        throw 'Fire OS did not retain the service. Use Fire OS Accessibility settings.'
    }
    Write-Host 'Home redirection configured. Run with -DisableRedirect to remove the service entry.'
} catch {
    [Console]::Error.WriteLine("Error: $($_.Exception.Message)")
    exit 1
} finally {
    if ($Scratch -and (Test-Path -LiteralPath $Scratch)) { Remove-Item -LiteralPath $Scratch -Recurse -Force }
}
