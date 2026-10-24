// One seed, one island, filmed: the scene in scene.js rendered frame by frame in Node, written out as a GIF and an MP4
// (with ffmpeg), or as single frames. The same scene runs live in a browser in index.html.
//
//   node examples/island/island.mjs                        island.gif and island.mp4 beside this file (needs ffmpeg)
//   node examples/island/island.mjs --still 11 frame.png   one frame, at 11 seconds
//   node examples/island/island.mjs --sheet sheet.png      a frame every second, on one sheet
//   --seed <words>  another island (heron-bay-8)          --scale <n>  the GIF's screen pixels per pixel (2)
//   --start <s>     the second the film opens on (it loops; by default the whole island, by day)
import { island } from './scene.js';
import { deflateSync } from 'node:zlib';
import { writeFileSync, openSync, readSync, writeSync, closeSync, mkdtempSync, rmSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const argv = process.argv.slice(2),
  opt = (k, d) => (argv.includes(k) ? argv[argv.indexOf(k) + 1] : d);
const scene = island(opt('--seed', 'heron-bay-8')),
  { W, H, FPS, DUR, frames } = scene,
  report = () => process.stderr.write(scene.report().join('\n') + '\n');

function png(path, w, h, px) {
  const crcT = Array.from({ length: 256 }, (_, k) => {
    let c = k;
    for (let j = 0; j < 8; j++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    return c >>> 0;
  });
  const crc = (b) => {
    let c = 0xffffffff;
    for (const v of b) c = crcT[(c ^ v) & 255] ^ (c >>> 8);
    return (c ^ 0xffffffff) >>> 0;
  };
  const chunk = (type, data) => {
    const td = Buffer.concat([Buffer.from(type), data]),
      out = Buffer.alloc(td.length + 8);
    out.writeUInt32BE(data.length, 0);
    td.copy(out, 4);
    out.writeUInt32BE(crc(td), td.length + 4);
    return out;
  };
  const raw = Buffer.alloc((w * 4 + 1) * h),
    bytes = Buffer.from(px.buffer, px.byteOffset, px.byteLength);
  for (let y = 0; y < h; y++) bytes.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  writeFileSync(
    path,
    Buffer.concat([
      Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
      chunk('IHDR', ihdr),
      chunk('IDAT', deflateSync(raw)),
      chunk('IEND', Buffer.alloc(0)),
    ]),
  );
}
const scaleUp = (px, w, h, s) => {
  const out = new Uint32Array(w * s * h * s);
  for (let y = 0; y < h * s; y++)
    for (let x = 0; x < w * s; x++) out[y * w * s + x] = px[((y / s) | 0) * w + ((x / s) | 0)];
  return out;
};
if (argv.includes('--still')) {
  const t = +opt('--still', 10),
    f = Math.round(t * FPS);
  png(argv[argv.indexOf('--still') + 2] || 'frame.png', W * 2, H * 2, scaleUp(scene.frame(f), W, H, 2));
  report();
} else if (argv.includes('--sheet')) {
  const shots = [],
    times = opt('--at', '')
      ? opt('--at')
          .split(',')
          .map((s) => Math.round(+s * FPS))
      : null;
  for (let f = 0; f < frames; f++) {
    if (times ? times.includes(f) : f % FPS === FPS / 2) shots.push(scene.frame(f).slice());
  }
  const sheet = scene.A.packSheet(shots, W, H, +opt('--across', 4));
  png(opt('--sheet', 'sheet.png'), sheet.w, sheet.h, sheet.data);
  report();
} else {
  // the frames go to a file first, then ffmpeg reads it three times: for the GIF's palette, the GIF, and the MP4, so
  // nothing has to hold the whole film in memory
  const scale = +opt('--scale', 2),
    here = new URL('.', import.meta.url).pathname,
    gif = opt('--gif', here + 'island.gif'),
    mp4 = opt('--mp4', here + 'island.mp4'),
    dir = mkdtempSync(join(tmpdir(), 'island-')),
    raw = join(dir, 'frames.rgba'),
    pal = join(dir, 'palette.png'),
    fd = openSync(raw, 'w');
  for (let f = 0; f < frames; f++) {
    writeSync(fd, new Uint8Array(scene.frame(f).buffer));
    if (f % FPS === 0) process.stderr.write(`\r${f / FPS} s of ${DUR.toFixed(1)}`);
  }
  closeSync(fd);
  // the film loops, so it may start anywhere: by default where the island is first whole, in daylight (the first
  // frame is what a feed shows before it plays), going round through the night and back to the seed
  const start = Math.round(+opt('--start', scene.opening) * FPS) % frames,
    looped = join(dir, 'looped.rgba'),
    size = W * H * 4,
    chunk = Buffer.alloc(size * FPS),
    from = openSync(raw, 'r'),
    to = openSync(looped, 'w');
  for (const [a, b] of [
    [start, frames],
    [0, start],
  ])
    for (let f = a; f < b; f += FPS) {
      const k = Math.min(FPS, b - f),
        got = readSync(from, chunk, 0, k * size, f * size);
      writeSync(to, chunk, 0, got);
    }
  closeSync(from);
  closeSync(to);
  const input = ['-f', 'rawvideo', '-pix_fmt', 'rgba', '-s', `${W}x${H}`, '-r', String(FPS), '-i', looped];
  const ffmpeg = (...a) => {
    const r = spawnSync('ffmpeg', ['-y', '-loglevel', 'error', ...a], { stdio: 'inherit' });
    if (r.status) throw new Error('ffmpeg failed: ' + a.join(' '));
  };
  ffmpeg(...input, '-vf', 'palettegen=max_colors=256:stats_mode=full', pal);
  ffmpeg(
    ...input,
    '-i',
    pal,
    '-filter_complex',
    `[0:v]scale=iw*${scale}:ih*${scale}:flags=neighbor[s];[s][1:v]paletteuse=dither=none:diff_mode=rectangle`,
    '-loop',
    '0',
    gif,
  );
  ffmpeg(
    ...input,
    '-vf',
    'scale=iw*4:ih*4:flags=neighbor',
    '-c:v',
    'libx264',
    '-pix_fmt',
    'yuv420p',
    '-crf',
    '14',
    '-preset',
    'slow',
    mp4,
  );
  rmSync(dir, { recursive: true });
  process.stderr.write(`\n${gif}\n${mp4}\n`);
  report();
}
