# Official-source research — 2026-11-02

Research was performed before choosing the implementation and repeated while validating background behavior. Live metadata and documentation were fetched, rather than assuming versions from memory. This document links the sources; downloaded reference source code and HTML are not part of the distributed source archive or APK.

## Android and design

| Source | Finding / decision |
|---|---|
| [Material Design 3](https://m3.material.io/) | Tonal surfaces, dynamic color, clear typography, large controls; original orbit/layer vector branding |
| [Jetpack Compose](https://developer.android.com/develop/ui/compose) | Declarative UI, state hoisting, ViewModel/StateFlow; no WebView/mockup substitute |
| [Compose animation](https://developer.android.com/develop/ui/compose/animation/introduction) | Animated content/progress, no forced long transitions; disable infinite motif when reduced/off |
| [MotionDurationScale](https://developer.android.com/reference/kotlin/androidx/compose/ui/MotionDurationScale) | Forward app/system motion scale to window recomposer so Material animations follow it too |
| [Adaptive icons](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive) | Separate foreground/background and true white monochrome vector layer; splash uses same motif |
| [Background work](https://developer.android.com/develop/background-work) | Persistent work is separate from the activity; no attempt to evade Android lifecycle restrictions |
| [Foreground services](https://developer.android.com/develop/background-work/services/fgs) | Visible user-started transfers, service declaration, notification and stop/pause semantics |
| [Service types](https://developer.android.com/develop/background-work/services/fgs/service-types) | `dataSync` + FOREGROUND_SERVICE_DATA_SYNC on API 34+ for actual network file transfers |
| [FGS timeouts](https://developer.android.com/develop/background-work/services/fgs/timeout) | Android 15 dataSync time budgets cannot be treated as unlimited VM/download hosting |
| [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) and [long-running workers](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running) | Foreground CoroutineWorker chosen for range/hash control; it remains subject to scheduling/quotas. DownloadManager would provide durability but not the required custom checkpoints/manual pause and response validation |
| [Android 16 behavior changes](https://developer.android.com/about/versions/16/behavior-changes-all) | Job runtime quotas affect some long-running WorkManager work; UIDT integration is deferred, not simulated |
| [User-initiated transfers](https://developer.android.com/develop/background-work/background-tasks/uidt) | Appropriate future path for very large transfers initiated by the user; not implemented in this milestone |
| [Android 10 execution changes](https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission) | Cannot invoke execve on downloaded native executables in app home for target 29+; do not lower target to bypass it |
| [Play device/network abuse policy](https://support.google.com/googleplay/android-developer/answer/9888379) | Downloaded executable code/distribution requires a separate policy assessment; no Play approval claimed |
| [Storage Access Framework](https://developer.android.com/guide/topics/providers/document-provider) | Explicit document selection, no broad storage permission; bytes copied to private storage |
| [ActivityManager.MemoryInfo](https://developer.android.com/reference/android/app/ActivityManager.MemoryInfo) | `availMem`, `totalMem`, `lowMemory`; do not label total-minus-available as per-app memory or pretend to kill unrelated apps |

## Version evidence and pinning

Live official metadata checked: [AGP](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml), [Compose BOM](https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/maven-metadata.xml), [Room](https://dl.google.com/dl/android/maven2/androidx/room/room-runtime/maven-metadata.xml), [WorkManager](https://dl.google.com/dl/android/maven2/androidx/work/work-runtime-ktx/maven-metadata.xml), [DataStore](https://dl.google.com/dl/android/maven2/androidx/datastore/datastore-preferences/maven-metadata.xml), [Activity](https://dl.google.com/dl/android/maven2/androidx/activity/activity-compose/maven-metadata.xml), [Lifecycle](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-viewmodel-compose/maven-metadata.xml), [Kotlin](https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin/maven-metadata.xml), [KSP](https://repo.maven.apache.org/maven2/com/google/devtools/ksp/symbol-processing-gradle-plugin/maven-metadata.xml), [Gradle current](https://services.gradle.org/versions/current), [SDK repository](https://dl.google.com/android/repository/repository2-1.xml), [Robolectric](https://repo.maven.apache.org/maven2/org/robolectric/robolectric/maven-metadata.xml).

| Component | Latest stable observed | Selected |
|---|---|---|
| AGP | 9.5.0; docs require Gradle 9.6+ | 8.13.2 |
| Gradle | 9.8.2 | 8.13, checksum-pinned Wrapper |
| Kotlin | 2.4.21 | 2.2.21 |
| KSP | 2.3.13 | 2.2.21-2.0.4, matching Kotlin series |
| Compose BOM | 2026.09.00 | 2025.11.01 |
| Room | 2.8.5 | 2.8.4 |
| WorkManager | 2.12.0 | 2.11.0 |
| DataStore | 1.3.0 | 1.2.0 |
| Activity Compose | 1.13.0 | 1.12.0 |
| Lifecycle | 2.11.0 | 2.10.0 |
| Robolectric | 4.17 | 4.17 |

The mature AGP 8/Kotlin 2.2 combination avoids an unrelated AGP 9 built-in Kotlin/DSL migration while building this application from an empty workspace. The selected versions were present in the metadata and actually resolved/built. This is a reproducible pinning choice, not a claim of using all latest versions or an independently audited security baseline. SDK compile/target 36, min 26; JDK 21. Build Tools explicitly 36.0.0. No native toolchain or guest binary is bundled.

## Engines and source inspection

- [QEMU documentation](https://www.qemu.org/docs/master/), [source/license](https://github.com/qemu/qemu/blob/master/COPYING): system emulation, machine/firmware/input/display plumbing and ABI-correct native packaging are substantial. GPL-2.0 and per-component/firmware obligations must be met before redistribution. x86-on-ARM TCG can be extremely slow. Hardware-assisted execution requires actual device/OS access, never inferred from root or Shizuku.
- [Termux](https://github.com/termux/termux-app), [license](https://github.com/termux/termux-app/blob/master/LICENSE.md), [RUN_COMMAND protocol](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent): GPL-3.0-only with documented component exceptions. Inspected RunCommandService and protocol constants to implement intents from documentation; no library/source copied into the APK. Requires an explicit optional permission and `allow-external-apps`; callback transcript may contain private data. Termux's own background/activity restrictions still apply.
- [proot-distro](https://github.com/termux/proot-distro), [README](https://github.com/termux/proot-distro/blob/master/README.md), [license](https://github.com/termux/proot-distro/blob/master/LICENSE): GPL-3.0; rootless Linux userlands sharing the host kernel. Current upstream describes OCI images; installed Termux package versions can differ. The adapter uses the fixed `alpine` alias and reports real errors/results rather than guessing version-specific success. PRoot is not a VM, does not provide its own kernel and is not a sandbox for hostile code.
- [Limbo](https://github.com/limboemu/limbo), [license](https://github.com/limboemu/limbo/blob/master/COPYING): GPL-2.0 reference for Android/QEMU integration, not a drop-in compatible engine for a new target-36 app. No code or binaries reused.
- [UTM](https://github.com/utmapp/UTM), [license](https://github.com/utmapp/UTM/blob/main/LICENSE): Apache-2.0 application shell, separate QEMU/firmware licenses; Apple-platform UI/virtualization paths are not Android functionality. No code reused.

Distribution choice: source plus sideloadable OSify debug APK, optional separately installed Termux. This does not establish app-store compliance of a future native VM product. Future native binaries must be built for Android/ABI/page size, embedded in the signed APK using an appropriate supported loading/execution path, accompanied by corresponding sources/notices where required, and tested on real ARM64 hardware.

## Catalog provenance

Official [Alpine downloads](https://alpinelinux.org/downloads/), [ARM64 release list](https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases/aarch64/latest-releases.yaml), [x86_64 release list](https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases/x86_64/latest-releases.yaml). Snapshot fetched on 2026-11-02: Alpine 3.24.3. URLs are derived from a strictly checked release filename and versioned official path, avoiding a moving latest-image URL for a queued hash. Only minirootfs/standard/virt are exposed. Official checksums are supplied, verified after transport, and never described as an independent signed trust chain. Licenses vary by package; installed size is only an estimate. [Microsoft official installation media](https://www.microsoft.com/software-download/) are linked, never mirrored or stripped.
