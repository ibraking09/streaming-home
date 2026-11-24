#!/usr/bin/env bash
set -euo pipefail
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
scratch=$(mktemp -d)
trap 'rm -rf -- "$scratch"' EXIT
javac -d "$scratch" "$root/app/src/main/java/local/firetv/home/HomeRedirectPolicy.java" \
    "$root/tests/local/firetv/home/HomeRedirectPolicyTest.java"
java -cp "$scratch" local.firetv.home.HomeRedirectPolicyTest
bash -n "$root/scripts/install.sh"
