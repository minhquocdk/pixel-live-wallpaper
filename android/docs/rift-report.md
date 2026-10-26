The engine's own numbers, as the build drew them (307 x 682, the frame a 20:9 phone gets):

```
rift twin-rivers-8 307x682 detail 0.80 grown in 92 ms, frame median 1.5 ms (min 0.4, max 47.4, 20 frames) -> /home/runner/work/pixel-live-wallpaper/pixel-live-wallpaper/android/rift-day.png
rift twin-rivers-8 307x682 detail 0.80 grown in 86 ms, frame median 1.9 ms (min 1.4, max 61.0, 20 frames) -> /home/runner/work/pixel-live-wallpaper/pixel-live-wallpaper/android/rift-night.png
rift twin-rivers-8 307x682 detail 0.80 grown in 60 ms, frame median 1.4 ms (min 1.1, max 59.6, 20 frames) -> /home/runner/work/pixel-live-wallpaper/pixel-live-wallpaper/android/rift-baked-day.png
rift twin-rivers-8 307x682 detail 0.80 grown in 62 ms, frame median 1.8 ms (min 1.7, max 70.6, 20 frames) -> /home/runner/work/pixel-live-wallpaper/pixel-live-wallpaper/android/rift-baked-night.png
```

If the baked run complained, here is what it said:

```
> Task :core:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :core:compileKotlin UP-TO-DATE
> Task :core:compileJava NO-SOURCE
> Task :core:processResources NO-SOURCE
> Task :core:classes UP-TO-DATE

> Task :core:preview
rift twin-rivers-8 307x682 detail 0.80 grown in 60 ms, frame median 1.4 ms (min 1.1, max 59.6, 20 frames) -> /home/runner/work/pixel-live-wallpaper/pixel-live-wallpaper/android/rift-baked-day.png
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/__run_4-1792986296538.json

BUILD SUCCESSFUL in 1s
2 actionable tasks: 1 executed, 1 up-to-date
> Task :core:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :core:compileKotlin UP-TO-DATE
> Task :core:compileJava NO-SOURCE
> Task :core:processResources NO-SOURCE
> Task :core:classes UP-TO-DATE

> Task :core:preview
rift twin-rivers-8 307x682 detail 0.80 grown in 62 ms, frame median 1.8 ms (min 1.7, max 70.6, 20 frames) -> /home/runner/work/pixel-live-wallpaper/pixel-live-wallpaper/android/rift-baked-night.png
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/__run_4-1792986297615.json

BUILD SUCCESSFUL in 988ms
2 actionable tasks: 1 executed, 1 up-to-date
```
