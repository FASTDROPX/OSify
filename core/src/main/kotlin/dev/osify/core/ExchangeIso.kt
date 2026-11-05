package dev.osify.core

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDateTime

/** Flat ISO9660 + Joliet CD. Filenames remain Unicode; contents are streamed, never loaded in RAM. */
object ExchangeIso {
    private const val S=2048
    fun write(files: List<File>,destination: File) {
        require(files.size<=256 && files.all {it.isFile && it.length() in 0..0xffffffffL})
        val names=files.map {it.name.take(60)}
        require(names.distinct().size==names.size) {"Duplicate exchange filenames"}
        fun sectors(n: Long)=((n+S-1)/S).toInt().coerceAtLeast(1)
        fun alias(i: Int)=String.format("F%06d",i)+files[i].extension.uppercase().filter {it.isLetterOrDigit()}.take(3).let {if(it.isEmpty()) ";1" else ".$it;1"}
        fun dirSize(joliet: Boolean): Int {
            var size=68
            for(i in files.indices) {
                val n=if(joliet) (names[i]+";1").toByteArray(Charsets.UTF_16BE).size else alias(i).length
                val len=33+n+(if(n%2==0) 1 else 0)
                if(size%S+len>S)size=sectors(size.toLong())*S
                size+=len
            };return sectors(size.toLong())*S
        }
        val pSize=dirSize(false);val jSize=dirSize(true);val pRoot=23;val jRoot=pRoot+pSize/S
        var next=jRoot+jSize/S
        val extents=files.map {f->next.also {next+=sectors(f.length())}}
        require(next.toLong()*S<0xffffffffL) {"Exchange disc exceeds 4 GiB"}
        fun ByteArray.le16(at: Int,v: Int) {ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).putShort(at,v.toShort())}
        fun ByteArray.be16(at: Int,v: Int) {ByteBuffer.wrap(this).order(ByteOrder.BIG_ENDIAN).putShort(at,v.toShort())}
        fun ByteArray.le32(at: Int,v: Int) {ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).putInt(at,v)}
        fun ByteArray.be32(at: Int,v: Int) {ByteBuffer.wrap(this).order(ByteOrder.BIG_ENDIAN).putInt(at,v)}
        fun ByteArray.both16(at: Int,v: Int) {le16(at,v);be16(at+2,v)}
        fun ByteArray.both32(at: Int,v: Int) {le32(at,v);be32(at+4,v)}
        val now=LocalDateTime.now()
        fun record(name: ByteArray,extent: Int,size: Int,directory: Boolean): ByteArray=ByteArray(33+name.size+if(name.size%2==0) 1 else 0).also {r->
            r[0]=r.size.toByte();r.both32(2,extent);r.both32(10,size)
            r[18]=(now.year-1900).toByte();r[19]=now.monthValue.toByte();r[20]=now.dayOfMonth.toByte();r[21]=now.hour.toByte();r[22]=now.minute.toByte();r[23]=now.second.toByte()
            r[25]=if(directory) 2 else 0;r.both16(28,1);r[32]=name.size.toByte();name.copyInto(r,33)
        }
        RandomAccessFile(destination,"rw").use {out->
            out.setLength(0);out.setLength(next.toLong()*S)
            fun sector(at: Int,b: ByteArray) {out.seek(at.toLong()*S);out.write(b)}
            for(j in 0..1) {
                val joliet=j==1;val root=if(joliet) jRoot else pRoot;val size=if(joliet) jSize else pSize
                val d=ByteArray(S) {32};d.fill(0,0,7);d[0]=if(joliet) 2 else 1;"CD001".toByteArray().copyInto(d,1);d[6]=1
                if(joliet) {
                    "OSIFY".padEnd(16).toByteArray(Charsets.UTF_16BE).copyInto(d,8)
                    "PHONE".padEnd(16).toByteArray(Charsets.UTF_16BE).copyInto(d,40)
                } else {"OSIFY".toByteArray().copyInto(d,8);"PHONE".toByteArray().copyInto(d,40)}
                d[7]=0;d.fill(0,72,80)
                for(start in listOf(813,830,847,864)) {d.fill(48.toByte(),start,start+16);d[start+16]=0};d.fill(0,882,S)
                d.both32(80,next);if(joliet) {d[88]=37;d[89]=47;d[90]=69} else d.fill(0,88,120)
                d.both16(120,1);d.both16(124,1);d.both16(128,S);d.both32(132,10)
                d.le32(140,19+j*2);d.le32(144,0);d.be32(148,20+j*2);d.be32(152,0)
                record(byteArrayOf(0),root,size,true).copyInto(d,156);d[881]=1
                sector(16+j,d)
                for(big in 0..1) {
                    val path=ByteArray(S);path[0]=1
                    if(big==1) {path.be32(2,root);path.be16(6,1)} else {path.le32(2,root);path.le16(6,1)}
                    sector(19+j*2+big,path)
                }
                val directory=ByteArray(size);var at=0
                fun put(r: ByteArray) {if(at%S+r.size>S)at=((at/S)+1)*S;r.copyInto(directory,at);at+=r.size}
                put(record(byteArrayOf(0),root,size,true));put(record(byteArrayOf(1),root,size,true))
                for(i in files.indices)put(record(if(joliet) (names[i]+";1").toByteArray(Charsets.UTF_16BE) else alias(i).toByteArray(Charsets.US_ASCII),extents[i],files[i].length().toInt(),false))
                sector(root,directory)
            }
            val end=ByteArray(S);end[0]=(-1).toByte();"CD001".toByteArray().copyInto(end,1);end[6]=1;sector(18,end)
            for(i in files.indices) {out.seek(extents[i].toLong()*S);files[i].inputStream().use {input->val buffer=ByteArray(65536);while(true) {val n=input.read(buffer);if(n<0)break;out.write(buffer,0,n)}}}
            out.fd.sync()
        }
    }
}
