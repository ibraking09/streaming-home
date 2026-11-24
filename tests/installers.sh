#!/usr/bin/env bash
set -euo pipefail
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
scratch=$(mktemp -d)
trap 'rm -rf -- "$scratch"' EXIT
mkdir "$scratch/bin"
export TEST_ADB_CALLS="$scratch/adb.calls"
export PATH="$scratch/bin:$PATH"
cat > "$scratch/bin/adb" <<'ADB'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$TEST_ADB_CALLS"
case "$*" in
    connect*) echo connected ;;
    *get-state) echo "${TEST_ADB_STATE:-device}" ;;
    *ro.product.manufacturer) echo Amazon ;;
    *ro.product.model) echo AFTMA08C15 ;;
    *ro.build.version.name) echo 'Fire OS 8.1.8.5 (RS8185/3898)' ;;
    *ro.build.version.sdk) echo "${TEST_ADB_API:-30}" ;;
    *'install -r'*) echo Success ;;
    *'pm path --user 0 local.firetv.home') echo 'package:/device/app/base.apk' ;;
    *) exit 9 ;;
esac
ADB
chmod +x "$scratch/bin/adb"
apk="$scratch/release.apk"
printf 'checksum fixture\n' > "$apk"
(cd "$scratch" && if command -v sha256sum >/dev/null; then sha256sum release.apk; else shasum -a 256 release.apk; fi) > "$scratch/SHA256SUMS"
printf '%064d  release.apk\n' 0 > "$scratch/bad-sums"
ip=203.0.113.25
: > "$TEST_ADB_CALLS"

must_fail() {
    if "$@" > "$scratch/failure" 2>&1; then
        printf 'Expected failure from %s\n' "$1" >&2
        exit 1
    fi
}
bash_installer=("$root/scripts/install.sh" --ip "$ip" --apk "$apk" --no-prompt)
"${bash_installer[@]}" --dry-run
[[ ! -s $TEST_ADB_CALLS ]]
must_fail "${bash_installer[@]}" --checksum "$scratch/bad-sums"
grep -q 'checksum mismatch' "$scratch/failure"
[[ ! -s $TEST_ADB_CALLS ]]
must_fail "$root/scripts/install.sh" --ip 203.0.113.256 --dry-run
must_fail env TEST_ADB_STATE=offline "${bash_installer[@]}"
must_fail env TEST_ADB_API=28 "${bash_installer[@]}"
"${bash_installer[@]}"
grep -q 'install -r' "$TEST_ADB_CALLS"
! grep -q 'settings.*put\|disable-user\|uninstall' "$TEST_ADB_CALLS"

if command -v pwsh >/dev/null; then
    : > "$TEST_ADB_CALLS"
    powershell=(pwsh -NoProfile -File "$root/scripts/Install-StreamingHome.ps1" -IP "$ip" -Apk "$apk" -NoPrompt)
    "${powershell[@]}" -DryRun
    [[ ! -s $TEST_ADB_CALLS ]]
    must_fail "${powershell[@]}" -Checksum "$scratch/bad-sums"
    grep -q 'checksum mismatch' "$scratch/failure"
    [[ ! -s $TEST_ADB_CALLS ]]
    must_fail env TEST_ADB_STATE=offline "${powershell[@]}"
    must_fail env TEST_ADB_API=28 "${powershell[@]}"
    "${powershell[@]}"
    grep -q 'install -r' "$TEST_ADB_CALLS"
    ! grep -q 'settings.*put\|disable-user\|uninstall' "$TEST_ADB_CALLS"
else
    echo 'PowerShell test skipped because pwsh is unavailable.'
fi
echo 'Installer checks passed'
