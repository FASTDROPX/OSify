# OSify 0.4.11 — присланные обложки

Версия 0.4.11 / code 18. Все 13 изображений установлены по указанному пользователем соответствию. Windows 10 и Tiny10 используют фон №2. Замены действуют в каталоге, раскрытой карточке и Launcher через существующий общий компонент OsArtwork.

| № | ОС | Исходный размер | Ресурс Android |
|---|---|---|---|
| 1 | Windows 7 | 596×335 | app/src/main/assets/branding/windows7-cover.webp |
| 2 | Windows 10 / Tiny10 | 3840×2160 | app/src/main/assets/branding/windows10-cover.webp |
| 3 | Alpine Linux | 2000×1125 | app/src/main/assets/branding/alpine-cover.webp |
| 4 | AlmaLinux | 1435×616 | app/src/main/assets/branding/alma-cover.webp |
| 5 | antiX Linux | 2560×1707 | app/src/main/assets/branding/antix-cover.jpg |
| 6 | Arch Linux | 1920×1080 | app/src/main/assets/branding/archlinux-cover.jpg |
| 7 | Artix Linux | 1280×720 | app/src/main/assets/branding/artixlinux-cover.webp |
| 8 | CachyOS | 6830×3840 | app/src/main/assets/branding/cachyos-cover.webp |
| 9 | Chimera Linux | 2560×1600 | app/src/main/assets/branding/chimeralinux-cover.webp |
| 10 | Edubuntu | 300×188 | app/src/main/assets/branding/edubuntu-cover.jpg |
| 11 | Fedora | 500×375 | app/src/main/assets/branding/fedora-cover.jpg |
| 12 | FreeDOS | 720×400 | app/src/main/assets/branding/freedos-cover.webp |
| 13 | Ubuntu Server | 596×335 | app/src/main/assets/branding/ubuntu_server-cover.webp |

Пропорции сохранены, увеличения маленьких исходников нет. Большие изображения уменьшены до 1536×1152 максимум. FreeDOS сохранён в WebP без потерь для чёткости текста. Общий объём 13 ресурсов: 736930 байт.

Изображения предоставлены пользователем в этом чате; новые разрешения правообладателей не заявляются. Оригиналы и SHA-256: `third_party/artwork-0.4.11/`. Точное соответствие файлов: `app/src/main/assets/branding/preview-sources-0.4.11.json`.

Код UI и логика приложения не изменены. Логотипы, сортировка, QEMU, анимации, навигация, загрузки, приветствие и NixOS сохранены. Побайтное сравнение существующих Kotlin-файлов и NixOS-ресурсов с базовой версией прошло.

Release/R8 собран успешно, APK подписан тем же ключом для установки обновлением. Все 13 изображений проверены на декодирование/пропорции и сверены с ресурсами внутри готового APK. Контактный лист просмотрен. Автоматизированные тесты и эмулятор для этой замены ресурсов не запускались.

Исходный проект: `work/osify-source/osify`.
