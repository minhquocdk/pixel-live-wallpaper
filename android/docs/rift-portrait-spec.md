# Đặc tả: bố cục "Cột Đôi Sông" — bản đồ Rift cho màn hình dọc

Trạng thái: **đặc tả để duyệt**, chưa code. Ngày 2026-10-24. Nhánh `arena/2126d684-pixel-live-wallpaper`.
Liên quan: `Theme.kt`, `Palette.kt`, `World.kt`, `IslandScene.kt`, `IslandWallpaperService.kt`.

> **Hình minh hoạ (sơ đồ, không phải ảnh cuối):** `android/docs/rift-column-mock.png` — dựng đúng các toạ độ ở §6
> trên khung tham chiếu 307 × 682 (phóng 2×). Ảnh này **chưa có** tán rừng, bụi cỏ, tường đá, gương nước và ánh
> sáng; nó chỉ để duyệt **bố cục**. Ba thứ còn thiếu đó nằm ở §6.6.

---

## 0. Tóm tắt trong một đoạn

Màn hình dọc không cho phép "đặt bản đồ Rift vuông vào giữa" (chỉ dùng ~45% chiều cao), cũng không cho phép xoay 45°
(hình thoi vẫn chiếm một hình vuông). Vì vậy ta **giữ nguyên topology của Rift** — hai base đối xứng tâm, một đường
giữa nối thẳng hai base, hai đường biên vòng ra hai bên, một con sông chắn ngang giữa, bốn khu rừng, hai hố quái
lớn trên hai bờ sông đối nhau — rồi **kéo giãn trục dọc**: mid thành xương sống thẳng đứng, sông thành dải ngang
hình chữ S, hai đường biên thành hai vành cung trái/phải. Bố cục này gọi là **Cột Đôi Sông** (`RIFT_COLUMN`),
đối xứng 180° quanh tâm khung, và **không** dùng lại địa hình nhiễu của đảo.

Khi màn hình ngang (máy tính bảng, preview trên laptop) thì dùng **`RIFT_WIDE`**: bản đồ vuông đúng hình dạng Rift,
đặt giữa khung, hai bên là hư không.

---

## 1. Vì sao phải có bố cục mới (số liệu thật của engine)

`IslandWallpaperService.pictureSize()` tính khung tranh từ ngân sách pixel và tỉ lệ màn hình:

```
budget = 210.000 px (mức Auto)        w = sqrt(budget / ratio) ∈ [224, 720]
ratio  = height / width               h = w * ratio            ∈ [140, 1600]
detail = w / 384                      ∈ [0.55, 2.4]
```

| Thiết bị | ratio | khung tranh (px) | ghi chú |
|---|---|---|---|
| 16:9 ngang (hiện tại) | 0.5625 | 611 × 344 | đảo gốc 384×216 |
| 18:9 dọc | 2.00 | 324 × 648 | |
| 19.5:9 dọc (phổ biến) | 2.17 | 311 × 674 | |
| 20:9 dọc | 2.22 | 307 × 682 | **khung tham chiếu của đặc tả này** |
| 21:9 dọc | 2.33 | 300 × 700 | |
| 4:3 dọc (tablet) | 1.33 | 397 × 529 | |

Với khung 307 × 682 (ratio 2.22, `detail` = 307/384 = 0.80):

- Một bản đồ vuông vừa chiều rộng (307 × 307) để lại **375 px trống ≈ 55% chiều cao** — màn hình chết.
- Xoay 45°: hình thoi có đường chéo 307 ⇒ vẫn chiếm đúng 307 × 307. Không giải quyết gì.
- Kéo giãn bản đồ vuông thành 307 × 682: sai hình học (sông và lane méo 2.2×), mất hết cảm giác bản đồ.

Hiện tại đảo gốc "tự co giãn" bằng cách cho **map** cao lên theo khung (`growTerrain`: `tilesY` tỉ lệ `H/W`,
`sx/sy` đổi theo `wide`). Đó là cách đúng, và Cột Đôi Sông đi tiếp đúng con đường đó: **hình học bản đồ được
định nghĩa lại cho khung cao, chứ không bị bóp méo**.

---

## 2. Ràng buộc kỹ thuật phải tôn trọng

Những điểm dưới đây là sự thật của code hiện tại, đặc tả không được vi phạm:

1. **Phép chiếu là oblique, không phải top-down.** `ground(x, y)` chiếu mặt đất lên màn hình, địa hình nâng theo
   `lift` (terrace), sprite là billboard có chiều cao và có `zb` (độ sâu) để che khuất. Rift là top-down thuần.
   → Ta **giữ phép chiếu của engine**: Rift trở thành một cao nguyên nhìn từ cùng góc máy như hòn đảo. Đây là điều
   làm nó "cùng một bộ wallpaper" với đảo gốc, và cho phép dùng lại toàn bộ sprite/stamp/z-buffer.
2. **`LEV[i] < 0` nghĩa là biển**, và `findShore()`/`waterPixel(kind = 1)` vẽ đại dương + cá voi + thuyền + hải âu.
   Rift không có biển. → Không dùng `LEV < 0` cho hư không; thay bằng mask `void` riêng (§6.3).
3. **`riv[i]` là nước sông** (kind 2, bảng màu `PR`) — dùng lại được nguyên vẹn cho sông của Rift.
4. **`path[i]` là màu đường đi**, `res[i]` là ô đã bị chiếm (1 = nhà, 2 = làng, 3 = ruộng, 4 = ven đường).
   Lane của Rift chính là `path` + `res = 4`; cấu trúc (trụ, nhà chính) đặt bằng `res = 1` để cây/rừng không mọc lên.
