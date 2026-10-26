// Trang trí MOBA cho đảo — lấy cảm hứng từ bản đồ MOBA mẫu (pixel art).
// Thêm: lính đi theo 3 đường (lane), quái rừng ở 4 bãi, cua sông, cờ phấp phới, cổng tím & hố dung nham.
// Tất cả vẽ bằng pixel đè lên frame — không tốn thêm texture, chỉ vài trăm pixel mỗi frame.

const CUT = (v) => (v < 0 ? 0 : v > 255 ? 255 : v | 0);
const PACK = (r, g, b, a = 255) => ((a << 24) | (CUT(b) << 16) | (CUT(g) << 8) | CUT(r)) >>> 0;
const R = (c) => c & 255, G = (c) => (c >>> 8) & 255, B = (c) => (c >>> 16) & 255;

// -- palette MOBA ---------------------------------------------------------
const COL = {
  // lính đỏ (top)
  red: PACK(232, 74, 74), redDark: PACK(140, 28, 28), redHi: PACK(255, 138, 138), redSkin: PACK(242, 188, 148),
  // lính xanh (bottom)
  blu: PACK(72, 132, 232), bluDark: PACK(22, 52, 112), bluHi: PACK(138, 192, 255), bluSkin: PACK(242, 188, 148),
  // cua sông
  crab: PACK(232, 108, 52), crabDark: PACK(120, 52, 18), crabHi: PACK(255, 184, 96),
  // cờ
  flagRed: PACK(200, 38, 38), flagRedHi: PACK(255, 90, 90),
  flagBlu: PACK(38, 98, 200), flagBluHi: PACK(90, 160, 255),
  // quái rừng
  golem: PACK(148, 138, 128), golemDark: PACK(88, 84, 76), golemHi: PACK(210, 200, 188),
  wolf: PACK(96, 96, 112), wolfHi: PACK(168, 168, 188), wolfDark: PACK(52, 52, 64),
  spider: PACK(128, 42, 172), spiderHi: PACK(198, 108, 255), spiderEye: PACK(255, 52, 92),
  slime: PACK(232, 82, 52), slimeHi: PACK(255, 160, 96), slimeDark: PACK(132, 32, 18),
  // cổng tím & dung nham
  portal: PACK(142, 62, 255), portalHi: PACK(188, 132, 255), portalCore: PACK(232, 212, 255),
  lava: PACK(255, 98, 22), lavaHi: PACK(255, 198, 92), lavaDark: PACK(112, 22, 8),
  // môi trường
  towerRing: PACK(255, 232, 128),
};

// -- lanes (đường lính) — 3 lane: mid thẳng, left/right vòng ngoài ---------------
// Waypoints xấp xỉ từ minimap của art/island.png (841x1870)
export const LANES = {
  mid: [
    { x: 420, y: 255 }, // red nexus
    { x: 420, y: 340 },
    { x: 420, y: 455 }, // tower 1 red
    { x: 420, y: 615 }, // tower 2 red
    { x: 420, y: 770 },
    { x: 420, y: 935 }, // bridge
    { x: 420, y: 1070 },
    { x: 420, y: 1170 }, // tower 1 blue
    { x: 420, y: 1330 },
    { x: 420, y: 1490 }, // tower 2 blue
    { x: 420, y: 1605 }, // blue nexus
  ],
  left: [
    { x: 300, y: 265 },
    { x: 200, y: 310 },
    { x: 120, y: 400 },
    { x: 65, y: 520 },
    { x: 52, y: 650 },
    { x: 52, y: 780 },
    { x: 75, y: 880 },
    { x: 150, y: 940 }, // crossing river west
    { x: 280, y: 980 },
    { x: 280, y: 1080 },
    { x: 200, y: 1180 },
    { x: 120, y: 1280 },
    { x: 85, y: 1400 },
    { x: 180, y: 1500 },
    { x: 300, y: 1580 },
  ],
  right: [
    { x: 540, y: 265 },
    { x: 640, y: 310 },
    { x: 720, y: 400 },
    { x: 780, y: 520 },
    { x: 800, y: 650 },
    { x: 800, y: 780 },
    { x: 770, y: 880 },
    { x: 700, y: 940 }, // crossing river east
    { x: 580, y: 980 },
    { x: 580, y: 1080 },
    { x: 640, y: 1180 },
    { x: 720, y: 1280 },
    { x: 760, y: 1400 },
    { x: 660, y: 1500 },
    { x: 540, y: 1580 },
  ],
};

