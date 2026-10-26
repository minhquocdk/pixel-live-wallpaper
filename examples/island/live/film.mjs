// The live island, filmed: the frames the browser paints (live.js) rendered in Node, so the look of an hour or a storm
// can be checked without a browser, and so a wallpaper's frames can be measured before they are trusted to a phone.
//
//   node examples/island/live/film.mjs                          one frame of noon, beside this file (live-noon.png)
//   node examples/island/live/film.mjs --tod 0.75               another hour (0.25 noon, 0.75 midnight)
//   node examples/island/live/film.mjs --weather storm --t 12   rain and lightning, twelve seconds in
//   node examples/island/live/film.mjs --sheet sheet.png        six frames: noon, dusk, night, storm, rain, dawn
//   --scale 2   the picture's pixels made screen pixels (2)     --out <path>  where a single frame goes
import { readFileSync, writeFileSync } from 'node:fs';
import { decodePng, encodePng } from './png.mjs';
import { analyse } from './analyse.js';
import { live, state } from './live.js';
import { ART, torches } from './art.js';

const argv = process.argv.slice(2),
  opt = (k, d) => (argv.includes(k) ? argv[argv.indexOf(k) + 1] : d);

const t0 = Date.now();
const art = decodePng(readFileSync(new URL(ART, import.meta.url)));
const reading = analyse(art.px, art.W, art.H, torches);
const island = live(reading);
const found = Date.now() - t0;
const { W, H } = island;

const scaleUp = (px, w, h, s) => {
  const out = new Uint32Array(w * s * h * s);
  for (let y = 0; y < h * s; y++)
    for (let x = 0; x < w * s; x++) out[y * w * s + x] = px[((y / s) | 0) * w + ((x / s) | 0)];
  return out;
};
const save = (path, px, s) => writeFileSync(path, encodePng(W * s, H * s, s === 1 ? px : scaleUp(px, W, H, s)));

// how long a frame takes: paint a second of them into a buffer and time it
function bench(st, seconds = 2, fps = 30) {
  const n = Math.round(seconds * fps),
    from = 6;
  let worst = 0,
    total = 0;
  for (let k = 0; k < n; k++) {
    const t = from + k / fps,
      a = process.hrtime.bigint();
    island.paint(t, st);
    const ms = Number(process.hrtime.bigint() - a) / 1e6;
    total += ms;
    if (k > 4) worst = Math.max(worst, ms);
  }
  return { mean: total / n, worst };
}

if (argv.includes('--sheet')) {
  const scale = +opt('--scale', 1),
    shots = [
      ['noon', 6, state({ tod: 0.25 })],
      ['dusk', 9, state({ tod: 0.42, clouds: 0.5 })],
      ['night', 12, state({ tod: 0.62 })],
      ['storm', 15, state({ tod: 0.5, weather: 'storm', clouds: 0.8 })],
      ['rain', 18, state({ tod: 0.33, weather: 'rain', clouds: 0.6 })],
      ['dawn', 21, state({ tod: 0.05 })],
    ],
    cols = 3,
    rows = Math.ceil(shots.length / cols),
    tile = 300,
    sheet = new Uint32Array(tile * cols * tile * rows);
  shots.forEach(([name, t, st], k) => {
    const px = island.paint(t, st),
      s = tile / Math.max(W, H),
      ox = (k % cols) * tile,
      oy = ((k / cols) | 0) * tile;
    for (let y = 0; y < tile; y++)
      for (let x = 0; x < Math.min(tile, Math.round(W * s)); x++) {
        const sx = Math.min(W - 1, (x / s) | 0),
          sy = Math.min(H - 1, (y / s) | 0);
        sheet[(oy + y) * tile * cols + ox + x] = px[sy * W + sx];
      }
    process.stderr.write(`  ${name}: ${JSON.stringify(bench(st))}\n`);
  });
  writeFileSync(opt('--sheet'), encodePng(tile * cols, tile * rows, sheet));
  process.stderr.write(`sheet: ${opt('--sheet')}\n`);
} else {
  const t = +opt('--t', 8),
    st = state({
      tod: +opt('--tod', 0.25),
      weather: opt('--weather', 'clear'),
      clouds: +opt('--clouds', 0.45),
      wind: +opt('--wind', 0.5),
    }),
    out = opt('--out', new URL(`./live-${opt('--tod', '0.25')}-${st.weather}.png`, import.meta.url).pathname);
  save(out, island.paint(t, st), +opt('--scale', 1));
  process.stderr.write(`wrote ${out}\n  ${JSON.stringify(bench(st))}\n`);
}
process.stderr.write(
  `art ${W} x ${H}, read and made live in ${found} ms: ${JSON.stringify(island.stats)}, ${reading.lights.length} lamps\n`,
);