5. **`base[]` được vẽ một lần** rồi mỗi khung `System.arraycopy` (`render()`); mọi thứ động phải nằm ngoài `base`.
   Cache sprite phải có khoá gồm **theme + layout** (Maple đang dùng `1 shl 20`; Rift phải thêm bit nữa).
6. **Tất định theo seed**: cùng seed + cùng kích thước + cùng theme ⇒ cùng ảnh. Có test khoá điều này.
7. **Mọi thứ vẽ theo `detail`** để cùng một seed cho cùng bản đồ ở mọi độ phân giải.

---

## 3. Đặc trưng bất biến của Summoner's Rift, và hình dạng của chúng ở bố cục dọc

Đây là phần "vẫn có các đặc trưng của map". Mỗi dòng là một bất biến — nếu bỏ dòng nào thì bản đồ thôi giống Rift,
nên mỗi dòng đều có một test ở §9.

| # | Đặc trưng gốc | Ở Cột Đôi Sông | Ghi chú |
|---|---|---|---|
| I1 | Hai base ở hai góc đối nhau, **đối xứng 180°** | Hai base ở **đáy** và **đỉnh**, đối xứng 180° quanh tâm khung | Đối xứng tuyệt đối về hình học; nhiễu bề mặt thì không nhân bản (xem §6.5) |
| I2 | Mid là đường **ngắn nhất**, nối thẳng hai base | **Trục dọc giữa** `p = 0.50`, từ base này tới base kia | Là "xương sống" của wallpaper dọc |
| I3 | Hai đường biên là **đường dài**, vòng ra hai mép | **Vành trái** (đường Baron) và **vành phải** (đường Rồng), bo góc ở nửa trên | Dài ≈ 1.4–1.5× mid, giống tỉ lệ thật |
| I4 | **Sông** chắn giữa, vuông góc với mid, nối hai đường biên | **Dải ngang** hình chữ S mảnh ở giữa khung, từ vành trái sang vành phải | Đi qua mid bằng **một cây cầu** duy nhất |
| I5 | **Bốn khu rừng** giữa các lane | Bốn góc phần tư quanh sông: trên-trái, trên-phải, dưới-trái, dưới-phải | Mỗi khu 3 bãi quái |
| I6 | **Hai hố quái lớn** trên hai bờ sông, đối nhau | **Baron** bờ trên, phía vành trái; **Rồng** bờ dưới, phía vành phải | Đối xứng 180°, giống Rift |
| I7 | **3 trụ mỗi lane mỗi bên**, rồi ức chế, rồi nhà chính + 2 trụ nhà | Y hệt, sinh ở nửa xanh rồi quay 180° | 18 trụ + 6 ức chế + 2 nhà chính + 4 trụ nhà |
| I8 | **Lính đi theo đợt**, ba lane gặp nhau ở giữa | Ba dòng lính chạy dọc khung, gặp nhau quanh sông | Nhịp 24 s/đợt |
| I9 | Bụi cỏ, tường đá, bậc địa hình, **rừng dày** | Bụi cỏ hai bên lane; **tường cao nguyên** một bậc bao quanh; mỗi bãi quái có tán rừng + tường đá bao | `terraceLevel` + `lift` có sẵn; mật độ ở §6.6 |
| I10 | Bản đồ là một **cao nguyên** giữa hư không | Cao nguyên chữ nhật bo góc, ngoài là hư không + sương trôi | Thay cho "biển" của đảo gốc |

Những thứ **cố ý bỏ** (và lý do):

- Trụ/ức chế đúng số lượng thật ở mọi lane: giữ, nhưng nhỏ (6–10 px) — đọc được ở 300 px bề ngang.
- Tướng, lính đúng 6 con/đợt, thời gian hồi sinh thật: rút gọn còn 3 lính/đợt để 9 sprite chuyển động là đủ sống.
- Địa hình chi tiết từng bụi cỏ như bản đồ thật: thay bằng bụi cỏ sinh theo seed, đối xứng *vị trí*, không đối xứng *hình dáng*.

---

## 4. Hệ toạ độ: plan → màn hình

Mọi con số hình học dưới đây viết trong **toạ độ plan** `(p, q)`, `p` sang phải, `q` xuống, cả hai trong `[0, 1]`.
Plan là hình chữ nhật **cao**, được đặt vào khung với lề trên/dưới rộng hơn lề hai bên (để chừa vùng đồng hồ
và dock của launcher):

```
INSET_X = 0.028          u = INSET_X + p * (1 - 2*INSET_X)        // 0.028 .. 0.972
INSET_TOP = 0.120        v = INSET_TOP + q * (1 - INSET_TOP - INSET_BOTTOM)
INSET_BOTTOM = 0.080                                              // 0.120 .. 0.920
```

Khung tham chiếu 307 × 682 ⇒ cao nguyên **290 × 546 px** (tỉ lệ 1 : 1.88).

> Sửa ở vòng 2 (xem §14): lề hai bên thu từ 0.055 xuống 0.028 và lề dưới từ 0.100 xuống 0.080. Hai dải an toàn
> mới là thứ phải giữ, không phải lề hai bên — ảnh tham chiếu cho thấy bản đồ sát mép trông khoẻ hơn hẳn, và đo
> được rằng vách đá tối dưới đáy (luma trung bình 0.10) vẫn an toàn dưới dock.

Đối xứng 180°: `(p, q) ↔ (1 - p, 1 - q)`. Mọi thứ dưới đây chỉ đặc tả **nửa xanh** (`q ≥ 0.5`); nửa đỏ là ảnh quay
180° của nó. Đây không chỉ là mẹo viết đặc tả — đó là **quy tắc sinh map** (§6.1).

---

## 5. Hình dung bố cục