// -- camps rừng -----------------------------------------------------------
export const CAMPS = [
  // bãi quái nằm trên khoảng trống cỏ cạnh rừng (như bản đồ MOBA mẫu), không bị cây che
  { id: "golem_tl", x: 240, y: 708, kind: "golem", r: 24, label: "Golem đá" }, // nam của rừng top-left
  { id: "crab_tr", x: 600, y: 708, kind: "slime", r: 24, label: "Cua đỏ" }, // nam của rừng top-right
  { id: "wolf_bl", x: 240, y: 1165, kind: "wolf", r: 24, label: "Sói xám" }, // bắc của rừng bottom-left
  { id: "spider_br", x: 600, y: 1165, kind: "spider", r: 24, label: "Nhện tím" }, // bắc của rừng bottom-right
];

export const RIVER_CRABS = [
  { x: 175, y: 972, dir: 1 }, // tránh thác nước bên trái
  { x: 355, y: 978, dir: -1 }, // lệch khỏi cầu giữa
  { x: 655, y: 972, dir: 1 },
];

export const PORTALS = [
  { x: 215, y: 775, r: 30, kind: "portal", label: "Cổng tím" }, // cạnh hố đen top-left, giống bản đồ mẫu
  { x: 625, y: 1008, r: 34, kind: "lava", label: "Hố dung nham" }, // phía bắc hố đen bottom-right, gần sông
];

export const FLAGS = [
  // đặt ngay trên tháp canh ngoài cùng (outer turrets) — dễ thấy, không trôi trên trời
  { x: 78, y: 308, poleX: 72, poleY: 272, kind: "red", w: 26, h: 20 },
  { x: 762, y: 308, poleX: 768, poleY: 272, kind: "red", w: 26, h: 20 },
  { x: 78, y: 1508, poleX: 72, poleY: 1472, kind: "blue", w: 26, h: 20 },
  { x: 762, y: 1508, poleX: 768, poleY: 1472, kind: "blue", w: 26, h: 20 },
  // thêm 2 cờ ngay cổng nhà chính (nexus) cho rực rỡ
  { x: 305, y: 202, poleX: 305, poleY: 170, kind: "red", w: 20, h: 16 },
  { x: 535, y: 202, poleX: 535, poleY: 170, kind: "red", w: 20, h: 16 },
  { x: 305, y: 1612, poleX: 305, poleY: 1580, kind: "blue", w: 20, h: 16 },
  { x: 535, y: 1612, poleX: 535, poleY: 1580, kind: "blue", w: 20, h: 16 },
];

// ---------------------------------------------------------------------
function dist(a, b) { return Math.hypot(a.x - b.x, a.y - b.y); }

function buildLane(lane) {
  let lens = [0];
  let total = 0;
  for (let i = 1; i < lane.length; i++) { total += dist(lane[i - 1], lane[i]); lens.push(total); }
  return { pts: lane, lens, total };
}

const LANES_BUILT = {
  mid: buildLane(LANES.mid),
  left: buildLane(LANES.left),
  right: buildLane(LANES.right),
};

function pointOnLane(built, d) {
  d = ((d % built.total) + built.total) % built.total;
  for (let i = 1; i < built.pts.length; i++) {
    if (d <= built.lens[i]) {
      const a = built.pts[i - 1], b = built.pts[i];
      const seg = built.lens[i] - built.lens[i - 1];
      const t = seg ? (d - built.lens[i - 1]) / seg : 0;
      return { x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t, ang: Math.atan2(b.y - a.y, b.x - a.x) };
    }
  }
  return { x: built.pts[built.pts.length - 1].x, y: built.pts[built.pts.length - 1].y, ang: 0 };
}

// ---------------------------------------------------------------------
// sprite plotting helpers (packed pixels)
function put(frame, W, H, x, y, col) {
  if (x < 0 || x >= W || y < 0 || y >= H) return;
  frame[y * W + x] = col;
}
function hline(frame, W, H, x0, x1, y, col) {
  if (y < 0 || y >= H) return;
  if (x0 > x1) [x0, x1] = [x1, x0];
  x0 = Math.max(0, x0); x1 = Math.min(W - 1, x1);
  for (let x = x0; x <= x1; x++) frame[y * W + x] = col;
}

