// A PNG in and out, in Node, with nothing but node:zlib: enough of the format for the art this example paints on (8 bits
// a channel, not interlaced) and for the stills it writes (32-bit pixels, as the almanac keeps them). The browser has no
// need of it: there a canvas decodes the art and putImageData shows a frame.
import { inflateSync, deflateSync } from 'node:zlib';

/**
 * A PNG file's pixels: 8 bits a channel, grey, RGB or RGBA, not interlaced, every filter (None, Sub, Up, Average,
 * Paeth). The picture comes back as the almanac's packed pixels, 0xAABBGGRR, ready to hand to the engine.
 * @param {Uint8Array} file the file's bytes
 * @returns {{W: number, H: number, px: Uint32Array}} the picture, packed
 * @see png-spec
 */
export function decodePng(file) {
  const sig = [137, 80, 78, 71, 13, 10, 26, 10];
  for (let i = 0; i < 8; i++) if (file[i] !== sig[i]) throw new Error('not a PNG');
  const view = new DataView(file.buffer, file.byteOffset, file.byteLength);
  let at = 8,
    W = 0,
    H = 0,
    depth = 0,
    type = 0,
    interlace = 0,
    palette = null,
    idat = [];
  while (at < file.length) {
    const len = view.getUint32(at),
      kind = String.fromCharCode(file[at + 4], file[at + 5], file[at + 6], file[at + 7]);
    const body = file.subarray(at + 8, at + 8 + len);
    if (kind === 'IHDR') {
      W = view.getUint32(at + 8);
      H = view.getUint32(at + 12);
      depth = body[8];
      type = body[9];
      interlace = body[12];
    } else if (kind === 'PLTE') palette = body;
    else if (kind === 'IDAT') idat.push(body);
    else if (kind === 'IEND') break;
    at += 12 + len;
  }
  if (depth !== 8) throw new Error(`only 8 bits a channel (this PNG has ${depth})`);
  if (interlace) throw new Error('interlaced PNGs are not read here');
  const channels = { 0: 1, 2: 3, 4: 2, 6: 4 }[type];
  if (!channels || (type === 3 && !palette)) throw new Error(`colour type ${type} is not read here`);
  const raw = inflateSync(Buffer.concat(idat)),
    stride = W * channels,
    out = new Uint8Array(stride * H),
    px = new Uint32Array(W * H);
  for (let y = 0, p = 0, q = 0; y < H; y++) {
    const filter = raw[p++];
    for (let i = 0; i < stride; i++, p++) {
      const a = i >= channels ? out[q + i - channels] : 0,
        b = y ? out[q - stride + i] : 0,
        c = y && i >= channels ? out[q - stride + i - channels] : 0;
      let v = raw[p];
      if (filter === 1) v += a;
      else if (filter === 2) v += b;
      else if (filter === 3) v += (a + b) >> 1;
      else if (filter === 4) {
        const g = a + b - c,
          pa = Math.abs(g - a),
          pb = Math.abs(g - b),
          pc = Math.abs(g - c);
        v += pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
      }
      out[q + i] = v & 255;
    }
    q += stride;
  }
  for (let i = 0, s = 0; i < px.length; i++, s += channels) {
    let r, g, b;
    if (type === 2 || type === 6) {
      r = out[s];
      g = out[s + 1];
      b = out[s + 2];
    } else if (type === 3) {
      const k = out[s] * 3;
      r = palette[k];
      g = palette[k + 1];
      b = palette[k + 2];
    } else r = g = b = type === 4 ? out[s] : out[s];
    px[i] = ((255 << 24) | (b << 16) | (g << 8) | r) >>> 0;
  }
  return { W, H, px };
}

const CRC = Array.from({ length: 256 }, (_, k) => {
  let c = k;
  for (let j = 0; j < 8; j++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});
function crc32(bytes) {
  let c = 0xffffffff;
  for (const v of bytes) c = CRC[(c ^ v) & 255] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

/**
 * Packed pixels as a PNG file's bytes (8 bits a channel, RGBA, no interlacing): the frames of the preview written out
 * as pictures.
 * @param {number} w
 * @param {number} h
 * @param {Uint32Array} px the picture, 0xAABBGGRR
 * @returns {Buffer} the file
 * @see png-spec
 */
export function encodePng(w, h, px) {
  const chunk = (kind, data) => {
    const body = Buffer.concat([Buffer.from(kind, 'latin1'), data]),
      out = Buffer.alloc(body.length + 8);
    out.writeUInt32BE(data.length, 0);
    body.copy(out, 4);
    out.writeUInt32BE(crc32(body), body.length + 4);
    return out;
  };
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++)
    for (let x = 0; x < w; x++) {
      const c = px[y * w + x],
        at = y * (w * 4 + 1) + 1 + x * 4;
      raw[at] = c & 255;
      raw[at + 1] = (c >>> 8) & 255;
      raw[at + 2] = (c >>> 16) & 255;
      raw[at + 3] = 255;
    }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  return Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}
