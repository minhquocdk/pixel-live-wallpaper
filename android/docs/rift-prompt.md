# Prompt cho ChatGPT / DALL·E / GPT-Image: hình nền dọc "Cột Đôi Sông"

Dùng kèm `rift-portrait-spec.md`. Prompt viết bằng tiếng Anh vì model ảnh hiểu tiếng Anh tốt hơn hẳn.
Dán nguyên khối **Prompt chính** là đủ; các mục dưới là để sửa tiếp sau khi có ảnh đầu.

---

## A. Prompt chính (dán nguyên khối này)

```
You are designing an ORIGINAL pixel-art live wallpaper for a modern vertical smartphone
screen, aspect ratio 9:20 (1080 x 2400).

Do not copy any existing game's artwork, logo, map layout, icon, character, item or
trademark. This is an original fantasy map that only borrows the genre's mood. No text, no
UI, no numbers, no watermark, not a screenshot of an existing game.

SUBJECT
A single magical highland battleground seen from a high three-quarter top-down camera (a
classic 16-bit strategy-game view, slightly tilted: we see the tops of towers and the faces
of the cliffs, not a flat bird's-eye view). The highland is one tall rounded-rectangle
plateau floating in dark emptiness, ringed by a one-step stone retaining wall, with faint
fog drifting beyond the edge.

LAYOUT — this is the point of the image, respect it closely
- 180-degree rotational symmetry around the centre of the canvas: one base at the top, a
  mirror-image base at the bottom, and every lane and pit mirrored the same way.
- A straight central road (mid lane) runs vertically down the exact middle from the bottom
  base to the top base. It is the shortest and widest road, and the visual spine of the
  whole picture.
- Two outer roads leave each base, bow outward toward the left and right edges, and rejoin
  the river at mid-height. They are clearly longer and narrower than the central road.
- A river crosses the map horizontally near the vertical centre in a gentle S-curve, from
  the left edge to the right edge, and it is crossed by exactly ONE small wooden bridge
  where it meets the central road.
- A round monster pit on the upper river bank, left of centre, glowing violet.
  A mirrored round pit on the lower river bank, right of centre, glowing ember orange.
- Four dense jungle blocks in the four quadrants. Each is a clumped dark canopy enclosed by
  a low stone wall with a single narrow gate facing a lane. Layered, overlapping trees —
  not a scattering of separate trees on an open lawn.
- Each base, from its outer edge inward: a glowing crystal nexus, a small healing fountain
  behind it, two guard towers, then three inhibitor crystals and three lane towers
  marching away up each road.
- Small blue and red soldier minions, in little groups of three, walking along all three
  roads. Two crabs wander the shallows of the river.
- Packed-dirt roads with darker rims and cart tracks; bushes, loose rocks and trampled
  ground along their edges.

PALETTE — limited, deliberate, 16-bit
grass #6f9b45 #547a37 #7aa64d; dirt #b99a68 with #8f7448 rims; stone #8a8f96 #4a4a52;
water #2f7fa8 to #54c7c0; hextech gold #e8c15a; blue team #3fa9f5; red team #d94a3d;
violet #8a5bd6; ember #e07a3c; void #0e1118 to #1a1e28.
Dithered shading, hand-placed pixels, one gentle film grade: warm highlights, cool
shadows. Light from the upper left.

COMPOSITION
- Keep the top 12% and the bottom 10% of the canvas calm and dark: empty void and very
  faint fog only. A phone's clock and dock live there, so no bright object and no hard edge
  in those two bands.
- The plateau fills the middle ~78% of the frame, with a narrow margin left and right.
- It must read instantly at thumbnail size: a dark frame, a glowing vertical spine, and
  violet plus ember light at the centre.

STYLE
Original 16-bit pixel art, crisp single-pixel detail, no anti-aliasing, no smooth gradients
(use dithering), visible pixel grid, high resolution render of a low-resolution image.
```

## B. Prompt phụ — dùng ngay sau khi có ảnh đầu

