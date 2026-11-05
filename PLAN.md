# OSify milestone plan

1. Inspect toolchain, official Android, engine and licensing documentation. [done]
2. Bootstrap reproducible Kotlin / Compose application and JVM domain module. [done]
3. Implement official Alpine catalog, persistent download state, strict manifests, SHA-256 verification, Range resume, SAF import, versioned presets and conservative capability recommendations. [done for milestone]
4. Implement onboarding, memory dashboard, library, catalog, settings, adaptive branding and a consent-driven experimental Termux/PRoot adapter. Keep unverified engines visibly unverified; QEMU unavailable. [done for milestone; Linux runtime not validated]
5. Add domain, transport, lifecycle and Compose UI tests. Build debug APK, run unit checks and lint. [done: 30 passing tests including Android API 30 smoke; release build and lint pass]
6. Deliver APK, source, licenses, build instructions and an evidence-based feature matrix with explicit deferred 1.0 requirements. [done]

## Environment facts
- Initially empty workspace. OpenJDK runtime present; no Android SDK, JDK compiler, Gradle, adb, emulator or Android device.
- No /dev/kvm. Installing a build toolchain from official endpoints.
- No fabricated successful Linux/QEMU boots. External Termux integration needs separate device validation.
- A software API 30 x86_64 emulator was subsequently installed and the final app smoke test passed. External Termux initialization encountered a framework ANR and launch timeout; no Linux boot is claimed.
- See docs/TEST_RESULTS.md for exact results and docs/FEATURE_MATRIX.md for remaining 1.0 scope.
