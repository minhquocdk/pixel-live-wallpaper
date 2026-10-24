// Seeds you can read: the words the almanac's island page offers, so a seed can be said, remembered and typed.
package dev.almanac.island.wallpaper

import dev.almanac.island.seedWords

object SeedWords {
    val FIRST = listOf(
        "amber", "ash", "briar", "cedar", "cloud", "coral", "dawn", "drift", "ember", "fern", "frost", "gale",
        "hazel", "ivory", "jade", "juniper", "kelp", "linen", "maple", "misty", "moss", "opal", "pearl", "pine",
        "quiet", "rain", "rose", "rust", "salt", "sage", "slate", "tidal",
    )
    val SECOND = listOf(
        "bay", "bank", "beach", "bluff", "brook", "cape", "cliff", "cove", "creek", "dune", "fen", "glen",
        "harbour", "haven", "heron", "isle", "key", "lagoon", "marsh", "mere", "point", "reef", "rock", "sands",
        "shoal", "sound", "spit", "strand", "tarn", "vale", "wharf", "wood",
    )

    /** A new seed: a word from each list and a number from 1 to 99 (amber-vale-7). */
    fun random(): String = seedWords(FIRST, SECOND)
}
