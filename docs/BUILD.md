# Build from source

Use JDK 17, Gradle 8.11.1, Android SDK platform 30, Build Tools 35.0.0, and Android SDK Platform-Tools for on-device testing. The Android Gradle Plugin is pinned to 8.9.2. Set `ANDROID_HOME` to your SDK directory or configure an untracked `local.properties`.

```sh
sdkmanager 'platforms;android-30' 'build-tools;35.0.0' platform-tools
scripts/test.sh
tests/installers.sh
gradle --no-daemon :app:lintDebug :app:assembleDebug :app:assembleRelease
```

Debug output is `app/build/outputs/apk/debug/app-debug.apk`. Its debug package suffix allows it to coexist with a release. Unsigned release output is `app/build/outputs/apk/release/app-release-unsigned.apk`. Use the official release's signer for an in-place update.

Keep signing keys outside the repository. For your own distribution, create and back up a release keystore, then use `zipalign` and `apksigner` from the installed build tools. The signer prompts for credentials.

```sh
zipalign -f 4 app/build/outputs/apk/release/app-release-unsigned.apk app-aligned.apk
apksigner sign --ks /path/to/private-release-keystore --out streaming-home-v0.1.0.apk app-aligned.apk
apksigner verify --verbose --print-certs streaming-home-v0.1.0.apk
sha256sum streaming-home-v0.1.0.apk > SHA256SUMS
```

On macOS, `shasum -a 256` can generate the release checksum. The public CI workflow builds and tests sources without the release key. The official release is signed locally. [Android app signing](https://developer.android.com/studio/publish/app-signing)