```
   v = 0.000 ┌─────────────────────────────┐
             │   hư không + sương trôi      │  ← vùng đồng hồ, không chi tiết
   v = 0.120 ├─────────────────────────────┤
             │        ╔═════ NEXUS ═════╗    │  q = 0.07
             │   ▄▄▄▄ ║  ▓ nhà chính ▓  ║ ▄▄▄│  tường cao nguyên 1 bậc
             │  ╔═════╩════════════════╩═════╗
             │  ║  ức  ức  ức   (3 ức chế)  ║
             │  ║   ●     ●     ●            ║   q = 0.20
             │  ║ ▄▄▄  RỪNG TRÊN  ▄▄▄       ║
             │  ║ ▄▄▄   (3 bãi)   ▄▄▄       ║
   đường     │  ║  ║          ┃          ║    ║  ← vành trái = lane Baron
   Baron ────┼──╫──╫──────────┃──────────╫────┼── vành phải = lane Rồng
             │  ║  ║       ▄  ┃  ▄       ║    ║
             │  ║  ║        ▄▄┃▄▄        ║    ║
   q = 0.46  │  ║  ║  BARON ○  ┃  ○ RỒNG  ║    ║   ○ = hố quái lớn
             │  ║  ║   ░░░░░░  ┃  ░░░░░░  ║    ║
   q = 0.50  │──╫──╫━━━━━━━━━━━╪━━━━━━━━━━╫────┼── sông (chữ S), cầu ở giữa
             │  ║  ║   ░░░░░░  ┃  ░░░░░░  ║    ║
             │  ║  ║        ▄▄┃▄▄        ║    ║
             │  ║  ║  RỪNG DƯỚI   ┃  ▄▄▄  ║    ║
             │  ║  ║   (3 bãi)    ┃  ▄▄▄  ║    ║
             │  ║   ●     ●      ┃   ●       ║   ← lane giữa: 3 trụ
             │  ║  ức  ức  ức   (3 ức chế)  ║
             │  ╚═════╦════════╦════════════╝
             │        ║ NEXUS  ║              │
   v = 0.900 └─────────────────────────────┘
             │   hư không + sương trôi      │  ← vùng dock
   v = 1.000 └─────────────────────────────┘
              ↑ vành trái      trục giữa ↑      vành phải ↑
```

---

## 6. Đặc tả hình học

### 6.1 Quy tắc sinh (bắt buộc)

1. Sinh **nửa xanh** (`q ≥ 0.5`) từ seed.
2. Sinh nửa đỏ bằng `rotate180()` của nửa xanh: `(p, q) → (1 - p, 1 - q)`.
3. Nhiễu **bề mặt** (cỏ, đá, bụi, hạt sương) lấy mẫu độc lập cho từng nửa bằng seed khác nhau
   ⇒ đối xứng *bố cục*, không đối xứng *vân* (nếu nhân bản cả vân, ảnh trông như soi gương bằng CG).
4. Bụi cỏ và hạt cát: vị trí theo seed, **không** đối xứng — đúng như Rift thật (bụi cỏ hai bên không giống nhau).

### 6.2 Cao nguyên, hư không, tường

| Thành phần | Giá trị |
|---|---|
| Cao nguyên | chữ nhật bo góc bán kính `0.045` (plan), mép hơi gợn bằng `wobbledRing` biên độ `0.006` |
| Tường bao | dải dày `0.020` plan, `LEV = 1` (nâng 1 bậc), mặt ngoài tối hơn 35% |
| **Vách đá dưới mép** | 8–12 px đá tầng (3 lớp màu tối dần) + dây leo rủ xuống; đây là thứ giữ cho cao nguyên không bị "dán" vào nền |
| **Thác nước** | 2–3 cột nước rộng 2 px đổ từ chỗ sông chạm mép xuống hư không, bọt trắng 1 px, động theo `waterTime` |
| **Cột cờ** | 6 cột mỗi nửa, cắm dọc tường bao ngoài, cờ đội mình, `spriteFromRows` 2 × 5 px |
| **Bệ đá dưới trụ** | đĩa `stone` tối 1 bậc, 3–4 px, dưới mỗi trụ và ức chế — làm trụ "đứng trên nền" thay vì dán phẳng |
| **Lửa trại trong rừng** | 1 cái mỗi khu rừng mỗi nửa map; 2 × 2 px + khói 1 px; ban đêm thành `Light` nhỏ (dùng `Marks.FIRE`) |
| Hư không | ngoài cao nguyên: một gradient dọc, tối dần ra mép; cộng sương trôi rất chậm (`fogBands`, biên độ thấp) |
| Mép hư không | 3 px "rìa đá" `#2a2d36` để cao nguyên không bị dán vào nền đen |

### 6.3 Nước sông

```
q_c(p) = 0.500 + 0.018 * sin(2π * 1.2 * (p - 0.12) + 0.7)      // chữ S rất nhẹ
nửa bề rộng: 0.028 plan  (±8 px ở khung 307)
p ∈ [0.120, 0.880]                                            // sông chết ở hai vành, như Rift
```

- Cầu: `p ∈ [0.465, 0.535]` (bề rộng lane giữa + 1 px mỗi bên), vẽ bằng `plank` (`roofTexel("plank")` đã có sẵn).
- Hố quái lớn: **Baron** tâm `(0.285, 0.462)`, **Rồng** tâm `(0.715, 0.538)`, bán kính `0.045` plan;
  hố khoét vào bờ (một nửa chìm trong nước), viền đá `stone` sáng hơn nền 1 bậc.
- Cá nhỏ, sáng nước: dùng lại `waterPixel` với bảng `PR`; thêm 2 con **cua sông (scuttle)** đi qua đi lại ở
  `(0.330, 0.500)` và `(0.670, 0.500)`.

