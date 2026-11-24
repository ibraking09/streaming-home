# Compatibility

Verified on Fire TV Stick 4K Plus, model AFTMA08C15, Fire OS 8.1.8.5 (RS8185/3898), Android 11 (API 30). Other versions or models are untested. Android API 30 or newer is the technical minimum. Vega OS has no Android APK support.

## Home behavior

Fire OS routes Home through its own launcher. The optional service starts Streaming Home after it sees the verified Amazon Home activity's window event. There can be an Amazon Home flash and a delay of roughly one to several seconds. The firmware, load, and source app affect the result.

The Appstore entry uses the same Amazon Home activity for its storefront. Streaming Home suspends redirection for an intentional Appstore or stock Home session. Use the Amazon Apps library to reopen Streaming Home and resume redirection. Pressing Home during that session stays in Amazon Home. A dedicated Fire OS Settings activity is preferred, with a guarded Settings fallback.

If other OEM shortcuts unexpectedly redirect, open Amazon Home from launcher Settings and use the shortcut from that session. Turning optional redirection off is a reliable fallback for standalone use. [Installation and recovery](INSTALL.md)