| Muốn gì | Dán câu này |
|---|---|
| Bản ban đêm | `Now make a night version: same layout, moonlit cool blue grade, only the nexus, the lane towers, the two pits and the windows of nothing else glowing. Keep the top 12% and bottom 10% calm and dark.` |
| Bản ngang (tablet) | `Now a landscape version at aspect ratio 16:9. Same plateau but square, centred, empty void on the left and right. Rotate the layout 45 degrees so the two bases sit at the lower-left and upper-right corners, mid lane on the diagonal, and the river on the other diagonal.` |
| Đậm chất tranh hơn | `Same layout, but push it toward a hand-painted storybook pixel style: softer canopy shapes, more visible brush dithering, richer colour depth. Keep the pixel grid and the symmetry.` |
| Sạch và tối giản | `Same layout, reduce the detail by half: fewer trees, flatter grass, fewer props. Emphasise silhouette and the glowing spine. It must read from across a room.` |
| Ba phương án | `Give me three different lighting moods of the same layout: dawn, deep night, and light rain with puddles on the roads.` |
| Nếu nó ra ảnh vuông | `The image must be vertical, aspect ratio 9:20. Regenerate at that ratio.` |
| Nếu nó dồn chi tiết kín khung | `Respect the safe bands: the top 12% and bottom 10% must be almost empty dark void. Only the middle 78% carries the map.` |

## C. Negative prompt (nếu ô nhập cho phép)

```
photorealistic, 3D render, isometric low-poly, generic fantasy RPG map, symmetric
wallpaper pattern, text, letters, numbers, UI, HUD, health bars, logo, watermark,
signature, blurry, anti-aliased, gradient sky, flat top-down view, crowded full-bleed
detail, existing game's map, character portraits
```

## D. Bản tiếng Việt (nếu muốn đưa thẳng bằng tiếng Việt)

```
Thiết kế một hình nền động pixel art NGUYÊN BẢN cho màn hình điện thoại dọc, tỉ lệ 9:20
(1080 x 2400). Không sao chép hình ảnh, logo, bản đồ, biểu tượng, nhân vật hay nhãn hiệu
của bất kỳ game nào; đây là bản đồ giả tưởng nguyên bản, chỉ mượn không khí của thể loại.
Không chữ, không giao diện, không số, không watermark, không phải ảnh chụp màn hình game.

Chủ thể: một cao nguyên ma thuật nhìn từ góc ba-phần-tư từ trên xuống (kiểu game chiến
thuật 16-bit, hơi nghiêng để thấy nóc tháp và vách đá), nổi giữa hư không tối, viền một
bậc tường đá, sương mờ trôi bên ngoài.

Bố cục, đối xứng 180 độ quanh tâm ảnh:
- Hai căn cứ ở đỉnh và đáy, là ảnh quay 180 độ của nhau.
- Một con đường giữa thẳng đứng, chạy đúng trục giữa từ căn cứ này tới căn cứ kia, ngắn
  và rộng nhất, là xương sống thị giác của cả bức.
- Hai con đường biên cong ra hai mép trái phải rồi nhập lại ở sông, dài và hẹp hơn rõ rệt.
- Một con sông chạy ngang giữa ảnh theo đường chữ S nhẹ, từ mép trái sang mép phải, và
  chỉ có ĐÚNG MỘT cây cầu gỗ nhỏ ở chỗ sông cắt đường giữa.
- Một hố quái tròn bờ trên bên trái, phát sáng tím; hố đối xứng bờ dưới bên phải, sáng cam.
- Bốn khu rừng dày ở bốn góc phần tư, mỗi khu là một khối tán tối chồng lên nhau, bao
  quanh bằng tường đá thấp, chỉ hở một cửa hẹp quay ra đường.
- Mỗi căn cứ: một nhà chính pha lê phát sáng, suối hồi máu phía sau, hai trụ gác, rồi ba
  ức chế pha lê và ba trụ dọc theo mỗi con đường.
- Lính nhỏ xanh và đỏ, từng nhóm ba con, đi trên cả ba con đường. Hai con cua bò ở chỗ
  nước nông.

Bảng màu: cỏ #6f9b45 #547a37 #7aa64d; đất #b99a68 viền #8f7448; đá #8a8f96 #4a4a52; nước
#2f7fa8 đến #54c7c0; vàng hextech #e8c15a; đội xanh #3fa9f5; đội đỏ #d94a3d; tím #8a5bd6;
cam than #e07a3c; hư không #0e1118 đến #1a1e28. Đánh bóng bằng dither, pixel đặt tay, một
lớp grade nhẹ: sáng ấm, tối lạnh, nắng từ trên-trái.

Bố cục khung: 12% trên và 10% dưới để trống và tối (chỉ hư không và sương rất mờ) vì đồng
hồ và dock của điện thoại nằm ở đó, không vật sáng, không cạnh tương phản trong hai dải
này. Cao nguyên chiếm khoảng 78% giữa khung. Ảnh phải đọc được ngay ở cỡ thumbnail.

Phong cách: pixel art 16-bit nguyên bản, chi tiết từng pixel sắc nét, không khử răng cưa,
không gradient mượt (dùng dither), thấy rõ lưới pixel. Tỉ lệ dọc 9:20.
```