### 6.4 Lane, trụ, nhà (nửa xanh, plan)

| Vật thể | Toạ độ `(p, q)` | Kích thước sprite (px @ khung 307) |
|---|---|---|
| Nhà chính (Nexus) | `(0.500, 0.930)` | 20 × 18 |
| Suối hồi máu | `(0.500, 0.972)` | 16 × 10 (mặt nước) |
| 2 trụ nhà | `(0.445, 0.905)`, `(0.555, 0.905)` | 6 × 12 |
| Ức chế trái / giữa / phải | `(0.365, 0.862)`, `(0.500, 0.848)`, `(0.635, 0.862)` | 10 × 10 |
| Lane giữa | thẳng `p = 0.500`, `q 0.900 → 0.100` | rộng `0.055` plan ≈ 15 px |
| Trụ giữa: ức chế / trong / ngoài | `(0.500, 0.795)`, `(0.500, 0.690)`, `(0.500, 0.585)` | 6 × 12 |
| Lane trái (Baron) — đường đi | `(0.455,0.900) → (0.300,0.880) → (0.185,0.815) → (0.130,0.700) → (0.120,0.600) → (0.125,0.500)` | rộng `0.042` plan ≈ 11 px |
| Trụ trái: ức chế / trong / ngoài | `(0.300, 0.880)`, `(0.130, 0.700)`, `(0.120, 0.600)` | 6 × 12 |
| Lane phải (Rồng) | ảnh `p → 1 - p` của lane trái | |
| Bụi cỏ | 6 cụm mỗi nửa, cách lane 2–4 px, hai bên | 7 × 4 |
| Bãi quái — nửa trái | `(0.235,0.640)` buff xanh, `(0.310,0.575)` sói, `(0.205,0.745)` ếch | 7 × 6 |
| Bãi quái — nửa phải | `(0.765,0.640)` buff đỏ, `(0.690,0.575)` diều hâu, `(0.795,0.745)` đá | 7 × 6 |

Kiểm tra tỉ lệ (đo theo các điểm điều khiển ở trên, plan): mid đầy đủ ≈ `0.86` (`q 0.930 → 0.070`);
lane biên đầy đủ ≈ `1.34` (0.67 mỗi nửa) ⇒ **≈ 1.56×** mid. Rift thật ≈ 1.4×, nên đây là lane biên *hơi* dài hơn bản
thật — chấp nhận, vì khung dọc cần hai vành cung có chỗ thở. T3 kiểm **thứ tự và sai khác trái/phải ≤ 5%**, không
khoá con số tuyệt đối.

### 6.5 Bề mặt và màu

Không vẽ từng ô cỏ bằng tay: dùng lại `OCT`/`GR`/`MO` để lấy vân, nhưng **thay bảng màu**:

| Vai trò | Màu | Ghi chú |
|---|---|---|
| Cỏ sáng / cỏ tối | `#6f9b45` / `#547a37` | xanh ô-liu, không quá bão hoà |
| Đất lane | `#b99a68` + `#8f7448` (viền) | lane có viền tối 1 px để đọc được ở 300 px |
| Đá / tường | `#8a8f96` / `#4a4a52` | tường ngoài tối hơn hẳn |
| Nước sông | `#2f7fa8` … `#54c7c0` | dùng `WaterPalette PR` |
| Hư không | `#0e1118` → `#1a1e28` | rất tối, gần như không chi tiết |
| Đội xanh / đội đỏ | `#3fa9f5` / `#d94a3d` | chỉ dùng cho cờ, viền trụ, hạt sáng |
| Vàng Hextech | `#e8c15a` | trụ, nhà chính, cầu |
| Baron | `#8a5bd6` | quầng tím |
| Rồng | `#e07a3c` | quầng cam |

Grade: `LOOK_RIFT = Grade(sat = 1.12, warm = 0.02, contrast = 1.10)` — bớt ấm hơn Maple, tương phản hơn đảo gốc
để lane/tường đọc rõ trên nền tối.

### 6.6 Rừng, bụi cỏ, tường đá — thứ lấp khoảng trống

Bản mock ở đầu tài liệu trông "trống" đúng vì thiếu mục này. Rift thật không có bãi cỏ rộng liền mạch: giữa các lane
là **rừng có tường đá bao quanh từng bãi**, nên mắt luôn thấy khối. Đặc tả:

| Thành phần | Số lượng / nửa map | Quy tắc |
|---|---|---|
| Rừng lớn có tường bao | **2 khu** (trái và phải), mỗi khu hình chữ D | vành `stone` kín, hở đúng 1 cửa 5–7 px quay ra lane; khu trái to hơn khu phải 15% để khỏi đối xứng máy móc |
| Tán rừng trong khu | 5–9 cây/khu, chồng lên nhau | bán kính tán 4–7 px; đọc thành **khối**, không phải từng cây rời |
| Bãi quái | 3/khu, nằm **trong** khu rừng | buff gần cửa, sói giữa, ếch/đá sát tường ngoài |
| Bụi rậm ngoài rừng | 10 cụm | sát lane và sát tường bao ngoài, cụm 4–6 px |
| Đá vụn, cỏ dại | ~60 hạt | `scatterAlong` dọc tường đá, mật độ giảm dần vào trong |
| Cây đổ / gốc cây | 3 | trang trí, không cản đường |

Quy tắc bắt buộc: mọi bãi quái phải **kín bằng tường đá** (không nhìn thấy cỏ trơn giữa hai lane), trừ cửa ra vào.
Đây là thứ khiến bản đồ "đọc" ra Rift chứ không phải một cái sân.

