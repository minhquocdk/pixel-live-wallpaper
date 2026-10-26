package dev.almanac.island

/**
 * Which map the wallpaper draws.
 * ORIGINAL = the almanac's own island: beiges, greens, browns.
 * MAPLE    = MapleStory-inspired: mushroom houses, maple autumn leaves, slimes.
 * RIFT     = the twin rivers: a tall battleground map of lanes, a river and two great pits, drawn by RiftScene.
 */
enum class Theme(val id: String) {
    ORIGINAL("original"),
    MAPLE("maple"),
    RIFT("rift");

    companion object {
        fun fromId(id: String?): Theme =
            values().find { it.id == id } ?: ORIGINAL
    }
}
