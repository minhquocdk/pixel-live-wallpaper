// A painted island, come alive: the engine behind examples/island/live. The art (art/island.png) is the island, and the
// engine paints over it, never instead of it: the river ripples and glints and its foam breathes, the falls either side
// of the two shrines pour, a breeze runs through the grass, every crystal glows with a beat of its own, and the hour
// turns from noon through dusk and night, with the crystals' light holding the dark off. Cloud shadows drift over it,
// rain falls on it, and now and then lightning lights the whole island.
//
// The trick that lets it run on a phone: nothing still is ever drawn twice. The picture is graded for the hour once each
// time the light changes (the almanac's hourTables and gradeFrame), and after that a frame is that graded picture copied,
// and only the pixels that move written over it: the water, the foam, the falls, the grass in the wind, the lamps' glow.
// About a tenth of the picture moves, so a frame costs about a tenth of a repaint.
//
// What is water, what is grass, where the crystals stand: none of it is written down here. analyse.js finds it in the
// art, so another painted map works too (see examples/island/live/README.md).
import {
  hourLight,
  hourTables,
  gradeFrame,
  hueShiftTone,
  packRgb,
  luma,
  bayerThreshold,
  twinkle,
  cloudShadows,
  lightningFlash,
  noiseTile,
  linearCongruential,
  clamp,
} from '../../../src/index.js';

const R = (c) => c & 255,
  G = (c) => (c >>> 8) & 255,
  B = (c) => (c >>> 16) & 255,
  CUT = (v) => (v < 0 ? 0 : v > 255 ? 255 : v | 0),
  PACK = (r, g, b) => ((255 << 24) | (CUT(b) << 16) | (CUT(g) << 8) | CUT(r)) >>> 0;

const NT = 128; // a tile of the water's noise, and the sine table the breeze reads
const SIN = Float32Array.from({ length: 1024 }, (_, k) => Math.sin((k / 1024) * Math.PI * 2));

// a tile of wrapping noise, made once from a seeded generator, so every visit moves the same water the same way
function noiseTileFrom(cells, seed) {
  const rnd = linearCongruential(seed),
    g = new Float64Array(cells * cells);
  for (let i = 0; i < g.length; i++) g[i] = rnd();
  return noiseTile(NT, cells, (x, y) => g[y * cells + x]);
}

/**
 * What the island is like at this moment: the hour and the weather, and how much of each effect to show.
 * @typedef {{tod: number, wind: number, weather: 'clear'|'rain'|'storm', clouds: number, glow: number, water: number,
 *   breeze: number, rain: number}} State tod: the time of day, as the almanac's hourLight reads it (0.25 noon, 0.75
 *   midnight); wind: 0..1 how hard the breeze blows; weather; clouds: 0..1 how much of the sky is cloud; glow, water,
 *   breeze, rain: 0..1 how much of each effect to show
 */

/** The hour and weather a live island starts in: noon on a clear day, everything on. */
export const DEFAULT_STATE = Object.freeze({
  tod: 0.25,
  wind: 0.5,
  weather: 'clear',
  clouds: 0.45,
  glow: 1,
  water: 1,
  breeze: 1,
  rain: 1,
});

/**
 * A state of the island, from what you want to set: anything left out is the default (DEFAULT_STATE).
 * @param {object} [o] the fields of a State to set
 * @returns {State}
 */
export function state(o = {}) {
  return { ...DEFAULT_STATE, ...o };
}

/**
 * The island found in a picture, ready to be painted on: every moving layer shaded as it is and a step lighter and
 * darker (hueShiftTone, the almanac's pixel-art shading, so the moving pixels keep the art's own palette), and each
 * lamp's reach and falloff worked out once rather than every frame.
 * @param {object} reading what analyse() found in the art
 * @param {{glow?: number, breeze?: number}} [o] glow: how much further the crystals' light reaches than the picture's
 *   own (1); breeze: how much of the grass answers the wind (1)
 * @returns {object} the world, to hand to paint()
 */
