#!/usr/bin/env bash
# Guided Streaming Home installer for Linux and macOS.
set -euo pipefail
release='v0.1.0'
repository='ibraking09/streaming-home'
asset="streaming-home-$release.apk"
package=local.firetv.home
service=$package/.HomeRedirect
ip='' apk='' checksum='' dry=false prompt=true disable=false

fail() { printf 'Error: %s\n' "$*" >&2; exit 1; }
usage() {
    printf '%s\n' 'Usage: install.sh [--ip ADDRESS] [--apk PATH] [--checksum PATH]' \
        '                  [--dry-run] [--no-prompt] [--disable-redirect]'
}
while [[ $# -gt 0 ]]; do
    case "$1" in
        --ip|--apk|--checksum)
            [[ $# -ge 2 && $2 != --* ]] || fail "Missing value for $1."
            case "$1" in --ip) ip=$2 ;; --apk) apk=$2 ;; --checksum) checksum=$2 ;; esac
            shift 2 ;;
        --dry-run) dry=true; shift ;;
        --no-prompt) prompt=false; shift ;;
        --disable-redirect) disable=true; shift ;;
        -h|--help) usage; exit 0 ;;
        *) fail "Unknown option $1." ;;
    esac
done
command -v adb >/dev/null || fail 'Install Android SDK Platform-Tools and add adb to PATH.'
if [[ -z $ip ]] && $prompt && [[ -t 0 ]]; then
    IFS= read -r -p 'Fire TV IPv4 address: ' ip
fi
[[ $ip =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]] || fail 'Specify a Fire TV IPv4 address with --ip.'
IFS=. read -r a b c d <<< "$ip"
for octet in "$a" "$b" "$c" "$d"; do
    [[ $octet == 0 || $octet != 0* ]] && (( 10#$octet <= 255 )) || fail 'Invalid IPv4 address.'
done
target=$ip:5555
tv() { adb -s "$target" "$@"; }
value() { tv shell "$@" | tr -d '\r'; }

verify() {
    [[ -f $apk && -f $checksum ]] || fail 'APK or checksum file is missing.'
    name=$(basename "$apk")
    expected=$(awk -v name="$name" '$2 == name {print tolower($1)}' "$checksum")
    [[ $expected =~ ^[a-f0-9]{64}$ ]] || fail 'Expected one SHA-256 entry for the APK filename.'
    if command -v sha256sum >/dev/null; then
        actual=$(sha256sum < "$apk" | awk '{print $1}')
    elif command -v shasum >/dev/null; then
        actual=$(shasum -a 256 < "$apk" | awk '{print $1}')
    else
        fail 'Install sha256sum or shasum to verify the APK.'
    fi
    [[ $actual == "$expected" ]] || fail 'APK checksum mismatch.'
    printf 'Verified SHA-256 for %s\n' "$name"
}
if [[ -n $apk && -z $checksum ]]; then checksum="$(dirname "$apk")/SHA256SUMS"; fi
if ! $disable && [[ -n $apk ]]; then verify; fi
if $dry; then
    if $disable; then
        printf 'Would connect to %s and remove only the Streaming Home accessibility entry.\n' "$target"
    else
        printf 'Would connect to %s, check Fire OS, verify the APK and install %s.\n' "$target" "$release"
        if [[ -z $apk ]]; then printf 'APK source: https://github.com/%s/releases/tag/%s\n' "$repository" "$release"; fi
        printf 'Home redirection requires a separate opt-in.\n'
    fi
    exit 0
fi

if ! $disable && [[ -z $apk ]]; then
    command -v curl >/dev/null || fail 'Install curl or supply a local APK and checksum.'
    tmp=$(mktemp -d)
    trap 'rm -rf -- "$tmp"' EXIT
    base="https://github.com/$repository/releases/download/$release"
    apk="$tmp/$asset"
    supplied=$checksum
    checksum="$tmp/SHA256SUMS"
    curl --proto '=https' --proto-redir '=https' -fsSL --output "$apk" "$base/$asset" ||
        fail "Could not download $asset. Check the release page."
    if [[ -n $supplied ]]; then checksum=$supplied
    else curl --proto '=https' --proto-redir '=https' -fsSL --output "$checksum" "$base/SHA256SUMS" ||
        fail 'Could not download SHA256SUMS.'
    fi
    verify
fi

adb connect "$target" || fail "ADB could not connect to $target."
[[ $(tv get-state 2>/dev/null || true) == device ]] ||
    fail 'Authorize ADB on the Fire TV and check that it is on the same network.'
maker=$(value getprop ro.product.manufacturer)
model=$(value getprop ro.product.model)
firmware=$(value getprop ro.build.version.name)
sdk=$(value getprop ro.build.version.sdk)
[[ $maker == Amazon && $sdk =~ ^[0-9]+$ ]] || fail 'Expected an Amazon Android-based Fire TV.'
(( sdk >= 30 )) || fail "Android API $sdk is below the minimum API 30."
printf 'Detected %s, %s (Android API %s)\n' "$model" "$firmware" "$sdk"

get_services() { value settings --user 0 get secure enabled_accessibility_services; }
set_services() {
    # Android settings stores a colon-separated list of component names.
    [[ $1 =~ ^[A-Za-z0-9_./:\$-]+$ ]] || fail 'Unexpected accessibility service list.'
    tv shell "settings --user 0 put secure enabled_accessibility_services '$1'"
}
if $disable; then
    current=$(get_services)
    next=''
    IFS=: read -r -a entries <<< "$current"
    for item in "${entries[@]}"; do
        case "$item" in ''|null|"$service"|"$package/local.firetv.home.HomeRedirect") ;; *)
            next="${next:+$next:}$item" ;;
        esac
    done
    if [[ $current == null || $current == "$next" ]]; then
        printf 'Streaming Home redirection is already disabled.\n'
    elif [[ -z $next ]]; then
        tv shell settings --user 0 delete secure enabled_accessibility_services
    else
        set_services "$next"
    fi
    case ":$(get_services):" in *":$service:"*|*":$package/local.firetv.home.HomeRedirect:"*)
        fail 'The redirect service is still listed.' ;;
    esac
    exit 0
