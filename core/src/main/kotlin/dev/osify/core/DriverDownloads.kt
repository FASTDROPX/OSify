package dev.osify.core

object DriverDownloads {
    val virtio=CatalogImage("virtio-win-0.1.302","VirtIO Windows drivers","0.1.302",Architecture.X86_64,"windows-drivers",
        "https://fedorapeople.org/groups/virt/virtio-win/direct-downloads/archive-virtio/virtio-win-0.1.302-1/virtio-win-0.1.302.iso",877373440L,"",
        license="Individual driver licenses are included on the VirtIO-Win ISO",source="https://virtio-win.github.io/Knowledge-Base/Driver-installation.html")
    // Publisher supplies no ISO SHA256 at this URL. Restrict the HTTPS exception to this exact media.
    fun matches(image: CatalogImage)=image.url==virtio.url && image.bytes==virtio.bytes && image.flavor==virtio.flavor && image.architecture==virtio.architecture && image.sha256.isEmpty()
}
