# Contributing

Build and test locally before opening a pull request.

```sh
scripts/test.sh
tests/installers.sh
gradle --no-daemon :app:assembleDebug :app:lintDebug
```

The debug package is `local.firetv.home.dev` and can coexist with the release. Use an authorized test device. For firmware-specific changes, include the model, Fire OS version, Android API level, steps to reproduce, observed foreground activity, and whether optional redirection was enabled. Review screenshots and logs for private data before sharing them. Keep changes focused and add regression coverage for behavior changes.

By contributing, you agree to license your original contribution under the MIT license.
