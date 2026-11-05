# Third-party components in OSify 0.4.12

The original OSify application code remains under Apache-2.0. Component licenses are retained: QEMU GPL-2.0 (see each source file for later-version permissions), Limbo GPL-2.0-or-later, Quickget catalog data GPL-3.0. The APK contains multiple licensed components; retain their individual source, notices and license terms. This document does not relicense the complete APK.

## Native engine

Unmodified ARM64 and x86_64 QEMU 5.1.0 binaries and their dependency libraries were extracted from the official Limbo 6.0.1 x86 APK:
https://github.com/limboemu/limbo/releases/tag/v6.0.1-LimboEmulator

The original liblimbo.so is included for provenance but is not loaded. OSify loads its own `libosifyvm.so` JNI adapter, whose complete source is in app/src/main/cpp/osify_vm.c. It runs qemu_init, qemu_main_loop and qemu_cleanup, keeping QEMU's DSO mapped until its private Android process exits. The original Java ABI adapter was reduced to the methods needed by OSify. Build it using tools/build-native-bridge.ps1 with NDK r28.2.

`native-sources/` includes the Limbo release source ZIP, upstream QEMU 5.1.0, GLib 2.56.1, libffi 3.3, pixman 0.40.0, SDL 2.0.8, matching EDK2 source and Debian patches. Limbo's original build configuration, Android compatibility patches, musl helpers, firmware sources, copyright and developer instructions are in its source ZIP. See README.developers in that archive for Linux/NDK build and patch steps. All native prebuilt hashes are recorded in native-sources/prebuilt-SHA256.json. QEMU includes subproject firmware copyright notices under pc-bios/README and roms/.

OVMF_CODE.fd and OVMF_VARS.fd come from Debian's ovmf_2022.11-6+deb12u2_all.deb. Its corresponding source and Debian packaging patches are included. No guest Windows or Linux installation ISO is distributed with OSify.

The original Limbo native libraries are 4 KB ELF aligned. Android's manifest pageSizeCompat mode is enabled for 16 KB systems, but a 16 KB device was not tested. The OSify adapter itself is linked with 16 KB alignment. Full native 16 KB compatibility requires rebuilding the third-party libraries.

## Catalog

The bundled quickget_data.json comes from the public daily endpoint:
https://github.com/quickemu-project/quickget_configs/releases/download/daily/quickget_data.json

The Quickget GPL-3.0 license is included. OSify uses an allowed-publisher HTTPS list and verifies the supported publisher checksum algorithms when supplied. Direct download entries were refreshed in 0.4.9, including Windows, Artix and Gentoo. Archive.org Windows entries use pinned media checksums; Microsoft evaluation media and the exact VirtIO-Win 0.1.302 ISO have narrow HTTPS exceptions where a publisher ISO SHA-256 is not supplied. Downloads still record a local SHA-256. A local hash is not presented as publisher authentication. See `core/.../SupplementalDownloads.kt`, `OfficialWindows.kt`, `DriverDownloads.kt` and `ImageChecksums.kt` for the actual allowlist/pins.

## Artwork

`artwork-originals/` contains downloaded original SVG/PNG/JPEG files. `app/src/main/assets/branding/sources.json` lists the source URL and SHA-256 for each used image. Only rasterization, scaling, cropping of wordmarks and removal of optional display backplates were applied; no OS marks or artwork were generated. Trademarks and screenshots remain the property of their respective owners.

Sources: distribution project artwork repositories, Quickemu icons, DistroWatch screenshots and Wikipedia/Wikimedia Windows screenshots/logos. Quickemu's license is included. Chimera artwork is CC-BY-SA-4.0 (Cura Sylfaen); NixOS and the wallpaper packages retain their upstream licenses. Ubuntu artwork licensing is included in app assets. The original four supplied OS marks and existing three cover resources are retained with their original provenance.

Current artwork revisions and rights notes: [0.4.10](../docs/UI-0.4.10.md), [0.4.11](../docs/UI-0.4.11.md). Unconfirmed rights are not replaced by an assumed open license.
