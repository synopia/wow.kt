package wow

import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import java.io.File
import java.util.*

/**
 * Created by synopia on 19/07/16.
 */
data class Batch(val item: Item, var quantity: Int, var cheapestBuyout: Long)

data class Character(val id: Int, val name: String) {
    val auctions = mutableSetOf<Auction>()
    val sold = mutableSetOf<Auction>()
    val batches = mutableMapOf<Item, Batch>()

    fun updateBatches() {
        val openList = HashSet(batches.keys)
        auctions.groupBy { it.item }.entries.forEach { entry ->
            var quantity = 0
            var cheapest = Long.MAX_VALUE
            entry.value.forEach {
                quantity += it.quantity
                if (it.buyout / it.quantity < cheapest) {
                    cheapest = it.buyout
                }
            }
            openList.remove(entry.key)
            val batch = Batch(entry.key, quantity, cheapest)
            batches[entry.key] = batch
            entry.value.forEach { it.batch = batch }
        }
        openList.forEach {
            batches.remove(it)
        }
    }
}

data class Item(val id: Long, var marketPrice: Long = 0L) {
    val auctions = mutableSetOf<Auction>()
    val sold = mutableSetOf<Auction>()
}

enum class AuctionResult {
    UNDEFINED,
    SOLD,
    BID,
    CANCELED,
    EXPIRED
}

class Auction(val id: Long, val startTick: Int, val seller: Character, var timeLeft: TimeLeft, val item: Item, val startBid: Long, val buyout: Long, val quantity: Int) {
    var lastChange = 0
    var tickEnded = 0
    var result = AuctionResult.UNDEFINED
    var lastBid = startBid
    var batch: Batch? = null

    init {
        seller.auctions += this
        item.auctions += this
        lastChange = startTick
    }

    fun update(tick: Int, timeLeft: TimeLeft, bid: Long) {
        if (timeLeft != this.timeLeft) {
            this.timeLeft = timeLeft
            lastChange = tick
        }
        lastBid = bid
    }

    fun end(tick: Int) {
        tickEnded = tick
        seller.auctions.remove(this)
        item.auctions.remove(this)

        val ticksSeen = tick - lastChange
        print(" ended: changed to $timeLeft at $lastChange ")
        if (ticksSeen < timeLeft.maxTicks) {
            println(".. sold/canceled")
            result = AuctionResult.SOLD
/*
            val cheaperStack = item.auctions.filter { it.quantity==quantity && it.startTick<tick }.find { it.buyout < buyout }
            if (cheaperStack != null) {
                result = AuctionResult.CANCELED
            } else {

                result = AuctionResult.SOLD
                seller.sold += this
                item.sold += this
            }
*/
        } else {
            println(".. expired")
            result = AuctionResult.EXPIRED
        }
    }
}

class AH {
    var lastTick = 0
    val characters = mutableMapOf<String, Character>()
    val items = mutableMapOf<Long, Item>()
    val currentAuctions = mutableMapOf<Long, Auction>()

    fun apply(snapshot: Snapshot) {
        if (lastTick > 0 && snapshot.tick - lastTick > 1) {
            println("Snapshots missing (last=$lastTick, current=${snapshot.tick}")
        }
        val openList = HashSet(currentAuctions.keys)
        println("tick ${snapshot.tick}")
        snapshot.auctions.forEach {
            val seller = findCharacter(it.characterName())
            val item = findItem(it.item)
            var auction = currentAuctions[it.auc]
            if (auction == null) {
                auction = Auction(it.auc, snapshot.tick, seller, it.timeLeft, item, it.bid, it.buyout, it.quantity)
                currentAuctions[it.auc] = auction
            } else {
                auction.update(snapshot.tick, it.timeLeft, it.bid)
                openList.remove(it.auc)
            }
        }

        val ended = openList.map {
            val auction = currentAuctions.remove(it)!!
            auction.end(snapshot.tick)
            auction
        }
        characters.values.forEach { char ->
            char.updateBatches()
        }
        ended.filter { it.result == AuctionResult.EXPIRED }.forEach {
            if (it.startBid != it.lastBid) {
                it.result = AuctionResult.BID
                it.seller.sold += it
                it.item.sold += it
            }
        }
        ended.filter { it.result == AuctionResult.SOLD }.filter { auction ->
            val cheaperStack = auction.item.auctions.filter { it.seller != auction.seller && it.quantity == auction.quantity && it.startTick < snapshot.tick }.find { it.buyout < auction.buyout }
            if (cheaperStack != null) {
                auction.result = AuctionResult.CANCELED
                false
            } else {
                true
            }
        }.filter { auction ->
            val currentBatch = auction.seller.batches[auction.item]
            val other = auction.item.auctions.find { it.seller != auction.seller }
            val betterBatch = currentBatch != null && other != null
                    && currentBatch.quantity >= auction.batch!!.quantity
//                    && currentBatch.cheapestBuyout<auction.batch!!.cheapestBuyout
            if (betterBatch) {
                auction.result = AuctionResult.CANCELED
                false
            } else {
                true
            }
        }.forEach {
            it.seller.sold += it
            it.item.sold += it
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
        lastTick = snapshot.tick
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
            char = Character(characters.size + 1, name)
            characters[name] = char
        }
        return char
    }
}

fun main(args: Array<String>) {
    val scanner = SnapshotScanner()
    val ah = AH()
    val gson = Gson()
    if (false) {
        val file = File("karokhrrzuh.json").writer()
        scanner.scan("snapshots/", {
            val filtered = Snapshot(it.realms, it.auctions.filter {
                it.owner == "Karokhrrzuh"
            }, it.tick)
            file.append(gson.toJson(filtered)).appendln()
        })
        file.close()
    } else {
        File("karokhrrzuh.json").readLines().forEach {
            val snapshot = gson.fromJson<Snapshot>(it)
            ah.apply(snapshot)
        }
    }


    println(ah.characters.size)
//    val api = WowApi(args[0])
//    println(api.item(25).name)
//    ah.items.values.sortedBy { -it.totalSold }.forEach {
//        println("${api.item(it.id).name} made ${it.totalSold}")
//    }
}