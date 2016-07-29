package wow

import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.nio.charset.Charset
import java.util.*

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
data class Snapshot(val realms: List<SnapshotInfo>, val auctions: List<SnapshotEntry>)

class SnapshotScanner {
    val gson = Gson()

    fun scan(stream: ArchiveInputStream, cb: Function1<Snapshot, Unit>) {
        var entry = stream.nextEntry
        while (entry != null) {
            println(entry.name)
            val json = stream.readBytes(entry.size.toInt()).toString(Charset.forName("UTF-8"))
            val snapshot = gson.fromJson<Snapshot>(json)
            cb.invoke(snapshot)
            entry = stream.nextEntry
        }
    }

    fun scan(filename: String, cb: Function1<Snapshot, Unit>) {
        val tar = ArchiveStreamFactory().createArchiveInputStream(ArchiveStreamFactory.TAR, XZInputStream(BufferedInputStream(FileInputStream(filename))))
        scan(tar, cb)
    }
}

data class Character(val name: String) {
    val auctions = mutableSetOf<Auction>()
    val sold = mutableSetOf<Auction>()
    var totalSold = 0L
}

data class Item(val id: Long, var marketPrice: Long = 0L) {
    val auctions = mutableSetOf<Auction>()
    val sold = mutableSetOf<Auction>()
    var totalSold = 0L

    fun updateMarketPrice() {
        var totalBuyout = 0L
        var total = 0

        auctions.forEach {
            totalBuyout += it.buyout
            total += it.quantity
        }

        marketPrice = if (total > 0) totalBuyout / total else 0
    }
}

enum class AuctionResult {
    UNDEFINED,
    SOLD,
    BID,
    CANCELED,
    EXPIRED
}

class Auction(val id: Long, val seller: Character, var timeLeft: TimeLeft, val item: Item, val startBid: Long, val buyout: Long, val quantity: Int) {
    var tickStarted = 0
    var tickEnded = 0
    var ticks = timeLeft.maxTicks
    var result = AuctionResult.UNDEFINED
    var lastBid = startBid

    fun start(tick: Int) {
        tickStarted = tick
        seller.auctions += this
        item.auctions += this
    }

    fun update(tick: Int, timeLeft: TimeLeft, bid: Long) {
        if (tickStarted == 0) {
            return
        }
        // TODO check timeLeft change
        lastBid = bid
    }

    fun end(tick: Int) {
        if (tickStarted == 0) {
            return
        }
        tickEnded = tick
        seller.auctions.remove(this)
        item.auctions.remove(this)

        val ticksSeen = tick - tickStarted
        if (ticksSeen < ticks) {
            var cheaperStack = seller.auctions.filter { it.item == item && it.quantity == quantity }.find { it.buyout < buyout }
            if (cheaperStack == null) {
                cheaperStack = item.auctions.find { it.buyout / it.quantity < buyout / quantity }
            }
            if (cheaperStack != null) {
                result = AuctionResult.CANCELED
            } else {
                result = AuctionResult.SOLD
                seller.totalSold += buyout
                seller.sold += this
                item.totalSold += buyout
                item.sold += this
            }
        } else {
            if (startBid != lastBid) {
                result = AuctionResult.BID
                seller.totalSold += lastBid
                seller.sold += this
                item.totalSold += lastBid
                item.sold += this
            } else {
                result = AuctionResult.EXPIRED
            }
        }
    }
}

class AH {
    var tick = 0
    val characters = mutableMapOf<String, Character>()
    val items = mutableMapOf<Long, Item>()
    val currentAuctions = mutableMapOf<Long, Auction>()

    fun apply(snapshot: Snapshot) {
        val openList = HashSet(currentAuctions.keys)

        snapshot.auctions.forEach {
            val seller = findCharacter(it.characterName())
            val item = findItem(it.item)
            var auction = currentAuctions[it.auc]
            if (auction == null) {
                auction = Auction(it.auc, seller, it.timeLeft, item, it.bid, it.buyout, it.quantity)
                currentAuctions[it.auc] = auction
                auction.start(tick)
            } else {
                auction.update(tick, it.timeLeft, it.bid)
                openList.remove(it.auc)
            }
        }

        val ended = openList.map {
            val auction = currentAuctions.remove(it)!!
            auction.end(tick)
            auction
        }

        items.values.forEach {
            it.updateMarketPrice()
        }

        val sold = ended.filter { it.result == AuctionResult.SOLD }
        val undef = ended.filter { it.result == AuctionResult.UNDEFINED }
        val bid = ended.filter { it.result == AuctionResult.BID }
        val expired = ended.filter { it.result == AuctionResult.EXPIRED }
        val canceled = ended.filter { it.result == AuctionResult.CANCELED }
        println(" missing ${ended.size} sold ${sold.size} bid ${bid.size} expired ${expired.size} canceled ${canceled.size} undefined ${undef.size}")

//        (sold+bid).groupBy { it.item }.keys.sortedBy { -it.marketPrice }.forEach {
//                println("$it sold for $")
//        }
        tick++
    }

    fun findItem(id: Long): Item {
        var item = items[id]
        if (item == null) {
            item = Item(id)
            items[id] = item
        }
        return item

    }

    fun findCharacter(name: String): Character {
        var char = characters[name]
        if (char == null) {
            char = Character(name)
            characters[name] = char
        }
        return char
    }
}

fun main(args: Array<String>) {
    val scanner = SnapshotScanner()
//    val ah = AH()
//    scanner.scan("ah.tar.xz", {
//        ah.apply(it)
//    })

//    println(ah.characters.size)
    val api = WowApi(args[0])
    println(api.item(25).name)
//    ah.items.values.sortedBy { -it.totalSold }.forEach {
//        println("${api.item(it.id).name} made ${it.totalSold}")
//    }
}