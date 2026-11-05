package dev.osify.app.data

import androidx.room.*
import dev.osify.core.TransferState
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "images") data class ImageRecord(
    @PrimaryKey val id: String, val title: String, val version: String, val architecture: String,
    val flavor: String, val url: String, val expectedBytes: Long, val sha256: String,
    val bytes: Long = 0, val state: String = TransferState.QUEUED.name, val error: String = "",
    val imported: Boolean = false, val fileName: String = "", val created: Long = System.currentTimeMillis()
)
@Entity data class CatalogCache(@PrimaryKey val architecture: String, val json: String, val fetchedAt: Long)
@Entity data class SavedPreset(@PrimaryKey val id: String, val json: String)
@Entity data class EngineSession(@PrimaryKey val id: Int = 1, val state: String = "IDLE", val token: String = "", val operation: String = "", val log: String = "")

@Entity data class VirtualMachine(
    @PrimaryKey val id: String,val name: String,val guest: String,val imageId: String="",
    val ramMiB: Int=1024,val cores: Int=2,val diskGiB: Int=32,val port: Int=5901,val password: String="",
    val state: String="STOPPED",val operation: String="",val token: String="",val log: String="",val firmware: String="BIOS",@ColumnInfo(defaultValue="''") val hardwareJson: String=""
)

@Dao abstract class OsifyDao {
    @Query("SELECT * FROM VirtualMachine WHERE id!='runtime' ORDER BY name") abstract fun virtualMachines(): Flow<List<VirtualMachine>>
    @Query("SELECT * FROM VirtualMachine WHERE id=:id") abstract suspend fun virtualMachine(id: String): VirtualMachine?
    @Query("SELECT * FROM VirtualMachine WHERE id='runtime'") abstract fun runtime(): Flow<VirtualMachine?>
    @Insert(onConflict=OnConflictStrategy.REPLACE) abstract suspend fun virtualMachine(row: VirtualMachine)
    @Query("UPDATE VirtualMachine SET state=:state, operation='', token='', log=:log WHERE id=:id AND token=:token") abstract suspend fun finishVm(id: String,token: String,state: String,log: String): Int
    @Query("UPDATE VirtualMachine SET name=:name, ramMiB=:ram, cores=:cores, firmware=:firmware WHERE id=:id AND state IN ('STOPPED','FAILED')") abstract suspend fun configureVm(id: String,name: String,ram: Int,cores: Int,firmware: String): Int
    @Query("UPDATE VirtualMachine SET name=:name, ramMiB=:ram, cores=:cores, firmware=:firmware, hardwareJson=:hardware WHERE id=:id AND state IN ('STOPPED','FAILED')") abstract suspend fun configureHardware(id: String,name: String,ram: Int,cores: Int,firmware: String,hardware: String): Int
    @Query("DELETE FROM VirtualMachine WHERE id=:id AND state='STOPPED'") abstract suspend fun deleteStoppedVm(id: String): Int
    @Query("SELECT * FROM images ORDER BY created DESC") abstract fun images(): Flow<List<ImageRecord>>
    @Query("SELECT * FROM images WHERE id=:id") abstract suspend fun image(id: String): ImageRecord?
    @Insert(onConflict=OnConflictStrategy.REPLACE) abstract suspend fun putImage(record: ImageRecord)
    @Query("UPDATE images SET state=:state, error=:error WHERE id=:id") abstract suspend fun state(id: String, state: String, error: String = "")
    @Query("UPDATE images SET bytes=:bytes WHERE id=:id") abstract suspend fun progress(id: String, bytes: Long)
    @Query("UPDATE images SET state='PAUSED', error=:error WHERE id=:id AND state IN ('QUEUED','RUNNING','VERIFYING')") abstract suspend fun pauseIfActive(id: String,error: String="")
    @Query("DELETE FROM images WHERE id=:id") abstract suspend fun deleteImage(id: String)
    @Query("SELECT * FROM CatalogCache WHERE architecture=:architecture") abstract suspend fun cache(architecture: String): CatalogCache?
    @Insert(onConflict=OnConflictStrategy.REPLACE) abstract suspend fun cache(record: CatalogCache)
    @Query("SELECT * FROM SavedPreset ORDER BY id") abstract fun presets(): Flow<List<SavedPreset>>
    @Insert(onConflict=OnConflictStrategy.REPLACE) abstract suspend fun preset(preset: SavedPreset)
    @Query("DELETE FROM SavedPreset WHERE id=:id") abstract suspend fun deletePreset(id: String)
    @Query("SELECT * FROM EngineSession WHERE id=1") abstract fun sessionFlow(): Flow<EngineSession?>
    @Query("SELECT * FROM EngineSession WHERE id=1") abstract suspend fun session(): EngineSession?
    @Insert(onConflict=OnConflictStrategy.REPLACE) abstract suspend fun session(session: EngineSession)
    @Query("UPDATE EngineSession SET state=:state, log=:log, token='', operation='' WHERE id=1 AND token=:token AND state IN ('INSTALL_REQUESTED','LAUNCH_REQUESTED')") abstract suspend fun finishSession(token: String,state: String,log: String): Int
}
@Database(entities=[ImageRecord::class,CatalogCache::class,SavedPreset::class,EngineSession::class,VirtualMachine::class],version=3,exportSchema=true)
abstract class OsifyDatabase: RoomDatabase() { abstract fun dao(): OsifyDao }

val VM_MIGRATION=object: androidx.room.migration.Migration(1,2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `VirtualMachine` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `guest` TEXT NOT NULL, `imageId` TEXT NOT NULL, `ramMiB` INTEGER NOT NULL, `cores` INTEGER NOT NULL, `diskGiB` INTEGER NOT NULL, `port` INTEGER NOT NULL, `password` TEXT NOT NULL, `state` TEXT NOT NULL, `operation` TEXT NOT NULL, `token` TEXT NOT NULL, `log` TEXT NOT NULL, `firmware` TEXT NOT NULL, PRIMARY KEY(`id`))")
    }
}

val HARDWARE_MIGRATION=object: androidx.room.migration.Migration(2,3) {
 override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {db.execSQL("ALTER TABLE `VirtualMachine` ADD COLUMN `hardwareJson` TEXT NOT NULL DEFAULT ''")}
}
