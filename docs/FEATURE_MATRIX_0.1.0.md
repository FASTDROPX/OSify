# Feature matrix — OSify 0.1.0 milestone

Status meanings: **Implemented + Tested** has direct evidence listed; **Implemented + Untested** means code exists but the relevant Android/device behavior has not been established; **Partial** describes an explicitly limited subset; **Not Implemented** means unavailable. A JVM test is not an ARM64 device test. The APK is not presented as complete OSify 1.0.

| Requirement | Status | Evidence / boundary |
|---|---|---|
| Kotlin / Compose / Material 3 application | Implemented + Tested | Actual Gradle APK build; Compose UI tests use the actual application surfaces |
| Architecture, explicit GuestEngine, Flow/UDF | Implemented + Tested | Domain/lifecycle tests, app state, Room and DataStore; two physical modules, packages per responsibility |
| Five-screen first-launch onboarding, skip/replay | Implemented + Tested | UI tests traverse all five screens with motion off and skip in Russian; replay uses persisted preference |
| Original adaptive icon, foreground/background, themed monochrome, splash | Implemented + Tested | Vector resources compile in APK. Launcher masks on physical devices not manually tested |
| Original visual system, tonal cards, themes | Implemented + Tested | Robolectric native rendering screenshots reviewed; dynamic-color behavior on OEM hardware untested |
| Global full/reduced/off motion | Implemented + Tested | Preference combination unit test; onboarding UI with motion disabled; custom window MotionDurationScale for Material |
| Expressive shared-element transitions / broad gesture haptics | Partial | Orbit motif, progress/fade transitions and contextual onboarding feedback; no elaborate shared-element choreography |
| Live physical/available/estimated-used RAM and trend | Implemented + Untested | ActivityManager.MemoryInfo, 2.5 s collection, real observed history. Robolectric reports no physical RAM and is labelled unavailable; hardware measurements not claimed from those screenshots |
| Device ABI/core/storage/battery/thermal/GL/Vulkan | Implemented + Untested | Documented APIs, unknown values labelled. Graphics feature exposure is not a benchmark or KVM test |
| Free up resources | Partial | Releases only disposable OSify previews; no native OSify guests exist to stop. Before/after measured, no gain attributed to OSify; external Termux is untouched |
| Verified official catalog, cache, search/filter | Implemented + Tested | Actual official YAML fixtures, strict parsing tests, two live HTTPS manifest fetches, search/detail UI tests |
| Broad multi-distribution catalog and official normal/minimal variants | Partial | Alpine minirootfs, standard and virt ISO, ARM64/x86_64. Other distributions deferred |
| Windows provenance/legal handling | Implemented + Tested | Official Microsoft link and user import only; no redistributed Windows image or boot claim |
| Downloads, pause/retry/resume/cancel/restart | Implemented + Tested | Real transport tests cover HTTP Range/If-Range, failures and tampering; cancellation is pause then confirmed deletion. Android background duration/notification-denial/OEM scenarios require device testing |
| SHA-256, partial integrity, disk-space checks, atomic completion | Implemented + Tested | Transport integrity tests and actual Alpine rootfs hash verification; checkpoints cannot guarantee recovery of an uncheckpointed crash tail |
| Durable Room state and WorkManager recovery | Implemented + Untested | Implemented persisted state, scheduling, completed-file recovery and user-visible retry. Real reboot/constraint-loss/LMK scenarios are not established by JVM transport tests |
| Max concurrent downloads 1–3 / network preferences | Implemented + Untested | Persisted settings, WorkManager network constraints and process-local concurrency leases; multiprocess/long-running Android timing not device-tested |
| SAF image import and local hash | Implemented + Tested | Robolectric ContentResolver test copies bytes, verifies local hash and rejects provider filename traversal by using UUID paths. Android system file-picker and provider failure paths not yet device-tested |
| Import image architecture/bootability verification | Not Implemented | Explicitly UNKNOWN; no assumptions that an arbitrary ISO boots |
| Conservative compatibility/recommendations | Implemented + Tested | Host pressure/ABI/resource tests; PRoot never reserves VM RAM, QEMU never marked available |
| Speed / Balanced / Visual comfort | Partial | Persisted default and preset recommendations; external Termux is not reconfigured; no fake performance profile application |
| Preset create/edit/duplicate/delete/import/export JSON v1 | Implemented + Tested | Schema/injection/bounds/round-trip tests and UI create/validation/persistence flow; SAF document export still needs provider/device testing |
| Expert CPU/RAM/disk/firmware/display/audio/network/input fields | Partial | Validated and persisted for future adapter; clearly labelled inapplicable to external PRoot and unavailable QEMU |
| Arbitrary advanced engine flags | Not Implemented | Explicitly rejected rather than executing untrusted command fragments |
| Embedded ARM64 Linux install + launch | Not Implemented | No packaged validated native engine in the OSify APK |
| External Termux/PRoot Alpine path | Implemented + Untested | Real fixed-command protocol and authenticated result receiver; setup is manual. Room stale-callback race is tested. External bootstrap attempt hit Android framework ANR/timeouts; no Linux success is inferred from dispatch |
| QEMU, ISO boot, firmware/KVM integration | Not Implemented | Unavailable adapter returns an explicit error; no fabricated VM boot |
| Embedded VNC/SPICE/framebuffer, guest gestures, trackpad, zoom | Not Implemented | No guest display is shown or simulated |
| Embedded IME/international keyboard, Ctrl/Alt/F keys, pointer capture | Not Implemented | External terminal input is owned by Termux, not claimed as OSify functionality |
| Native VM service, safe shutdown/force stop/leave-dialog | Not Implemented | No own running VM exists. External ownership is explained; no stop request or hidden action against Termux |
| Transfer foreground service and contextual notifications | Implemented + Untested | dataSync type and permissions compile/lint; no claim of unlimited background execution or notification permission on API 33+ without device evidence |
| Optional battery-settings route | Implemented + Untested | System settings only; no exemption permission/request and no reliability promise |
| English/Russian settings and UI | Implemented + Tested | Russian onboarding and switching language through actual DataStore-backed UI. Technical/platform error transcripts can remain English |
| Light/dark/system/dynamic theme | Implemented + Tested | UI code and persisted preferences; dark render reviewed. Wallpaper-driven colors on physical devices untested |
| Accessibility/responsive layout | Partial | Material touch targets, text semantics, scrollable content/editor, wide navigation rail, vector assets. TalkBack, hardware keyboard, every large-font/rotation/OEM variant not formally audited |
| Settings persistence and reset | Implemented + Tested | DataStore-backed primary UI flows, default profile propagation and individual reset implementation. Storage relocation and some 1.0 settings are absent |
| Logs, diagnostic export/privacy/about | Partial | App/engine logs and SAF text export implemented; no telemetry. Export provider behavior not device-tested |
| HTTPS/domain/schema/path/command security | Implemented + Tested | Domain/redirect/traversal/manifest/preset tests; untrusted archives never extracted or executed |
| Third-party licenses and distribution review | Implemented + Tested | Referenced source/license inventory and runtime coordinates; no Play publication or legal certification claimed |
| Debug APK and release signing instructions | Implemented + Tested | Debug v2 signature and ZIP alignment verified, final Android smoke passed, unsigned minified release built. Release key belongs to the user |
| Full OSify 1.0 acceptance | Partial | Remaining engine/display/device resilience requirements are substantive work, not completed UI placeholders |

## Next milestones

1. Validate the external adapter on actual ARM64 Android 14–16 hardware, including callbacks, Termux/proot-distro versions, process death and terminal exit. This is distinct from embedding a runtime in OSify.
2. Build/legal-review an APK-packaged Android ARM64 runtime, then prove a Linux install and interactive launch before labelling an embedded engine functional.
3. Integrate packaged QEMU, appropriate redistributable firmware, per-architecture machine profiles and a real display/input backend. Add integration tests for actual guest boot/shutdown and corrupt/untrusted images.
4. Implement UIDT for very large foreground transfers on supported versions, formal reboot/LMK/notification-denial tests and richer transfer instrumentation.
5. Expand signed/trusted distro sources, relocatable storage, safe shared-folder/network consent and advanced runtime settings only as the engine truly supports them.
6. Complete large-font/TalkBack/keyboard/rotation/launcher-mask/device matrix validation and a release/distribution audit before calling the product 1.0.
