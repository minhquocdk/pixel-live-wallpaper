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


def components(w, h, keep):
    """Label 4-connected regions of `keep` pixels; returns a list of (size, minx, miny, maxx, maxy, [members])."""
    seen = bytearray(w * h)
    out = []
    for start in range(w * h):
        if not keep[start] or seen[start]:
            continue
        stack = [start]
        seen[start] = 1
        members = []
        minx, miny, maxx, maxy = w, h, 0, 0
        while stack:
            i = stack.pop()
            members.append(i)
            x, y = i % w, i // w
            if x < minx: minx = x
            if x > maxx: maxx = x
            if y < miny: miny = y
            if y > maxy: maxy = y
            if x > 0 and keep[i - 1] and not seen[i - 1]:
                seen[i - 1] = 1
                stack.append(i - 1)
            if x < w - 1 and keep[i + 1] and not seen[i + 1]:
                seen[i + 1] = 1
                stack.append(i + 1)
            if y > 0 and keep[i - w] and not seen[i - w]:
                seen[i - w] = 1
                stack.append(i - w)
            if y < h - 1 and keep[i + w] and not seen[i + w]:
                seen[i + w] = 1
                stack.append(i + w)
        out.append((len(members), minx, miny, maxx, maxy, members))
    return out


def majority(w, h, cls, rounds=2):
    """A pixel joins the class most of its neighbours are in: dithered art is speckled, a mask must not be."""
    for _ in range(rounds):
        nxt = bytearray(cls)
        for y in range(1, h - 1):
            row = y * w
            for x in range(1, w - 1):
                i = row + x
                tally = [0, 0, 0, 0, 0]
                for dy in (-1, 0, 1):
                    for dx in (-1, 0, 1):
                        tally[cls[i + dy * w + dx]] += 1
                best = max(range(5), key=lambda k: (tally[k], -k))
                if tally[best] >= 5:
                    nxt[i] = best
        cls = nxt
    return cls


