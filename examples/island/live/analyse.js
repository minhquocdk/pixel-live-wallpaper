// Reading a painted island: given a picture of a map, work out what in it can move. The engine (live.js) paints light and
// water over art it does not own, so first it has to find the parts of the picture worth painting on: the water (and
// which of the water is falling), the foam at its edges, the land, the grass, and every crystal that could glow. Nothing
// is placed by hand: a colour test and a walk over the pixels find them, so the engine works for another map too.
//
// The art this example paints on is examples/island/live/art/island.png: an island in two halves, a red shrine at the top
// and a blue one at the bottom, a river with a bridge between them, and a path between crystal-topped pillars.

/**
 * A connected group of marked pixels: how big it is, where its middle is, the box round it, and how much of that box it
 * fills (a diamond fills about half of its box, a banner nearly all of it).
 * @typedef {{area: number, cx: number, cy: number, x0: number, y0: number, x1: number, y1: number, fill: number,
 *   best: number}} Blob
 */

/**
 * The blobs of a marked picture: every group of marked pixels joined at a side or a corner, walked once with a stack of
 * its own, so a 1.6-million-pixel picture costs one pass and no recursion.
 * @param {Uint8Array} mark w by h, row by row: nonzero where the picture is marked
 * @param {number} w
 * @param {number} h
 * @returns {{list: Blob[], label: Int32Array}} one entry for each group, and which group each pixel of the picture
 *   belongs to (-1 for none), so a group that is wanted can be found again in the picture
 * @see flood-fill-wikipedia
 */
export function blobs(mark, w, h) {
  const seen = new Uint8Array(w * h),
    stack = new Int32Array(w * h),
    label = new Int32Array(w * h).fill(-1),
    out = [];
  for (let s = 0; s < mark.length; s++) {
    if (seen[s] || !mark[s]) continue;
    let top = 0,
      area = 0,
      sx = 0,
      sy = 0,
      best = s,
      bx0 = w,
      by0 = h,
      bx1 = -1,
      by1 = -1;
    stack[top++] = s;
    seen[s] = 1;
    label[s] = out.length;
    while (top) {
      const i = stack[--top],
        y = (i / w) | 0,
        x = i - y * w;
      area++;
      sx += x;
      sy += y;
      if (x < bx0) bx0 = x;
      if (x > bx1) bx1 = x;
      if (y < by0) by0 = y;
      if (y > by1) by1 = y;
      if (i > best) best = i;
      for (let dy = -1; dy <= 1; dy++) {
        const ny = y + dy;
        if (ny < 0 || ny >= h) continue;
        for (let dx = -1; dx <= 1; dx++) {
          const nx = x + dx;
          if (nx < 0 || nx >= w) continue;
          const k = ny * w + nx;
          if (seen[k] || !mark[k]) continue;
          seen[k] = 1;
          label[k] = out.length;
          stack[top++] = k;
        }
      }
    }
    const bw = bx1 - bx0 + 1,
      bh = by1 - by0 + 1;
    out.push({
      area,
      cx: Math.round(sx / area),
      cy: Math.round(sy / area),
      x0: bx0,
      y0: by0,
      x1: bx1,
      y1: by1,
      fill: area / (bw * bh),
      best,
      id: out.length,
    });
  }
  return { list: out, label };
}

/**
 * How long a run of water a pixel is in, across and down: for each pixel, the length of the unbroken run of water through
 * it left to right, and the same top to bottom. Water in a river is long across and long down too; water down a
 * waterfall is long down and short across, which is how the falls are told from the river.
 * @param {Uint8Array} mask w by h, row by row
 * @param {number} w
 * @param {number} h
 * @returns {{runX: Uint16Array, runY: Uint16Array}} the run's length through each pixel, each way (1 where the pixel is
 *   not water)
 * @see run-length-encoding-wikipedia
 */
function runs(mask, w, h) {
  const runX = new Uint16Array(w * h),
    runY = new Uint16Array(w * h);
  for (let y = 0; y < h; y++) {
    const row = y * w;
    let run = 0;
    for (let x = 0; x < w; x++) {
      const i = row + x;
      if (mask[i]) runX[i] = ++run;
      else run = 0;
    }
    run = 0;
    for (let x = w - 1; x >= 0; x--) {
      const i = row + x;
      if (mask[i]) {
        run++;
        if (run > runX[i]) runX[i] = run;
      } else run = 0;
    }
  }
  for (let x = 0; x < w; x++) {
    let run = 0;
    for (let y = 0, i = x; y < h; y++, i += w) {
      if (mask[i]) runY[i] = ++run;
      else run = 0;
    }
    run = 0;
    for (let y = h - 1, i = y * w + x; y >= 0; y--, i -= w) {
      if (mask[i]) {
        run++;
        if (run > runY[i]) runY[i] = run;
      } else run = 0;
    }
  }
  return { runX, runY };
}