Cây dùng lại `buildTree()` với bảng lá `leaves["wood"]` / `"meadow"` của Rift (§6.5) và `res[i] = 1` để không mọc
chồng lên lane, trụ, hố.

---

## 7. Bố cục ngang: `RIFT_WIDE`

Khi `W >= H * 1.15` (máy tính bảng nằm ngang, preview trên laptop, ảnh trong docs):

- Cao nguyên **vuông**, cạnh `S = H` (không phải `W`), đặt giữa khung; hai bên là hư không (mỗi bên `(W-S)/2` px).
- Hình học **đúng Rift**: xanh ở góc dưới-trái, đỏ ở góc trên-phải; mid là **đường chéo** dưới-trái → trên-phải;
  sông là đường chéo còn lại (trên-trái → dưới-phải); lane trên men mép trái + mép trên; lane dưới men mép dưới +
  mép phải; Baron ở bờ trên-trái của sông, Rồng ở bờ dưới-phải.
- Cùng một `RiftPlan`, chỉ đổi **hàm chiếu** `toScreen(p, q)`: ở dọc là phép co giãn chữ nhật, ở ngang là phép quay
  45° + co vào hình vuông. Nhờ vậy toạ độ plan, lane, trụ, bãi quái, test đối xứng **dùng chung một bộ**.

Đây là câu trả lời cho "vẫn có đặc trưng của map": bản dọc là **cùng một bản đồ, nhìn qua một phép chiếu khác**,
không phải một bản đồ khác.

---

## 8. Sự sống, ánh sáng, thời gian

### 8.1 Lính (thay cho người đi đường)

- Nhịp đợt **24 s**; 3 lính/đợt/lane (2 cận chiến + 1 bắn xa), đợt thứ 3 có thêm 1 lính pháo to hơn.
- Đi từ base tới **điểm gặp**: mid `q = 0.50`, lane biên `q = 0.50` (chỗ sông cắt lane).
- Gặp nhau: dừng 4–6 s, nhấp nháy va chạm, rồi mờ dần và tan (không vẽ máu, không vẽ sát thương — đây là wallpaper).
- Tổng sprite động: **9 lính + 2 cua + 6 quái rừng** (quái đứng yên, chỉ thở) + 2 quái lớn trong hố = **19**.
- Tốc độ đi đặt theo `detail` để cùng một nhịp ở mọi độ phân giải.

### 8.2 Ánh sáng ban đêm

Dùng nguyên `hourLight` + `gradeFrame` + `lightsAt`, chỉ thêm nguồn sáng:

- 18 trụ + 2 nhà chính: quầng nhỏ (bán kính 8–14 `d`), nhấp nháy theo `blink()` như hải đăng hiện tại.
- Hố Baron/Rồng: quầng lớn (22–30 `d`) **chỉ khi "quái đang ở nhà"**, chu kỳ 6 phút (không theo giờ thật của trận).
- Lính: mỗi con một đốm 2 px như đèn lồng; ban ngày tắt.
- Ban ngày: nắng chếch trên-trái thống nhất với `Palette.wallK`, không đổi hướng theo lane.

### 8.3 Hư không

Sương trôi ngang rất chậm (1 vòng 4 phút), độ tương phản thấp, **không** có hạt sáng ở hai dải an toàn (§10).

---

## 9. Vùng an toàn cho launcher — giải bằng `scrim`, không bằng bố cục

Ba vòng ảnh tham chiếu cho thấy một điều: **ép bố cục tránh hai đầu khung thì mất nhiều hơn được.** Vòng 2 để
`v = 0.103 … 0.883` (an toàn, nhưng bản đồ nhỏ, thừa viền). Vòng 3 kéo `v = 0.083 … 0.916` và `u = 0.021 … 0.996`
— đẹp hơn hẳn, sát mép, nhưng đỉnh pha lê và cột cờ của base trên lọt vào dải đồng hồ.

Nên đổi cách giải: **để tranh chạm mép, và bảo đảm độ đọc bằng một lớp `scrim` trong engine.**

```
scrimTop(v)    = 0.52 + 0.48 * smoothstep(0.00, 0.135, v)      // nhân vào R,G,B
scrimBottom(v) = 0.52 + 0.48 * smoothstep(0.00, 0.125, 1 - v)
```

- Áp **sau** `gradeFrame`, trước khi blit — cùng chỗ với vignette, gần như miễn phí (một bảng 1 × H).
- Không đổi màu, chỉ tối đi: ban đêm dải này vốn đã tối nên không thấy khác biệt; ban ngày nó thành một
  "viền mờ" tự nhiên, giống ảnh chụp có vignette.
- Chỉ số phải đạt: **luma trung bình `v < 0.10` ≤ 0.10** và **không pixel nào > 0.45 trong `v < 0.06`**
  (đồng hồ Android nằm ở ~4–8% đầu, chữ trắng nên nền chỉ cần tối là đủ).

Đo lại trên ảnh vòng 3 (chưa có scrim): dải trên 13% có luma trung bình 0.155, đỉnh sáng nhất 0.36. Nhân scrim
0.52–0.65 ở dải đó ⇒ trung bình ≈ 0.08, đỉnh ≈ 0.20. **Đạt.** Không cần cắt bớt base trên.

| Dải | Trước scrim (vòng 3) | Sau scrim (tính) | Ngưỡng |
|---|---|---|---|
| `v < 0.10` | 0.14 | ≈ 0.08 | ≤ 0.10 ✓ |
| đỉnh sáng nhất `v < 0.15` | 0.36 | ≈ 0.20 | ≤ 0.45 ✓ |
| `v > 0.90` | 0.13 | ≈ 0.08 | ≤ 0.10 ✓ |