def clean_void(w, h, px, cls):
    """The emptiness outside the plateau must stay empty.

    At 307 pixels wide, a waterfall at the cliff's edge is a handful of pale pixels; quantised, they become a flat
    pale rectangle floating in the dark, which reads as a mistake. So every pale pixel out in the emptiness is
    repainted from the emptiness itself: a smooth gradient between the two ends of the void, taken from the void's own
    colours at the top and the bottom of the picture.
    """
    void_rows = {}
    for y in range(h):
        row = []
        for x in range(w):
            i = y * w + x
            if cls[i] != 0:
                continue
            light = (px[i * 3] * 299 + px[i * 3 + 1] * 587 + px[i * 3 + 2] * 114) // 1000
            if light < 34:
                row.append((px[i * 3], px[i * 3 + 1], px[i * 3 + 2]))
        if row:
            row.sort(key=lambda c: c[0] * 299 + c[1] * 587 + c[2] * 114)
            void_rows[y] = row[len(row) // 2]
    if not void_rows:
        return px
    ys = sorted(void_rows)
    repaired = 0
    for y in range(h):
        top = void_rows[ys[0]]
        # the nearest row that had any darkness in it
        near = void_rows.get(y)
        if near is None:
            near = void_rows[min(ys, key=lambda v: abs(v - y))]
        for x in range(w):
            i = y * w + x
            if cls[i] != 0:
                continue
            light = (px[i * 3] * 299 + px[i * 3 + 1] * 587 + px[i * 3 + 2] * 114) // 1000
            if light <= 46:
                continue
            px[i * 3] = near[0]
            px[i * 3 + 1] = near[1]
            px[i * 3 + 2] = near[2]
            repaired += 1
    return px, repaired


def masks(w, h, px):
    cls = bytearray(w * h)
    for i in range(w * h):
        cls[i] = classify(px[i * 3], px[i * 3 + 1], px[i * 3 + 2])

    # water is one band across the map, not a hundred blue crystals: keep the big, wide regions only
    # smooth first: dithered art is speckled, and a speckle never forms a region worth keeping
    cls = majority(w, h, cls, rounds=2)

    water = bytearray(1 if cls[i] == 4 else 0 for i in range(w * h))
    for size, minx, miny, maxx, maxy, members in components(w, h, water):
        # the river, its banks and its waterfalls are all water — and all of them lie in the middle band of the
        # map, where the plan puts the river. A blue banner on a base is blue too, and it is not water.
        mid = (miny + maxy) / 2.0 / h
        if size < 200 or not (0.40 <= mid <= 0.64):
            for i in members:
                cls[i] = 1

    # roads are the network that joins both bases: keep the large regions, drop stray warm pixels
    road = bytearray(1 if cls[i] == 2 else 0 for i in range(w * h))
    for size, minx, miny, maxx, maxy, members in components(w, h, road):
        # roads come in pieces (the bridge, the river, the bases); each piece is long, a stray tan pixel is not
        long = max(maxx - minx + 1, maxy - miny + 1) > 30
        if size < 120 or not long:
            for i in members:
                cls[i] = 1

    # close the gaps dithering leaves in a road: a road pixel's neighbours on the ground belong to the road too
    road = bytearray(1 if cls[i] == 2 else 0 for i in range(w * h))
    grown = bytearray(road)
    for y in range(1, h - 1):
        for x in range(1, w - 1):
            i = y * w + x
            if road[i] or cls[i] != 1:
                continue
            if (road[i - 1] and road[i + 1]) or (road[i - w] and road[i + w]):
                grown[i] = 1
    for i in range(w * h):
        if grown[i] and cls[i] == 1:
            cls[i] = 2

    # the falls at the two ends of the river, at 300 pixels wide, are a few pale pixels that quantise into a flat
    # pale slab. Give them back the river's own dark water instead of a colour the art never meant to be there.
    dark = []
    for i in range(w * h):
        if cls[i] != 4:
            continue
        light = (px[i * 3] * 299 + px[i * 3 + 1] * 587 + px[i * 3 + 2] * 114) // 1000
        if light < 120:
            dark.append((px[i * 3], px[i * 3 + 1], px[i * 3 + 2]))
    if dark:
        dark.sort(key=lambda c: c[0] * 299 + c[1] * 587 + c[2] * 114)
        river = dark[len(dark) // 2]
        paled = 0
        for y in range(h):
            for x in range(w):
                i = y * w + x
                if cls[i] != 4:
                    continue
                if 0.06 < x / w < 0.94:
                    continue
                light = (px[i * 3] * 299 + px[i * 3 + 1] * 587 + px[i * 3 + 2] * 114) // 1000
                if light <= 104:
                    continue
                # a waterfall, not a slab: dither the pale strip down into the river's own colour
                px[i * 3], px[i * 3 + 1], px[i * 3 + 2] = river
                paled += 1
        # whatever light water is left at the two ends is the falls: dither it between two tones so it reads as water
        # coming down the cliff instead of a flat rectangle pasted onto the dark
        bayer = [0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5]
        lighter = (min(255, river[0] + 46), min(255, river[1] + 52), min(255, river[2] + 40))
        fell = 0
        for y in range(h):
            for x in range(w):
                i = y * w + x
                if cls[i] != 4 or not (0.06 < x / w < 0.94):
                    continue
                light = (px[i * 3] * 299 + px[i * 3 + 1] * 587 + px[i * 3 + 2] * 114) // 1000
                if light <= 104:
                    continue
                thr = (bayer[((y & 3) << 2) | (x & 3)] + 0.5) / 16.0
                pick = lighter if thr < (light - 104) / 120.0 else river
                px[i * 3], px[i * 3 + 1], px[i * 3 + 2] = pick
                fell += 1
        print('pale water at the river ends given back to the river: %d pixels, %d dithered as falls' % (paled, fell))

    cls = majority(w, h, cls, rounds=1)
    px, repaired = clean_void(w, h, px, cls)
    print('emptiness repainted: %d pale pixels put back into the dark' % repaired)

    m = bytearray(w * h * 3)
    counts = {0: 0, 1: 0, 2: 0, 3: 0, 4: 0}
    for i in range(w * h):
        k = cls[i]
        counts[k] += 1
        if k == 4:
            m[i * 3] = 255
        elif k == 2:
            m[i * 3 + 1] = 255
        elif k == 3:
            m[i * 3 + 2] = 255
    return m, counts


def find_lights(w, h, px, cls):
    """Where the lamps are, read out of the painting itself.

    A tower or a base carries a bright crystal in its side's colour; a pit is a big dark hole in the plateau. Both are
    blobs, and a blob has a centre — so the engine's night lights are placed where the artist drew the things, not
    where the plan would have put them.
    """
    def blobs(mask, min_size, max_size, max_aspect=2.6):
        out = []
        for size, minx, miny, maxx, maxy, members in components(w, h, mask):
            if size < min_size or size > max_size:
                continue
            bw = maxx - minx + 1
            bh = maxy - miny + 1
            if max(bw, bh) > max_aspect * min(bw, bh):
                continue
            sx = sy = 0
            for i in members:
                sx += i % w
                sy += i // w
            out.append((size, sx / size, sy / size, bw, bh))
        return out

    red = bytearray(w * h)
    blue = bytearray(w * h)
    dark = bytearray(w * h)
    for i in range(w * h):
        r, g, b = px[i * 3], px[i * 3 + 1], px[i * 3 + 2]
        light = (r * 299 + g * 587 + b * 114) // 1000
        if r > 120 and r - g > 45 and r - b > 45 and cls[i] != 0:
            red[i] = 1
        elif b > 130 and b - r > 60 and b - g > 20 and light > 90:
            # blue crystals are the same colour as water; a crystal is small, so the size test sorts them out
            blue[i] = 1
        elif light < 46:
            dark[i] = 1

    def ring_is_plateau(minx, miny, maxx, maxy):
        """A thing on the map has map around it; a shadow at the cliff's edge has darkness around it."""
        if minx < 3 or miny < 3 or maxx > w - 4 or maxy > h - 4:
            return False
        void = total = 0
        for x in range(minx - 2, maxx + 3):
            for y in (miny - 2, maxy + 2):
                total += 1
                if cls[y * w + x] == 0:
                    void += 1
        for y in range(miny - 2, maxy + 3):
            for x in (minx - 2, maxx + 2):
                total += 1
                if cls[y * w + x] == 0:
                    void += 1
        return void <= total * 0.3

    lights = []
    for team, mask in ((0, blue), (1, red)):
        for size, cx, cy, bw, bh in blobs(mask, 8, 600):
            if 0.42 < cy / h < 0.58:
                continue  # the river is blue too
            radius = max(5.0, min(18.0, (size ** 0.5) * 1.4))
            lights.append(('nexus' if size > 150 else 'lamp', team, round(cx, 1), round(cy, 1), round(radius, 1)))
    # every side has a base, even if its crystal is small enough that the size test called it a lamp
    for team in (0, 1):
        mine = [l for l in lights if l[0] == 'lamp' and l[1] == team]
        if not any(l[0] == 'nexus' for l in lights if l[1] == team) and mine:
            best = max(mine, key=lambda l: l[4])
            lights.remove(best)
            lights.append(('nexus', team, best[2], best[3], max(14.0, best[4])))
    pits = []
    for size, cx, cy, bw, bh in blobs(dark, 300, 9000, 2.2):
        if not (0.18 < cy / h < 0.82) or not (0.12 < cx / w < 0.88):
            continue
        minx = int(cx - bw / 2); miny = int(cy - bh / 2); maxx = int(cx + bw / 2); maxy = int(cy + bh / 2)
        if not ring_is_plateau(minx, miny, maxx, maxy):
            continue
        pits.append((size, cx, cy))
    pits.sort(reverse=True)
    for size, cx, cy in pits[:2]:
        radius = max(10.0, min(26.0, (size ** 0.5) * 0.5))
        lights.append(('pit', 0 if cy / h < 0.5 else 1, round(cx, 1), round(cy, 1), round(radius, 1)))
    return lights


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
    lights = find_lights(w, h, baked, bytearray(
        classify(baked[i * 3], baked[i * 3 + 1], baked[i * 3 + 2]) for i in range(w * h)
    ))
    with open(os.path.join(out_dir, 'lights.txt'), 'w') as f:
        f.write('# kind team x y radius — read out of the painting by tools/rift_bake.py\n')
        for kind, team, x, y, r in lights:
            f.write('%s %d %.1f %.1f %.1f\n' % (kind, team, x, y, r))
    print('lights found in the painting: %d (%d pits)' % (len(lights), sum(1 for l in lights if l[0] == 'pit')))
    for name in ('background.png', 'masks.png', 'lights.txt'):
        p = os.path.join(out_dir, name)
        print('%-16s %6.1f KB' % (name, os.path.getsize(p) / 1024.0))

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