fi

tv install -r "$apk" || fail 'Install failed. Updates require the same signing certificate.'
value pm path --user 0 "$package" | awk '/^package:/ { installed=1 } END { exit !installed }' ||
    fail 'Android did not report the Streaming Home package after install.'
printf 'Streaming Home installed. Open it from Amazon’s Apps library.\n'
if [[ $model != AFTMA08C15 || $firmware != 'Fire OS 8.1.8.5 (RS8185/3898)' || $sdk != 30 ]]; then
    printf 'This firmware is unverified. The installer leaves Home redirection alone.\n'
    exit 0
fi
if ! $prompt || [[ ! -t 0 ]]; then exit 0; fi
printf '%s\n' 'Optional Home redirection observes Amazon launcher window metadata only.' \
    'It does not read screen contents or intercept keys. Amazon Home can briefly appear.' \
    'In Appstore and Amazon Home sessions, reopen Streaming Home to resume redirection.'
IFS= read -r -p 'Enable optional Home redirection? [y/N] ' answer || exit 0
case "$answer" in y|Y|yes|YES) ;; *) exit 0 ;; esac
current=$(get_services)
case ":$current:" in *":$service:"*|*":$package/local.firetv.home.HomeRedirect:"*)
    printf 'Home redirection is already configured.\n'; exit 0 ;;
esac
if [[ $current == null ]]; then current=''; fi
enabled=$(value settings --user 0 get secure accessibility_enabled)
if [[ -n $current && $enabled != 1 ]]; then
    fail 'Existing accessibility services are off. Use Fire OS Accessibility to enable Streaming Home.'
fi
set_services "${current:+$current:}$service"
case ":$(get_services):" in *":$service:"*|*":$package/local.firetv.home.HomeRedirect:"*) ;;
    *) fail 'Fire OS did not retain the service. Use Fire OS Accessibility settings.' ;;
esac
printf 'Home redirection is configured. Run with --disable-redirect to remove the service entry.\n'