Bảng vùng an toàn cũ (dải tuyệt đối trống ở §9 bản trước) **bị thay** bởi mục này: ràng buộc chuyển từ "nghệ sĩ
không được vẽ gì" sang "engine bảo đảm độ tương phản", thứ kiểm được bằng máy và không bóp méo tranh.

## 10. Ngân sách hiệu năng

| Hạng mục | Chi phí | Ghi chú |
|---|---|---|
| Cao nguyên + tường + lane + trụ (tĩnh) | 1 lần khi dựng | vào `base[]` |
| `arraycopy` base mỗi khung | 210 k int ≈ 0.8 MB | ~0.1 ms, đã có |
| Nước sông động | ≈ 240 × 17 = 4.1 k px | so với biển hiện tại ít hơn nhiều |
| 19 sprite động | < 1 k px | không đáng kể |
| `gradeFrame` toàn khung | đã có | chi phối, như hiện tại |
| **Mục tiêu** | **≤ 8 ms/khung** ở mức Auto, máy tầm trung | cơ chế tự hạ cỡ (`autoScale`) giữ nguyên |

Rủi ro duy nhất: quầng sáng ban đêm nếu vẽ bằng toán học từng pixel. Bắt buộc dùng `glowSprite` (sprite quầng
đã cache) — không `sin`/`sqrt` theo pixel.

---

## 11. Kế hoạch code (4 PR, theo thứ tự)

### PR 1 — khung sườn (không đổi hình ảnh của theme cũ)

| File | Việc |
|---|---|
| `Theme.kt` | thêm `RIFT("rift")`; `fromId` tự nhận |
| `MapLayout.kt` (mới) | `enum class MapLayout { ISLAND, RIFT_COLUMN, RIFT_WIDE }`, `fun layoutFor(theme, W, H)` |
| `World.kt` | tách `init` thành `growIsland()` (nguyên văn code hiện tại) và `growRift()` (stub: cao nguyên trống) |
| `Palette.kt` | thêm nhánh `RIFT` + `LOOK_RIFT` + 20 màu ở §6.5 |
| `Prefs.kt`, `MainActivity.kt`, `activity_main.xml`, `strings.xml`, `values-vi/strings.xml` | radio thứ ba, chuỗi, `RESTART_KEYS` đã có sẵn `KEY_THEME` |
| `EngineTest` | test `layoutFor`, và **test ảnh cũ không đổi một bit** khi theme = ORIGINAL/MAPLE |

Điều kiện dừng: chọn theme Rift ra một cao nguyên xanh trống, có sương, app vẫn build, test cũ xanh.

### PR 2 — hình học

| File | Việc |
|---|---|
| `RiftPlan.kt` (mới) | toạ độ plan, `toScreen`, `rotate180`, lane paths, sông `q_c(p)`, hố, bãi quái, mask `void`/`wall`/`bridge` |
| `World.kt` | `growRift()` ghi `LEV`, `E`, `riv`, `path`, `res`, `void`, `wall`; dựng `nexus`, `turrets`, `inhibitors`, `camps`, `pits`, tường đá + tán rừng (§6.6) |
| `IslandScene.kt` | nhánh vẽ hư không/tường thay cho biển; cầu; bóng đổ của tường bằng `zb` |
| Preview | `--theme rift --layout column|wide` |

Điều kiện dừng: ảnh tĩnh đúng hình §5, đối xứng 180° đo được bằng máy.

### PR 3 — sự sống & ánh sáng

`Minion`, `CampMonster`, `PitMonster`, `Scuttle` trong `IslandScene.kt`; tắt người/hươu/gà/cá voi/thuyền/hải âu
khi layout là Rift; đèn đêm cho trụ/nhà/hố; particle tro bay.

### PR 4 — đóng gói

README (VI + EN), CHANGELOG, `android/docs/rift-day.png`, `rift-night.png`, `rift-wide.png`, ảnh vàng trong test.

---

## 12. Kiểm thử

| # | Bài | Cách kiểm |
|---|---|---|
| T1 | Đối xứng 180° | xoay mask `void`/`wall`/`riv`/`path` 180° ⇒ sai khác 0 pixel |
| T2 | Thông đường | BFS trên `path` từ nhà xanh tới nhà đỏ qua **từng lane** đều tới đích |
| T3 | Mid ngắn nhất | `len(mid) < len(left)`, `len(left) ≈ len(right)` ± 1% |
| T4 | Sông đúng | sông cắt lane giữa đúng một lần, cắt hai lane biên đúng một lần |
| T5 | Trụ đủ | 18 trụ, 6 ức chế, 2 nhà, 4 trụ nhà; mỗi bên 3 trụ/lane |
| T6 | Vùng an toàn | sau `scrim`: luma TB `v < 0.10` ≤ 0.10 và không pixel > 0.45 trong `v < 0.06`; kiểm trên ảnh render thật ở 307 × 682, cả ngày lẫn đêm |
| T11 | Bãi quái kín | mỗi bãi có ≥ 1 vành `wall` khép kín và đúng 1 cửa; không có ô cỏ trơn nối thẳng hai lane qua bãi |
| T7 | Tất định | cùng seed + cùng khung ⇒ giống từng pixel; khác seed ⇒ khác |
| T8 | Ma trận tỉ lệ | 1.33 / 1.60 / 2.00 / 2.17 / 2.22 / 2.40 dựng không lỗi, không vật thể nào nằm ngoài khung |
| T9 | Chọn layout | `layoutFor(RIFT, 307, 682) = COLUMN`; `layoutFor(RIFT, 611, 344) = WIDE`; `ORIGINAL` luôn `ISLAND` |
| T10 | Ảnh cũ không đổi | hash `IslandScene(ORIGINAL)` và `(MAPLE)` trùng giá trị đã ghi trong test hiện có |

Lệnh preview:

