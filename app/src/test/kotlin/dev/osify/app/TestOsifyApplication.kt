package dev.osify.app

/** JVM Room tests do not bind an Android IPC service. Real two-process invalidation is covered by NativeBootDeviceTest. */
class TestOsifyApplication: OsifyApplication() {override val multiProcessDatabase=false}
