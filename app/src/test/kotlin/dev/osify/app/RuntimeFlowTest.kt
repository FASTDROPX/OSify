package dev.osify.app

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import dev.osify.app.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=TestOsifyApplication::class)
class RuntimeFlowTest {
    @Test fun preparationUsesActualTermuxContractAndDoesNotClaimBoot()=runBlocking(Dispatchers.IO) {
        val app=ApplicationProvider.getApplicationContext<OsifyApplication>()
        app.startupRecovery.join()
        shadowOf(app.packageManager).installPackage(PackageInfo().apply {packageName="com.termux";applicationInfo=ApplicationInfo().apply {packageName="com.termux"}})
        shadowOf(app).grantPermissions("com.termux.permission.RUN_COMMAND")
        app.preferences.boolean("termux",true)
        dev.osify.app.engine.QemuTermuxEngine(app).prepare()
        val request=shadowOf(app).nextStartedService
        assertEquals("com.termux.RUN_COMMAND",request.action)
        assertEquals("com.termux.app.RunCommandService",request.component!!.className)
        assertEquals("/data/data/com.termux/files/usr/bin/bash",request.getStringExtra("com.termux.RUN_COMMAND_PATH"))
        assertTrue(request.getStringArrayExtra("com.termux.RUN_COMMAND_ARGUMENTS")!!.last().contains("qemu-system-x86-64-headless"))
        assertEquals("REQUESTED",app.database.dao().virtualMachine("runtime")!!.state)
    }
    @Test fun upgradeKeepsExistingPresetsAndCreatesValidatedVmTable()=runBlocking(Dispatchers.IO) {
        val app=ApplicationProvider.getApplicationContext<OsifyApplication>();app.startupRecovery.join()
        val name="migration-runtime-test.db";app.deleteDatabase(name)
        val schema=org.json.JSONObject(java.io.File("schemas/dev.osify.app.data.OsifyDatabase/1.json").readText()).getJSONObject("database")
        val path=app.getDatabasePath(name);path.parentFile!!.mkdirs()
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(path,null).use {old->
            val entities=schema.getJSONArray("entities")
            for(i in 0 until entities.length()) {val entity=entities.getJSONObject(i);old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",entity.getString("tableName")))}
            old.execSQL("INSERT INTO SavedPreset (id,json) VALUES ('retained','existing configuration')")
            old.version=1
        }
        val database=androidx.room.Room.databaseBuilder(app,OsifyDatabase::class.java,name).addMigrations(VM_MIGRATION,HARDWARE_MIGRATION).build()
        try {
            assertEquals("existing configuration",database.dao().presets().first().single().json)
            database.dao().virtualMachine(VirtualMachine("vm","Migration","LINUX"))
            assertEquals("BIOS",database.dao().virtualMachine("vm")!!.firmware)
        } finally {database.close();app.deleteDatabase(name)}
    }
    @Test fun staleVmResultCannotOverwriteNewCommand()=runBlocking(Dispatchers.IO) {
        val app=ApplicationProvider.getApplicationContext<OsifyApplication>();app.startupRecovery.join()
        val dao=app.database.dao()
        dao.virtualMachine(VirtualMachine("test-vm","Test VM","WINDOWS",state="REQUESTED",token="new-token",firmware="UEFI"))
        assertEquals(0,dao.finishVm("test-vm","old-token","DISPLAY_AVAILABLE","stale"))
        assertEquals("REQUESTED",dao.virtualMachine("test-vm")!!.state)
        assertEquals(1,dao.finishVm("test-vm","new-token","DISPLAY_AVAILABLE","real callback"))
        assertEquals("UEFI",dao.virtualMachine("test-vm")!!.firmware)
    }
}
