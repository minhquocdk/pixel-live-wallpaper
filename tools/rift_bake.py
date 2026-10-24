"""Turn a painted reference (an image model's picture) into a wallpaper's fixed layer: a real pixel-art background at
the engine's own resolution, plus the mask that tells the engine where the water is, where the roads are and where the
stone is — because a background is only paint: the water still has to move, the soldiers still have to walk the lanes,
and the lamps still have to come on at night.

    python3 tools/rift_bake.py in.png out-dir [--w 614] [--h 1364] [--colours 40]

Writes, into out-dir:
    background.png   the fixed layer, at exactly W x H, quantised to a small palette
    masks.png        one pixel a cell: R = water (depth), G = road, B = stone
    bake.json        what was found, and how to regenerate it

Only the standard library, as the rest of tools/ is.
"""
import json
import os
import struct
import sys
import zlib


# ---------------------------------------------------------------------------------------------- PNG in and out
def read_png(path):
    data = open(path, 'rb').read()
    assert data[:8] == b'\x89PNG\r\n\x1a\n', 'not a PNG'
    i, idat, w, h, depth, ctype = 8, b'', None, None, None, None
    while i < len(data):
        (n,) = struct.unpack('>I', data[i:i + 4])
        tag = data[i + 4:i + 8]
        body = data[i + 8:i + 8 + n]
        i += 12 + n
        if tag == b'IHDR':
            w, h, depth, ctype = struct.unpack('>IIBB', body[:10])
        elif tag == b'IDAT':
            idat += body
        elif tag == b'IEND':
            break
    assert depth == 8, 'only 8-bit channels'
    ch = {0: 1, 2: 3, 4: 2, 6: 4}[ctype]
    raw = zlib.decompress(idat)
    stride = w * ch
    out = bytearray(h * stride)
    prev = bytearray(stride)
    o = 0
    for y in range(h):
        f = raw[o]
        o += 1
        line = bytearray(raw[o:o + stride])
        o += stride
        if f == 1:
            for x in range(ch, stride):
                line[x] = (line[x] + line[x - ch]) & 255
        elif f == 2:
            for x in range(stride):
                line[x] = (line[x] + prev[x]) & 255
        elif f == 3:
            for x in range(stride):
                a = line[x - ch] if x >= ch else 0
                line[x] = (line[x] + ((a + prev[x]) >> 1)) & 255
        elif f == 4:
            for x in range(stride):
                a = line[x - ch] if x >= ch else 0
                b = prev[x]
                c = prev[x - ch] if x >= ch else 0
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[x] = (line[x] + pr) & 255
        out[y * stride:(y + 1) * stride] = line
        prev = line
    if ch == 4:
        rgb = bytearray(w * h * 3)
        for k in range(w * h):
            rgb[k * 3:k * 3 + 3] = out[k * 4:k * 4 + 3]
        return w, h, rgb
    if ch == 2:
        rgb = bytearray(w * h * 3)
        for k in range(w * h):
            g = out[k * 2]
            rgb[k * 3:k * 3 + 3] = bytes((g, g, g))
        return w, h, rgb
    return w, h, out


def write_png(path, w, h, rgb):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        raw += rgb[y * w * 3:(y + 1) * w * 3]

    def chunk(tag, body):
        c = struct.pack('>I', len(body)) + tag + body
        return c + struct.pack('>I', zlib.crc32(tag + body) & 0xFFFFFFFF)

    png = (b'\x89PNG\r\n\x1a\n'
           + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0))
           + chunk(b'IDAT', zlib.compress(bytes(raw), 9))
           + chunk(b'IEND', b''))
    open(path, 'wb').write(png)