export function buildWorld(reading, o = {}) {
  const { W, H, px } = reading,
    N = W * H;
  const variants = (idx, steps) => {
    const out = { base: new Uint32Array(idx.length) };
    for (const [name] of steps) out[name] = new Uint32Array(idx.length);
    for (let j = 0; j < idx.length; j++) {
      const c = px[idx[j]];
      out.base[j] = c;
      for (const [name, k] of steps) out[name][j] = hueShiftTone(c, k);
    }
    return out;
  };
  // the grass that catches the light: the pale tips, the flower heads, the young leaves. Only these are painted, so the
  // breeze costs a fraction of the grass and reads as light running over it.
  const tips = [];
  for (const i of reading.grassIdx) if (luma(px[i]) > 96) tips.push(i);
  const stride = Math.max(1, Math.floor(tips.length / 70000)),
    grassIdx = Int32Array.from(tips.filter((_, k) => k % stride === 0));

  const water = variants(reading.waterIdx, [
      ['light', 0.55],
      ['dark', -0.6],
      ['deep', -1.25],
      ['glint', 1.9],
    ]),
    foam = variants(reading.foamIdx, [
      ['bright', 0.85],
      ['dim', -0.5],
    ]),
    fall = variants(reading.fallIdx, [
      ['bright', 1.1],
      ['dark', -0.45],
    ]),
    grass = variants(grassIdx, [
      ['light', 0.75],
      ['dark', -0.5],
    ]),
    gem = variants(reading.gemIdx, [['hot', 1.6]]);

  // ---------------------------------------------------------------- the lamps: their reach, worked out once
  const glow = o.glow ?? 1,
    lights = reading.lights.map((L) => {
      const r = Math.max(10, L.r * glow),
        y0 = Math.max(0, Math.floor(L.y - r / 1.6)),
        y1 = Math.min(H - 1, Math.ceil(L.y + r / 1.6)),
        x0 = Math.max(0, Math.floor(L.x - r)),
        x1 = Math.min(W - 1, Math.ceil(L.x + r)),
        off = [],
        alpha = [];
      for (let y = y0; y <= y1; y++)
        for (let x = x0; x <= x1; x++) {
          const dx = (x - L.x) / r,
            dy = ((y - L.y) * 1.6) / r,
            d = Math.sqrt(dx * dx + dy * dy);
          if (d >= 1) continue;
          off.push(y * W + x);
          alpha.push(Math.round((1 - d) * (1 - d) * 255));
        }
      return {
        x: L.x,
        y: L.y,
        r,
        phase: L.phase,
        beat: L.beat,
        glow: L.glow,
        colour: [R(L.colour), G(L.colour), B(L.colour)],
        off: Int32Array.from(off),
        alpha: Uint8Array.from(alpha),
      };
    });

  // ---------------------------------------------------------------- how deep the water is
  // (how far each water pixel is from the shore, by the chamfer distance transform in two passes over the grid): deeper
  // water runs darker and shows fewer glints
  const mark = new Uint8Array(N);
  for (const i of reading.waterIdx) mark[i] = 1;

  // each water pixel's row, and the beat of the dash it is part of (a dash is four pixels across, one down), worked out
  // once: a glint then comes and goes as a dash on a crest rather than flickering pixel by pixel
  const waterPhase = new Uint8Array(reading.waterIdx.length),
    waterRow = new Int32Array(reading.waterIdx.length);
  for (let k = 0; k < waterPhase.length; k++) {
    const i = reading.waterIdx[k],
      x = i % W,
      y = (i - x) / W;
    waterPhase[k] = ((x >> 2) * 37 + y * 11) & 255 || 1;
    waterRow[k] = y;
  }

  // every pixel a frame can paint: those are the only ones to put back when the picture is painted again, so a frame
  // copies a fifth of the picture instead of all of it
  const touched = new Uint8Array(N),
    add = (idx) => {
      for (let k = 0; k < idx.length; k++) touched[idx[k]] = 1;
    };
  for (const idx of [reading.waterIdx, reading.foamIdx, reading.fallIdx, reading.gemIdx, grassIdx]) add(idx);
  for (const L of lights) add(L.off);
  let dirtyN = 0;
  for (let i = 0; i < N; i++) if (touched[i]) dirtyN++;
  const dirty = new Int32Array(dirtyN);
  for (let i = 0, k = 0; i < N; i++) if (touched[i]) dirty[k++] = i;

  return {
    W,
    H,
    N,
    base: px,
    reading,
    grassIdx,
    dirty,
    staticFrame: new Uint32Array(px),
    cloudFrame: new Uint32Array(px),
    frame: new Uint32Array(px),
    water,
    foam,
    fall,
    grass,
    gem,
    waterPhase,
    waterRow,
    depth: chamfer(mark, W, H),
    lights,
    swell: noiseTileFrom(6, 11),
    ripple: noiseTileFrom(26, 23),
    key: '',
    cloudKey: '',
    cloudGrid: undefined,
  };
}

