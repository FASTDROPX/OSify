package dev.osify.core

/** Public ISO sources, pinned to the file metadata retrieved on 2026-11-04. */
object SupplementalDownloads {
 val archivedWindows=listOf(
  CatalogImage("tiny11-source","Tiny11","2311 · EN",Architecture.X86_64,"windows-tiny11-source","https://archive.org/download/tiny11-2311/tiny11%202311%20x64.iso",3788177408L,"sha1:8cc38c966097544614836bacfbdd12baee270b07",license="Windows installation media; your Windows license is required",source="https://ntdotdev.wordpress.com/2024/01/08/the-complete-tiny10-and-tiny11-list/"),
  CatalogImage("windows-xp-source","Windows XP","Professional SP3 · EN · x86",Architecture.X86,"windows-xp-source","https://archive.org/download/windows-xp-all-sp-msdn-iso-files-en-de-ru-tr-x86-x64/en_windows_xp_professional_with_service_pack_3_x86_cd_vl_x14-73974.iso",617754624L,"sha1:66ac289ae27724c5ae17139227cbe78c01eefe40",license="Windows installation media; your Windows license is required",source="https://archive.org/details/windows-xp-all-sp-msdn-iso-files-en-de-ru-tr-x86-x64"),
  CatalogImage("windows-7-source","Windows 7","Ultimate SP1 · EN",Architecture.X86_64,"windows-7-source","https://archive.org/download/en_windows_7_ultimate_with_sp1_x64_dvd_u_677332_202006/en_windows_7_ultimate_with_sp1_x64_dvd_u_677332.iso",3320903680L,"sha1:36ae90defbad9d9539e649b193ae573b77a71c83",license="Windows installation media; your Windows license is required",source="https://archive.org/details/en_windows_7_ultimate_with_sp1_x64_dvd_u_677332_202006"),
  CatalogImage("tiny10-source","Tiny10","23H2 · EN",Architecture.X86_64,"windows-tiny10-source","https://archive.org/download/tiny-10-23-h2/tiny10%20x64%2023h2.iso",3839819776L,"sha1:6d07b21a8ce2314f481349d3d9513c25bd413bb5",license="Windows installation media; your Windows license is required",source="https://ntdotdev.wordpress.com/2024/01/08/the-complete-tiny10-and-tiny11-list/")
 )
 val windows get()=listOf(archivedWindows[0],OfficialWindows.windows10)+archivedWindows.drop(1)
 val linux=listOf(
  CatalogImage("quickget-artixlinux-x86_64","Artix Linux","20260813 · base OpenRC",Architecture.X86_64,"quickget-artixlinux","https://artix.arkhost.com/iso/artix-base-openrc-20260813-x86_64.iso",1235945472L,"6f75cf428b6b2df2d387eceff0d08fb0d93eeff39ec30e0957fbca7851bdf5c6",license="See Artix Linux distribution licenses",source="https://artixlinux.org/download.php"),
  CatalogImage("quickget-gentoo-x86_64","Gentoo","20261101 · minimal",Architecture.X86_64,"quickget-gentoo","https://distfiles.gentoo.org/releases/amd64/autobuilds/20261101T164556Z/install-amd64-minimal-20261101T164556Z.iso",1035399168L,"sha512:5deffe95d677446c3a745e4bbc44060b67fd7d272a286b00ef90e9cfaf1ba7574e3628cb7aa773faa6a62b45df5153e86c635ce12038415fd61161a9b9e68334",license="See Gentoo distribution licenses",source="https://www.gentoo.org/downloads/"),
  CatalogImage("quickget-debian-x86_64","Debian","13.7.0 · Cinnamon",Architecture.X86_64,"quickget-debian","https://cdimage.debian.org/debian-cd/13.7.0-live/amd64/iso-hybrid/debian-live-13.7.0-amd64-cinnamon.iso",0L,"678b7c67ca3ab507a457fe1a0495e4030564177ea17387af1c743c969cdc8e6c",license="See distribution license",source="https://www.debian.org/"),
  CatalogImage("quickget-chimeralinux-x86_64","Chimera Linux","20261103 · base",Architecture.X86_64,"quickget-chimeralinux","https://repo.chimera-linux.org/live/20261103/chimera-linux-x86_64-LIVE-20261103-base.iso",0L,"869abe5ac1f2384fe14f06b16673e85ff020a92f8e9d6ec9694d33f4707b2a11",license="See distribution license",source="https://chimera-linux.org/")
 )
 fun archiveMatches(image: CatalogImage)=archivedWindows.any {it.url==image.url && it.bytes==image.bytes && it.sha256==image.sha256 && it.flavor==image.flavor && it.architecture==image.architecture}
}
