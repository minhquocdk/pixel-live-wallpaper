package dev.almanac.island

/**
 * What the wallpaper draws, whichever map it is: the almanac's island, or the Rift's twin rivers. The wallpaper service
 * only ever sees this, so a theme can bring a whole new map without the service knowing what a lane is.
 */
interface LiveScene {
    /** The words the map grew from. */
    val phrase: String

    /** The picture's size, in the almanac's own pixels. */
    val W: Int
    val H: Int

    val theme: Theme
    val seed: Int
    val palette: Palette

    /** The picture, packed as an Android bitmap packs colours (0xAARRGGBB). */
    val frame: IntArray

    /** One line for the status screen: what stands on this map. */
    val summary: String

    /** One frame of time. */
    fun step(dt: Double)

    /** Paints the frame at the scene's time of day. */
    fun render()

    /** What the map was grown from and what grew on it, line by line. */
    fun report(): List<String>
}
