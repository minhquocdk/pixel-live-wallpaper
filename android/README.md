# Đảo sống — hình nền động viết bằng Kotlin

![Đảo ban ngày: ruộng bậc thang, sông, rừng và làng](docs/island-day.png)

![Đảo ban đêm: đèn lồng, cửa sổ sáng, đom đóm](docs/island-night.png)

Một hòn đảo pixel được **sinh ra từ một cụm từ hạt giống**, rồi sống tiếp trên màn hình chính: nước chảy, thuyền
bơi, người đi trên đường, hươu gặm cỏ, hải âu lượn, đèn lồng sáng lên khi chiều xuống, và một ngày — đêm — bình minh
lặp lại mãi. Mọi pixel đều do code vẽ, **không có một file ảnh nào** trong app.

Đây là bản chuyển [ví dụ `examples/island`](../examples/island/) của repo
[procedural-almanac](../README.md) sang **Kotlin**, chạy như một **live wallpaper thật** (một `WallpaperService`),
không phải app mở lên rồi vẽ vào một canvas.

Cùng một hạt giống cho ra **cùng một hòn đảo** như bản chạy trên trình duyệt: hạt giống `heron-bay-8` sinh ra
13 nhà, 190 cây, 12 cây dừa, 34 khóm sậy, 11 đèn lồng, 3 con đường và ngọn hải đăng ở (362, 131) — giống từng con số.

---

## Lấy APK (GitHub build hộ)

Không cần cài Android Studio hay JDK trên máy bạn:

1. Mở tab **Actions** của repo, chọn workflow **android**, bấm vào lần chạy mới nhất (workflow tự chạy mỗi lần có
   thay đổi trong `android/`, mỗi đêm, hoặc bấm **Run workflow** để chạy ngay).
2. Ở cuối trang, mục **Artifacts**, tải:
   - **`island-live-wallpaper`** — trong đó có `island-live-wallpaper-debug.apk` và `island-live-wallpaper-release.apk`
     (giải nén ra là cài được);
   - `island-still` — ảnh PNG do chính engine vẽ, để xem trước mà không cần điện thoại;
   - `test-report` — kết quả 11 bài kiểm tra của engine.
3. Bản release cũng được đính vào tab **Releases** khi bạn push một tag `v*` (ví dụ `git tag v1.0 && git push origin v1.0`).

Mỗi APK được ký bằng khoá debug nên cài thẳng lên máy là chạy; muốn ký bằng khoá riêng thì đặt các biến
`KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` trong phần **Secrets** của repo.

## Cài và đặt làm hình nền

1. Chép APK sang điện thoại và cài (cho phép "Cài từ nguồn không xác định" một lần).
2. Mở app **Island** → bấm **Đặt làm hình nền động** / *Set as live wallpaper*.
3. Hoặc tự làm: giữ lâu màn hình chính → **Hình nền** → **Hình nền động** → **Island**.
4. Android 8.0 trở lên (API 26). Không cần quyền nào, không có mạng, không quảng cáo, không thu thập gì.

## Dùng trong app

| Thứ | Làm gì |
| --- | --- |
| **Hạt giống** | Gõ bất cứ gì: `heron-bay-8`, `mua-he-3`, `42`. Số thì dùng thẳng số đó, chữ thì băm thành số. |
| **Ngẫu nhiên** | Bấm là ra hạt giống mới (một từ đầu + một từ cuối + số, ví dụ `amber-vale-7`) và đảo mọc lại ngay. |
| **Nhấn đúp lên hình nền** | Đổi sang một hạt giống ngẫu nhiên khác mà không cần mở app. |
| **Cỡ tranh** | *Tự động* (mặc định) tự đo tốc độ máy rồi chọn cỡ; hoặc ép *Nhỏ / Vừa / Lớn*. |
| **Số khung mỗi giây** | 24 (êm), 30 (mặc định), 60 (mượt, tốn pin hơn). |
| **Một ngày dài** | 5, 10, 20 hay 40 phút — chu kỳ ngày/đêm lặp lại vô hạn. |
| **Hình nền đang chạy** | Dòng trạng thái do chính hình nền ghi ra: hạt giống, số hiệu, cỡ tranh thật, số ms mỗi khung, fps, số nhà và cây. |

## Nhanh hơn bản trên trình duyệt ở đâu