// -- minion sprites ------------------------------------------------------
// Lính 9x10 px — viền đen để nổi trên cả đường đất và cỏ, dễ thấy khi thu nhỏ
function drawMinion(frame, W, H, cx, cy, team, t, bob) {
  const isRed = team === "red";
  const body = isRed ? COL.red : COL.blu;
  const bodyDark = isRed ? COL.redDark : COL.bluDark;
  const bodyHi = isRed ? COL.redHi : COL.bluHi;
  const skin = isRed ? COL.redSkin : COL.bluSkin;
  const outline = PACK(18, 14, 20);
  const by = Math.round(cy + Math.sin(t * 8 + cx * 0.1) * 0.7 + bob);
  const bx = Math.round(cx);
  // viền đen ngoài để lính nổi bật trên nền
  for (let dy = -3; dy <= 4; dy++) for (let dx = -2; dx <= 2; dx++) {
    if (Math.abs(dx) === 2 && Math.abs(dy) >= 3) continue;
    // chỉ viền ngoài, không đè ruột
    if ((dx === 0 && dy >= -2 && dy <= 3) || (Math.abs(dx) === 1 && dy >= -2 && dy <= 3)) continue;
    put(frame, W, H, bx + dx, by + dy, outline);
  }
  // bóng dưới chân
  hline(frame, W, H, bx - 2, bx + 2, by + 4, PACK(0, 0, 0, 180));
  // chân
  put(frame, W, H, bx - 1, by + 3, bodyDark); put(frame, W, H, bx + 1, by + 3, bodyDark);
  // thân
  put(frame, W, H, bx, by + 1, body); put(frame, W, H, bx, by + 2, body);
  put(frame, W, H, bx - 1, by + 1, body); put(frame, W, H, bx + 1, by + 1, body);
  put(frame, W, H, bx - 1, by + 2, bodyDark); put(frame, W, H, bx + 1, by + 2, bodyDark);
  // tay + vũ khí lấp lánh
  const swing = Math.sin(t * 9 + bx) > 0 ? 1 : 0;
  put(frame, W, H, bx + (isRed ? 2 : -2), by + 1 + swing, bodyHi);
  put(frame, W, H, bx + (isRed ? 2 : -2), by + swing, PACK(240, 220, 160)); // lưỡi kiếm
  // đầu
  hline(frame, W, H, bx - 1, bx + 1, by - 1, skin);
  put(frame, W, H, bx, by - 2, skin);
  put(frame, W, H, bx, by - 2, bodyHi); // mũ/helm highlight
  // mắt
  put(frame, W, H, bx - 1, by - 1, PACK(22, 8, 12));
  put(frame, W, H, bx + 1, by - 1, PACK(22, 8, 12));
  // khiên nhỏ phía trước cho dễ phân biệt đội
  put(frame, W, H, bx + (isRed ? 0 : 1), by + 1, bodyHi);
}

function drawCrab(frame, W, H, cx, cy, t, dir) {
  const bx = Math.round(cx + Math.sin(t * 1.3) * 3 * dir);
  const by = Math.round(cy + Math.cos(t * 2.8) * 1.2);
  const outline = PACK(20, 14, 10);
  // viền đen cho cua nổi trên nước xanh
  for (let dx = -6; dx <= 6; dx++) put(frame, W, H, bx + dx, by + 2, outline);
  for (let dx = -5; dx <= 5; dx++) put(frame, W, H, bx + dx, by - 2, outline);
  // thân cua 9x5 lớn hơn, dễ thấy
  hline(frame, W, H, bx - 4, bx + 4, by, COL.crab);
  hline(frame, W, H, bx - 4, bx + 4, by + 1, COL.crabDark);
  hline(frame, W, H, bx - 3, bx + 3, by - 1, COL.crabHi);
  hline(frame, W, H, bx - 2, bx + 2, by - 1, PACK(255, 210, 130));
  // càng to
  put(frame, W, H, bx - 5, by, COL.crabHi); put(frame, W, H, bx + 5, by, COL.crabHi);
  put(frame, W, H, bx - 6, by, outline); put(frame, W, H, bx + 6, by, outline);
  put(frame, W, H, bx - 5, by - 1, COL.crab); put(frame, W, H, bx + 5, by - 1, COL.crab);
  // mắt stalk cao hơn
  put(frame, W, H, bx - 2, by - 2, PACK(20, 20, 24)); put(frame, W, H, bx + 2, by - 2, PACK(20, 20, 24));
  put(frame, W, H, bx - 2, by - 3, PACK(255, 255, 255)); put(frame, W, H, bx + 2, by - 3, PACK(255, 255, 255));
  put(frame, W, H, bx - 2, by - 4, outline); put(frame, W, H, bx + 2, by - 4, outline);
  // chân di chuyển
  const leg = Math.sin(t * 10) > 0 ? 1 : -1;
  put(frame, W, H, bx - 3, by + 2, COL.crabDark); put(frame, W, H, bx + 3, by + 2 + leg, COL.crabDark);
  // bọt nước nhỏ quanh cua khi bơi
  if (Math.sin(t * 4 + bx) > 0.7) put(frame, W, H, bx + dir * 3, by + 1, PACK(200, 230, 255));
}

