# Island, alive

A painted island, brought to life in the browser with nothing but the almanac: the river ripples and glints, the falls
either side of the two shrines pour, a breeze runs through the grass, thirty-three lamps (crystals and braziers) glow with beats of their own,
and the hour turns from noon through dusk to night, while cloud shadows drift over the land, rain falls, and lightning
now and then lights the whole island.

This is a preview for a live wallpaper. The art is a picture (`art/island.png`, 841 × 1870, a phone's 9:20); the engine
paints **over** it, never instead of it. That is what makes it a wallpaper rather than an animation: the island stays the
island, and only what moves costs anything.

```sh
python3 -m http.server 8000          # then open http://localhost:8000/examples/island/live/
```

The page wants to be served over http (a browser will not load ES modules from a file). It shows the live island in a
phone's frame, with the hour, the weather and how much of each effect to show, a button that lays a **lens** over the
picture showing exactly which pixels move, and a meter reading how long a frame takes here. `?tod=0.62&weather=storm&t=12`
in the address sets the look at once.

In Node, the same frames can be looked at and timed without a browser:

```sh
node examples/island/live/film.mjs                          # one frame of noon, live-0.25-clear.png beside the script
node examples/island/live/film.mjs --tod 0.62 --t 12        # the same island at night
node examples/island/live/film.mjs --weather storm --sheet sheet.png   # six looks on one sheet, each timed
```

## How it is done

| file             | what it does                                                                                                                                                               |
| ---------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `art/island.png` | the painting: the island, its shrines, river, bridge, roads, forests and crystals                                                                                          |
| `analyse.js`     | reads the painting and finds what can move in it: the water, which of that water is falling, the foam at its edges, the land, the grass, and every crystal that could glow |
| `live.js`        | the engine: the world built from what was found (`buildWorld`), and `paint(world, t, state)` for each frame                                                                |
| `decode.js`      | the art read in a browser (an image and a canvas, so the page carries no decoder)                                                                                          |
| `png.mjs`        | the art read and the stills written in Node, with nothing but `node:zlib`                                                                                                  |
| `art.js`         | where the art is, and the four braziers colour alone cannot find                                                                                                           |
| `index.html`     | the preview: a phone's frame, the hour, the weather, the lens, the meter                                                                                                   |
| `decor.js`       | trang trí MOBA — lính đi lane, quái rừng, cua sông, cờ phấp phới, cổng tím & hố dung nham (lấy cảm hứng từ bản đồ MOBA mẫu)                                               |
| `film.mjs`       | stills and a contact sheet in Node, each timed                                                                                                                             |

### Trang trí MOBA (mới)

Lấy cảm hứng từ bản đồ MOBA mẫu, `decor.js` thêm lớp trang trí vẽ đè lên đảo:

- **Lính (minions)** — 3 lane (mid/left/right), mỗi lane 5 lính đỏ đi xuống & 5 lính xanh đi lên, nhịp bước theo `t` và gió;
- **Quái rừng** — 4 bãi cạnh rừng: Golem đá (top-left), Cua đỏ (top-right), Sói xám (bottom-left), Nhện tím (bottom-right), mỗi bãi có vòng đá & lửa trại thở;
- **Cua sông** — 3 cua bơi qua lại trên sông, càng vẫy theo `t`;
- **Cổng tím & hố dung nham** — portal xoáy ở phía bắc sông, lava sôi ở phía nam sông, có lửa & khói bay lên;
- **Cờ** — 8 lá cờ (đỏ trên, xanh dưới) phấp phới theo gió `sin(t)`, có emblem lửa/nước;
- **Hào quang trụ** — vòng sáng nhấp nháy quanh trụ khi đêm xuống.

Tất cả là pixel vẽ trực tiếp lên `frame` (không cần sprite sheet), tôn trọng dirty-optimisation: `decorDirty` được gộp vào `world.dirty` nên mỗi frame chỉ tốn thêm ~35k pixels (~2%) — tổng ~190k/1.57M (~12%) vẫn mượt 30fps trên điện thoại. Tắt/mở từng loại trong panel **Trang trí · MOBA**; `state({decor:{minions:false}})` cũng tắt được qua code/URL `?t=`.

### What is drawn, and how much of it

A frame is the **still picture**, graded for the hour, and then the pixels that move written over it:

```text
still picture, graded once each time the light turns        (hourTables + gradeFrame, a whole picture)
  a frame = that picture, patched only where the last frame painted
  ├ water     98,903 px   two tiles of wrapping noise, drawn out sideways: swells, ripples, glints on the crests
  ├ foam      41,471 px   the lapping edge, breathing
  ├ falls     14,488 px   the water scrolled a few rows round and round its own run, so it pours
  ├ grass     63,000 px   only the pale tips: the breeze reads as light running over the grass, not as noise
  ├ lamps     33 of them  each crystal's halo, worked out once, added in dithered steps as it pulses
  ├ clouds                 cloudShadows, laid down once a second (a drifting shadow is slow)
  └ rain, lightning        streaks on their own beats; lightningFlash when the storm strikes
```

155,000 of the picture's 1,572,670 pixels are painted in a frame — a tenth of it. On this machine that is about **3.5–4 ms
a frame** for 841 × 1870 in Node, and a frame in ten seconds (the light turning, or the clouds moving a step) pays for the
whole picture on top of that (`film.mjs --sheet` prints the timings; the page shows its own per paint and per blit while
it runs).

The water is worth a word, because it was the hard part: two tiles of wrapping noise, the slow one shading whole reaches
a step darker or lighter, the quick one drawing thin crest lines where it crosses its own middle value. Crest lines made
of noise wander and break as the noise drifts, and the art's own blue never leaves the water, where dithering or a
dithered threshold on this scale reads as a screen or as stripes.

### The hour

The almanac's `hourLight` and `hourTables` grade the picture: night sinks to a deep blue, dusk lifts the shadows into
violet, and at night the crystals' light holds the dark off (the lamps are laid on after the grading, as `gradeFrame`
does with its own lights). Because grading a whole picture costs a whole picture, the light is keyed to a sixteenth of the
way from day to night; the key changes a few times a minute, not every frame.

