package dev.osify.core

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class BootMedia(val kind: String,val architectures: Set<String> = emptySet(),val bios: Boolean=false,val efi: Boolean=false,val loaders: Set<String> = emptySet(),val note: String="") {
    val architecture: String get()=architectures.singleOrNull()?:"UNKNOWN"
}

/** Bounded, read-only inspection. An unrecognized filesystem is unknown, never called corrupt. */
object BootMediaProbe {
    fun inspect(file: File): BootMedia=RandomAccessFile(file,"r").use {f->
        require(f.length()>0) {"Empty image / Пустой образ"}
        fun read(offset: Long,size: Int): ByteArray {
            require(size in 0..4*1024*1024 && offset>=0 && offset+size<=f.length()) {"Image structure exceeds file bounds / Неполный образ"}
            return ByteArray(size).also {f.seek(offset);f.readFully(it)}
        }
        val magic=read(0,minOf(72L,f.length()).toInt())
        if(magic.size>=32 && magic.copyOfRange(0,4).contentEquals(byteArrayOf(81,70,73,-5))) {
            val b=ByteBuffer.wrap(magic).order(ByteOrder.BIG_ENDIAN)
            require(b.getInt(4) in 2..3 && b.getInt(20) in 9..21 && b.getLong(24)>0) {"Invalid QCOW2 header / Повреждён заголовок QCOW2"}
            require(b.getLong(8)==0L) {"QCOW2 requires an external backing file / Нужен базовый файл QCOW2"}
            return@use BootMedia("qcow2")
        }
        val loaders=linkedSetOf<String>();val arch=linkedSetOf<String>();var bios=false;var efi=false
        fun pe(bytes: ByteArray): String? {
            if(bytes.size<64 || bytes[0]!=77.toByte() || bytes[1]!=90.toByte())return null
            val at=ByteBuffer.wrap(bytes,60,4).order(ByteOrder.LITTLE_ENDIAN).int
            if(at<0 || at+6>bytes.size || !bytes.copyOfRange(at,at+4).contentEquals(byteArrayOf(80,69,0,0)))return null
            return when((bytes[at+4].toInt() and 255) or ((bytes[at+5].toInt() and 255) shl 8)) {0x14c->"X86";0x8664->"X86_64";0xaa64->"ARM64";else->null}
        }
        var iso=false;var udf=false;var catalog=0L;var root=ByteArray(0)
        if(f.length()>=19*2048) for(sector in 16..79) {
            if((sector+1L)*2048>f.length())break
            val d=read(sector*2048L,2048);val signature=d.copyOfRange(1,6).toString(Charsets.US_ASCII)
            if(signature in setOf("NSR02","NSR03"))udf=true
            if(signature!="CD001")continue
            iso=true
            if(d[0]==1.toByte())root=d.copyOfRange(156,190)
            if(d[0]==0.toByte() && d.copyOfRange(7,30).toString(Charsets.US_ASCII).startsWith("EL TORITO"))catalog=u32(d,71)*2048
            if(d[0]==(-1).toByte())break
        }
        val queue=ArrayDeque<Pair<String,ByteArray>>()
        if(root.isNotEmpty())queue.add("" to root)
        val visited=mutableSetOf<Long>();var entries=0
        while(queue.isNotEmpty() && visited.size<48 && entries<4096) {
            val (path,r)=queue.removeFirst();val offset=u32(r,2)*2048;val length=u32(r,10).coerceAtMost(262144).toInt()
            if(!visited.add(offset))continue
            val data=read(offset,length);var at=0
            while(at<data.size && entries++<4096) {
                val len=data[at].toInt() and 255
                if(len==0) {at=((at/2048)+1)*2048;continue}
                if(len<34 || at+len>data.size)break
                val rec=data.copyOfRange(at,at+len);val n=rec[32].toInt() and 255
                if(n>1 && 33+n<=rec.size) {
                    val name=rec.copyOfRange(33,33+n).toString(Charsets.US_ASCII).substringBefore(';').uppercase()
                    val full="$path/$name"
                    if(rec[25].toInt() and 2!=0) {if(path.count {it=='/'}<4)queue.add(full to rec)}
                    else if(name in setOf("BOOTX64.EFI","BOOTIA32.EFI","BOOTAA64.EFI","BOOTMGR","SETUPLDR.BIN")) {
                        loaders.add(full)
                        val size=u32(rec,10).coerceAtMost(65536).toInt()
                        pe(read(u32(rec,2)*2048,size))?.let {arch.add(it)}
                    }
                    if(name=="I386")loaders.add("/I386")
                    if(name=="AMD64")loaders.add("/AMD64")
                }
                at+=len
            }
        }
        // El Torito's platform byte describes firmware, not CPU architecture.
        if(catalog>0) {
            val c=read(catalog,2048)
            val checksum=(0 until 32 step 2).sumOf {(c[it].toInt() and 255) or ((c[it+1].toInt() and 255) shl 8)} and 65535
            require(c[0]==1.toByte() && c[30]==0x55.toByte() && c[31]==0xaa.toByte() && checksum==0) {"Invalid El Torito boot catalog / Повреждён каталог загрузки ISO"}
            var platform=c[1].toInt() and 255
            for(at in 32 until 2048 step 32) {
                val tag=c[at].toInt() and 255
                if(tag==0x90 || tag==0x91) {platform=c[at+1].toInt() and 255;continue}
                if(tag!=0x88)continue
                if(platform==0)bios=true
                if(platform==0xef) {
                    efi=true
                    val lba=u32(c,at+8)
                    // EFI boot images are FAT filesystems; find PE loaders, including ARM64-only media.
                    runCatching {fatLoaders(f,lba*2048)}.getOrDefault(emptyMap()).forEach {(name,cpu)->loaders.add("ESP/$name");arch.add(cpu)}
                }
            }
        }
        if(arch.isEmpty()) {
            if("/AMD64" in loaders)arch.add("X86_64") else if("/I386" in loaders)arch.add("X86")
        }
        BootMedia(if(iso) "iso" else if(udf) "udf" else "raw",arch,bios,efi,loaders,
            if(!iso) "Boot filesystem not fully recognized" else "ISO9660 / El Torito inspected")
    }
    private fun u32(b: ByteArray,at: Int)=ByteBuffer.wrap(b,at,4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
    private fun fatLoaders(f: RandomAccessFile,base: Long): Map<String,String> {
        fun read(at: Long,n: Int): ByteArray {require(at>=base && n in 0..65536 && at+n<=f.length());return ByteArray(n).also {f.seek(at);f.readFully(it)}}
        val b=read(base,512)
        fun u16(at: Int)=(b[at].toInt() and 255) or ((b[at+1].toInt() and 255) shl 8)
        val sector=u16(11);val cluster=b[13].toInt() and 255;val reserved=u16(14);val fats=b[16].toInt() and 255;val roots=u16(17)
        require(sector in setOf(512,1024,2048,4096) && cluster in 1..128 && reserved>0 && fats in 1..2)
        val fatSize=if(u16(22)>0)u16(22).toLong() else u32(b,36)
        val rootSectors=(roots*32L+sector-1)/sector;val firstData=reserved+fats*fatSize+rootSectors
        val total=if(u16(19)>0)u16(19).toLong() else u32(b,32)
        val count=(total-firstData)/cluster;val type=if(count<4085)12 else if(count<65525)16 else 32
        fun clusterOffset(c: Long)=base+(firstData+(c-2)*cluster)*sector
        fun directory(c: Long): ByteArray=if(c==0L && type!=32)read(base+(reserved+fats*fatSize)*sector,(roots*32).coerceAtMost(65536)) else read(clusterOffset(c),(sector*cluster).coerceAtMost(65536))
        val result=mutableMapOf<String,String>()
        fun child(data: ByteArray,wanted: String): Pair<Long,Long>? {
            for(at in 0..data.size-32 step 32) {
                if(data[at]==0.toByte())break
                if(data[at]==0xe5.toByte() || data[at+11]==15.toByte())continue
                val name=data.copyOfRange(at,at+8).toString(Charsets.US_ASCII).trim()+data.copyOfRange(at+8,at+11).toString(Charsets.US_ASCII).trim().let {if(it.isEmpty()) "" else ".$it"}
                if(name.equals(wanted,true))return (((data[at+27].toLong() and 255) shl 8) or (data[at+26].toLong() and 255) or ((data[at+20].toLong() and 255) shl 16) or ((data[at+21].toLong() and 255) shl 24)) to u32(data,at+28)
            };return null
        }
        val rootCluster=if(type==32)u32(b,44) else 0L
        val efi=child(directory(rootCluster),"EFI")?:return result
        val boot=child(directory(efi.first),"BOOT")?:return result
        for((name,cpu) in mapOf("BOOTX64.EFI" to "X86_64","BOOTIA32.EFI" to "X86","BOOTAA64.EFI" to "ARM64")) {
            val entry=child(directory(boot.first),name)?:continue
            val header=read(clusterOffset(entry.first),minOf(entry.second,4096).toInt())
            if(header.size>=64 && header[0]==77.toByte() && header[1]==90.toByte()) {
                val pe=u32(header,60).toInt();val expected=when(cpu) {"ARM64"->0xaa64;"X86"->0x14c;else->0x8664}
                if(pe>=0 && pe+6<=header.size && header[pe]==80.toByte() && header[pe+1]==69.toByte() && ((header[pe+4].toInt() and 255) or ((header[pe+5].toInt() and 255) shl 8))==expected)result[name]=cpu
            }
        }
        return result
    }
}