function drawCampBase(frame, W, H, x, y) {
  // vòng đá rõ nét quanh bãi — nhìn thấy ngay cả khi thu nhỏ như thumbnail MOBA
  const r = 14;
  const stone = PACK(148, 142, 130), stoneDark = PACK(92, 88, 82), dirt = PACK(175, 148, 98), dirtDark = PACK(135, 110, 70);
  // nền đất
  for (let dy = -10; dy <= 10; dy++) for (let dx = -10; dx <= 10; dx++) {
    const d = Math.hypot(dx, dy);
    if (d < 11) {
      const i = (y + dy) * W + (x + dx);
      if (x + dx >= 0 && x + dx < W && y + dy >= 0 && y + dy < H) {
        frame[i] = d < 8 ? dirt : dirtDark;
      }
    }
  }
  // vòng đá ngoài
  for (let dy = -r; dy <= r; dy++) for (let dx = -r; dx <= r; dx++) {
    const d = Math.hypot(dx, dy);
    if (d > r - 1.2 && d < r + 0.8) put(frame, W, H, x + dx, y + dy, stone);
    else if (d > r - 3 && d < r - 1) {
      if ((dx * 2 + dy) % 3 === 0) put(frame, W, H, x + dx, y + dy, stoneDark);
    }
  }
  // lửa trại nhỏ ở giữa dưới quái 1 chút để nổi
  put(frame, W, H, x, y + 7, COL.lavaHi); put(frame, W, H, x + 1, y + 7, COL.lava);
  put(frame, W, H, x - 1, y + 7, COL.lavaDark); put(frame, W, H, x, y + 8, COL.lavaDark);
}
function drawGolem(frame, W, H, x, y, t) {
  drawCampBase(frame, W, H, x, y);
  const bob = Math.sin(t * 0.9) * 1.1;
  const bx = x, by = Math.round(y + bob);
  // viền tối cho nổi trên cỏ
  hline(frame, W, H, bx - 6, bx + 6, by - 3, PACK(30, 28, 24));
  hline(frame, W, H, bx - 6, bx + 6, by + 2, PACK(30, 28, 24));
  // body blocky đá 11x9 — làm to hơn, màu ấm hơn cho dễ thấy
  hline(frame, W, H, bx - 5, bx + 5, by - 2, COL.golem);
  hline(frame, W, H, bx - 5, bx + 5, by - 1, COL.golem);
  hline(frame, W, H, bx - 5, bx + 5, by, COL.golem);
  hline(frame, W, H, bx - 4, bx + 4, by + 1, COL.golemDark);
  hline(frame, W, H, bx - 3, bx + 3, by + 2, COL.golemDark);
  hline(frame, W, H, bx - 4, bx + 4, by - 3, COL.golemHi);
  // gai đá nhỏ trên lưng
  put(frame, W, H, bx, by - 4, COL.golemDark); put(frame, W, H, bx - 2, by - 4, COL.golemDark);
  // eyes glow
  const blink = Math.sin(t * 0.7) > 0.92 ? 0 : 1;
  if (blink) { put(frame, W, H, bx - 2, by - 1, COL.lava); put(frame, W, H, bx + 2, by - 1, COL.lava); put(frame, W, H, bx - 2, by - 2, COL.lavaHi); put(frame, W, H, bx + 2, by - 2, COL.lavaHi); }
  else { put(frame, W, H, bx - 2, by - 1, COL.golemDark); put(frame, W, H, bx + 2, by - 1, COL.golemDark); }
  // hơi thở: bụi nhẹ
  if (Math.sin(t * 1.2) > 0.85) put(frame, W, H, bx, by - 4, PACK(180, 170, 160));
}

function drawSlime(frame, W, H, x, y, t) {
  drawCampBase(frame, W, H, x, y);
  const wob = Math.sin(t * 2.2) * 1.4;
  const bx = Math.round(x + wob), by = Math.round(y + Math.cos(t * 1.8) * 0.9);
  // viền cho nổi
  hline(frame, W, H, bx - 5, bx + 5, by + 1, PACK(80, 20, 10));
  // slime round 9x7 — màu cam đỏ nổi trên cỏ
  hline(frame, W, H, bx - 3, bx + 3, by, COL.slime);
  hline(frame, W, H, bx - 4, bx + 4, by + 1, COL.slime);
  hline(frame, W, H, bx - 4, bx + 4, by - 1, COL.slimeHi);
  hline(frame, W, H, bx - 2, bx + 2, by - 2, COL.slimeHi);
  hline(frame, W, H, bx - 1, bx + 1, by - 2, PACK(255, 255, 255));
  // eyes
  put(frame, W, H, bx - 2, by, PACK(20, 10, 20)); put(frame, W, H, bx + 2, by, PACK(20, 10, 20));
  put(frame, W, H, bx - 2, by - 1, PACK(255, 255, 255)); put(frame, W, H, bx + 2, by - 1, PACK(255, 255, 255));
  // highlight breath
  if (Math.sin(t * 3) > 0.8) hline(frame, W, H, bx - 2, bx + 2, by + 2, COL.slimeHi);
  // bong bóng nhỏ
  if (Math.sin(t * 2.5 + x) > 0.85) put(frame, W, H, bx + 3, by - 3, PACK(255, 220, 180));
}

