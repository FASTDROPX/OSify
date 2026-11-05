# Contributing to OSify

Use JDK 21, Android SDK 36 and Build Tools 36.0.0. The Gradle wrapper is included. See the [build instructions](README.md#сборка-из-исходников).

- Keep Material 3 Expressive styling, existing font roles, navigation and onboarding consistent.
- Test changes at the scope they affect. A UI-only change does not require downloading or installing every guest OS.
- Never treat a ready VNC connection as proof that a guest OS has booted.
- Preserve installed guest disks, configuration and per-machine OVMF variables.
- Validate architecture and the media actually attached to QEMU. Do not rename EFI loaders to bypass an architecture mismatch.
- Do not commit signing keys, credentials, local SDK paths, build caches or guest installation media.
- Preserve third-party source archives, source hashes, notices and image provenance when redistributing the native engine or artwork.

For a boot bug, include the app version, Android/device ABI, guest image filename and architecture, firmware, RAM/CPU settings, what is visible on screen, and a redacted TXT log exported from OSify. Do not post product keys or private file-transfer addresses.

For pull requests, explain the resulting behavior, why the change is needed, how it was checked and any remaining limitation. Include screenshots for visible UI changes.
