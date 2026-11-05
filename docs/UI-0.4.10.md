# OSify 0.4.10 — точечная доработка UI

4 ноября 2026. Версия 0.4.10 / code 17. APK для ARM64 и x86_64, Android 8+.

## Реализация

**Сортировка:** отдельный Material bottom sheet, векторные иконки и отметка выбранного пункта. Пять вариантов: исходный порядок, название А–Я / Я–А, размер по возрастанию / убыванию. Для названий используется Collator текущего языка; одинаковые значения сохраняют исходный порядок. Неизвестный размер (0) всегда в конце. Даты и популярность не выдумываются. Реальный порядок списка меняется с коротким затуханием/проявлением; выбор сохраняется в DataStore и восстанавливается при возвращении. Linux, Windows, Minimal и ISO остаются отдельными прежними фильтрами. Открытие и закрытие — анимация Material sheet, выбор и подпись анимированы. Настройка отключения движения учитывается в новых переходах.

**QEMU:** прежняя большая карточка заменена строкой высотой 52 dp при обычном размере шрифта. Статусы проверки, готовности компонентов, нехватки компонентов, ошибки, запуска, работы, паузы и остановки берутся из проверок APK и реального состояния машин. Проверяются поддерживаемая 64-битная ABI, ELF-заголовки десяти библиотек движка/зависимостей, наличие BIOS/VGA/UEFI. Проверка работает вне UI-потока и не запускает гостевую ОС. «Готов» означает доступность компонентов, не гарантирует загрузку любого ISO. Ошибка запуска связана с конкретной машиной и её журналом. По нажатию доступны пояснение, повторная проверка через существующий native prepare и журнал активной/ошибочной машины. При возврате на экран компоненты проверяются снова. Смена состояния и раскрытие анимированы. Детектор ошибок настроек машины не возвращён; нативный движок и его запуск не менялись.

**Выход из ОС:** порядок действий теперь «Остановить машину» → «Картинка в картинке» → «Скрыть экран» → «Отмена». Перемещены только существующие блоки; их обработчики, цвета, индикация и остановка сохранены.

**Изображения:** заменены 14 превью в общем OsArtwork для каталога и Launcher. Логотипы и композиция карточек сохранены. Пропорциональное уменьшение максимум до 1536×1152, JPEG/WebP, без увеличения исходников. Общий объём 14 ресурсов — около 1.4 МБ. Существующий Crop сохраняет пропорции и обрезает края, не растягивает изображение. Существующие затемнения/цвета не изменены.

## Заменённые изображения и лицензии

Изображения взяты из реальных пакетов оформления, репозиториев разработчиков или Wikimedia Commons. AI-генерация не применялась. Источники, оригиналы, контрольные суммы, размеры и полные найденные уведомления находятся в `third_party/artwork-0.4.10/`, `app/src/main/assets/branding/preview-sources-0.4.10.json` и `app/src/main/assets/artwork-licenses-0.4.10/`. Кредиты включены в APK.