## E. Ba mẹo để lần đầu ra đúng

1. **Luôn ghi tỉ lệ ở cuối prompt.** Model ảnh mặc định về 1:1; thiếu dòng "9:20" là ra ảnh vuông.
2. **Nói rõ "not a screenshot of an existing game".** Nếu không, nó sẽ vẽ lại đúng bản đồ quen thuộc
   (sai bố cục dọc, và vướng bản quyền). Prompt trên đã có sẵn câu đó.
3. **Xin 3 phương án trước, đừng xin 1.** Đưa dòng ở mục B ("Ba phương án"), chọn bố cục đúng nhất,
   rồi mới sửa chi tiết. Sửa trên một ảnh đã đúng khung bao giờ cũng nhanh hơn sinh lại từ đầu.

Ảnh ChatGPT tạo ra là **ý tưởng để duyệt**, không phải asset để nhúng vào app: nó không tôn trọng đúng
toạ độ, ngân sách 8 ms/khung hay vùng an toàn. Khi đã chốt được cảm giác, ta dựng lại bằng
`World.growRift()` theo `rift-portrait-spec.md` — lúc đó seed, độ phân giải và ánh sáng mới thật sự dưới
quyền điều khiển của mình.

## F. Prompt v3 — sau khi duyệt ảnh vòng 2

Ảnh vòng 2 đã đúng tỉ lệ (841 × 1870 = 2.224) và đúng bố cục. Vòng 3 chỉ cần siết hai thứ: **dải an toàn** và
**ngôn ngữ mảng** (ít chi tiết hơn, khối rõ hơn). Dán tiếp câu này vào cuộc hội thoại đang có ảnh:

```
Keep this exact layout, palette and camera. Change only these three things:

1. The top base intrudes into the top 12% of the canvas: its crystal spire and flags sit where a
   phone's clock lives. Move the whole map down so that NOTHING brighter than the background
   appears above 13% of the height, and nothing below 88%. Keep the dark rock cliff and mist
   inside those two bands — that is fine — but no tower, no crystal, no flag, no fire.

2. Reduce the detail by about 40%: fewer individual props on the grass, no small scattered trees
   beside the lanes. Every forest should read as ONE dark mass with two tones and dithered edges,
   not as a group of separate trees.

3. Strengthen the three things that make it read at thumbnail size: the vertical mid lane, the
   violet pit and the ember pit, and the two rivers meeting the bridge.

Keep: the D-shaped walled forests with their single gates, the cliff face with hanging vines and
the waterfalls at both ends of the river, the banner poles on the outer wall, the crabs in the
shallows, the 180-degree symmetry.
```

Prompt "thu nhỏ để kiểm tra" — dùng sau khi có ảnh, để biết nó có sống ở 307 px không:

```
Now show me the same image at 307 pixels wide, pixel-art downscaled with nearest neighbour, no
anti-aliasing. Do not redraw anything: just show how it reads at that size.
```

