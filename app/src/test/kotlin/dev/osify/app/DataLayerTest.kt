package dev.osify.app

import android.content.ContentProvider
import android.content.ContentValues
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import dev.osify.app.data.ImageRecord
import dev.osify.app.data.ImageRepository
import dev.osify.app.data.EngineSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=TestOsifyApplication::class)
class DataLayerTest {
    @Test fun amoledPreferencePersistsAndResetClearsIt() = runBlocking {
        val preferences=ApplicationProvider.getApplicationContext<OsifyApplication>().preferences
        preferences.boolean("amoled",true)
        assertTrue(preferences.flow.first().amoled)
        preferences.reset()
        assertFalse(preferences.flow.first().amoled)
    }

    @Test fun staleExternalCallbackCannotResurrectResetSession() = runBlocking(Dispatchers.IO) {
        val dao=ApplicationProvider.getApplicationContext<OsifyApplication>().database.dao()
        dao.session(EngineSession(state="INSTALL_REQUESTED",token="old-request",operation="install"))
        dao.session(EngineSession())
        assertEquals(0,dao.finishSession("old-request","INSTALLED_EXTERNAL","stale callback"))
        assertEquals("IDLE",dao.session()!!.state)
        dao.session(EngineSession(state="INSTALL_REQUESTED",token="new-request",operation="install"))
        assertEquals(1,dao.finishSession("new-request","INSTALLED_EXTERNAL","actual result"))
        assertEquals("INSTALLED_EXTERNAL",dao.session()!!.state)
    }
    @Test fun stalePauseCannotRevertCompletedImage() = runBlocking(Dispatchers.IO) {
        val app=ApplicationProvider.getApplicationContext<OsifyApplication>();val dao=app.database.dao()
        dao.putImage(ImageRecord("complete","image","1","ARM64","rootfs","",4,"hash",state="COMPLETE"))
        dao.pauseIfActive("complete")
        assertEquals("COMPLETE",dao.image("complete")!!.state)
        dao.putImage(ImageRecord("active","image","1","ARM64","rootfs","",4,"hash",state="RUNNING"))
        dao.pauseIfActive("active")
        assertEquals("PAUSED",dao.image("active")!!.state)
    }
    @Test fun safImportCopiesBytesAndDoesNotTrustProviderFilenameAsPath() = runBlocking(Dispatchers.IO) {
        val app=ApplicationProvider.getApplicationContext<OsifyApplication>()
        val uri=Uri.parse("content://dev.osify.test/image")
        val bytes=ByteArray(8192) {(it%251).toByte()}
        ShadowContentResolver.registerProviderInternal("dev.osify.test",object: ContentProvider() {
            override fun onCreate()=true
            override fun query(uri: Uri,projection: Array<out String>?,selection: String?,args: Array<out String>?,sort: String?)=MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE)).also {it.addRow(arrayOf<Any>("../../outside.img",bytes.size.toLong()))}
            override fun getType(uri: Uri)="application/octet-stream"
            override fun insert(uri: Uri,values: ContentValues?): Uri?=null
            override fun update(uri: Uri,values: ContentValues?,selection: String?,args: Array<out String>?)=0
            override fun delete(uri: Uri,selection: String?,args: Array<out String>?)=0
        })
        shadowOf(app.contentResolver).registerInputStream(uri,ByteArrayInputStream(bytes))
        ImageRepository(app,app.database.dao(),app.preferences).import(uri)
        val imported=app.database.dao().images().first().single {it.imported}
        assertEquals("COMPLETE",imported.state,imported.error);assertEquals("UNKNOWN",imported.architecture)
        val file=File(app.filesDir,"images/${imported.fileName}")
        assertTrue(file.canonicalPath.startsWith(File(app.filesDir,"images").canonicalPath+File.separator))
        assertFalse(imported.fileName.contains("outside"));assertContentEquals(bytes,file.readBytes())
        assertEquals(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)},imported.sha256)
    }
}