// the chamfer distance transform (the almanac's chamferDistance, over the whole picture): how far each water pixel is
// from the nearest shore, in thirds of a pixel
function chamfer(mask, w, h) {
  const INF = 60000,
    D = new Uint16Array(w * h);
  for (let i = 0; i < D.length; i++) D[i] = mask[i] ? INF : 0;
  for (let y = 0; y < h; y++)
    for (let x = 0; x < w; x++) {
      const i = y * w + x;
      if (!D[i]) continue;
      let d = D[i];
      if (x > 0) d = Math.min(d, D[i - 1] + 3);
      if (y > 0) {
        d = Math.min(d, D[i - w] + 3);
        if (x > 0) d = Math.min(d, D[i - w - 1] + 4);
        if (x < w - 1) d = Math.min(d, D[i - w + 1] + 4);
      }
      D[i] = d;
    }
  for (let y = h - 1; y >= 0; y--)
    for (let x = w - 1; x >= 0; x--) {
      const i = y * w + x;
      if (!D[i]) continue;
      let d = D[i];
      if (x < w - 1) d = Math.min(d, D[i + 1] + 3);
      if (y < h - 1) {
        d = Math.min(d, D[i + w] + 3);
        if (x < w - 1) d = Math.min(d, D[i + w + 1] + 4);
        if (x > 0) d = Math.min(d, D[i + w - 1] + 4);
      }
      D[i] = d;
    }
  return D;
}

// the hour's key: the light the picture was graded for. A change of key means the still picture is graded again, which
// costs a whole picture, so the key is coarser than the light itself: a sixteenth of the way from day to night, an
// eighth of the way into the rain.
const hourKey = (h, wet) =>
  `${Math.round(h.night * 16)},${Math.round(h.dusk * 16)},${h.morning ? 1 : 0},${Math.round(wet * 8)}`;

/**
 * Paint one frame of the live island.
 * @param {object} world from buildWorld
 * @param {number} t the time, in seconds (it only ever grows)
 * @param {State} [s] the hour and the weather (state())
 * @returns {Uint32Array} the frame, packed 0xAABBGGRR (the world's own buffer, painted anew each call)
 */