## G. Prompt v3 — bản đầy đủ, tự chứa (dùng cho cuộc hội thoại MỚI)

Dùng khi bạn muốn bắt đầu lại từ đầu thay vì vá trên ảnh vòng 2. Đã tích hợp sẵn 6 bài học từ ảnh tham chiếu:
vách đá có dây leo, thác nước hai đầu sông, hai khu rừng chữ D có tường bao, cột cờ dọc tường ngoài, lề hai bên
hẹp, và dải an toàn tuyệt đối.

```
Design an ORIGINAL pixel-art live wallpaper for a vertical smartphone screen, aspect ratio 9:20
(841 x 1870 or any size at that ratio).

Original work only. Do not copy any existing game's artwork, map, logo, icon, character, item or
trademark. No text, no numbers, no UI, no watermark, not a screenshot of an existing game.

CAMERA AND SETTING
A magical highland fortress seen from a high three-quarter top-down view (16-bit strategy-game
camera, slightly tilted: we see the tops of towers and the faces of the cliff). The highland is one
tall rounded-rectangle plateau floating in dark void, its edge a layered rock cliff 8-12 pixels
thick with moss and vines hanging down, fading into fog below. Two or three thin waterfalls pour
off the cliff at the two ends of the river.

LAYOUT — respect this closely, it is the point of the image
180-degree rotational symmetry around the centre: a red base at the top, a blue base at the bottom,
each the mirror of the other.
- A straight central road runs vertically down the exact middle from the bottom base to the top
  base. It is the shortest, widest road and the visual spine of the picture.
- Two outer roads leave each base, bow out toward the left and right edges, and rejoin the river
  at mid-height. They are clearly longer and narrower than the central road.
- A river crosses horizontally near the vertical centre in a gentle S-curve from the left edge to
  the right edge, crossed by exactly ONE small wooden bridge where it meets the central road.
  Two crabs wander the shallows.
- A round monster pit on the upper river bank, left of centre, glowing violet with a stone rim.
  A mirrored round pit on the lower river bank, right of centre, glowing ember orange.
- TWO large walled forests per half, one on the left and one on the right, each shaped like a
  letter D: a closed low stone wall with exactly one narrow gate facing a lane, and inside it one
  dense dark canopy that reads as a SINGLE mass, not as separate trees. A few monster camps sit
  inside the forest.
- Each base, inward: a glowing crystal nexus on a round stone dais with a healing fountain behind
  it, two guard towers, three inhibitor crystals, then three lane towers along each road.
- Small soldier minions, in groups of three, walking the three roads: red ones on the red half,
  blue ones on the blue half. Banner poles with team flags stand along the outer wall.
- Packed-dirt roads with darker rims and cart tracks; bushes, loose rocks and trampled ground
  along their edges.

COMPOSITION — safe bands come first
- The top 13% and the bottom 12% of the canvas must stay calm and dark: only void, rock cliff and
  very faint fog. No tower, no crystal, no flag, no fire, nothing brighter than the background.
  A phone's clock and dock live there.
- The plateau fills everything between those bands and runs almost to the left and right edges,
  with only a thin margin.
- It must read at thumbnail size: a dark frame, a glowing vertical spine, and violet plus ember
  light at the centre. If it fails at 300 pixels wide, it fails.

DETAIL BUDGET — draw in masses, not in specks
- Every forest is one mass with two tones and dithered edges.
- Do not scatter small individual trees beside the lanes. No pebbles, no single blades of grass,
  no clutter smaller than a soldier.
- Fewer props, stronger silhouettes.

PALETTE
grass #6f9b45 #547a37 #7aa64d; dirt #b99a68 with #8f7448 rims; stone #8a8f96 #4a4a52;
water #2f7fa8 to #54c7c0; hextech gold #e8c15a; blue team #3fa9f5; red team #d94a3d;
violet #8a5bd6; ember #e07a3c; void #0e1118 to #1a1e28.
Dithered shading, hand-placed pixels, one gentle grade: warm highlights, cool shadows, light from
the upper left.

STYLE
Original 16-bit pixel art, crisp single-pixel detail, no anti-aliasing, no soft gradients (use
dithering), visible pixel grid, a high-resolution render of a low-resolution image.
```

