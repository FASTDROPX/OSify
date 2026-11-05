package dev.osify.core

import org.junit.Test
import kotlin.test.*

class OfficialWindowsTest {
    @Test fun ubuntuDownloadKeepsPublisherChecksumAndTrustBoundary() {
        val image=OfficialUbuntu.desktop
        assertEquals("releases.ubuntu.com",TrustedUrls.check(image.url).host)
        assertTrue(image.sha256.matches(Regex("[a-f0-9]{64}")))
        assertFalse(OfficialWindows.isEvaluation(image))
        assertFails {TrustedUrls.check("https://releases.ubuntu.com.evil.example/image.iso")}
    }

    @Test fun checksumExceptionIsLimitedToPinnedMicrosoftEvaluation() {
        val image=OfficialWindows.windows11
        assertTrue(OfficialWindows.isEvaluation(image))
        assertFalse(OfficialWindows.isEvaluation(image.copy(url="https://example.com/image.iso")))
        assertFalse(OfficialWindows.isEvaluation(image.copy(bytes=image.bytes+1)))
        assertFalse(OfficialWindows.isEvaluation(image.copy(architecture=Architecture.ARM64)))
        assertEquals(OfficialWindows.HOST,TrustedUrls.check(image.url).host)
        assertFails {TrustedUrls.check("https://software-static.download.prss.microsoft.com.evil.example/image.iso")}
    }
}
