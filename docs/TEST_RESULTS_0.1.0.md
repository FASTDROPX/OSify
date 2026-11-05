# Test and build report — 2026-11-02 UTC

**30 automated tests passed, 0 failures, 0 skipped.** The final source builds a real signed debug APK and an unsigned, R8-minified release APK. Linux guest execution is **not validated**. QEMU is unavailable in the application.

## Final validation

The final acceptance invocation completed successfully in **4m 58s**, 145 tasks (70 executed, 75 up-to-date):

```bash
./gradlew :app:connectedDebugAndroidTest :core:test :app:testDebugUnitTest \
  :app:lintDebug :app:assembleRelease :app:runtimeInventory --console=plain
```

Debug APK packaging is a dependency of the connected test. A separate `:app:assembleDebug` also completed successfully. Final `lintDebug`: **0 errors, 20 warnings**. The warnings are retained, not suppressed: newer dependency versions, intentionally version-gated Android resource attributes, conservative usable-space checks and KTX suggestions. The compiler also reports a deprecated clipboard convenience API; this does not fail compilation. Release `lintVitalRelease`, R8, packaging and assembly passed.

| Test suite | Environment | Tests | Failures / skipped |
|---|---|---:|---:|
| DomainTest | JDK 21 | 8 | 0 / 0 |
| DownloaderTest | JDK 21 + real local MockWebServer HTTP transport | 12 | 0 / 0 |
| DataLayerTest | Robolectric 4.17, Android API 34 | 3 | 0 / 0 |
| OnboardingUiTest | Robolectric native graphics, API 34 | 3 | 0 / 0 |
| PrimaryFlowUiTest | Robolectric native graphics, API 34 | 2 | 0 / 0 |
| MotionScaleTest | JVM/Compose runtime | 1 | 0 / 0 |
| DeviceSmokeTest | Installed APK, Android 11 / API 30 x86_64 emulator | 1 | 0 / 0 |
| **Total** | | **30** | **0 / 0** |

Tests that were unchanged were correctly reported up-to-date by Gradle; their successful XML reports are included. The actual Android smoke was rerun against the final packaged debug build and passed in **58.795 seconds**. It covers onboarding skip/first launch, memory dashboard presence, catalog, library/import action and settings navigation. It does not start a guest or background download. A prior smoke run also passed in 48.066 seconds.

### What the tests establish

- PRoot recommendations never pretend to reserve VM RAM; architecture mismatch, storage shortage and host memory pressure are rejected. QEMU is never marked supported by this build.
- Preset round-trip, schema version, unknown fields, numeric bounds and executable flag rejection; lifecycle never equates dispatch with a successful boot; terminal download states cannot silently transition to a fresh transfer.
- Actual bundled official YAML for both architectures parses correctly, including Alpine's bare `-` list markers. Invalid filenames, missing/malformed SHA-256, duplicate fields, oversized manifests and untrusted/traversal URLs are rejected.
- Download success, pause/range resume, If-Range/ETag validation, ignored Range, changed ETag, damaged partial data, uncheckpointed crash tail, final hash mismatch, invalid Content-Range, untrusted redirect, weak validators and pause during verification. Completed files are not published before size/hash checks.
- Real Room guards protect completed files from late Pause actions and protect reset external sessions from stale callbacks. ContentResolver import copies real bytes, computes a local hash and uses UUID filenames even when a provider advertises `../../outside.img`.
- All five onboarding pages and skip work with motion disabled; Russian copy renders. Invalid presets cannot save, valid presets persist into the actual Room-backed library, search/details navigation works, and switching language uses actual DataStore preferences.
- App/system motion scales combine correctly, including the system scale of zero. Native Robolectric renders of the actual dashboard/catalog/settings were reviewed. They are test renders, not physical-device RAM measurements.

## Real official-network integration check

Executed separately, successfully:

```bash
./gradlew :core:officialNetworkSmoke
```

This uses the real production HTTPS/manifest/downloader implementation on the **host JVM**, fetching both live official manifests and two actual minirootfs files. It is not a guest boot, Android transfer-service test or performance benchmark.

