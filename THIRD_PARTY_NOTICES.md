# Third-party inventory

OSify-authored application source and original vectors use [Apache-2.0](LICENSE). The APK also bundles the QEMU/Limbo native engine, native dependencies, BIOS/UEFI firmware, fonts, catalog data and OS artwork under their own licenses. No guest installation ISO is bundled. The JNI adapter `app/src/main/cpp/osify_vm.c` is GPL-2.0-or-later as stated in its header. See [third_party/README.md](third_party/README.md) and the source archives in `third_party/native-sources/` for the bundled engine and dependencies. The root license does not relicense these components or the complete APK as Apache-2.0.

## Application runtime

| Dependency family | Pinned direct artifacts / purpose | License / source |
|---|---|---|
| JetBrains Kotlin | stdlib 2.2.21; language/runtime | [Apache-2.0](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt) |
| kotlinx.coroutines | 1.10.2; structured concurrency/Flow | [Apache-2.0](https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt) |
| kotlinx.serialization JSON | 1.9.0; strict versioned schemas | [Apache-2.0](https://github.com/Kotlin/kotlinx.serialization/blob/master/LICENSE.txt) |
| AndroidX Compose/Material | BOM 2025.11.01; UI, Material 3, icons, animation, graphics and debug tooling | [Apache-2.0](https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt), with notices supplied by artifacts |
| AndroidX Activity | activity-compose 1.12.0 | Apache-2.0 |
| AndroidX Lifecycle | runtime-compose/viewmodel-compose 2.10.0 | Apache-2.0 |
| AndroidX Room / SQLite | runtime/ktx/compiler 2.8.4; persistent records | Apache-2.0; generated compiler code not a bundled guest runtime |
| AndroidX DataStore | preferences 1.2.0; settings | Apache-2.0; includes native shared-counter support |
| AndroidX WorkManager / Startup / Core | work-runtime-ktx 2.11.0 and transitive components; foreground durable scheduling | Apache-2.0 |
| AndroidX Core Splashscreen | 1.2.0 | Apache-2.0 |
| AndroidX graphics-path / Skia components | Transitive Compose path support, including libandroidx.graphics.path.so | AndroidX Apache-2.0; [Skia BSD-3-Clause and third-party notices](https://skia.googlesource.com/skia/+/main/LICENSE) apply to included Skia portions |
| Guava listenablefuture and annotations | Transitive AndroidX support | [Apache-2.0](https://github.com/google/guava/blob/master/COPYING) |
| JetBrains annotations / JSpecify | Transitive nullability annotations | [Apache-2.0](https://github.com/JetBrains/java-annotations/blob/master/LICENSE.txt), [JSpecify](https://github.com/jspecify/jspecify/blob/main/LICENSE) |

The build can emit exact resolved debug-runtime coordinates with `./gradlew :app:runtimeInventory`; output goes to `app/build/reports/runtime-dependencies.txt`. This includes debug-only Compose tooling; release excludes those debug dependencies. Preserve artifact notices in redistributed binaries and update this inventory if dependencies change. APK packaging excludes the redundant META-INF AL2.0/LGPL2.1 files but this source distribution supplies the applicable license text/notices. The bundled native engine and dependencies retain their GPL/LGPL and other component terms; corresponding source archives, original build instructions, patches and notices are supplied in `third_party/native-sources/`.

## Build and test only

Gradle/AGP/KSP and Android build tools are not redistributed in the source ZIP or APK. Wrapper bootstrap JAR is included under Gradle's Apache-2.0 terms. Tests use JUnit 4.13.2 (EPL-1.0), Kotlin test (Apache-2.0), Robolectric 4.17 (MIT), AndroidX Test/Compose tests (Apache-2.0), and OkHttp MockWebServer/Okio 4.12.0-compatible artifacts (Apache-2.0). These test frameworks are not in the application APK; instrumentation test APK is a separate development artifact.

## References and optional external applications

| Project | License inspected | Redistribution/use in OSify |
|---|---|---|
| [Termux](https://github.com/termux/termux-app) | [GPL-3.0-only with component exceptions](https://github.com/termux/termux-app/blob/master/LICENSE.md) | Optional separately installed application. Protocol constants/behavior implemented from documentation; no library/source/binary bundled |
| [proot-distro](https://github.com/termux/proot-distro) | [GPL-3.0](https://github.com/termux/proot-distro/blob/master/LICENSE) | Managed externally by Termux; no program/rootfs bundled |
| [PRoot](https://github.com/proot-me/proot) | [GPL-2.0](https://github.com/proot-me/proot/blob/master/COPYING) | No binary bundled. Confirm complete version/component licensing before native distribution |
| [QEMU](https://www.qemu.org/) | GPL-2.0 with per-file/component terms; bundled COPYING retained | QEMU 5.1 native libraries bundled from Limbo; full upstream and Limbo source archives supplied |
| [swtpm](https://github.com/stefanberger/swtpm) | [BSD-3-Clause](https://github.com/stefanberger/swtpm/blob/master/LICENSE) plus linked dependencies | Optional external Termux package for TPM 2.0; not bundled |
| [Limbo](https://github.com/limboemu/limbo) | GPL-2.0 / GPL-2.0-or-later as identified by individual files | Native engine and compatibility libraries from Limbo 6.0.1; source, notices and provenance supplied |
| [UTM](https://github.com/utmapp/UTM) | [Apache-2.0 application](https://github.com/utmapp/UTM/blob/main/LICENSE), separate engine/firmware obligations | Reference only; no source or assets reused |
| [Alpine Linux](https://alpinelinux.org/) | Open-source package-specific licensing, including copyleft packages | Official release-list metadata only in APK; images downloaded by user. No single permissive license asserted for an entire root filesystem |
| Microsoft Windows | Proprietary OS/media/artwork; user OS license required | Installation media are not bundled. Some Windows preview artwork and logos are bundled with provenance; these are not relicensed under Apache-2.0 |

The current APK includes a native engine and firmware. Retain component copyright notices, license texts and complete corresponding sources when distributing them. This inventory describes provenance; it is not a new license grant for third-party code, trademarks or artwork. No claim of Play approval or independent legal audit is made.

## OS artwork added in 0.4.1

Bundled reduced WebP illustrations and vector OS marks have separate provenance and rights. See [artwork credits](docs/artwork/CREDITS.txt) and the full [Ubuntu wallpaper copyright/license text](docs/artwork/ubuntu-wallpapers-copyright.txt). Windows Bloom remains Microsoft copyrighted artwork; it is not relicensed as Apache-2.0 or CC0. Ubuntu wallpaper remains CC BY-SA 4.0, the Alpine tile photo uses the Unsplash license, and Simple Icons vectors use CC0 subject to their owners' trademarks. The credit text is also packaged as `assets/artwork-credits.txt`.


## Current native and firmware inventory (0.4.12)

QEMU 5.1.0 and Limbo 6.0.1, GLib 2.56.1, libffi 3.3, pixman 0.40.0, SDL 2.0.8, Limbo's compatibility/musl build support, and EDK2/OVMF 2022.11 Debian packaging are represented in `third_party/native-sources/`. Source URLs and hashes are recorded in `sources.json`; prebuilt native hashes are in `prebuilt-SHA256.json`. The source archives retain their upstream licenses and the original Limbo build configuration/patches. Firmware notices are also present in QEMU's `pc-bios/README`, `roms/`, and the EDK2 source.

The optional VirtIO-Win ISO is downloaded from the publisher only when requested; it is not embedded in the APK or this repository. Its individual driver licenses are supplied on that ISO. Windows installation media and ISO files used in device tests are not distributed here.

## Current artwork provenance (0.4.10–0.4.12)

See `docs/UI-0.4.10.md`, `docs/UI-0.4.11.md`, `third_party/artwork-0.4.10/`, `third_party/artwork-0.4.11/`, and `app/src/main/assets/branding/sources.json`. User-supplied covers and historical proprietary artwork are not asserted to be freely licensed. Unconfirmed redistribution permissions are explicitly retained in those provenance records. No OS preview or logo was changed for the GitHub publication.
