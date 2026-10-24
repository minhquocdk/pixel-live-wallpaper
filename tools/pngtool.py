#!/usr/bin/env python3
"""Tiny PNG reader/writer for looking at the engine's output: crop, zoom, and read pixels.

    python3 tools/pngtool.py crop in.png out.png x y w h [scale]
    python3 tools/pngtool.py pixel in.png x y [x y ...]
    python3 tools/pngtool.py diff a.png b.png [out.png]
"""
import sys
import zlib
import struct


def read_png(path):
    data = open(path, 'rb').read()
    assert data[:8] == b'\x89PNG\r\n\x1a\n'
    pos = 8
    w = h = None
    idat = b''
    depth = ctype = None
    while pos < len(data):
        length = struct.unpack('>I', data[pos:pos + 4])[0]
        typ = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if typ == b'IHDR':
            w, h, depth, ctype = struct.unpack('>IIBB', chunk[:10])
        elif typ == b'IDAT':
            idat += chunk
        elif typ == b'IEND':
            break
    raw = zlib.decompress(idat)
    channels = 4 if ctype == 6 else 3
    stride = w * channels
    out = bytearray(w * h * channels)
    prev = bytearray(stride)
    p = 0
    for y in range(h):
        f = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride
        if f == 1:
            for i in range(channels, stride):
                line[i] = (line[i] + line[i - channels]) & 255
        elif f == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 255
        elif f == 3:
            for i in range(stride):
                a = line[i - channels] if i >= channels else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 255
        elif f == 4:
            for i in range(stride):
                a = line[i - channels] if i >= channels else 0
                b = prev[i]
                c = prev[i - channels] if i >= channels else 0
                pp = a + b - c
                pa, pb, pc = abs(pp - a), abs(pp - b), abs(pp - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 255
        out[y * stride:(y + 1) * stride] = line
        prev = line
    return w, h, channels, out


def write_png(path, w, h, ch, px):
    raw = bytearray()
    stride = w * ch
    for y in range(h):
        raw.append(0)
        raw += px[y * stride:(y + 1) * stride]

    def chunk(typ, data):
        return struct.pack('>I', len(data)) + typ + data + struct.pack('>I', zlib.crc32(typ + data) & 0xffffffff)

    ihdr = struct.pack('>IIBBBBB', w, h, 8, 6 if ch == 4 else 2, 0, 0, 0)
    open(path, 'wb').write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', ihdr) +
                           chunk(b'IDAT', zlib.compress(bytes(raw), 6)) + chunk(b'IEND', b''))


def main():
    cmd = sys.argv[1]
    if cmd == 'crop':
        src, dst, x, y, w, h = sys.argv[2], sys.argv[3], *map(int, sys.argv[4:8])
        scale = int(sys.argv[8]) if len(sys.argv) > 8 else 1
        W, H, ch, px = read_png(src)
        out = bytearray(w * scale * h * scale * ch)
        for yy in range(h * scale):
            for xx in range(w * scale):
                sx = min(W - 1, x + xx // scale)
                sy = min(H - 1, y + yy // scale)
                i = (sy * W + sx) * ch
                j = (yy * w * scale + xx) * ch
                out[j:j + ch] = px[i:i + ch]
        write_png(dst, w * scale, h * scale, ch, out)
        print(f'{dst}: {w * scale}x{h * scale} from {src} at {x},{y}')
    elif cmd == 'pixel':
        W, H, ch, px = read_png(sys.argv[2])
        vals = list(map(int, sys.argv[3:]))
        for k in range(0, len(vals), 2):
            x, y = vals[k], vals[k + 1]
            i = (y * W + x) * ch
            print(x, y, '#%02x%02x%02x' % (px[i], px[i + 1], px[i + 2]))
    elif cmd == 'diff':
        Wa, Ha, cha, a = read_png(sys.argv[2])
        Wb, Hb, chb, b = read_png(sys.argv[3])
        assert (Wa, Ha) == (Wb, Hb), f'sizes differ: {Wa}x{Ha} vs {Wb}x{Hb}'
        diff = 0
        big = 0
        for y in range(Ha):
            for x in range(Wa):
                i = (y * Wa + x) * cha
                j = (y * Wb + x) * chb
                d = sum(abs(a[i + k] - b[j + k]) for k in range(3))
                if d > 30:
                    diff += 1
                if d > 120:
                    big += 1
        print(f'{diff} of {Wa * Ha} pixels differ (by more than 30), {big} strongly')
        if len(sys.argv) > 4:
            out = bytearray(Wa * Ha * 3)
            for y in range(Ha):
                for x in range(Wa):
                    i = (y * Wa + x) * cha
                    j = (y * Wb + x) * chb
                    d = sum(abs(a[i + k] - b[j + k]) for k in range(3))
                    v = min(255, d * 2)
                    o = (y * Wa + x) * 3
                    out[o] = v
                    out[o + 1] = 255 - v if d < 30 else 0
                    out[o + 2] = 0
            write_png(sys.argv[4], Wa, Ha, 3, out)
            print('wrote', sys.argv[4])


main()