Câu kiểm tra sau khi có ảnh:

```
Show me this same image at 307 pixels wide, downscaled with nearest neighbour, no anti-aliasing,
not redrawn. I need to see whether it still reads at that size.
```

## H. Prompt v4 — nền cho đường ống bake (mới nhất)

Đây là bản dành cho ảnh **sẽ được nhúng vào app làm lớp tĩnh**, nên nó khác prompt v3 ở ba điểm: **không vẽ lính**
(engine tự vẽ lính động, vẽ sẵn sẽ trùng), **nước một hệ màu sạch** (bộ bake phân loại theo hue), và **ánh sáng phẳng
ban ngày** (engine tự phủ ngày/đêm).

```
Design an ORIGINAL pixel-art background for a vertical smartphone live wallpaper, aspect ratio 9:20
(1080 x 2400, or any size at exactly that ratio).

Original work only. Do not copy any existing game's artwork, map, logo, icon, character, item or
trademark. No text, no numbers, no UI, no watermark, not a screenshot of an existing game.

WHAT THIS PICTURE IS FOR — read this first
This is the FIXED LAYER of a live wallpaper. A game engine will animate water on the river, walk soldiers
along the three roads, and grade the map from noon to night. So:
- Draw NO soldiers, NO heroes, NO monsters walking. The roads and forests must be EMPTY of living things.
  Empty roads are correct, not a mistake.
- Draw NO lighting effects: no glow, no bloom, no torchlight, no baked-in shadow gradients, no vignette,
  no god rays. Flat, even, mid-day light from the upper left, nothing more.
- Draw NO baked reflections on the water. Plain coloured water only.

CAMERA
A magical highland fortress seen from a high three-quarter top-down view (16-bit strategy-game camera,
slightly tilted: we see the tops of towers and the faces of the cliff). One tall rounded-rectangle plateau
floating in dark emptiness, its edge a layered rock cliff 10-14 pixels thick with moss and vines hanging
down, fading into fog below. Two thin waterfalls pour off the cliff at the two ends of the river.

LAYOUT — 180-degree rotational symmetry about the centre, a red base at the top and a blue base at the
bottom, each the mirror of the other
- A straight central road runs vertically down the exact middle, base to base. It is the shortest, widest
  road and the visual spine of the picture.
- Two outer roads bow out toward the left and right edges and come back to the river. They are clearly
  longer and narrower than the central road.
- A river crosses horizontally near the vertical centre in a gentle S-curve, edge to edge, crossed by
  exactly ONE small wooden bridge where it meets the central road. Wide enough to read at 300 pixels.
- A round monster pit on the upper bank, left of centre; a mirrored round pit on the lower bank, right of
  centre. The pits are recessed stone bowls, empty and dark inside — no figures.
- TWO large walled forests per half, one left one right, each a letter-D: a closed low stone wall with
  exactly one narrow gate facing a lane, and inside, one dense dark canopy that reads as a SINGLE mass.
  Empty inside: no camps, no monsters, no tents.
- Each base, inward: a glowing crystal nexus on a round stone dais with a fountain behind it, two guard
  towers, three inhibitor crystals, then three lane towers along each road. Banners on poles along the
  outer wall.
- Packed dirt roads with darker rims and cart tracks; bushes, loose rocks, tall grass along their edges.

COLOUR RULES — these let a tool tell the parts apart, so keep them clean
- Roads: ONE family of warm pale dirt, hue about 30-42 degrees, clearly lighter than grass and clearly
  warmer than stone. Nothing else in the picture uses that pale warm tan.
- Grass: mid green, hue 80-110, medium brightness. Do not let grass drift toward blue-grey.
- Water: ONE family of cyan to blue, hue 185-215, saturated, mid-to-dark. It must be obviously bluer and
  more saturated than the stone and the void, so a hue test can find every water pixel.
- Stone and cliff: low saturation, blue-grey, hue 210-230 but at LOW saturation.
- The emptiness outside the plateau: very dark, near-black blue, brightness under 10%.
- Team colours: blue #3fa9f5, red #d94a3d. Hextech gold #e8c15a. Violet #8a5bd6. Ember #e07a3c.
- Maximum 40 colours in the whole picture. Dithered edges, no anti-aliasing, no smooth gradients.

COMPOSITION
- The top 13% and the bottom 12% must be calm and dark: only the empty void, the dark cliff and very faint
  fog. No tower, no crystal, no banner, nothing bright. A phone's clock and dock live there.
- The plateau fills everything between those bands and runs almost to the left and right edges.
- It must still read at 307 pixels wide: a dark frame, a glowing vertical spine of road, and the two pits.

STYLE
Original 16-bit pixel art, crisp single-pixel detail, no anti-aliasing, visible pixel grid, a
high-resolution render of a low-resolution image.
```

