The engine's own numbers, as the build drew them (307 x 682, the frame a 20:9 phone gets):

```
rift twin-rivers-8 307x682 detail 0.80 grown in 147 ms, frame median 1.8 ms (min 0.6, max 68.7, 20 frames) -> /home/runner/work/pixel-live-wallpaper/pixel-live-wallpaper/android/rift-day.png
rift twin-rivers-8 307x682 detail 0.80 grown in 147 ms, frame median 3.0 ms (min 1.4, max 95.7, 20 frames) -> /home/runner/work/pixel-live-wallpaper/pixel-live-wallpaper/android/rift-night.png
rift twin-rivers-8 307x682 detail 0.80 grown in 102 ms, frame median 2.0 ms (min 1.7, max 91.7, 20 frames) -> /home/runner/work/pixel-live-wallpaper/pixel-live-wallpaper/android/rift-baked-day.png
rift twin-rivers-8 307x682 detail 0.80 grown in 95 ms, frame median 4.3 ms (min 2.4, max 117.2, 20 frames) -> /home/runner/work/pixel-live-wallpaper/pixel-live-wallpaper/android/rift-baked-night.png
```

If the baked run complained, here is what it said:

```
> Task :core:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :core:compileKotlin UP-TO-DATE
> Task :core:compileJava NO-SOURCE
> Task :core:processResources NO-SOURCE
> Task :core:classes UP-TO-DATE

> Task :core:preview
rift twin-rivers-8 307x682 detail 0.80 grown in 102 ms, frame median 2.0 ms (min 1.7, max 91.7, 20 frames) -> /home/runner/work/pixel-live-wallpaper/pixel-live-wallpaper/android/rift-baked-day.png
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/__run_4-1792986420098.json

BUILD SUCCESSFUL in 1s
2 actionable tasks: 1 executed, 1 up-to-date
> Task :core:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :core:compileKotlin UP-TO-DATE
> Task :core:compileJava NO-SOURCE
> Task :core:processResources NO-SOURCE
> Task :core:classes UP-TO-DATE

> Task :core:preview
rift twin-rivers-8 307x682 detail 0.80 grown in 95 ms, frame median 4.3 ms (min 2.4, max 117.2, 20 frames) -> /home/runner/work/pixel-live-wallpaper/pixel-live-wallpaper/android/rift-baked-night.png
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/__run_4-1792986421814.json

BUILD SUCCESSFUL in 1s
2 actionable tasks: 1 executed, 1 up-to-date
```