| ОС | Изображение / источник | Автор | Лицензия / результат проверки |
|---|---|---|---|
| artixlinux | [Artix_wallpaper.png](https://github.com/artix-linux/artwork/blob/master/backgrounds/Artix_wallpaper.png) · 1920×1080 → 1536×864 | Artix Linux artwork contributors | No explicit artwork license found |
| chimeralinux | [breeze-fullscreenpreview.jpg](https://github.com/chimera-linux/chimera-artwork/blob/master/breeze-previews/breeze-fullscreenpreview.jpg) · 1920×1080 → 1536×864 | Chimera Linux artwork contributors | CC-BY-SA-4.0 |
| debian | [desktop-base_13.0.4_all.deb](https://deb.debian.org/debian/pool/main/d/desktop-base/desktop-base_13.0.4_all.deb) · 1920×1200 → 1536×960 | Elise Couper (Ceratopsian) | GPL-2.0-or-later |
| devuan | [your-way_sapphire_1080p.png](https://git.devuan.org/devuan/desktop-base/src/branch/master/backgrounds/your-way_sapphire_1080p.png) · 1920×1080 → 1536×864 | Devuan desktop-base artists (see copyright) | Specific Sapphire wallpaper license not confirmed by the available desktop-base copyright notice |
| fedora | [File:Fedora_43_default_wallpaper.png](https://commons.wikimedia.org/wiki/File:Fedora_43_default_wallpaper.png) · 4032×3024 → 1536×1152 | Fedora Design Team | CC-BY-SA-4.0 |
| edubuntu | [edubuntu-wallpapers_26.04.11_all.deb](https://archive.ubuntu.com/ubuntu/pool/universe/e/edubuntu-artwork/edubuntu-wallpapers_26.04.11_all.deb) · 6000×4000 → 1536×1024 | Lucas George Wendt (2020) | Unsplash License |
| garuda | [Black%20Eagle.png](https://gitlab.com/garuda-linux/themes-and-settings/artwork/garuda-wallpapers/-/blob/master/src/garuda-wallpapers/Black%20Eagle.png) · 1920×1080 → 1536×864 | Garuda Linux artwork contributors | GPL-3.0 |
| kubuntu | [5120x2880.jpg](https://github.com/KDE/plasma-workspace-wallpapers/blob/master/Flow/contents/images/5120x2880.jpg) · 5120×2880 → 1536×864 | Sandra Smukaste | CC-BY-SA-4.0 |
| lubuntu | [lubuntu-artwork_26.04.3_all.deb](https://archive.ubuntu.com/ubuntu/pool/universe/l/lubuntu-artwork/lubuntu-artwork_26.04.3_all.deb) · 3840×2160 → 1536×864 | Aaron Rainbolt / MediaMom Ministries (package notice) | CC-BY-SA-4.0 |
| ubuntu_studio | [ubuntustudio-wallpapers-resolute_26.04.12_all.deb](https://archive.ubuntu.com/ubuntu/pool/universe/u/ubuntustudio-look/ubuntustudio-wallpapers-resolute_26.04.12_all.deb) · 4096×4096 → 1152×1152 | Eylul Dogruel (2024–2026) | CC-BY-SA-4.0 |
| ubuntu_unity | [ubuntu-unity-backgrounds_26.04-0ubuntu1_all.deb](https://archive.ubuntu.com/ubuntu/pool/universe/u/ubuntu-unity-backgrounds/ubuntu-unity-backgrounds_26.04-0ubuntu1_all.deb) · 1920×1080 → 1536×864 | Ariel Closs Novais (2026) | GPL-3.0 |
| ubuntu | [ubuntu-wallpapers_26.04.2_all.deb](https://archive.ubuntu.com/ubuntu/pool/main/u/ubuntu-wallpapers/ubuntu-wallpapers_26.04.2_all.deb) · 3840×2160 → 1536×864 | Canonical / Ubuntu Design Team | CC-BY-SA-3.0; see package copyright |
| alpine | [File:Alpine_Linux_Xfce_-_English.png](https://commons.wikimedia.org/wiki/File:Alpine_Linux_Xfce_-_English.png) · 1024×768 → 1024×768 | Alpine Linux development team; uploaded by Ahunt | GPL-2.0-or-later |
| ubuntu_server | [92bda8a0-basic_installation_1.png](https://documentation.ubuntu.com/server/_images/92bda8a0-basic_installation_1.png) · 800×600 → 800×600 | Canonical / Ubuntu Server documentation contributors | CC-BY-SA-3.0 (Ubuntu documentation; verify image-specific rights) |

CC BY-SA 4.0: https://creativecommons.org/licenses/by-sa/4.0/; GPL 2+: https://www.gnu.org/licenses/old-licenses/gpl-2.0.html; GPL 3: https://www.gnu.org/licenses/gpl-3.0.html; Unsplash: https://unsplash.com/license. Производные изображения сохраняют соответствующие лицензии. Изменения: пропорциональный ресайз, сжатие; экранное кадрирование прежним компонентом. Оригиналы включены в исходники.

**Неподтверждённые права:** официальный репозиторий Artix не содержит явного разрешения для выбранного файла; доступное copyright-уведомление Devuan не подтверждает лицензию конкретных обоев Sapphire. Они отмечены как неподтверждённые, а не объявлены свободными. Для публичной публикации необходимо уточнить разрешения. Существующие Windows-изображения также проприетарные; наличие в интернете или предоставление пользователем не подтверждает права на распространение. Условия Microsoft не дают общего разрешения на скриншоты в UI сторонних продуктов: https://www.microsoft.com/en-us/legal/intellectualproperty/copyright/permissions. Эти ограничения не скрыты под фиктивной общей GPL/CC-лицензией.

Fedora — настоящие обои Fedora 43, не обещание оформления другой версии. Kubuntu — настоящие обои KDE Plasma Flow. Alpine — настоящий снимок дополнительного Xfce-десктопа; минимальный virt ISO сам по себе его не включает. Ubuntu Server — настоящий установщик Subiquity, у серверного ISO нет штатного графического рабочего стола. Серверный оригинал 800×600 сохранён без увеличения. Одинаковый Ubuntu Desktop wallpaper больше не используется для Ubuntu Server/Unity/Studio.

## Сохранённые изображения

Подходящие существующие изображения не заменялись только ради замены. Проверены их соответствие ОС и текущие источники. Авторские права на часть старых ресурсов не подтверждены; это не юридически очищенный комплект для публичной дистрибуции.

| ОС | Сохранённый источник |
|---|---|
| bodhi | https://raw.githubusercontent.com/BodhiDev/bodhi-backgrounds/main/bl6/Bodhi_Background/bodhi-bg.jpg |
| cachyos | https://raw.githubusercontent.com/CachyOS/cachyos-wallpapers/develop/usr/share/wallpapers/cachyos-wallpapers/CachyOS_GreenSpace.png |
| alma | https://almalinux.org/images/hero_1.png |
| athenaos | https://athenaos.org/_astro/athena-chibi.C4AxdAFD.png |
| freedos | https://www.freedos.org/_images/screenshots/screenshot_freedos14.png |
| windows10 | https://en.wikipedia.org/wiki/Windows_10 |
| windows7 | https://en.wikipedia.org/wiki/Windows_7 |
| antix | https://raw.githubusercontent.com/LinuxKits/Distro-wallpapers/main/antiX/19/login/antiX-ice.jpg |
| archlinux | https://raw.githubusercontent.com/LinuxKits/Distro-wallpapers/main/Arch/Wallpapers/archlinux-arrival.jpg |
| archcraft | https://raw.githubusercontent.com/archcraft-os/archcraft-wallpapers/main/archcraft-backgrounds/files/default.jpg |
| gentoo | https://raw.githubusercontent.com/LinuxKits/Distro-wallpapers/main/Gentoo/Wallpapers/gentoo-larry-bg-1920x1080.png |
| ubuntu_budgie | https://raw.githubusercontent.com/ubuntubudgie/budgie-wallpapers/master/Xplo_by_Hugo_Cliff.jpg |
| ubuntu_cinnamon | https://raw.githubusercontent.com/Ubuntu-Cinnamon-Remix/ubuntucinnamon-wallpapers/master/usr/share/backgrounds/ubuntucinnamon/Ubuntu_Cinnamon_Gradient.jpg |
| ubuntu_mate | https://raw.githubusercontent.com/ubuntu-mate/ubuntu-mate-artwork/master/usr/share/backgrounds/ubuntu-mate-common/Green-Wall-Logo.png |
| xubuntu | https://raw.githubusercontent.com/Xubuntu/xubuntu-artwork/master/usr/share/xfce4/backdrops/xubuntu-jammy.png |
| ghostbsd | https://raw.githubusercontent.com/ghostbsd/ghostbsd-wallpapers/master/blue-mountain.jpg |
| ubuntu_kylin | https://raw.githubusercontent.com/ubuntukylin/ubuntukylin-theme/Debian/usr/share/grub/themes/UKUI/background.png |
| windowsxp | User supplied image 2026-11-04 |

Windows 11 / Tiny11 сохраняют узнаваемый Microsoft Bloom из существующих ресурсов; Tiny11 подписана как облегчённая Windows от NTDEV. Windows 10 / Tiny10 сохраняют соответствующее изображение Windows 10; XP — предоставленный пользователем Bliss. Tiny10/Tiny11 не представлены как отдельные официальные продукты Microsoft. Ubuntu MATE: Roberto Perico / уведомление проекта; Ubuntu Budgie Xplo: Hugo Cliff, CC BY-SA 4.0; Ubuntu Cinnamon Gradient: Allan Carvalho, GPL 3+; Archcraft: GPL 3 проекта. Их найденные уведомления добавлены в credits/licenses. Для оставшихся старых ресурсов нельзя автоматически переносить лицензию программного кода на обои.

## Границы изменений

NixOS исключена из замены: все существующие ресурсы с nixos в пути и её записи источников совпадают с базовой копией по SHA-256/содержимому. Nav Reflow, Theme/шрифты/цветовая система, онбординг/приветствие, core, загрузки и native QEMU engine не изменены. В GuestConsoleScreen diff содержит только перестановку Hide/PiP относительно существующего блока Stop.

## Проверки и сборка

- Debug + release/R8 собраны. Финальное перепаковывание release после уточнения credits — успешно за 8 секунд; основная сборка — 1 мин 51 с. Подпись v2/v3 тем же ключом, что 0.4.9.
- Два коротких JVM-теста реальной сортировки: название/исходный порядок, размеры/стабильные совпадения/неизвестный размер в конце — успешно.
- Один короткий Android UI-сценарий, 19.106 с, API 35 x86_64: действительная перестановка Windows-карточек по имени, сохранение выбора при уходе и возврате, выбранная отметка, отдельный Windows-фильтр, высота QEMU ≤64 dp, раскрытие QEMU, светлая тема, альбомное меню с прокруткой и выбор сортировки по размеру — успешно.
- Полученные скриншоты просмотрены; новые меню/плашка без наложений и обрезанных подписей. В альбомной ориентации sheet прокручивается до нижнего пункта.
- Итоговый подписанный release установлен обновлением, холодный запуск Activity успешен (964 мс). Каталог и компактная плашка QEMU дополнительно просмотрены в подписанном APK.
- Полный регресс, запуск реальных гостевых ОС, проверка на физическом ARM64-телефоне и всех аварийных состояний QEMU не выполнялись. Поведение этих подсистем не менялось.

Исходный проект: `work/osify-source/osify`. APK: `OSify-0.4.10.apk`. Скриншоты: `OSify-0.4.10-preview.html`. Сборочные логи, результаты коротких тестов и проверка границ: `docs/evidence/ui-0.4.10/`.
