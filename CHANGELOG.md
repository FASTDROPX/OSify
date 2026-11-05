# Changelog

## 0.4.12 — 2026-11-04

### Added

- File exchange through a separate PHONE CD with Unicode filenames, plus a private guest-to-phone upload page and Android file export.
- Persistent catalog favorites and a favorites filter.
- Launcher search by machine name, OS family and source image.
- Cancellable cloning of stopped machines, including disk, configuration and UEFI variables; new identity and credentials for each clone.
- A separate Windows driver CD: download VirtIO-Win 0.1.302 or import your own ISO, then attach or eject it while the VM is running.
- Automatic fit after rotation and optional guest display helpers for Windows with PowerShell 3+ and Linux X11 with Python 3/xrandr.
- Read-only boot-media inspection and explicit x86/x64 selection in Advanced.

### Fixed

- Windows XP x86 catalog metadata and defaults for newly created XP machines: BIOS, pc, qemu32, IDE, cirrus and PS/2.
- Installed-disk boot no longer requires the original installer ISO.
- Explicit QEMU media devices and boot priorities keep auxiliary CDs out of the installer boot order.
- Firmware asset preparation, native error handling and monitoring of the private QEMU service.
- Long pasted text is paced to avoid dropping PS/2 key events; framebuffer updates continue during paste.
- Hot CD changes use commands supported by the bundled QEMU 5.1.
- Exported VM logs redact private guest-tools URLs as well as operation tokens and VNC passwords.
- Guest-tools layout adapts to portrait/landscape; tab titles fit on one line.

### Verified and limited

- The supplied Windows XP SP3 x86 ISO reached **Welcome to Setup** in an Android API 35 emulator. A complete installed Windows desktop was not verified.
- Alpine BIOS boot, PHONE file reading, return upload, hot CD refresh and driver-CD attachment/ejection passed a real guest test.
- Favorites, search, cloning and guest-tools panels passed focused device UI checks in both orientations.
- Tiny11 was not boot-tested because the reported `tiny11a64 r1.iso` was unavailable locally.
- The Alpine UEFI/Q35/Nehalem attempt reached GRUB but did not reach login within 100 seconds; this path remains unconfirmed.
- Guest resolution changes depend on a helper and the guest video driver's supported modes. XP and Wayland are not supported by these helpers.

The GitHub publication on 2026-11-05 updates documentation and repository presentation. It does not change the 0.4.12 APK.
