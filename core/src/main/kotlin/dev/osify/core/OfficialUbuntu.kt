package dev.osify.core

/** Snapshot checked against releases.ubuntu.com/24.04/SHA256SUMS and the ISO HEAD. */
object OfficialUbuntu {
    const val HOST="releases.ubuntu.com"
    val desktop=CatalogImage("ubuntu-desktop-24.04","Ubuntu","24.04.5.1 LTS",Architecture.X86_64,"ubuntu-desktop",
        "https://releases.ubuntu.com/24.04/ubuntu-24.04.5.1-desktop-amd64.iso",6250332160L,
        "4da4a0c9035da8e68a59a838674f403f0a54472c78a83b4fb7f78d03588f85a7",
        license="Open-source packages with individual licenses; some optional components have separate terms",source="https://releases.ubuntu.com/24.04/")
}