| Release | Bytes | Verified SHA-256 |
|---|---:|---|
| Alpine Linux 3.24.3 ARM64 | 4,029,999 | `4d3f80afaebd447c5591a63a73bde380a149fe8b2d85488cafabb2ff97551f50` |
| Alpine Linux 3.24.3 x86_64 | 3,704,080 | `f8e1cafba9b1b283b5d30d06f9885c8d2bc161ffe9083ade8e2e157813a53407` |

Each live manifest produced three exposed entries: minirootfs, standard ISO and virt ISO. The first integration attempt uncovered the official YAML list-marker difference, which was fixed and protected by a real-fixture regression test before this successful run. Downloaded guest files live only under ignored build output and are not redistributed in the APK/source archive.

## APK verification

- Android Build Tools 36.0.0 `apksigner verify --verbose --print-certs`: **Verifies**, APK Signature Scheme **v2**, one RSA-2048 Android Debug signer. Debug certificate SHA-256: `8d5fa2cad22b6f00231fc3ed61eada29e3ebf9d4743a36cdad3fc159ae06897f`.
- `zipalign -c -P 16 4 app-debug.apk`: **exit 0**. This checks APK ZIP/native-library container alignment; it is not a test on a physical 16 KiB-page device.
- `assembleRelease`: successful; output explicitly **unsigned**, about 2.2 MiB. No release signing credentials were invented or distributed. Release signing instructions are in README.
- Exact artifact hashes, sizes, source digest and tool versions are in `docs/evidence/BUILD_PROVENANCE.json` and the delivery folder's `SHA256SUMS.txt`.

## Environment and limits

Initially the workspace had no repository, Android SDK, Gradle, javac, emulator or attached Android device. A toolchain was downloaded from official sources: Temurin JDK 21.0.13+9, Gradle 8.13, Android SDK/Build Tools 36.0.0 and emulator 37.2.13.0. `/dev/kvm` is absent. The workspace filesystem is limited to about 8 GiB.

The API 30 default x86_64 system image was booted with software CPU emulation, SwiftShader, two virtual cores and approximately 2 GiB guest RAM. Initial boot took about 283.5 seconds. Sparse test disk allocation was necessary because the standard image installation exhausted disk space. Host/emulator performance is not representative of an ARM64 phone. No physical ARM64 device, Android 14–16 device or KVM guest was available.

Termux 0.118.3 official GitHub x86_64 debug APK was downloaded (SHA-256 `3550e61f4d9eb49b712fd1bd9519dc37085a4d8eb597c57a340f0a64859b7144`) and installed while investigating the optional engine. A reliable bootstrap was not established: ADB intermittently went offline, `am start -W` timed out (16,004 ms), and the Android framework displayed **“Process system isn't responding”**. The screenshot and launch log are retained as evidence. This was a framework ANR during external setup, not a claimed OSify Linux boot. **proot-distro installation, Linux install/launch, interactive external terminal and callbacks were not validated end to end.** No successful guest state was fabricated. The emulator was stopped after testing.

Not established by these checks: physical-device RAM/battery/thermal accuracy, actual OEM dynamic colors, TalkBack/large-font/rotation/keyboard audits, notification denial on API 33+, foreground service duration/quota behavior on API 34–36, real download reboot/LMK/network-loss recovery, multiprocess timing, Android SAF system picker/export provider behavior, and any native or graphical guest. The feature matrix marks these separately.

## Evidence files

- `evidence/junit/`: final successful XML reports for all suites.
- `evidence/acceptance-build.log`: successful final Gradle run.
- `evidence/lint-results-debug.txt`: exact warnings/errors.
- `evidence/apk-signature.txt`: public debug signature verification.
- `evidence/official-network-smoke.txt`: live HTTPS/checksum results.
- `evidence/runtime-dependencies.txt`: resolved runtime coordinates.
- `evidence/screenshots/`: native Robolectric UI renders and the separate Android framework ANR screenshot during Termux setup.
- `evidence/termux-launch.log`: failed external setup evidence, not a success result.

Reports may contain build-machine paths as generated evidence; the source build configuration does not hardcode those paths. Toolchains, emulator disks, signing keys, downloaded guest images and raw reference-source/HTML research files are excluded from the source archive.