# ---------------------------------------------------------------------------------------------- resample
def box_resample(sw, sh, src, dw, dh):
    """Average each source box into one destination pixel: the honest way to shrink a painted picture."""
    out = bytearray(dw * dh * 3)
    xmap = []
    for x in range(dw):
        x0 = x * sw / dw
        x1 = (x + 1) * sw / dw
        xmap.append((int(x0), max(int(x0) + 1, int(-(-x1 // 1)))))
    for y in range(dh):
        y0 = int(y * sh / dh)
        y1 = max(y0 + 1, int(-(-(y + 1) * sh // dh)))
        row = y * dw * 3
        for x in range(dw):
            xa, xb = xmap[x]
            r = g = b = 0
            n = 0
            for sy in range(y0, y1):
                base = (sy * sw + xa) * 3
                for sx in range(xa, xb):
                    r += src[base]
                    g += src[base + 1]
                    b += src[base + 2]
                    base += 3
                    n += 1
            out[row + x * 3] = r // n
            out[row + x * 3 + 1] = g // n
            out[row + x * 3 + 2] = b // n
    return out


# ---------------------------------------------------------------------------------------------- palette
def kmeans(pixels, k, seed=7, rounds=8):
    """A small palette for the whole picture: k colours, from a spread-out sample, then a few refinements."""
    n = len(pixels) // 3
    step = max(1, n // 6000)
    sample = [tuple(pixels[i * 3:i * 3 + 3]) for i in range(0, n, step)]
    # seed: the k most spread out of a shuffled handful
    lcg = seed
    def rnd():
        nonlocal lcg
        lcg = (lcg * 1103515245 + 12345) & 0x7FFFFFFF
        return lcg / 0x7FFFFFFF
    centres = [sample[int(rnd() * len(sample))] for _ in range(k)]
    for _ in range(rounds):
        sums = [[0, 0, 0, 0] for _ in range(k)]
        for (r, g, b) in sample:
            best, bd = 0, 1 << 30
            for ci, (cr, cg, cb) in enumerate(centres):
                d = (r - cr) ** 2 + (g - cg) ** 2 + (b - cb) ** 2
                if d < bd:
                    bd, best = d, ci
            s = sums[best]
            s[0] += r
            s[1] += g
            s[2] += b
            s[3] += 1
        for ci in range(k):
            if sums[ci][3]:
                centres[ci] = (
                    sums[ci][0] // sums[ci][3],
                    sums[ci][1] // sums[ci][3],
                    sums[ci][2] // sums[ci][3],
                )
    return centres


def quantise(w, h, src, centres):
    cache = {}
    out = bytearray(w * h * 3)
    for i in range(w * h):
        r, g, b = src[i * 3], src[i * 3 + 1], src[i * 3 + 2]
        key = (r >> 3, g >> 3, b >> 3)
        q = cache.get(key)
        if q is None:
            best, bd = centres[0], 1 << 30
            for c in centres:
                d = (r - c[0]) ** 2 + (g - c[1]) ** 2 + (b - c[2]) ** 2
                if d < bd:
                    bd, best = d, c
            q = best
            cache[key] = q
        out[i * 3] = q[0]
        out[i * 3 + 1] = q[1]
        out[i * 3 + 2] = q[2]
    return out


# ---------------------------------------------------------------------------------------------- masks
def hue_of(r, g, b):
    """Hue in degrees, and how saturated and how light the colour is."""
    mx, mn = max(r, g, b), min(r, g, b)
    d = mx - mn
    sat = 0.0 if mx == 0 else d / mx
    light = (r * 299 + g * 587 + b * 114) // 1000
    if d == 0:
        return 0.0, sat, light
    if mx == r:
        h = (60.0 * ((g - b) / d)) % 360.0
    elif mx == g:
        h = 60.0 * ((b - r) / d) + 120.0
    else:
        h = 60.0 * ((r - g) / d) + 240.0
    return h, sat, light


def classify(r, g, b):
    """What a pixel of the fixed layer is: 4 water, 2 road, 3 stone, 1 ground, 0 void.

    Judged by hue, not by brightness alone: the emptiness beyond the plateau is a dark blue-grey much like deep water,
    and the cliff is the same blue-grey again, so both are taken out before water is looked for.
    """
    h, sat, light = hue_of(r, g, b)
    if light < 26:
        return 0                                   # the emptiness the map floats in
    if light < 52 and sat < 0.42:
        return 3                                   # a cliff face, in shadow
    if 150.0 <= h <= 235.0 and sat > 0.30 and light > 55:
        return 4                                   # water: cyan to blue, and never grey
    if 18.0 <= h <= 55.0 and sat > 0.28 and light > 96:
        return 2                                   # packed dirt: warm and pale
    if sat < 0.22 and light >= 52:
        return 3                                   # bare stone
    return 1


def masks(w, h, px):
    m = bytearray(w * h * 3)
    counts = {0: 0, 1: 0, 2: 0, 3: 0, 4: 0}
    for i in range(w * h):
        r, g, b = px[i * 3], px[i * 3 + 1], px[i * 3 + 2]
        k = classify(r, g, b)
        counts[k] += 1
        if k == 4:
            m[i * 3] = 255
        elif k == 2:
            m[i * 3 + 1] = 255
        elif k == 3:
            m[i * 3 + 2] = 255
    return m, counts


def main(argv):
    if len(argv) < 3:
        print(__doc__)
        return 1
    src_path, out_dir = argv[1], argv[2]
    w = int(argv[argv.index('--w') + 1]) if '--w' in argv else 614
    h = int(argv[argv.index('--h') + 1]) if '--h' in argv else 1364
    colours = int(argv[argv.index('--colours') + 1]) if '--colours' in argv else 40

    sw, sh, src = read_png(src_path)
    print('source %dx%d  ratio %.4f' % (sw, sh, sh / sw))
    small = box_resample(sw, sh, src, w, h)
    print('resampled to %dx%d  (ratio %.4f)' % (w, h, h / w))
    centres = kmeans(small, colours)
    print('palette of %d colours: %s' % (len(centres), ' '.join('#%02x%02x%02x' % c for c in centres[:8]) + ' ...'))
    baked = quantise(w, h, small, centres)
    mask, counts = masks(w, h, baked)
    total = w * h
    names = {0: 'void', 1: 'ground', 2: 'road', 3: 'stone', 4: 'water'}
    print('what the mask found: ' + ', '.join('%s %.1f%%' % (names[k], 100.0 * v / total) for k, v in counts.items()))
    os.makedirs(out_dir, exist_ok=True)
    write_png(os.path.join(out_dir, 'background.png'), w, h, baked)
    write_png(os.path.join(out_dir, 'masks.png'), w, h, mask)
    meta = {
        'source': os.path.basename(src_path),
        'source_size': [sw, sh],
        'size': [w, h],
        'colours': colours,
        'share': {names[k]: round(v / total, 4) for k, v in counts.items()},
        'note': 'background.png is the fixed layer; masks.png is R=water, G=road, B=stone. '
                'Regenerate with: python3 tools/rift_bake.py <reference.png> <out-dir> --w %d --h %d' % (w, h),
    }
    open(os.path.join(out_dir, 'bake.json'), 'w').write(json.dumps(meta, indent=2) + '\n')
    for name in ('background.png', 'masks.png'):
        p = os.path.join(out_dir, name)
        print('%-16s %dx%d  %6.1f KB' % (name, w, h, os.path.getsize(p) / 1024.0))
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
