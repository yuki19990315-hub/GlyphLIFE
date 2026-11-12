package dev.glyphlife.monsterquest.data

import java.util.UUID

/** Safe metadata-only view over immutable, locally saved 64px armor PNGs. */
data class ArmorAssetFile(val filename: String, val bytes: Long, val modifiedAt: Long)

object ArmorAssetCatalog {
    private const val MAX_IMAGE_BYTES = 3_500_000L
    private const val MIN_IMAGE_BYTES = 100L

    /**
     * Only canonical UUID.png filenames created by the app are discoverable.
     * Pending, corrupted-size, hidden and arbitrary path-like files are ignored.
     * Sort newest first with a deterministic filename tie-breaker.
     */
    fun ids(files: List<ArmorAssetFile>, limit: Int = 500): List<String> {
        require(limit in 1..500)
        return files.asSequence()
            .filter { it.bytes in MIN_IMAGE_BYTES..MAX_IMAGE_BYTES }
            .mapNotNull { file ->
                if (!file.filename.endsWith(".png")) return@mapNotNull null
                val id = file.filename.removeSuffix(".png")
                val canonical = runCatching { UUID.fromString(id).toString() }.getOrNull()
                if (canonical != id) null else Triple(id, file.modifiedAt, file.filename)
            }
            .sortedWith(compareByDescending<Triple<String, Long, String>> { it.second }
                .thenBy { it.third })
            .distinctBy { it.first }
            .take(limit)
            .map { it.first }
            .toList()
    }
}