Sau khi có ảnh, bake bằng:

```bash
python3 tools/rift_bake.py anh-moi.png android/app/src/main/assets/rift --w 307 --h 682 --colours 40
```

Bộ bake in ra tỉ lệ phần trăm từng loại; muốn chắc thì đọc mấy con số đó: **nước nên 5–9%**, **đường 11–16%**,
**đá 8–13%**, **hư không 12–18%**. Lệch nhiều nghĩa là ảnh vi phạm "colour rules", sửa prompt chứ đừng sửa ngưỡng.

## I. Prompt v5 — nền cho đường ống bake, bản đọc được bằng máy (mới nhất)

v5 khác v4 ở chỗ nó không chỉ nói "đừng vẽ gì engine sẽ vẽ", mà **vẽ đúng những thứ bộ bake biết tìm**:

| bộ bake tìm gì | prompt phải vẽ gì | nếu ảnh sai thì hỏng ra sao |
|---|---|---|
| nước (hue 185–215, một vùng liền) | sông liền một mạch, edge to edge, không bị đá hay cầu cắt vụn | sông bị lọc thành nhiều mảnh, mask mất nước |
| đường (tan ấm, hue 30–42) | đường liền từng đoạn, rộng ≥ 4 px ở 307 | đường biến mất, lính không có đường đi |
| tháp / trụ (một viên pha lê sáng ở màu đội) | **mỗi tháp một viên pha lê nhỏ, màu phẳng, sáng** | không có đèn nào được tìm thấy, đêm tối om |
| hố (khoảng tối tròn, giữa bản đồ) | hố tròn, rỗng, tối, có vành đá sáng quanh | hố bị bỏ qua, mất hai vệt sáng đêm |
| thác (nước nhạt ở hai đầu sông) | **hai thác nước vẽ bằng vệt dọc nhạt ở hai đầu sông** | engine không đổ nước xuống được, sông "cụt" |

