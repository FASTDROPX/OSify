package dev.osify.core

import java.io.File

/** Publisher checksum formats vary; partial-download checkpoints always remain SHA-256. */
object ImageChecksums {
 fun valid(image: CatalogImage)=image.sha256.matches(Regex("[a-f0-9]{64}")) || image.sha256.matches(Regex("sha512:[a-f0-9]{128}")) || SupplementalDownloads.archiveMatches(image) || image.sha256.isEmpty() && (OfficialWindows.isEvaluation(image) || DriverDownloads.matches(image))
 fun label(value: String)=when {value.startsWith("sha1:")->"SHA-1";value.startsWith("sha512:")->"SHA-512";else->"SHA-256"}
 fun value(value: String)=value.substringAfter(':',value)
 fun matches(file: File,image: CatalogImage): Boolean {
  if(image.sha256.isEmpty()) return (OfficialWindows.isEvaluation(image) || DriverDownloads.matches(image))
  if(!valid(image)) return false
  return ResumableDownloader.hash(file,algorithm=label(image.sha256))==value(image.sha256)
 }
}
