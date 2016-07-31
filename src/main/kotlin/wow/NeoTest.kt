package wow

import com.google.gson.Gson
import org.neo4j.driver.v1.GraphDatabase
import org.neo4j.driver.v1.Session
import org.neo4j.driver.v1.Transaction
import org.neo4j.driver.v1.Values
import org.neo4j.driver.v1.types.Node
import java.io.File

/**
 * Created by synopia on 29.07.2016.
 */

class DB(val session: Session) {
    var tx: Transaction? = null

    fun findTick(tick: Int): Node {
        val record = tx!!.run("MERGE (tick:Tick{tick:{tick}}) RETURN tick", Values.parameters("tick", tick)).single()
        return record.get("tick").asNode()

    }

    fun findCharacter(realm: String, charName: String): Node {
        val name = "$realm#$charName"
        val record = tx!!.run("MERGE (char:Character{name:{name}}) RETURN char", Values.parameters("name", name)).single()
        return record.get("char").asNode()
    }

    fun findAuction(auctionId: Long, quantity: Int): Node {
        val id = "auc_$auctionId"
        val record = tx!!.run("MERGE (auction:Auction{id:{id}}) SET auction.quantity={quantity} RETURN auction", Values.parameters("id", id, "quantity", quantity)).single()
        return record.get("auction").asNode()
    }

    fun findItem(itemId: Long): Node {
        val id = "item_$itemId"
        val record = tx!!.run("MERGE (item:Item{id:{id}}) RETURN item", Values.parameters("id", id)).single()
        return record.get("item").asNode()
    }

    fun assignChar(auction: Node, char: Node) {
        val aid = auction.id()
        val cid = char.id()
        tx!!.run("MATCH (auction:Auction) WHERE id(auction)={aid} MATCH (char:Character) WHERE id(char)={cid} MERGE (auction)-[:OWNER]->(char)", Values.parameters("aid", aid, "cid", cid))
    }

    fun assignItem(auction: Node, item: Node) {
        val aid = auction.id()
        val cid = item.id()
        tx!!.run("MATCH (auction:Auction) WHERE id(auction)={aid} MATCH (item:Item) WHERE id(item)={cid} MERGE (auction)-[:OWNER]->(item)", Values.parameters("aid", aid, "cid", cid))
    }

    fun assignTick(auction: Node, tick: Node, buyout: Long, bid: Long, timeLeft: TimeLeft) {
        val aid = auction.id()
        val cid = tick.id()
        tx!!.run("MATCH (auction:Auction) WHERE id(auction)={aid} MATCH (tick:Tick) WHERE id(tick)={cid} MERGE (tick)-[:LISTED{buyout:{buyout}, bid:{bid}, timeLeft:{timeLeft}}]->(auction)", Values.parameters("aid", aid, "cid", cid, "buyout", buyout, "bid", bid, "timeLeft", timeLeft.maxTicks))
    }

    fun beginTransaction() {
        tx = session.beginTransaction()
    }

    fun commitTransaction() {
        tx!!.success()
        tx!!.close()
    }
}

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val scanner = SnapshotScanner()
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
            val driver = GraphDatabase.driver("bolt://localhost")
            val session = driver.session()
            val db = DB(session)
            db.beginTransaction()
            db.tx!!.run("MATCH (t:Tick)-[:LISTED]->(a:Auction) WHERE t.tickId=\"4837\" RETURN a").forEach {
                println(it)
            }
            db.commitTransaction()
/*
            db.beginTransaction()
            db.tx!!.run("CREATE INDEX ON :Character(name)")
            db.tx!!.run("CREATE INDEX ON :Item(id)")
            db.tx!!.run("CREATE INDEX ON :Auction(id)")
            db.tx!!.run("CREATE INDEX ON :Tick(tick)")
            db.commitTransaction()
            scanner.scan("snapshots/", { snapshot->
                println(snapshot.tick)
                db.beginTransaction()
                val tick = db.findTick(snapshot.tick)
                var count = 0
                snapshot.auctions.forEach {
                    count ++
                    if( count%10000==0 ) {
                        db.commitTransaction()
                        db.beginTransaction()
                        println(100*count/snapshot.auctions.size)
                    }
                    val character = db.findCharacter(it.ownerRealm, it.owner)
                    val auction = db.findAuction(it.auc, it.quantity)
                    val item = db.findItem(it.item)
                    db.assignItem(auction, item)
                    db.assignChar(auction, character)
                    db.assignTick(auction, tick, it.buyout, it.bid, it.timeLeft)
                }
                db.commitTransaction()
            })
            session.close()
*/
        }
    }
}