function drawWolf(frame, W, H, x, y, t) {
  drawCampBase(frame, W, H, x, y);
  const bob = Math.sin(t * 1.4) * 1.0;
  const bx = Math.round(x), by = Math.round(y + bob);
  // viền
  hline(frame, W, H, bx - 6, bx + 6, by + 1, PACK(30, 28, 36));
  // wolf body 11x6 low
  hline(frame, W, H, bx - 5, bx + 5, by, COL.wolf);
  hline(frame, W, H, bx - 4, bx + 4, by + 1, COL.wolfDark);
  hline(frame, W, H, bx - 3, bx + 3, by - 1, COL.wolfHi);
  // ears
  put(frame, W, H, bx - 4, by - 2, COL.wolfDark); put(frame, W, H, bx + 4, by - 2, COL.wolfDark);
  put(frame, W, H, bx - 4, by - 3, COL.wolfHi); put(frame, W, H, bx + 4, by - 3, COL.wolfHi);
  // snout
  put(frame, W, H, bx, by - 1, PACK(30, 30, 36)); put(frame, W, H, bx, by, PACK(60, 60, 72));
  // eyes glow at night
  if (Math.sin(t * 2) > 0.6) { put(frame, W, H, bx - 2, by - 1, PACK(255, 220, 80)); put(frame, W, H, bx + 2, by - 1, PACK(255, 220, 80)); }
  else { put(frame, W, H, bx - 2, by - 1, PACK(40, 10, 10)); put(frame, W, H, bx + 2, by - 1, PACK(40, 10, 10)); }
  // tail wag
  const wag = Math.sin(t * 6) > 0 ? 1 : -1;
  put(frame, W, H, bx + 6 * wag, by, COL.wolfDark);
  put(frame, W, H, bx + 6 * wag, by - 1, COL.wolfHi);
}

function drawSpider(frame, W, H, x, y, t) {
  drawCampBase(frame, W, H, x, y);
  const bob = Math.sin(t * 1.1) * 1.2;
  const bx = Math.round(x), by = Math.round(y + bob);
  // viền
  hline(frame, W, H, bx - 4, bx + 4, by + 1, PACK(40, 10, 60));
  // body 7x5 — tím nổi
  hline(frame, W, H, bx - 3, bx + 3, by, COL.spider);
  hline(frame, W, H, bx - 3, bx + 3, by + 1, COL.spider);
  hline(frame, W, H, bx - 2, bx + 2, by - 1, COL.spiderHi);
  hline(frame, W, H, bx - 1, bx + 1, by - 2, COL.spiderHi);
  // eyes 4 glow
  put(frame, W, H, bx - 2, by - 1, COL.spiderEye); put(frame, W, H, bx + 2, by - 1, COL.spiderEye);
  put(frame, W, H, bx - 1, by - 2, PACK(255, 180, 200)); put(frame, W, H, bx + 1, by - 2, PACK(255, 180, 200));
  if (Math.sin(t * 3) > 0.5) { put(frame, W, H, bx - 2, by - 1, PACK(255, 255, 255)); put(frame, W, H, bx + 2, by - 1, PACK(255, 255, 255)); }
  // legs creepy crawl — to hơn
  const legPhase = t * 5;
  for (let i = -3; i <= 3; i += 2) {
    const ly = Math.round(by + Math.sin(legPhase + i) * 1.2);
    put(frame, W, H, bx + i, ly + 1, COL.spider);
    put(frame, W, H, bx + i + Math.sign(i || 1), ly + 2, COL.spider);
    put(frame, W, H, bx + i, ly + 2, PACK(80, 20, 100));
  }
  // web shimmer occasionally
  if (Math.sin(t * 0.8 + x) > 0.92) { put(frame, W, H, bx - 6, by - 2, PACK(200, 200, 255)); put(frame, W, H, bx + 6, by - 2, PACK(200, 200, 255)); }
}

