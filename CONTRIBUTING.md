# Contributing

You can help by documenting a Fire TV configuration, reproducing a bug, or improving an install guide. Check [open issues](https://github.com/ibraking09/streaming-home/issues) for existing reports.

- **Tried another Fire TV?** [Share a device result](https://github.com/ibraking09/streaming-home/issues/new?template=device_compatibility.yml), including successful installs. Report standalone use separately from optional Home redirection.
- **Found a problem?** [File a bug](https://github.com/ibraking09/streaming-home/issues/new?template=bug_report.yml) with the steps, expected and observed behavior, and firmware. Review screenshots and logs for account details, private addresses, and device serial numbers before sharing.
- **Opening a pull request?** Keep the change focused and describe how you checked it. For behavior changes, add regression coverage where possible.

For code changes, follow the [build guide](docs/BUILD.md) and run these checks locally.

```sh
scripts/test.sh
tests/installers.sh
gradle --no-daemon :app:assembleDebug :app:lintDebug
```

The debug package (`local.firetv.home.dev`) can coexist with the release. Use an authorized test device. For firmware-specific changes, include the model, Fire OS build, Android API, redirect state, and the relevant foreground activity if known.

By contributing, you agree to license your original contribution under the [MIT license](LICENSE).
