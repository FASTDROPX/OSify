package dev.osify.app.engine

import dev.osify.app.OsifyApplication
import dev.osify.app.data.VirtualMachine
import dev.osify.core.*
import java.io.File
import java.io.RandomAccessFile

internal data class VmBootFiles(val disk: File,val format: String,val iso: File?,val media: BootMedia?,val architecture: String,val hardware: VmHardware)

/** Same resolver in the caller and the QEMU process: validates the files actually attached. */
internal suspend fun resolveBoot(app: OsifyApplication,vm: VirtualMachine,requestedIso: Boolean,prepareDisk: Boolean=false): VmBootFiles {
    val h=VmHardware.decode(vm.hardwareJson)
    val folder=VmLogs.folder(app,vm.id)
    val image=app.database.dao().image(vm.imageId)
    val root=File(app.filesDir,"images").canonicalFile
    val source=image?.fileName?.takeIf {it.isNotEmpty()}?.let {File(root,it).canonicalFile.also {file->require(file.parentFile==root) {"Invalid image path"}}}
    val imported=image?.title?.lowercase()?.let {it.endsWith(".img") || it.endsWith(".qcow2") }==true
    val existingDisks=listOf("disk.raw","import.qcow2","import.img").map {File(folder,it)}.filter {it.isFile}
    check(existingDisks.size<=1) {"Обнаружено несколько виртуальных дисков. Выберите нужный диск перед запуском."}
    val disk=existingDisks.singleOrNull()
        ?:File(folder,if(imported) {if(image!!.title.endsWith(".qcow2",true)) "import.qcow2" else "import.img"} else "disk.raw")
    val useIso=h.useIso(requestedIso) && !imported
    val needsSource=useIso || imported && !disk.exists()
    if(needsSource) {
        check(image?.state=="COMPLETE" && source?.isFile==true && source.length()==image.bytes && image.bytes>0) {"Образ отсутствует или скопирован не полностью. Добавьте исходный ISO/диск заново."}
        check(image!!.flavor!="alpine-minirootfs" && !image.title.endsWith(".tar.gz",true)) {"Архив rootfs не является загрузочным ISO."}
    }
    if(!disk.exists()) {
        check(useIso || imported) {"Виртуальный диск отсутствует. Сначала запустите установку с ISO."}
        if(prepareDisk) {
            val temp=File(folder,"${disk.name}.creating")
            try {
                check(!temp.exists() || temp.delete()) {"Cannot clean interrupted disk preparation"}
                if(imported) source!!.inputStream().use {input->temp.outputStream().use {input.copyTo(it,1048576)}}
                else RandomAccessFile(temp,"rw").use {it.setLength(vm.diskGiB.toLong()*1024*1024*1024)}
                check(temp.renameTo(disk)) {"Cannot commit virtual disk"}
            } finally {temp.delete()}
        }
    }
    val media=if(needsSource) BootMediaProbe.inspect(source!!) else null
    if(useIso) require(media!!.kind in setOf("iso","udf")) {"Файл не распознан как ISO. Для образа диска используйте IMG или QCOW2."}
    if(disk.exists()) {
        val inspected=BootMediaProbe.inspect(disk)
        require(disk.length()>=512) {"Виртуальный диск усечён."}
        if(disk.extension=="qcow2") require(inspected.kind=="qcow2") {"Повреждён заголовок QCOW2."}
        if(!useIso && inspected.kind=="raw") RandomAccessFile(disk,"r").use {f->
            val b=ByteArray(minOf(f.length(),4096).toInt());f.readFully(b)
            require(b.any {it!=0.toByte()}) {"На диске не найдены загрузочные данные. Выберите установочный ISO."}
        }
    }
    val arch=BootPlan.architecture(h,image?.architecture?:"UNKNOWN",if(useIso) media else null)
    BootPlan.validate(h,vm.firmware,arch,if(useIso) media else null)
    return VmBootFiles(disk,if(disk.extension=="qcow2") "qcow2" else "raw",if(useIso) source else null,media,arch,h.copy(architecture=arch))
}
