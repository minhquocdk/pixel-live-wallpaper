// The modules of the build: the island engine (plain Kotlin, no Android, so it can be run and tested on a laptop) and
// the live wallpaper app that draws it.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "island-live-wallpaper"
include(":core")
include(":app")
