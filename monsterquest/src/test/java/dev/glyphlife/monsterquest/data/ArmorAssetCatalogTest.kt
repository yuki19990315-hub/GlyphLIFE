package dev.glyphlife.monsterquest.data

import org.junit.Assert.*
import org.junit.Test

class ArmorAssetCatalogTest {
    private val a = "10000000-0000-4000-8000-000000000001"
    private val b = "10000000-0000-4000-8000-000000000002"
    private fun asset(id: String, time: Long = 100L, bytes: Long = 234L) =
        ArmorAssetFile("$id.png", bytes, time)

    @Test fun filtersInvalidNamesPendingFilesAndBrokenSizes() {
        val files = listOf(
            asset(a, 10),
            asset(b, 20),
            ArmorAssetFile("untrusted.png", 200, 40),
            ArmorAssetFile("../secret.png", 200, 100),
            ArmorAssetFile("$a.pending", 200, 100),
            ArmorAssetFile("$a.PNG", 200, 100),
            ArmorAssetFile("$a.png", 5, 100),
            ArmorAssetFile("$a.png", 3_500_001, 100),
            asset(a.uppercase(), 90)
        )
        assertEquals(listOf(b, a), ArmorAssetCatalog.ids(files))
    }

    @Test fun newestFirstWithStableTieBreakAndNoDuplicates() {
        val input = listOf(asset(a, 1), asset(b, 3), asset(a, 1), asset(a, 3))
        assertEquals(listOf(a, b), ArmorAssetCatalog.ids(input))
        assertEquals(listOf(a), ArmorAssetCatalog.ids(input, limit = 1))
    }

    @Test fun catalogIsBoundedButPagesCanReachOlderRecords() {
        val entries = (1..36).map { number ->
            val id = "10000000-0000-4000-8000-" + number.toString().padStart(12, '0')
            asset(id, number.toLong())
        }
        val ids = ArmorAssetCatalog.ids(entries)
        assertEquals(36, ids.size)
        assertEquals(entries.last().filename.removeSuffix(".png"), ids.first())
        assertEquals(entries.first().filename.removeSuffix(".png"), ids.last())
        assertEquals(8, ids.drop(8).take(8).size)
    }

    @Test fun invalidCatalogLimitIsRejected() {
        for (n in listOf(0, -1, 501)) {
            try {
                ArmorAssetCatalog.ids(emptyList(), n)
                fail("Bad limit accepted: $n")
            } catch (_: IllegalArgumentException) {}
        }
    }
}