```bash
./gradlew :core:preview --args="--seed heron-bay-8 --theme rift --layout column \
  --size 307x682 --tod 0.30 --fixed-day --out rift-day.png --scale 2"
./gradlew :core:preview --args="--seed heron-bay-8 --theme rift --layout wide \
  --size 611x344 --tod 0.82 --fixed-day --out rift-night.png --scale 2"
```

---

## 13. Đặt tên & bản quyền

- Id nội bộ: `rift`. **Không** dùng chữ "Summoner's Rift", tên tướng, tên item, logo Riot trong app.
- Tên hiển thị gợi ý: **"Đảo Đôi Sông"** (EN: *Twin Rivers*) — mô tả bố cục, không phải tên sản phẩm của Riot.
- Mọi hình là **tự vẽ bằng code**, không lấy asset từ game.
- Nếu README/CHANGELOG có nhắc "lấy cảm hứng từ Liên Minh Huyền Thoại", thêm câu:
  *"Fan project, phi thương mại, không được Riot Games bảo trợ hay chấp thuận."*
  (Riot chỉ cho fan project phi thương mại; app có IP của họ trên Google Play cần license riêng.)

---

## 14. Ảnh tham chiếu — ba vòng, và những gì lấy được

### 14.a Vòng 2 — ảnh đầu tiên đúng bố cục

Ảnh tham chiếu: 841 × 1870 (tỉ lệ **2.224**, đúng khung 20:9 của đặc tả), sinh từ prompt trong
`rift-prompt.md`. Đo bằng máy trên chính ảnh đó:

| Phép đo | Kết quả | Đối chiếu đặc tả |
|---|---|---|
| Tỉ lệ khung | 2.224 | ✓ trùng khung tham chiếu 2.22 |
| Nội dung bản đồ chiếm | `v = 0.103 … 0.883` | ✓ vừa hai dải an toàn (0.120 / 0.900) |
| Luma trung bình dải trên 12% | 0.106 | ✓ tối, đồng hồ đọc được |
| Luma trung bình dải dưới 10% | 0.102 | ✓ tối |
| Pixel sáng (>0.45 luma) trong dải trên | 2.1% | ✗ nên ≈ 0: đó là đỉnh pha lê và cờ của base trên |
| Rừng | 2 khu chữ D có tường bao + bụi rậm | ✓ đúng cảm giác, đọc ra Rift ngay |

**Những gì lấy từ ảnh (đã sửa vào các mục trên):**

1. **Vách đá + dây leo dưới mép cao nguyên** — thay dải tường phẳng. Ảnh cho thấy đây là thứ làm cao nguyên
   "nổi" thay vì "dán"; và nó dùng lại đúng `wallShade` + `lift` đã có. → §6.2
2. **Thác nước ở hai đầu sông** đổ xuống hư không: rẻ (2 cột 2 px + bọt), đổi lại rất nhiều sự sống. → §6.2
3. **Hai khu rừng chữ D có tường bao, một cửa** thay cho 3 bãi quái rời mỗi góc phần tư. → §6.6
4. **Cột cờ dọc tường ngoài** — 12 cột cho cả map, nhận diện đội tức thì. → §6.2
5. **Lề hai bên thu còn 0.028, lề dưới 0.080** — bản đồ sát mép khoẻ hơn; dải an toàn mới là ràng buộc thật. → §4
6. ~~**Base trên phải hạ xuống**: đặt `q` của nhà chính trên ở `0.100` và cắt quầng sáng ở `v < 0.130`.~~
   **Đã bỏ ở vòng 3** — vòng 3 cho thấy ép bố cục tránh hai đầu khung thì mất nhiều hơn được. Thay bằng `scrim`
   trong engine (§9). Giữ lại `q = 0.070` cho nhà chính, cho pha lê vươn cao.

### 14.b Vòng 3 — lấp khung sát mép, và quyết định dừng lặp

| Phép đo | Vòng 2 | Vòng 3 | Nhận xét |
|---|---|---|---|
| Tỉ lệ | 2.224 | 2.224 | ✓ |
| Nội dung | `v 0.103 … 0.883` | `v 0.083 … 0.916` | vòng 3 lấp khung tốt hơn |
| Bề ngang cao nguyên | ~0.88 | **0.021 … 0.996** | vòng 3 sát mép, đúng ý |
| Luma TB dải trên 13% | 0.106 | 0.155 | ✗ nhưng scrim giải được (§9) |
| Pixel sáng > 0.45, dải trên | 2.1% | 6.1% | ✗ như trên |
| Rừng | 2 khu chữ D | 2 khu chữ D + **lửa trại trong rừng** | ✓ vòng 3 hơn |
| Thác nước | 2 | 4 (hai đầu sông + hai bên) | ✓ |
| Vách đá dưới đáy | có, mờ | rõ, có dây leo rủ | ✓ vòng 3 hơn |

**Lấy thêm từ vòng 3:** lửa trại trong rừng (1 cái/khu/nửa map, nhấp nháy ban đêm — dùng lại `Marks.FIRE`),
bệ đá tròn dưới mỗi trụ (3–4 px, `stone` tối 1 bậc), và **bỏ** ràng buộc "dải an toàn phải trống" để đổi lấy
bố cục sát mép.

**Quyết định: dừng lặp ở đây.** Ba vòng đã hội tụ — vòng 3 chỉ còn khác vòng 2 ở chỗ lấp khung và mấy đạo cụ,
không còn khác về bố cục. Vẽ thêm bằng ChatGPT không đổi được gì nữa, vì thứ còn thiếu (nhịp sống, ánh sáng
theo giờ, seed) chỉ có trong code.

**Những gì không lấy:**