// portal & lava — làm to & rõ hơn, giống bản đồ mẫu
function drawPortal(frame, W, H, x, y, t) {
  const pulse = 0.9 + Math.sin(t * 1.8) * 0.12;
  const r = 18;
  for (let dy = -r; dy <= r; dy++) for (let dx = -r; dx <= r; dx++) {
    const d = Math.hypot(dx, dy * 1.15);
    if (d > r) continue;
    const ang = Math.atan2(dy, dx) + t * 1.1 * (1 - d / r * 0.4);
    const arm = (Math.sin(ang * 3 + t * 2.8) + 1) * 0.5;
    if (d < 4) { put(frame, W, H, x + dx, y + dy, COL.portalCore); continue; }
    if (d < 8 && arm > 0.55) put(frame, W, H, x + dx, y + dy, COL.portalHi);
    else if (d < r * pulse && arm > 0.32) {
      put(frame, W, H, x + dx, y + dy, arm > 0.68 ? COL.portalHi : COL.portal);
    } else if (d < r && arm > 0.25) {
      // edge particles
      if ((dx + dy) % 4 === 0) put(frame, W, H, x + dx, y + dy, COL.portal);
    }
  }
  // outer ring breath — rõ hơn
  for (let a = 0; a < 6.28; a += 0.3) {
    const rx = Math.round(x + Math.cos(a + t * 0.9) * (r + Math.sin(t * 2 + a) * 1.2)), ry = Math.round(y + Math.sin(a + t * 0.9) * r * 0.85);
    const col = Math.sin(t * 1.6 + a) > 0.2 ? COL.portalHi : COL.portal;
    put(frame, W, H, rx, ry, col);
  }
  // core flare
  if (Math.sin(t * 2.2) > 0.7) { put(frame, W, H, x, y - 2, PACK(255, 255, 255)); put(frame, W, H, x + 1, y, PACK(255, 255, 255)); }
}

function drawLava(frame, W, H, x, y, t) {
  const r = 20;
  for (let dy = -r; dy <= r; dy++) for (let dx = -r; dx <= r; dx++) {
    const d = Math.hypot(dx, dy);
    if (d > r) continue;
    if (d < 5) { // core bright sôi
      const bub = Math.sin(t * 3.2 + dx * 0.55 + dy * 0.75);
      put(frame, W, H, x + dx, y + dy, bub > 0.3 ? COL.lavaHi : COL.lava);
      continue;
    }
    if (d < 9) {
      const n = Math.sin(dx * 0.65 + t * 2.0) + Math.cos(dy * 0.85 + t * 1.6);
      if (n > 0.5) put(frame, W, H, x + dx, y + dy, COL.lavaHi);
      else if (n > -0.2) put(frame, W, H, x + dx, y + dy, COL.lava);
    } else if (d < r) {
      const pop = Math.sin(t * 3.8 + x * 0.05 + y * 0.05) > 0.88;
      if (pop && d > 13) put(frame, W, H, x + dx, y + dy, COL.lavaHi);
      else if ((dx * 2 + dy) % 3 === 0) put(frame, W, H, x + dx, y + dy, COL.lavaDark);
      else if (d < r - 1) put(frame, W, H, x + dx, y + dy, PACK(60, 18, 10));
    }
  }
  // rising ember particles + smoke puff
  for (let k = 0; k < 5; k++) {
    const py = y - 8 - ((t * 14 + k * 18) % 26);
    const px = x + Math.sin(t * 1.9 + k) * 7;
    if (py > y - r && py < y + r) {
      put(frame, W, H, Math.round(px), Math.round(py), k % 2 === 0 ? COL.lavaHi : PACK(120, 120, 120));
    }
  }
  // outer glow ring pulsing
  if (Math.sin(t * 1.3) > 0.4) {
    for (let a = 0; a < 6.28; a += 0.5) {
      const rx = Math.round(x + Math.cos(a) * r), ry = Math.round(y + Math.sin(a) * r);
      if ((Math.floor(a * 3 + t * 5) % 2) === 0) put(frame, W, H, rx, ry, COL.lavaHi);
    }
  }
}

