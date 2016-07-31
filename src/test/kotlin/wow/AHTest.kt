package wow

import org.junit.Assert
import org.junit.Test

/**
 * Created by synopia on 29.07.2016.
 */
class AHTest {

    @Test
    fun testBought() {
        val ah = AH()
        ah.apply(Snapshot(listOf(), listOf(), 1))
        ah.apply(Snapshot(listOf(), listOf(SnapshotEntry(1L, 100L, "test", "test", 100L, 100L, 1, TimeLeft.LONG)), 2))
        ah.apply(Snapshot(listOf(), listOf(), 3))
        Assert.assertEquals(1, ah.characters["test#test"]!!.sold.size)
    }

    @Test
    fun testSingleAuctionCancel() {
        val ah = AH()
        ah.apply(Snapshot(listOf(), listOf(SnapshotEntry(2L, 100L, "better", "better", 99L, 99L, 1, TimeLeft.LONG), SnapshotEntry(1L, 100L, "test", "test", 100L, 100L, 1, TimeLeft.LONG)), 1))
        ah.apply(Snapshot(listOf(), listOf(SnapshotEntry(2L, 100L, "better", "better", 99L, 99L, 1, TimeLeft.LONG)), 2))
        Assert.assertEquals(0, ah.characters["test#test"]!!.sold.size)
    }

    @Test
    fun testMassCancel() {
        val ah = AH()
        ah.apply(Snapshot(listOf(), listOf(
                SnapshotEntry(0L, 100L, "x", "x", 100L, 100L, 20, TimeLeft.LONG),
                SnapshotEntry(1L, 100L, "test", "test", 100L, 100L, 20, TimeLeft.LONG),
                SnapshotEntry(2L, 100L, "test", "test", 100L, 100L, 20, TimeLeft.LONG)), 1)
        )
        ah.apply(Snapshot(listOf(), listOf(
                SnapshotEntry(0L, 100L, "x", "x", 100L, 100L, 20, TimeLeft.LONG),
                SnapshotEntry(3L, 100L, "test", "test", 99L, 99L, 20, TimeLeft.LONG),
                SnapshotEntry(4L, 100L, "test", "test", 99L, 99L, 20, TimeLeft.LONG)), 2)
        )
        Assert.assertEquals(0, ah.characters["test#test"]!!.sold.size)
    }

    @Test
    fun testExpired1() {
        val ah = AH()
        ah.apply(Snapshot(listOf(), listOf(), 1))
        ah.apply(Snapshot(listOf(), listOf(SnapshotEntry(1L, 100L, "test", "test", 100L, 100L, 1, TimeLeft.MEDIUM)), 2))
        ah.apply(Snapshot(listOf(), listOf(SnapshotEntry(1L, 100L, "test", "test", 100L, 100L, 1, TimeLeft.MEDIUM)), 3))
        ah.apply(Snapshot(listOf(), listOf(), 4))
        Assert.assertEquals(0, ah.characters["test#test"]!!.sold.size)
    }

    @Test
    fun testExpired2() {
        val ah = AH()
        ah.apply(Snapshot(listOf(), listOf(), 1))
        ah.apply(Snapshot(listOf(), listOf(SnapshotEntry(1L, 100L, "test", "test", 100L, 100L, 1, TimeLeft.MEDIUM)), 2))
        ah.apply(Snapshot(listOf(), listOf(SnapshotEntry(1L, 100L, "test", "test", 100L, 100L, 1, TimeLeft.SHORT)), 3))
        ah.apply(Snapshot(listOf(), listOf(), 4))
        Assert.assertEquals(0, ah.characters["test#test"]!!.sold.size)
    }
}