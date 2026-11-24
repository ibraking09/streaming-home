# Compatibility

| Device | Model | Firmware | Android |
| --- | --- | --- | --- |
| Fire TV Stick 4K Plus | `AFTMA08C15` | Fire OS 8.1.8.5 (`RS8185/3898`) | Android 11 (API 30) |

This is the verified configuration for v0.1.0. Other models and firmware versions are untested. Standalone installation requires Android API 30 or newer. Vega OS is incompatible with the Android APK. [Amazon’s Vega OS packaging documentation](https://developer.amazon.com/docs/cloud-app-program/amazon-cap-migrate-vega-os)

## Home behavior

Fire OS routes Home through its own launcher. The optional service starts Streaming Home after it sees the verified Amazon Home activity's window event. There can be an Amazon Home flash and a delay of roughly one to several seconds. The firmware, load, and source app affect the result.

The Appstore entry uses the same Amazon Home activity for its storefront. Streaming Home suspends redirection for an intentional Appstore or stock Home session. Use the Amazon Apps library to reopen Streaming Home and resume redirection. Pressing Home during that session stays in Amazon Home. A dedicated Fire OS Settings activity is preferred, with a guarded Settings fallback.

If other OEM shortcuts unexpectedly redirect, open Amazon Home from launcher Settings and use the shortcut from that session. Turning optional redirection off is a reliable fallback for standalone use. [Installation and recovery](INSTALL.md)

## Report a result

Share successful and unsuccessful installs through the [device compatibility form](https://github.com/ibraking09/streaming-home/issues/new?template=device_compatibility.yml). Include the exact model code, Fire OS build, Android API if known, and Streaming Home version. Describe standalone launch and any optional Home-button tests separately. The model code identifies a product variant. Leave out serial numbers, private network addresses, and account details.

[Installation](INSTALL.md) · [Performance](PERFORMANCE.md) · [Documentation](README.md)
