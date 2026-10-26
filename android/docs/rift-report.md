The engine's own numbers, as the build drew them (307 x 682, the frame a 20:9 phone gets):

```
rift twin-rivers-8 307x682 detail 0.80 grown in 86 ms, frame median 1.3 ms (min 0.3, max 42.3, 20 frames) -> /home/runner/work/pixel-live-wallpaper/pixel-live-wallpaper/android/rift-day.png
rift twin-rivers-8 307x682 detail 0.80 grown in 87 ms, frame median 1.9 ms (min 0.9, max 59.4, 20 frames) -> /home/runner/work/pixel-live-wallpaper/pixel-live-wallpaper/android/rift-night.png
rift twin-rivers-8 307x682 detail 0.80 grown in 58 ms, frame median 1.3 ms (min 1.0, max 53.5, 20 frames) -> /home/runner/work/pixel-live-wallpaper/pixel-live-wallpaper/android/rift-baked-day.png
rift twin-rivers-8 307x682 detail 0.80 grown in 60 ms, frame median 1.8 ms (min 1.7, max 70.6, 20 frames) -> /home/runner/work/pixel-live-wallpaper/pixel-live-wallpaper/android/rift-baked-night.png
```

If the baked run complained, here is what it said:

```
> Task :core:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :core:compileKotlin UP-TO-DATE
> Task :core:compileJava NO-SOURCE
> Task :core:processResources NO-SOURCE
> Task :core:classes UP-TO-DATE

> Task :core:preview
rift twin-rivers-8 307x682 detail 0.80 grown in 58 ms, frame median 1.3 ms (min 1.0, max 53.5, 20 frames) -> /home/runner/work/pixel-live-wallpaper/pixel-live-wallpaper/android/rift-baked-day.png
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/__run_4-1792986193070.json

BUILD SUCCESSFUL in 890ms
2 actionable tasks: 1 executed, 1 up-to-date
> Task :core:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :core:compileKotlin UP-TO-DATE
> Task :core:compileJava NO-SOURCE
> Task :core:processResources NO-SOURCE
> Task :core:classes UP-TO-DATE

> Task :core:preview
rift twin-rivers-8 307x682 detail 0.80 grown in 60 ms, frame median 1.8 ms (min 1.7, max 70.6, 20 frames) -> /home/runner/work/pixel-live-wallpaper/pixel-live-wallpaper/android/rift-baked-night.png
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/__run_4-1792986194057.json

BUILD SUCCESSFUL in 934ms
2 actionable tasks: 1 executed, 1 up-to-date
```
