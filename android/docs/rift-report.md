The engine's own numbers, as the build drew them (307 x 682, the frame a 20:9 phone gets):

```
rift twin-rivers-8 307x682 detail 0.80 grown in 123 ms, frame 1.2 ms -> /home/runner/work/pixel-live-wallpaper/pixel-live-wallpaper/android/rift-day.png
rift twin-rivers-8 307x682 detail 0.80 grown in 91 ms, frame 4.0 ms -> /home/runner/work/pixel-live-wallpaper/pixel-live-wallpaper/android/rift-night.png
rift twin-rivers-8 307x682 detail 0.80 grown in 44 ms, frame 3.8 ms -> /home/runner/work/pixel-live-wallpaper/pixel-live-wallpaper/android/rift-baked-day.png
rift twin-rivers-8 307x682 detail 0.80 grown in 40 ms, frame 5.9 ms -> /home/runner/work/pixel-live-wallpaper/pixel-live-wallpaper/android/rift-baked-night.png
```

If the baked run complained, here is what it said:

```
> Task :core:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :core:compileKotlin UP-TO-DATE
> Task :core:compileJava NO-SOURCE
> Task :core:processResources NO-SOURCE
> Task :core:classes UP-TO-DATE

> Task :core:preview
rift twin-rivers-8 307x682 detail 0.80 grown in 44 ms, frame 3.8 ms -> /home/runner/work/pixel-live-wallpaper/pixel-live-wallpaper/android/rift-baked-day.png
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/__run_4-1792837067847.json

BUILD SUCCESSFUL in 966ms
2 actionable tasks: 1 executed, 1 up-to-date
> Task :core:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :core:compileKotlin UP-TO-DATE
> Task :core:compileJava NO-SOURCE
> Task :core:processResources NO-SOURCE
> Task :core:classes UP-TO-DATE

> Task :core:preview
rift twin-rivers-8 307x682 detail 0.80 grown in 40 ms, frame 5.9 ms -> /home/runner/work/pixel-live-wallpaper/pixel-live-wallpaper/android/rift-baked-night.png
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/__run_4-1792837068870.json

BUILD SUCCESSFUL in 1s
2 actionable tasks: 1 executed, 1 up-to-date
```