// flags waving — cloth sim với viền & bóng để nổi trên tường đá
function drawFlag(frame, W, H, flag, t) {
  const { x, y, poleX, poleY, kind, w, h } = flag;
  const baseCol = kind === "red" ? COL.flagRed : COL.flagBlu;
  const hiCol = kind === "red" ? COL.flagRedHi : COL.flagBluHi;
  const outline = PACK(32, 24, 24);
  // pole — gỗ sậm + đầu nhọn vàng
  for (let py = poleY; py <= y + h; py++) {
    put(frame, W, H, poleX, py, PACK(110, 90, 68));
    put(frame, W, H, poleX + 1, py, PACK(86, 70, 52));
    put(frame, W, H, poleX - 1, py, outline);
  }
  put(frame, W, H, poleX, poleY - 1, PACK(220, 200, 160)); // finial
  put(frame, W, H, poleX, poleY - 2, PACK(255, 240, 180));
  // cloth waving: each row offset by sin — càng dưới càng bay mạnh
  for (let dy = 0; dy < h; dy++) {
    const wave = Math.sin(t * 3.0 + dy * 0.5 + poleX * 0.02) * (1.2 + dy * 0.22);
    const ox = Math.round(wave);
    const rowX = x + ox;
    const shade = Math.sin(t * 3.0 + dy * 0.5) > 0.2 ? hiCol : baseCol;
    // viền đen cho cờ dễ thấy
    put(frame, W, H, rowX - 1, y + dy, outline);
    put(frame, W, H, rowX + w + 1, y + dy, outline);
    hline(frame, W, H, rowX, rowX + w, y + dy, shade);
    // edge flicker highlight
    if (dy % 2 === 0) put(frame, W, H, rowX + w, y + dy, hiCol);
    // emblem: flame / water drop — làm to hơn
    if (dy === Math.floor(h / 2) && w > 14) {
      const ex = rowX + Math.floor(w / 2);
      if (kind === "red") {
        put(frame, W, H, ex, y + dy, PACK(255, 220, 80)); put(frame, W, H, ex, y + dy - 1, PACK(255, 240, 180));
        put(frame, W, H, ex - 1, y + dy, PACK(255, 180, 40)); put(frame, W, H, ex + 1, y + dy, PACK(255, 180, 40));
      } else {
        put(frame, W, H, ex, y + dy, PACK(120, 200, 255)); put(frame, W, H, ex, y + dy - 1, PACK(200, 230, 255));
        put(frame, W, H, ex - 1, y + dy, PACK(80, 160, 255)); put(frame, W, H, ex + 1, y + dy, PACK(80, 160, 255));
      }
    }
    // bóng dưới cờ
    if (dy === h - 1) hline(frame, W, H, rowX, rowX + w, y + dy + 1, PACK(0, 0, 0, 100));
  }
}

// lane creep direction arrow (small chevron on road) — optional road pulse
function drawLaneGlow(frame, W, H, lane, t, team) {
  // subtle moving dash on lane (like contested light)
  const built = LANES_BUILT[lane];
  const speed = 42; // px/s
  for (let k = 0; k < 3; k++) {
    const d = (t * speed + k * built.total / 3) % built.total;
    const p = pointOnLane(built, team === "red" ? d : built.total - d);
    const col = team === "red" ? PACK(255, 80, 80, 110) : PACK(80, 140, 255, 110);
    // soft 3x3 dot blend: just put a cross
    put(frame, W, H, Math.round(p.x), Math.round(p.y), col);
    put(frame, W, H, Math.round(p.x) + 1, Math.round(p.y), col);
    put(frame, W, H, Math.round(p.x) - 1, Math.round(p.y), col);
    put(frame, W, H, Math.round(p.x), Math.round(p.y) + 1, col);
  }
}

// ---------------------------------------------------------------------
// buildDecor: precompute lanes + list of dirty rects to merge into world.dirty
export function buildDecor(world) {
  const W = world.W, H = world.H;
  // estimate dirty by sweeping lane bboxes + camps etc — we brute mark a mask of 40k pixels then add to world.dirty
  const decor = {
    lanes: LANES_BUILT,
    // minion spacing
    minionsPerLane: 5, // mỗi bên
    crabPhase: [0, 1.2, 2.4],
  };
  return decor;
}

// state decor options
export const DEFAULT_DECOR = Object.freeze({
  enabled: true,
  minions: true,
  jungle: true,
  crabs: true,
  flags: true,
  portals: true,
  laneGlow: true,
});

export function decorState(o = {}) { return { ...DEFAULT_DECOR, ...o }; }

