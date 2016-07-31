package wow

import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Created by synopia on 25/07/16.
 */

enum class TimeLeft(val maxTicks: Int) {
    SHORT(1),
    MEDIUM(2),
    LONG(12),
    VERY_LONG(24);
}

data class SnapshotEntry(val auc: Long, val item: Long, val owner: String, val ownerRealm: String, val bid: Long, val buyout: Long, val quantity: Int, val timeLeft: TimeLeft) {
    fun characterName(): String {
        return ownerRealm + "#" + owner
    }
}

data class SnapshotInfo(val name: String, val slug: String)
data class Snapshot(val realms: List<SnapshotInfo>, val auctions: List<SnapshotEntry>, var tick: Int = 0) {
}

class SnapshotScanner {
    val gson = Gson()

    fun toTick(name: String): Int {
        val result = Regex("auction_([0-9]{8})_([0-9]{2})\\.json").find(name)
        if (result != null) {
            val day = LocalDate.parse(result.groupValues[1], DateTimeFormatter.BASIC_ISO_DATE)
            val hour = result.groupValues[2].toInt()

            return day.dayOfYear * 24 + hour
        }
        return 0
    }

    fun scan(stream: ArchiveInputStream, cb: Function1<Snapshot, Unit>) {
        var entry = stream.nextEntry
        while (entry != null) {
            println(entry.name)
            val json = stream.readBytes(entry.size.toInt()).toString(Charset.forName("UTF-8"))
            val snapshot = gson.fromJson<Snapshot>(json)
            snapshot.tick = toTick(entry.name)
            cb.invoke(snapshot)
            entry = stream.nextEntry
        }
    }

    fun scan(dirname: String, cb: Function1<Snapshot, Unit>) {
        val dir = File(dirname)
        if (!dir.exists()) {
            return
        }
        if (dir.isFile) {
            scanFile(dirname, cb)
        } else if (dir.isDirectory) {
            val re = Regex("ah[0-9]{8}.tar.xz")
            val files = dir.list().filter { it.matches(re) }.sorted()
            files.forEach {
                scanFile(dirname + it, cb)
            }
        }
    }

    private fun scanFile(filename: String, cb: (Snapshot) -> Unit) {
        val tar = ArchiveStreamFactory().createArchiveInputStream(ArchiveStreamFactory.TAR, XZInputStream(BufferedInputStream(FileInputStream(filename))))
        scan(tar, cb)
    }
}
