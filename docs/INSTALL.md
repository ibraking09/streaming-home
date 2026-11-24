# Install Streaming Home

Get the signed APK and `SHA256SUMS` from **Assets** on the [latest release page](https://github.com/ibraking09/streaming-home/releases/latest). Review [verified compatibility](COMPATIBILITY.md) before installing. The commands below use the v0.1.0 filename as an example. Use the name of the APK you downloaded.

- [Standalone sideload](#a-standalone-sideload)
- Guided ADB installer for [Linux and macOS](#linux-and-macos) or [Windows PowerShell](#windows-powershell)
- [Manual ADB](#c-manual-adb)
- [Optional Home redirection](#optional-home-redirection)
- [Update, uninstall, and recovery](#update-uninstall-and-recovery)

## A. Standalone sideload

Compare the APK’s SHA-256 with its entry in `SHA256SUMS`. Use `sha256sum streaming-home-v0.1.0.apk` on Linux, `shasum -a 256 streaming-home-v0.1.0.apk` on macOS, or `Get-FileHash -Algorithm SHA256 .\streaming-home-v0.1.0.apk` in PowerShell. Transfer the verified APK to Fire TV using a trusted method, then open it from a file manager or sideloading app. Allow installation from that app if Fire OS prompts. Open **Streaming Home** in Amazon's Apps library. Home redirection is optional.

## B. Guided ADB installer

Install [Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools) and put `adb` on your PATH. [Enable ADB debugging on the Fire TV](#c-manual-adb). Download the installer for your platform and compare its SHA-256 with the matching entry in the release’s `SHA256SUMS`. Run the installer from the download directory.

### Linux and macOS

Download [install.sh](https://github.com/ibraking09/streaming-home/releases/latest/download/install.sh). To verify it, run `sha256sum install.sh` on Linux or `shasum -a 256 install.sh` on macOS and compare the result with `SHA256SUMS`.

```sh
bash ./install.sh
bash ./install.sh --ip FIRE_TV_IP --apk ./streaming-home-v0.1.0.apk --checksum ./SHA256SUMS --dry-run
```

### Windows PowerShell

Download [Install-StreamingHome.ps1](https://github.com/ibraking09/streaming-home/releases/latest/download/Install-StreamingHome.ps1). To verify it, run `Get-FileHash -Algorithm SHA256 .\Install-StreamingHome.ps1` and compare the result with `SHA256SUMS`.

```powershell
.\Install-StreamingHome.ps1
.\Install-StreamingHome.ps1 -IP FIRE_TV_IP -Apk .\streaming-home-v0.1.0.apk -Checksum .\SHA256SUMS -DryRun
```

Omit the dry-run option to install from a local APK. For a noninteractive install, supply the IP argument and `--no-prompt` or `-NoPrompt`. When no local APK is supplied, the installer downloads the APK and checksum for its matching release. On the verified firmware it offers redirection separately, with No as the default. Scripts do not alter system packages.

## C. Manual ADB

1. On the TV, open **Settings → My Fire TV → About**. If Developer Options is hidden, select the device name seven times.
2. Open **Developer Options**, enable **ADB debugging**, and find the address under **About → Network**.
3. Connect from the computer, accept the TV's debugging authorization prompt, and install the verified APK.

```sh
adb connect FIRE_TV_IP:5555
adb -s FIRE_TV_IP:5555 get-state
adb -s FIRE_TV_IP:5555 install -r streaming-home-v0.1.0.apk
```

Open the launcher in Amazon’s Apps library. From ADB you can use `adb -s FIRE_TV_IP:5555 shell am start -n local.firetv.home/.HomeActivity`. [Amazon ADB instructions](https://developer.amazon.com/docs/fire-tv/connecting-adb-to-device)

## Optional Home redirection

On the TV, look for **Streaming Home redirect** under **Settings → Accessibility**, often under other or installed services. If Fire OS hides the switch, use the guided opt-in on the verified firmware. The service observes only Amazon launcher window metadata, with neither key filtering nor window-content retrieval. It cannot distinguish the Appstore's stock Home handoff from a Home press. The launcher therefore opens an Amazon session for the Appstore or the Amazon Home recovery action. Reopen Streaming Home from the Amazon Apps library to resume redirection.

## Update, uninstall, and recovery

Install the next verified release with the installer or `adb install -r` against the selected device. Android requires a compatible signing certificate for an in-place update.

Turn off the service in Fire OS Accessibility, or run the guided installer with `--ip FIRE_TV_IP --disable-redirect` on Bash, or `-IP FIRE_TV_IP -DisableRedirect` on PowerShell. These paths remove only Streaming Home's accessibility entry. Revert to Amazon Home using Back on Streaming Home's root screen or **Launcher settings → Amazon Home**.

Uninstall using Fire OS **Manage Installed Applications**, or use the command below. Uninstalling removes the launcher's local preferences.

```sh
adb -s FIRE_TV_IP:5555 uninstall local.firetv.home
```