| | Bản web (`examples/island`) | Bản Kotlin |
| --- | --- | --- |
| Một khung | ~0,85 giây (cả khung, kể cả chữ) | **~7 ms** ở 384×216, **~13 ms** ở 768×432 (JVM trên laptop) |
| Cỡ tranh | cố định 384×216 | máy tự chọn (mặc định ~210 000 pixel, ví dụ 300×660), đổi được tới 420 000 |
| Dựng đảo | ~0,5 giây mỗi lần đổi hạt giống | ~0,5 giây, **trên thread nền**, hình nền cũ vẫn chạy |
| Khi không nhìn thấy | vẫn vẽ | **dừng hẳn** (khoá màn hình, app khác che) |

Bốn điều làm nên tốc độ đó:

- **Nền tĩnh vẽ một lần.** Địa hình, ruộng, nhà, cây, đường, bóng đổ được vẽ vào một *khung nền* ngay khi đảo mọc
  xong; mỗi khung sau chỉ copy khung nền rồi vẽ phần động (nước, người, thú, chim, thuyền, ánh sáng theo giờ, đèn).
- **Mỗi vật đứng yên là một sprite dựng sẵn** — mỗi cây, mỗi nhà — thay vì vẽ lại từ đầu mỗi khung.
- **Blit nguyên bitmap.** Engine vẽ ở cỡ tranh của nó (vài trăm pixel), rồi đưa cả bitmap lên `Surface` một lần,
  không lọc, không nội suy: điện thoại không phải tô lại 1440 pixel cho mỗi pixel tranh, mà tranh vẫn sắc kiểu pixel.
- **Tự chỉnh trong chế độ Tự động.** Engine đo thời gian thật của từng khung; nếu khung vượt 70% ngân sách thời gian
  thì thu tranh nhỏ lại một nấc, nếu còn nhanh thì nới ra — tối đa mỗi giây một lần, nên nó ổn định chứ không giật.

## Cấu trúc

```
android/
  core/                       Kotlin thuần, không phụ thuộc Android
    src/main/kotlin/dev/almanac/island/
      Maths, Rng, Noise, Colour, Palette, Water, Shapes, Sprite,
      World, Relief, Light, IslandScene      ← engine, 1:1 với scene.js
      preview/PreviewMain.kt                 ← vẽ ảnh PNG trên laptop/CI
    src/test/kotlin/dev/almanac/island/EngineTest.kt   ← 11 bài so khớp với bản JS
  app/
    src/main/java/dev/almanac/island/wallpaper/
      IslandWallpaperService.kt   ← WallpaperService: vòng vẽ, đo tốc độ, nhấn đúp, nghe đổi cài đặt
      MainActivity.kt             ← màn hình cài đặt duy nhất
      Prefs.kt, SeedWords.kt
    src/main/res/                 ← layout, màu, drawable (toàn bộ bằng XML, không ảnh)
  gradlew, gradle/                ← Gradle 8.9 wrapper, build được ở CI
```

`core/` không có một dòng `android.*` nào, nên nó chạy được trên JVM thường: đó là lý do có thể kiểm tra engine
và vẽ ảnh ngay trong GitHub Actions, không cần máy ảo Android.

## Tự build

Cần JDK 17 và Android SDK 35 (`ANDROID_HOME`), hoặc cứ để GitHub Actions làm:

```bash
cd android
./gradlew :core:test                      # 11 bài kiểm tra engine
./gradlew :app:assembleDebug              # ra app/build/outputs/apk/debug/*.apk
./gradlew :core:preview --args="--seed heron-bay-8 --t 13.05 --fixed-day --tod 0.3 --out island.png --scale 2"
```

`--tod` là giờ trong ngày (0..1: 0,3 là buổi sáng, 0,8 là đêm), `--scale 2` phóng to ảnh cho dễ nhìn.

## Kiểm thử

`EngineTest` không so engine với chính nó: mỗi con số kỳ vọng được lấy từ bản JavaScript trong `src/`, nên bài kiểm
tra bắt được lệch một bit. Nó phủ: băm và hạt giống, nhiễu value/fractal, `SeededRandom`, hình dạng đảo, đóng gói
màu `0xAARRGGBB`, pha màu (grade), nước, khoảng cách chamfer, tìm đường A*, mọc đảo thật (có pixel, có chuyển động,
cùng seed thì cùng đảo), khung dọc kiểu điện thoại, và cách sinh hạt giống từ hai danh sách từ.

## Ghi công và giấy phép

Engine là bản chuyển của [procedural-almanac](../README.md) — 162 kỹ thuật, mỗi kỹ thuật dẫn nguồn — theo giấy phép
MIT. Bản Kotlin này cũng dùng giấy phép MIT như repo mẹ.