| Không lấy | Vì sao |
|---|---|
| Vân cỏ, sỏi, lá cây chi tiết từng mảng | Ở 307 px bề ngang nó thành nhiễu hạt, phá vỡ mảng màu |
| Hàng cây nhỏ riêng lẻ dọc lane | Bị đọc thành "hạt bụi"; tán phải gộp thành khối |
| Chống răng cưa, gradient mềm, ánh sáng tản | Engine vẽ pixel thật; giữ dither, không làm mờ |
| Số lượng đạo cụ dày (đá, thùng, giường, giếng…) | Mỗi đạo cụ < 3 px là vô nghĩa; chỉ giữ thứ đọc được ở thumbnail |

### 14.1 Ngân sách chi tiết theo độ phân giải

Ảnh tham chiếu rộng 841 px; engine vẽ 307 px ở máy phổ thông, 384 px ở mức High, tới ~700 px khi `detail` lớn.
Một cây trong ảnh tham chiếu rộng ~18 px ⇒ ở 307 px chỉ còn **6.5 px**. Vì vậy:

| Vật thể | @ detail 0.8 (307 px) | @ detail 1.0 (384 px) | @ detail ≥ 1.4 |
|---|---|---|---|
| Tán rừng | 1 khối 5–8 px, 2 sắc + dither | khối 7–10 px, 2 sắc | từng cây 4–6 px, 3 sắc |
| Trụ | 4 × 9 px, pha lê 2 px | 6 × 12 px | 8 × 16 px, có bệ đá |
| Nhà chính | 16 × 14 px | 20 × 18 px | 28 × 24 px, có bậc thềm |
| Lính | 2 × 3 px, 1 đốm màu | 3 × 4 px | 4 × 6 px, có bóng |
| Bụi cỏ | 4 × 2 px | 6 × 3 px | 7 × 4 px |
| Cột cờ | 1 × 3 px + 1 px cờ | 2 × 5 px | 3 × 7 px |

Quy tắc: **dưới `detail` 1.2 thì vẽ bằng mảng, không vẽ bằng chi tiết.** Một tán rừng ở 307 px là một hình
bo tròn 2 màu có dither ở mép, không phải 9 cây.

---

### 14.c Vòng 4 — chính engine vẽ

Sau khi code xong, engine tự vẽ ở đúng khung của điện thoại 20:9 (307 × 682):

![Đảo Đôi Sông, engine vẽ, ban ngày](../docs/rift-day.png)
![Đảo Đôi Sông, engine vẽ, ban đêm](../docs/rift-night.png)

| Phép đo | Ảnh ChatGPT vòng 3 | Engine (307 × 682) |
|---|---|---|
| Dựng bản đồ | — | **77 ms** một lần, trên CI |
| Một khung | — | **1.3 ms** ban ngày, **2.4 ms** ban đêm (có grade + đèn) |
| Ngân sách §10 | — | 8 ms/khung ⇒ còn thừa ~3× |
| Luma dải trên 6% | 0.155 (chưa scrim) | **0.05** sau scrim — đạt ngưỡng 0.10 |
| Đối xứng 180° | gần đúng | **đúng tuyệt đối** (cùng một nửa, quay 180°) |
| Số trụ / ức chế / rừng / hố | ước lượng | **18 / 6 / 4 / 2**, kiểm bằng test |

**Những gì engine làm được mà ảnh vẽ không:** đối xứng chính xác, lane dài ngắn đo được, sông cắt mỗi lane đúng
một lần, mọi thứ tất định theo seed, và khung hình chạy 30–60 fps trên máy thật.

**Những gì còn kém hơn ảnh vẽ** (ghi lại để vòng sau, không giấu):

1. **Rừng vẫn hơi giống "vũng"**: bốn cụm tán tròn chồng lên nhau đọc ra khối, nhưng chưa ra *cây*. Cần crown lệch
   tâm hơn và một vài tán nhỏ ở rìa.
2. **Sông gần như thẳng** so với chữ S của ảnh — biên độ 0.013 là quá nhỏ ở 307 px. Tăng lên 0.022 và cho bờ đá
   nhô ra thụt vào.
3. **Trụ vẫn nhỏ** so với tường thành trong ảnh; và thiếu bệ đá nhiều tầng.
4. **Thiếu vách đá có chiều sâu**: hiện là ba dải màu phẳng, chưa có khối đá lồi lõm như ảnh.
5. **Thiếu cột cờ đúng chất** — cờ hiện đọc được nhưng cột mảnh.

Không cái nào là lỗi kiến trúc: cả năm đều là bậc thang chi tiết trong `RiftScene`, sửa được mà không đụng
`RiftPlan` hay test nào.

---

## 15. Câu hỏi mở — cần bạn quyết trước khi code

1. **Mức trung thành**: giữ đủ 3 trụ/lane + 3 ức chế + 2 trụ nhà (18 + 8 sprite nhỏ, hơi rậm ở 300 px), hay rút
   còn 2 trụ/lane cho thoáng? Tôi nghiêng về **giữ đủ** để "đúng chất map".
2. **Hư không**: để tối gần như đen (đẹp, đồng hồ dễ đọc) hay cho hư không có mây tím/xanh như Rift khi xem replay?
3. **Có thêm chế độ "toàn cảnh"** (panorama: khung dọc chỉ nhìn một đoạn sông, camera trôi chậm dọc bản đồ) như
   lựa chọn thứ ba trong phần Giao diện không? Mặc định tắt.
4. **Nhịp sống**: lính 24 s/đợt và hố quái 6 phút (giống trận thật) hay chậm hơn (48 s / 10 phút) cho êm mắt?
   Tôi nghiêng về **chậm hơn**, vì đây là hình nền chứ không phải trận đấu.
