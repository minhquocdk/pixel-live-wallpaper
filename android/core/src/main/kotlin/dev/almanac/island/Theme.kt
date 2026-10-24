package dev.almanac.island

/**
 * Which skin the island is drawn with.
 * ORIGINAL = the almanac's own colours (beiges, greens, browns).
 * MAPLE    = MapleStory-inspired: mushroom houses, maple autumn leaves, slimes.
 */
enum class Theme(val id: String) {
    ORIGINAL("original"),
    MAPLE("maple");

    companion object {
        fun fromId(id: String?): Theme =
            values().find { it.id == id } ?: ORIGINAL
    }
}