// main paint
export function paintDecor(frame, world, decor, t, s) {
  const W = world.W, H = world.H;
  const d = s.decor ?? DEFAULT_DECOR;
  if (!d.enabled) return;

  // Lane glow (nhịp đường)
  if (d.laneGlow) {
    drawLaneGlow(frame, W, H, "mid", t, "red");
    drawLaneGlow(frame, W, H, "mid", t, "blu");
    drawLaneGlow(frame, W, H, "left", t, "red");
    drawLaneGlow(frame, W, H, "right", t, "blu");
  }

  // Lính — march along 3 lanes, both directions
  if (d.minions) {
    const lanes = ["mid", "left", "right"];
    const speed = 28 + (s.wind * 6); // wind nudges them a bit faster
    for (const lane of lanes) {
      const built = LANES_BUILT[lane];
      // red wave đi xuống (tăng d), blue wave đi lên (giảm d)
      for (let i = 0; i < decor.minionsPerLane; i++) {
        const off = i * 48; // spacing along lane
        const dRed = (t * speed + off) % built.total;
        const dBlu = built.total - ((t * speed + off + 24) % built.total);
        const pR = pointOnLane(built, dRed);
        const pB = pointOnLane(built, dBlu);
        // staggered spawn delay per lane so they don't clump
        const laneShift = lane === "mid" ? 0 : lane === "left" ? 14 : 28;
        const pr = pointOnLane(built, (dRed + laneShift) % built.total);
        const pb = pointOnLane(built, (dBlu - laneShift + built.total) % built.total);
        // don't draw if near nexus core (inside base)
        if (pr.y > 250 && pr.y < 1610) drawMinion(frame, W, H, pr.x, pr.y, "red", t + i, 0);
        if (pb.y > 250 && pb.y < 1610) drawMinion(frame, W, H, pb.x, pb.y, "blu", t + i + 0.5, 0);
      }
    }
  }

  // Quái rừng — 4 bãi, thở/nháy, lửa trại lấp lánh
  if (d.jungle) {
    for (const camp of CAMPS) {
      // campfire flicker beneath
      const flick = Math.sin(t * 7 + camp.x * 0.1) > 0.3 ? COL.lavaHi : COL.lava;
      if (camp.y > 400 && camp.y < 1600) {
        put(frame, W, H, camp.x, camp.y + 8, flick);
        put(frame, W, H, camp.x + 1, camp.y + 8, flick);
        put(frame, W, H, camp.x - 1, camp.y + 8, flick);
        // slight light bloom 1px around
        if (Math.sin(t * 4 + camp.x) > 0.6) {
          put(frame, W, H, camp.x, camp.y + 9, COL.lava);
        }
      }
      switch (camp.kind) {
        case "golem": drawGolem(frame, W, H, camp.x, camp.y, t + camp.x * 0.01); break;
        case "slime": drawSlime(frame, W, H, camp.x, camp.y, t + camp.y * 0.01); break;
        case "wolf": drawWolf(frame, W, H, camp.x, camp.y, t + camp.x * 0.008); break;
        case "spider": drawSpider(frame, W, H, camp.x, camp.y, t + camp.y * 0.007); break;
      }
    }
  }

  // Cua sông
  if (d.crabs) {
    for (let i = 0; i < RIVER_CRABS.length; i++) {
      const c = RIVER_CRABS[i];
      // cua bơi qua lại trong sông, không đi quá bờ
      const swimX = c.x + Math.sin(t * 0.5 + i) * 28 * c.dir;
      drawCrab(frame, W, H, swimX, c.y, t + i, c.dir);
    }
  }

  // Cổng & dung nham
  if (d.portals) {
    for (const p of PORTALS) {
      if (p.kind === "portal") drawPortal(frame, W, H, p.x, p.y, t);
      else drawLava(frame, W, H, p.x, p.y, t);
    }
  }

  // Cờ phấp phới
  if (d.flags) {
    for (const f of FLAGS) drawFlag(frame, W, H, f, t);
  }

  // Tower range faint pulse at night (như trong MOBA, trụ có vòng)
  if (d.enabled && s.tod > 0.45) {
    const nightPulse = Math.sin(t * 1.2) * 0.5 + 0.5;
    if (nightPulse > 0.6) {
      // pick a few tower lights to show faint ring
      const picks = [2, 3, 4, 7, 14, 15, 18, 26];
      for (const idx of picks) {
        const L = world.lights[idx]; if (!L) continue;
        const r = Math.round(L.r * 0.55);
        const x = L.x, y = L.y;
        // dotted circle 12 points
        for (let a = 0; a < 12; a++) {
          const ang = (a / 12) * Math.PI * 2 + t * 0.3;
          const rx = Math.round(x + Math.cos(ang) * r), ry = Math.round(y + Math.sin(ang) * r * 0.65);
          const col = nightPulse > 0.8 ? COL.towerRing : PACK(255, 232, 128);
          // only draw if bright enough to see at night
          if ((a + Math.floor(t * 2)) % 3 === 0) put(frame, W, H, rx, ry, col);
        }
      }
    }
  }
}

// dirty estimation: lanes + camps + crabs + portals + flags need repaint
// we grow world.dirty to cover them approx by 40px bboxes — caller merges
export function decorDirty(W, H) {
  const set = new Set();
  const addBox = (cx, cy, rw, rh) => {
    for (let dy = -rh; dy <= rh; dy++) for (let dx = -rw; dx <= rw; dx++) {
      const x = cx + dx, y = cy + dy;
      if (x >= 0 && x < W && y >= 0 && y < H) set.add(y * W + x);
    }
  };
  // lanes thickened
  for (const lane of Object.values(LANES)) for (const pt of lane) addBox(pt.x, pt.y, 10, 10);
  // camps
  for (const c of CAMPS) addBox(c.x, c.y, 18, 18);
  // crabs
  for (const c of RIVER_CRABS) addBox(c.x, c.y, 28, 12);
  // portals
  for (const p of PORTALS) addBox(p.x, p.y, p.r + 4, p.r + 4);
  // flags
  for (const f of FLAGS) addBox(f.x + 11, f.y + 9, 18, 14);
  return Int32Array.from(set);
}