// the packed pixel's channels, as the almanac keeps them: red lowest
const red = (c) => c & 255,
  green = (c) => (c >>> 8) & 255,
  blue = (c) => (c >>> 16) & 255,
  pack = (r, g, b) => ((255 << 24) | ((b & 255) << 16) | ((g & 255) << 8) | (r & 255)) >>> 0;

/**
 * A count of pixels that pass a test, as a mask to walk: a map of everything of one kind in the picture.
 * @param {Uint32Array} px the picture, packed
 * @param {number} w
 * @param {number} h
 * @param {function(number, number, number, number): boolean} test takes red, green, blue and the pixel's index
 * @returns {Uint8Array} 1 where the test passed
 * @see thresholding-image-processing-wikipedia
 */
function mark(px, w, h, test) {
  const out = new Uint8Array(w * h);
  for (let i = 0; i < out.length; i++) {
    const c = px[i];
    if (test(red(c), green(c), blue(c), i)) out[i] = 1;
  }
  return out;
}

/**
 * Everything found in the art, ready for the engine to paint on: each layer as the list of the pixels it paints (an
 * index into the picture's pixels, row by row), the runs of falling water, the lamps, and how much of each there is.
 * @typedef {{W: number, H: number, px: Uint32Array, waterIdx: Int32Array, fallIdx: Int32Array, foamIdx: Int32Array,
 *   fallTop: Int32Array, fallLen: Uint16Array, gemIdx: Int32Array, grassIdx: Int32Array, lights: object[],
 *   stats: {water: number, fall: number, foam: number, land: number, grass: number, gems: number, lights: number}}}
 *   Reading
 */

/**
 * Read a painted island: the water in it and which of that water is falling, the foam round the water's edges, the land,
 * the grass, and the crystals and torches that could glow. Everything is found by colour and by shape, so another map
 * serves too (its own crystals and its own water are found in it).
 * @param {Uint32Array} px the picture, packed 0xAABBGGRR
 * @param {number} W its width
 * @param {number} H its height
 * @param {{x: number, y: number, r?: number, colour?: number, kind?: string}[]} [extra] lights placed by hand, for art
 *   whose lamps are not crystals (this island's braziers, on the shrine's gold stands)
 * @returns {Reading} what was found, and the lists of pixels to paint
 * @see connected-component-labelling-wikipedia
 * @see colorimetry-wikipedia
 */