export function paint(world, t, s = DEFAULT_STATE) {
  const { W, H, N, base, staticFrame, cloudFrame, frame, reading, water, foam, fall, grass, gem } = world;
  const wet = s.weather === 'clear' ? 0 : s.weather === 'rain' ? 0.55 : 1,
    h = hourLight(s.tod, wet > 0.8 ? 0.9 : 1, wet);

  // ---------------------------------------------------------------- the light: grade the still picture when it turns
  let full = false; // whether the still picture changed this frame, and so the whole frame is copied rather than patched
  if (hourKey(h, wet) !== world.key) {
    world.key = hourKey(h, wet);
    const T = hourTables(h.night, h.dusk, h.morning, 1, wet);
    staticFrame.set(base);
    gradeFrame(staticFrame, W, H, T, { night: 0 });
    // the layers that move, graded the same way, so a ripple is lit like the water it sits in
    for (const layer of [water, foam, fall, grass, gem])
      for (const name of Object.keys(layer)) gradeFrame(layer[name], layer[name].length, 1, T, { night: 0 });
    world.cloudKey = '';
    full = true;
  }

  // ---------------------------------------------------------------- cloud shadows, drifting
  // (a whole-picture pass, so only when the clouds have moved a little: the shadows drift over the island rather than
  // crawling frame by frame)
  let still = staticFrame;
  if (s.clouds > 0.02 && h.night < 0.8) {
    // the shadows drift, so they are laid down once a second rather than every frame: over a moving cloud a second's
    // drift is a few pixels of a slow, soft shape, and the rest of the second costs nothing
    const ck = `${Math.round(t)},${s.clouds.toFixed(2)},${world.key}`;
    if (ck !== world.cloudKey) {
      world.cloudKey = ck;
      full = true;
      cloudFrame.set(staticFrame);
      world.cloudGrid = cloudShadows(cloudFrame, W, H, {
        x: t * 3,
        y: t * 1.2,
        seed: 31,
        cover: s.clouds,
        strong: h.night < 0.35,
        scratch: world.cloudGrid,
      });
    }
    still = cloudFrame;
  }
  // the picture, copied whole when the light has turned or the clouds have moved, and otherwise patched only where the
  // last frame painted: the ninth of the picture that moves
  if (full) frame.set(still);
  else {
    const dirty = world.dirty;
    for (let k = 0; k < dirty.length; k++) {
      const i = dirty[k];
      frame[i] = still[i];
    }
  }

  const ox = (t * 3) | 0,
    oy = (t * 1) | 0,
    o2 = (t * 7) | 0,
    MASK = NT - 1,
    SW = world.swell,
    RP = world.ripple;

  // ---------------------------------------------------------------- the water: swells, ripples, glints
  if (s.water > 0) {
    const idx = reading.waterIdx,
      phase = world.waterPhase,
      row = world.waterRow,
      dm = world.depth,
      fade = s.water;
    for (let k = 0; k < idx.length; k++) {
      const i = idx[k],
        x = i % W,
        y = row[k],
        deep = dm[i] > 24;
      // the water's surface: the broad swell (a slow noise) shades whole reaches of it a step darker or lighter, and the
      // fine ripple (a quicker noise, drawn out sideways) draws thin crest lines where it crosses its own middle value.
      // Crest lines wander and break as the noise drifts, which is what water looks like from above, without any pattern
      // of dither or stripes showing.
      const sw = SW[(((y >> 2) + oy) & MASK) * NT + (((x >> 2) + ox) & MASK)],
        rp = RP[((y + o2) & MASK) * NT + (((x >> 1) + o2) & MASK)] - 0.5;
      let c;
      if (rp > -0.022 && rp < 0.022) c = water.light[k];
      else if (sw > 0.63) c = water.base[k];
      else if (sw < 0.34) c = deep ? water.deep[k] : water.dark[k];
      else c = water.base[k];
      // glints: a dash on a crest, coming and going on the pixel's own beat
      if (rp > -0.03 && rp < 0.03 && !deep && fade > 0.5 && twinkle(t * 0.7, phase[k] * 0.0246, 0.9) > 0.9)
        c = water.glint[k];
      frame[i] = c;
    }
  }

  // ---------------------------------------------------------------- the falls: water pouring, round and round
  {
    const idx = reading.fallIdx,
      top = reading.fallTop,
      len = reading.fallLen,
      shift = Math.floor(t * 34);
    for (let k = 0; k < idx.length; k++) {
      const i = idx[k],
        L = len[i],
        off = i - top[i],
        from = L ? top[i] + ((((off - shift) % L) + L) % L) : i,
        // bands of foam running down the fall, a few pixels apart, over the water it carries down with them
        q = (off + shift) % 8;
      frame[i] = q < 2 ? fall.bright[k] : q === 5 ? fall.dark[k] : still[from];
    }
  }

  // ---------------------------------------------------------------- the foam: lapping, breathing
  {
    const idx = reading.foamIdx;
    for (let k = 0; k < idx.length; k++) {
      const i = idx[k],
        x = i % W,
        y = (i - x) / W,
        n =
          RP[(y & MASK) * NT + ((x + ((t * 13) | 0)) & MASK)] * 0.6 +
          SW[((y + ((t * 6) | 0)) & MASK) * NT + ((x - ((t * 4) | 0)) & MASK)] * 0.4;
      frame[i] = n > 0.56 ? foam.bright[k] : n < 0.4 ? foam.dim[k] : foam.base[k];
    }
  }

  // ---------------------------------------------------------------- the breeze: light running over the grass
  if (s.breeze > 0 && s.wind > 0) {
    const idx = world.grassIdx,
      wind = s.wind * s.breeze,
      // the wind sets how high the light must rise to show: still air, nothing shows; a gale, the grass is full of it
      hi = 0.6 + (1 - wind) * 3.2,
      lo = -0.9 - (1 - wind) * 3.4,
      drift = (t * 95) % 1024;
    for (let k = 0; k < idx.length; k++) {
      const i = idx[k],
        x = i % W,
        y = (i - x) / W,
        // two waves crossing: a slow one down the picture, a quicker one across it, so the grass moves in gusts
        v = SIN[(x * 4 + y * 2 + drift) & 1023] + SIN[(x * 2 - y * 3 + drift * 1.7) & 1023] * 0.6;
      if (v > hi) frame[i] = grass.light[k];
      else if (v < lo) frame[i] = grass.dark[k];
    }
  }

  // ---------------------------------------------------------------- the lamps: each crystal, its own beat
  if (s.glow > 0 && h.night > 0.06) {
    for (const L of world.lights) {
      const pulse = twinkle(t / Math.max(1, L.beat), L.phase, 0.4) * L.glow * s.glow * clamp(h.night * 2.2),
        // now and then a crystal flares, its flare rolling round the island as the shrine's breath
        flare = Math.max(0, Math.sin(t * 0.37 + L.phase * 0.25) - 0.94) * 12 * L.glow,
        k = clamp(pulse + flare, 0, 1.5),
        [lr, lg, lb] = L.colour,
        off = L.off,
        alpha = L.alpha;
      if (k < 0.03) continue;
      for (let j = 0; j < off.length; j++) {
        const i = off[j],
          a = (alpha[j] * k) / 255,
          step = bayerThreshold(i % W, (i / W) | 0) * 0.3;
        if (a <= step) continue;
        const c = frame[i];
        frame[i] = PACK(R(c) + lr * a * 0.9, G(c) + lg * a * 0.9, B(c) + lb * a * 0.9);
      }
    }
    // the crystals' own hearts, brighter as they pulse
    const gi = reading.gemIdx;
    for (let k = 0; k < gi.length; k++) {
      const i = gi[k],
        x = i % W,
        y = (i - x) / W;
      if (twinkle(t * 1.3, ((x * 5 + y * 3) & 255) * 0.0246, 0.55) > 0.92) frame[i] = gem.hot[k];
    }
  }

  // ---------------------------------------------------------------- the weather
  if (wet > 0) {
    if (s.weather === 'storm') {
      // lightning every eight seconds or so: twice lit, then the island dark again, as a flash flickers
      const cycle = 8.3,
        at = Math.floor(t / cycle) * cycle + cycle * 0.42,
        left = 0.34 - (t - at);
      if (left > 0 && left <= 0.34) lightningFlash(frame, left);
    }
    if (s.rain > 0) {
      // rain: short streaks, falling and blown a little, each from its own beat; pale by day, and dimmer (but still
      // catching the light) as the night comes on
      const lit = 1 - 0.5 * h.night,
        drops = Math.floor(N * 0.0016 * s.rain * wet),
        pale = packRgb(214 * lit, 232 * lit, 246 * lit),
        faint = packRgb(150 * lit, 186 * lit, 214 * lit),
        blow = (t * 40) % 5,
        fall = t * 520;
      for (let d = 0; d < drops; d++) {
        let h32 = Math.imul(d + 1, 2654435761) | 0;
        h32 = Math.imul(h32 ^ (h32 >>> 15), 2246822519);
        h32 = (h32 ^ (h32 >>> 13)) >>> 0;
        const x = h32 % W,
          start = h32 >>> 11,
          y0 = ((start % H) + ((fall + d * 7) | 0)) % H,
          x2 = (x + ((blow + d) | 0)) % W,
          len = 5 + (d & 3);
        // a streak down the picture, leaning with the wind: bright at its head, faint behind it
        for (let k = 0; k < len; k++) {
          const y = (y0 + k) % H;
          if (k === 0) frame[y * W + x2] = pale;
          else if (((x2 + y + k) & 1) === 0) frame[y * W + x2] = faint;
        }
      }
    }
  }
  return frame;
}

/**
 * The live island, ready for a browser or a film: the world built from the art, and paint(t, s) giving back each frame.
 * @param {object} reading what analyse() found in the art
 * @param {object} [o] buildWorld's options
 * @returns {{W: number, H: number, world: object, stats: object, paint: function(number, State=): Uint32Array}}
 */
export function live(reading, o = {}) {
  const world = buildWorld(reading, o);
  return {
    W: world.W,
    H: world.H,
    world,
    stats: reading.stats,
    paint: (t, s = DEFAULT_STATE) => paint(world, t, s),
  };
}
