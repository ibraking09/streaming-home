# Performance

The figures below were recorded during Streaming Home V2 development on a **Fire TV Stick 4K Plus** (`AFTMA08C15`) running **Fire OS 8.1.8.5** and **Android 11 (API 30)**.

| Measurement | Previously recorded result | Scope |
| --- | --- | --- |
| Proportional memory (PSS) | 21–27 MiB | Memory attributed to the launcher process |
| Direct launcher startup | 101 ms median | Opening the launcher directly |
| Home-button redirection | 1.77–2.59 seconds | Pressing Home with optional redirection enabled |

## Reading the numbers

- **Proportional set size (PSS)** counts the process’s private memory and assigns it a proportion of shared memory pages. It describes the launcher’s memory footprint within the device’s overall RAM use.
- **Direct startup** measures a direct launch of Streaming Home. The available record does not specify whether launches were cold or warm, or how launch completion was defined.
- **Home-button redirection** includes the Fire OS handoff. The optional accessibility service listens for an Amazon Home window event, then opens Streaming Home. Amazon Home may appear briefly. An intentional Appstore or Amazon Home session suspends redirection until Streaming Home is reopened.

## Applicability to v0.1.0

The public [v0.1.0 source](https://github.com/ibraking09/streaming-home/tree/v0.1.0/app) uses a native Java Android activity, framework views and a local app catalog. The Home redirect goes through a separate accessibility-service path. Those design details are consistent with the two distinct timings above. The public history does not identify the exact development build used for the measurements, and the original reports and raw samples are unavailable for review. The figures have not been independently reproduced against the signed APK.

Sample sizes, exact measurement commands, device load and timing boundaries are unavailable. Treat the results as historical observations for one device and firmware. They provide no comparison with other launchers or prediction for other devices.

[Compatibility](COMPATIBILITY.md) · [Release source](https://github.com/ibraking09/streaming-home/tree/v0.1.0) · [Documentation](README.md)