```
Design an ORIGINAL pixel-art background for a vertical smartphone live wallpaper, aspect ratio 9:20
(1080 x 2400, or any size at exactly that ratio).

Original work only. Do not copy any existing game's artwork, map, logo, icon, character, item or
trademark. No text, no numbers, no UI, no watermark, not a screenshot of an existing game.

WHAT THIS PICTURE IS FOR
This is the FIXED LAYER of a live wallpaper. A game engine animates the river, the falls, soldiers on
the roads and the hour's light, and it finds what it must animate by COLOUR and by SHAPE. So draw the
things listed under THE FIVE THINGS THE ENGINE MUST BE ABLE TO FIND, and draw nothing that moves.
- Draw NO soldiers, NO heroes, NO monsters. Empty roads are correct, not a mistake.
- Draw NO lighting effects: no glow, no bloom, no torchlight, no vignette, no god rays, no baked
  shadows. Flat, even mid-day light from the upper left. A crystal is a flat bright colour, not a glow.
- Draw NO reflections on the water.

CAMERA
A magical highland fortress seen from a high three-quarter top-down view (16-bit strategy-game camera,
slightly tilted: we see tower tops and the faces of the cliff). One tall rounded-rectangle plateau
floating in dark emptiness, its edge a layered rock cliff 10-14 pixels thick with moss and vines,
fading into fog below.

THE FIVE THINGS THE ENGINE MUST BE ABLE TO FIND
1. THE RIVER — one continuous band of saturated cyan-to-blue, hue 185-215, edge to edge near the
   vertical centre, in a gentle S, crossed by exactly ONE small wooden bridge where the central road
   meets it. It must be ONE region: no rocks, no reeds, no bank punching holes through it. Its surface
   is a mid-to-dark blue; the only pale water in the picture is the falls (item 5).
2. THE ROADS — one family of warm pale tan, hue 30-42. The central road runs straight down the exact
   middle, base to base, and is the widest. Two outer roads bow out to the left and right, clearly
   narrower. Every road is at least 4 pixels wide at 307 pixels wide, and unbroken from base to river.
3. THE TOWERS — every tower, guard tower, inhibitor and base carries ONE small flat bright crystal in
   its side's colour (blue #3fa9f5 at the bottom half, red #d94a3d at the top half). It should be a
   clean 2-4 pixel blob of that colour, clearly separate from the stone around it. The engine puts a
   lamp glow on each one at night.
4. THE PITS — one round pit on the upper bank left of centre, one mirrored on the lower bank right of
   centre. A recessed stone bowl: a bright rim, a dark empty inside at least 12 pixels across, no
   figures, no glow inside.
5. THE FALLS — at BOTH ends of the river, the water goes over the cliff in thin PALE vertical streaks
   (light cyan, nearly white in the gaps), 6-20 pixels wide, falling a little way down the cliff face
   and breaking into a few pale specks. This is the only place in the picture with light water.

LAYOUT — 180-degree rotational symmetry about the centre, red base at the top, blue base at the bottom
- Two walled forests per half, one left one right, each a letter-D: a closed low stone wall with
  exactly one narrow gate facing a lane, and inside one dense dark canopy that reads as a single mass.
- Each base, inward: a crystal nexus on a round stone dais with a fountain behind it, two guard towers,
  three inhibitor crystals, then three lane towers along each road. Banners on poles along the wall.
- Packed dirt roads with darker rims and cart tracks; bushes, loose rocks and tall grass at their edges.

COLOUR RULES — these are what a colour test reads, so keep them clean
- Roads: ONE warm pale tan family, hue 30-42, lighter than grass, warmer than stone. Nothing else in
  the picture may use that tan.
- Grass: mid green, hue 80-110, medium brightness; it must not drift toward blue-grey.
- Water: ONE cyan-to-blue family, hue 185-215, saturated, mid-to-dark; obviously bluer than stone.
- Stone and cliff: blue-grey, hue 210-230, LOW saturation.
- Emptiness outside the plateau: near-black blue, brightness under 10%.
- Team colours: blue #3fa9f5, red #d94a3d. Hextech gold #e8c15a. Violet #8a5bd6. Ember #e07a3c.
- Maximum 48 colours, dithered edges, no anti-aliasing, no smooth gradients.

COMPOSITION
- The top 13% and the bottom 12% are calm and dark: only void, dark cliff and very faint fog. No
  tower, no crystal, no banner, nothing bright there. A phone's clock and dock live there.
- The plateau fills everything between and runs almost to the left and right edges.
- It must read at 307 pixels wide: a dark frame, a glowing vertical spine of road, two pits, one river.

STYLE
Original 16-bit pixel art, crisp single-pixel detail, no anti-aliasing, visible pixel grid, a
high-resolution render of a low-resolution image.
```

Bake và đọc lại mấy con số bộ bake in ra. Với ảnh đang dùng (`image-2.png` thành 36 đèn: 2 nexus, 32 tháp, 2 hố;
1722 điểm thác), các khoảng hợp lý là:

| hạng | hợp lý | ảnh hiện tại |
|---|---|---|
| hư không | 12–20% | 16.3% |
| cỏ / đất | 55–70% | 66.8% |
| đường | 5–12% | 7.4% |
| đá | 3–10% | 3.6% |
| nước | 4–9% | 5.9% |
| đèn tìm được | 20–45 | 36 |
