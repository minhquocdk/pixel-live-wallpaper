# The engine is called from the wallpaper service only, and has no reflection: nothing needs keeping.
# Keep the service and activity names as the manifest spells them (the system looks them up by name).
-keep class dev.almanac.island.wallpaper.IslandWallpaperService { *; }
-keep class dev.almanac.island.wallpaper.MainActivity { *; }