### Reading the art, not hard-coding it

Nothing about this particular island is written into the engine. `analyse.js` finds it:

- **water**: blue, bright, and blue over red (`b > 120 && b > r + 25 && g > 60 && b >= g - 15`) — the sky the island
  floats on is blue too, but dark;
- **falling water**: water long down and short across (a run-length pass), which the river never is;
- **foam**: water touching land, and the ring of water just behind it;
- **grass**: green over both its neighbours, and the pale tips of it catching the light;
- **crystals**: small diamonds of one strong colour filling about half their box (a banner on a wall fills most of its
  own), found by connected-component labelling, then kept one per lamp where a bright core splits one crystal in two.

So another painted map works too: drop it in, point `ART` at it, and adjust `torches` in `art.js` for lamps that are not
crystals. A map wants water bright enough to pass the test, crystals of one strong colour, and land lit enough not to be
the sky. `analyse` prints what it found (`stats`, `lights`) — the numbers should look like the map.

## Where a real live wallpaper goes next

This is the preview of a wallpaper, not yet the wallpaper. What it shows is the shape of one:

1. **The engine is portable.** `analyse.js` and `live.js` are plain ES: no canvas, no DOM, no Node. The frame is a
   `Uint32Array` of packed pixels, and that is the same memory a `Bitmap` on Android holds (BGRA) — so the Android
   live wallpaper (see [`android/`](../../../android/)) can paint the same frames and upload them once a frame.
2. **The budget is the whole trick.** Grade the picture when the light turns (a second or so of work, off the render
   thread), then patch only the pixels that move. A phone's screen may be 1080 × 2400; this art is 841 × 1870 and scales.
3. **The hour can be the real one**: `tod` from the clock (and sunrise and sunset from the phone's location) instead of a
   slider, so the island is dark when it is dark out. Weather can come from a forecast, the same `weather` field.
4. **Battery.** Thirty frames a second is generous for water; the lens makes it easy to see what costs what, and the rain,
   the clouds and the lamps can each be turned down or off.

## Ghi chú tiếng Việt

Đây là bản xem trước (preview) cho live wallpaper: tranh `art/island.png` là nền, engine chỉ **vẽ đè** lên những chỗ
chuyển động (nước, thác, bọt, ngọn cỏ, ánh sáng pha lê), phần còn lại giữ nguyên. Nhờ vậy mỗi frame chỉ tốn ~1/9 số pixel.

```sh
python3 -m http.server 8000     # mở http://localhost:8000/examples/island/live/
node examples/island/live/film.mjs --tod 0.62 --t 12     # xuất ảnh tĩnh, xem trước khi lên wallpaper
```

Tham số trên địa chỉ để mở nhanh một khung hình: `?tod=0.62&weather=storm&t=12`. Nút **What moves** phủ một lớp lên
tranh để thấy đúng những pixel được vẽ lại mỗi frame. Muốn dùng tranh khác: thay `art/island.png`, sửa `ART` trong
`art.js` (và `torches` cho những ngọn lửa không phải pha lê) — `analyse.js` sẽ tự tìm nước, cỏ và pha lê trong tranh mới.