export function analyse(px, W, H, extra = []) {
  const N = W * H;
  // ---------------------------------------------------------------- the crystals, found first
  // a crystal is a small diamond of one strong colour: red over the top half of the island, blue over the bottom. It
  // fills about half its box, where a banner on a wall fills most of its own. Crystals are looked for before
  // the water, because the palest of them pass a water test too, and a crystal must not be painted as a ripple.
  const gemTest = (r, g, b) => (r > 150 && r > g + 60 && r > b + 60) || (b > 150 && b > r + 80 && g < b - 20),
    waters = mark(px, W, H, (r, g, b) => b > 120 && b > r + 25 && g > 60 && b >= g - 15);
  const found = blobs(mark(px, W, H, gemTest), W, H);
  const gems = found.list.filter((s) => {
    const w = s.x1 - s.x0 + 1,
      h = s.y1 - s.y0 + 1,
      c = px[s.best],
      bluish = ((c >>> 16) & 255) > (c & 255);
    // a stream is pale and wide as well as tall, where a crystal shard is a narrow sliver: hold the falls back to water
    return (
      s.area >= 120 &&
      s.area <= 6000 &&
      s.fill >= 0.34 &&
      s.fill < 0.62 &&
      w >= 6 &&
      h >= 6 &&
      !(bluish && w > 19 && h > w * 1.6)
    );
  });
  // a crystal is one lamp, even where its bright core breaks its own colour into a second group: keep the largest of
  // any two that stand within a lamp's width of each other
  gems.sort((a, b) => b.area - a.area);
  const kept = [];
  for (const s of gems) if (!kept.some((k) => Math.abs(k.cx - s.cx) < 26 && Math.abs(k.cy - s.cy) < 26)) kept.push(s);
  // the crystals' own pixels are taken out of the water, so a gem does not ripple (a pool it stands in still does)
  const keepIds = new Set(kept.map((s) => s.id)),
    gemPixel = new Uint8Array(N);
  for (let i = 0; i < N; i++) if (keepIds.has(found.label[i])) gemPixel[i] = 1;

  // ---------------------------------------------------------------- the water, the land, the grass
  // blue, bright and blue over red: a river, a pool, a fall, the sea. (The sky the island floats on is blue too, but it
  // is dark: its blue is under 120.) Grass is green over both; land is anything lit enough not to be that sky.
  const water = new Uint8Array(N),
    land = mark(px, W, H, (r, g, b) => r + g + b > 150),
    grass = mark(px, W, H, (r, g, b) => g > r + 16 && g > b + 22 && g > 64);
  for (let i = 0; i < N; i++) water[i] = waters[i] && !gemPixel[i] ? 1 : 0;

  // ---------------------------------------------------------------- falling water
  // a fall is water long down and short across (runs); the river is long both ways
  const { runX, runY } = runs(water, W, H),
    fall = new Uint8Array(N);
  for (let i = 0; i < N; i++) if (water[i] && runY[i] > 12 && runY[i] > runX[i] * 1.8) fall[i] = 1;

  // how each fall's water can scroll: the run of falling water a pixel is in, from its top and its length, so a pixel can
  // be redrawn with the one a few rows above it, round and round, and the fall appears to pour
  const fallTop = new Int32Array(N),
    fallLen = new Uint16Array(N);
  for (let x = 0; x < W; x++)
    for (let y = 0; y < H;) {
      const i = y * W + x;
      if (!fall[i]) {
        y++;
        continue;
      }
      let end = y;
      while (end + 1 < H && fall[(end + 1) * W + x]) end++;
      for (let k = y; k <= end; k++) {
        fallTop[k * W + x] = y * W + x;
        fallLen[k * W + x] = end - y + 1;
      }
      y = end + 1;
    }

  // ---------------------------------------------------------------- the foam at the water's edge
  // water with land beside it, in two rings: the lapping line, and the water just behind it
  const foam = new Uint8Array(N),
    near = new Uint8Array(N);
  for (let y = 1; y < H - 1; y++)
    for (let x = 1, i = y * W + 1; x < W - 1; x++, i++) {
      if (!water[i]) continue;
      let touching = false;
      for (let dy = -1; dy <= 1 && !touching; dy++)
        for (let dx = -1; dx <= 1; dx++)
          if (!water[i + dy * W + dx]) {
            touching = true;
            break;
          }
      if (touching) foam[i] = 1;
    }
  for (let y = 1; y < H - 1; y++)
    for (let x = 1, i = y * W + 1; x < W - 1; x++, i++)
      if (water[i] && !foam[i] && (foam[i - 1] || foam[i + 1] || foam[i - W] || foam[i + W])) near[i] = 1;

  // ---------------------------------------------------------------- the lamps
  const lights = [];
  for (const s of kept) {
    const c = px[s.best],
      r = red(c),
      g = green(c),
      b = blue(c),
      big = s.area > 1200;
    // the tint of the glow: the crystal's own colour, pulled away from grey so it reads as light, not paint
    const m = (r + g + b) / 3;
    lights.push({
      x: s.cx,
      y: s.cy,
      r: Math.min(84, Math.max(13, Math.sqrt(s.area) * (big ? 2.1 : 1.7))),
      colour: pack(m + (r - m) * 1.5, m + (g - m) * 1.5, m + (b - m) * 1.5),
      glow: big ? 1 : 0.42,
      // each gem keeps its own beat, from where it stands: a slow swell, and a quicker glint now and then
      phase: ((s.cx * 7 + s.cy * 13) % 628) / 100,
      beat: 2.6 + ((s.cx * 3 + s.cy) % 17) / 10,
      kind: 'gem',
    });
  }
  for (const L of extra)
    lights.push({
      x: L.x,
      y: L.y,
      r: L.r ?? 26,
      colour: L.colour ?? pack(255, 186, 96),
      glow: L.glow ?? 0.5,
      phase: ((L.x * 7 + L.y * 13) % 628) / 100,
      beat: 8,
      kind: L.kind ?? 'torch',
    });

  // ---------------------------------------------------------------- the lists of pixels each layer paints
  const list = (mask) => {
    let n = 0;
    for (let i = 0; i < mask.length; i++) if (mask[i]) n++;
    const out = new Int32Array(n);
    for (let i = 0, k = 0; i < mask.length; i++) if (mask[i]) out[k++] = i;
    return out;
  };
  const merged = new Uint8Array(N);
  for (let i = 0; i < N; i++) if (foam[i] || near[i]) merged[i] = 1;
  const waterIdx = list(water),
    fallIdx = list(fall),
    foamIdx = list(merged),
    grassIdx = list(grass);
  return {
    W,
    H,
    px,
    waterIdx,
    fallIdx,
    fallTop,
    fallLen,
    foamIdx,
    gemIdx: list(gemPixel),
    grassIdx,
    lights,
    stats: {
      water: waterIdx.length,
      fall: fallIdx.length,
      foam: foamIdx.length,
      land: land.reduce((n, v) => n + v, 0),
      grass: grassIdx.length,
      gems: kept.length,
      lights: lights.length,
    },
  };
}
