package wow

import com.google.gson.Gson
import java.io.File
import java.util.*

/**
 * Created by synopia on 30.07.2016.
 */
/**
characters.csv
charId:ID(Character); name

items.csv
itemId:ID(Item); name

tick.csv
tickId:ID(Tick)

auction_header.csv
auctionId:ID(Auction); itemId:IGNORE; charId:IGNORE; quantity

auction_item.csv
:END_ID(Auction); :START_ID(Item); charId:IGNORE; quantity:IGNORE
auction_char.csv
:END_ID(Auction); itemId:IGNORE; :START_ID(Character); quantity:IGNORE

auction_tick.csv
:END_ID(Tick); :START_ID(Auction); buyout; bid; timeLeft

auctions.csv
auc;item;char;quantity

data.csv
tick;auc;buyout;bid;timeLeft

--into default.db --id-type string --nodes:Item items.csv --nodes:Character chars.csv --nodes:Auction auction_header.csv,auctions.csv --nodes:Tick ticks.csv --relationships:FOR_ITEM auction_item.csv,auctions.csv --relationships:FOR_CHAR auction_char.csv,auctions.csv --relationships:SEEN auction_tick.csv,auction_changes.csv --delimiter ';' --array-delimiter ','

 */


object Main2 {
    @JvmStatic
    fun main(args: Array<String>) {
        val characterFile = File("chars.csv").writer()
        val itemFile = File("items.csv").writer()
        val tickFile = File("ticks.csv").writer()
        val auctionsFile = File("auctions.csv").writer()
        val auctionChangesFile = File("auction_changes.csv").writer()
        val characters = mutableMapOf<String, Int>()
        val items = mutableSetOf<Long>()
        val auctions = mutableSetOf<Long>()
        val currentAuctions = mutableSetOf<Long>()
        val auctionTimeLeft = mutableMapOf<Long, TimeLeft>()
        val auctionBid = mutableMapOf<Long, Long>()

        characterFile.appendln("charId:ID(Character);name:STRING")
        itemFile.appendln("itemId:ID(Item);name:STRING")
        tickFile.appendln("tickId:ID(Tick)")

        val scanner = SnapshotScanner()
        val gson = Gson()
        val api = WowApi("...")
        if (true) {
            scanner.scan("snapshots/", { snapshot ->
                tickFile.appendln("${snapshot.tick}")
                val lastAuctions = HashSet(currentAuctions)
                currentAuctions.clear()
                snapshot.auctions.forEach { auction ->
                    val charName = auction.ownerRealm + "#" + auction.owner
                    if (!characters.contains(charName)) {
                        val id = characters.size + 1
                        characters[charName] = id
                        characterFile.appendln("$id;$charName")
                    }
                    if (!items.contains(auction.item)) {
                        items += auction.item
                        val name = api.item(auction.item).name?.replace("\"", "\'")?.replace(",", "_")?.replace(";", "_")
                        itemFile.appendln("${auction.item};$name")
                    }
                    if (!auctions.contains(auction.auc)) {
                        auctions += auction.auc
                        auctionsFile.appendln("${auction.auc};${auction.item};${characters[auction.ownerRealm + "#" + auction.owner]};${auction.quantity};${auction.buyout};${auction.bid};${auction.timeLeft.maxTicks}")
                        auctionTimeLeft[auction.auc] = auction.timeLeft
                        auctionBid[auction.auc] = auction.bid
                    }
                    if (auctionTimeLeft[auction.auc] != auction.timeLeft || auctionBid[auction.auc] != auction.bid) {
                        auctionChangesFile.appendln("${snapshot.tick};${auction.auc};${auction.bid};${auction.timeLeft.maxTicks}")
                        auctionTimeLeft[auction.auc] = auction.timeLeft
                        auctionBid[auction.auc] = auction.bid
                    }
                    lastAuctions -= auction.auc
                    currentAuctions += auction.auc
                }
                lastAuctions.forEach { auction ->
                    auctionTimeLeft.remove(auction)
                    auctionBid.remove(auction)
                }
            })
        }

        characterFile.close()
        itemFile.close()
        tickFile.close()
        auctionsFile.close()
        auctionChangesFile.close()
    }
}
