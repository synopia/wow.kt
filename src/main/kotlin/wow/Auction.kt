package wow

/**
 * Created by synopia on 19/07/16.
data class Price(val itemId:Long, val tick:Long, val price:Long ) {
}
enum class AuctionState {
UNDEFINED,
STARTED,
RUNNING,
BUYOUT,
EXPIRED
}
class Auction(val id:Long, var timeLeft: TimeLeft, val itemId:Long, val startBid: Long, val buyout: Long, val quantity: Int) {
var ticksLeft = Int.MAX_VALUE
var state = AuctionState.UNDEFINED
var startTick = 0L
var bid = startBid

fun start(tick:Long) {
startTick = tick
ticksLeft = timeLeft.maxTicks
state = AuctionState.STARTED
}

fun updateAuction(tick:Long, timeLeft: TimeLeft, bid:Long) {
state = AuctionState.RUNNING
this.bid = bid
if( timeLeft!=this.timeLeft ) {
ticksLeft = timeLeft.maxTicks
this.timeLeft = timeLeft
}
ticksLeft --
if( ticksLeft==0 ) {
state == AuctionState.EXPIRED
}
}

}

class AuctionHouse {
val open = mutableMapOf<Long, Auction>()
val prices = mutableListOf<Price>()

fun apply(tick:Long, auctions: List<SnapshotEntry>) {
val missing = HashSet<Long>(open.keys)
val first = open.size == 0

auctions.forEach {
var auction = open[it.auc]
if ( auction == null ) {
auction = Auction(it.auc, it.timeLeft, it.item, it.bid, it.buyout, it.quantity)
if( !first ) {
auction.start(tick)
}
open[it.auc] = auction
} else {
auction.updateAuction(tick, it.timeLeft, it.bid)
missing.remove(it.auc)
}
}

missing.filter { open[it]!!.startTick>0 }.forEach {
val auction = open[it]!!
if ( auction.ticksLeft > 0 ) {
prices += Price(auction.itemId, tick, auction.buyout/auction.quantity)
} else {
if( auction.startBid!=auction.bid ) {
prices += Price(auction.itemId, tick, auction.bid / auction.quantity)
}
}
open.remove(it)
}
}
}




fun main(args: Array<String>) {
val ah = AuctionHouse()
val api = WowApi("gjc8xh6b32v5c7gxqappjagq6wqczrdr")

val list = ah.prices.filter { it.itemId==14047L }
println(list.size)
list.forEach {
println(api.item(it.itemId).name)
println(it)
}
println(list.size)
} */
