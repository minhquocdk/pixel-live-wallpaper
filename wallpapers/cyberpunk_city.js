// Scene 2: Cyberpunk Tokyo Rain (Đêm Mưa Cyberpunk Neon)
// Futuristic pixel cityscape with towering skyscrapers, holographic/neon billboards, glowing rain streaks, animated flying hover cars, puddles with reflection.
import * as almanac from '../src/index.js';

export function createCyberpunkCityScene(seedPhrase = 'neo-tokyo-2099') {
  const {
    clamp, lerp, smoothstep, SeededRandom, fractalNoise,
    packRgb, hexToRgb, gradeColour, hourLight, hourTables, gradeFrame, twinkle, blink,
    lineCells, blitSprite, outlinePass, seedOf, scalePacked, mixPacked, hueShiftTone, ditheredBorder
  } = almanac;

  const W = 384, H = 216;
  const SEED = seedOf(seedPhrase);
  const rng = new SeededRandom(SEED);

  const LOOK = { sat: 1.35, warm: -0.05, contrast: 1.18 };
  const hex = (s) => packRgb(...hexToRgb(gradeColour(s, LOOK)));
  const tone = (c, k) => hueShiftTone(c, k * 0.8);

  const C = {
    skyTop: hex('#0a0815'),
    skyBottom: hex('#18102b'),
    neonCyan: hex('#00f0ff'),
    neonPink: hex('#ff007f'),
    neonPurple: hex('#a020f0'),
    neonYellow: hex('#ffe600'),
    neonOrange: hex('#ff6600'),
    neonGreen: hex('#00ff66'),
    buildingDark: hex('#0d0f18'),
    buildingMid: hex('#181b2a'),
    buildingEdge: hex('#272c42'),
    windowWarm: hex('#ffd166'),
    windowCyan: hex('#70e0ff'),
    windowOff: hex('#121520'),
    streetAsphalt: hex('#13131a'),
    streetPuddle: hex('#1e1f30'),
    streetLine: hex('#e0a020'),
    rainCol: hex('#78a0d0'),
    fogCol: hex('#2d1b40'),
    carHeadlight: hex('#fff8e0'),
    carTaillight: hex('#ff1a40')
  };

  const buffer = (w, h) => ({ w, h, px: new Uint32Array(w * h) });

  // Generate 3 layers of buildings for 3D city depth (Parallax & Depth)
  // Layer 0: Distant mega-spires and silhouette monoliths
  // Layer 1: Mid-ground skyscrapers with glowing neon grids and antenna towers
  // Layer 2: Foreground architecture with large holographic signboards and cables

  const layers = [
    { count: 18, minW: 16, maxW: 36, minH: 90, maxH: 170, baseY: 175, col: C.buildingDark },
    { count: 12, minW: 24, maxW: 48, minH: 80, maxH: 155, baseY: 185, col: C.buildingMid },
    { count: 7, minW: 42, maxW: 75, minH: 70, maxH: 140, baseY: 195, col: hex('#202438') }
  ];

  const cityLayers = layers.map((cfg, lIdx) => {
    const buildings = [];
    let curX = -10;
    while (curX < W + 20) {
      const bw = rng.int(cfg.minW, cfg.maxW);
      const bh = rng.int(cfg.minH, cfg.maxH);
      const bx = curX;
      curX += bw - rng.int(2, 6); // slight overlap

      // Window grid configuration
      const winCols = Math.floor(bw / 4);
      const winRows = Math.floor(bh / 6);
      const winStates = [];
      for (let wy = 0; wy < winRows; wy++) {
        for (let wx = 0; wx < winCols; wx++) {
          winStates.push(rng.chance(0.28) ? (rng.chance(0.65) ? C.windowCyan : C.windowWarm) : C.windowOff);
        }
      }

      // Neon sign on building facade (especially mid and foreground)
      const hasNeon = lIdx >= 1 && rng.chance(0.65);
      const neonType = rng.int(0, 4); // 0: vertical kanji/text, 1: cyber logo, 2: cyber ring, 3: horizontal bar
      const neonCol = rng.pick([C.neonCyan, C.neonPink, C.neonYellow, C.neonPurple, C.neonGreen]);

      buildings.push({
        x: bx,
        w: bw,
        h: bh,
        baseY: cfg.baseY,
        winCols,
        winRows,
        winStates,
        hasNeon,
        neonType,
        neonCol,
        hasAntenna: rng.chance(0.4),
        antennaH: rng.int(8, 22)
      });
    }
    return { buildings, col: cfg.col };
  });

  // Hover cars / Flying traffic lanes across the skyline
  const HOVER_LANES = [
    { y: 55, dir: 1, speed: 45 },
    { y: 85, dir: -1, speed: 60 },
    { y: 120, dir: 1, speed: 75 }
  ];

  const hoverCars = Array.from({ length: 9 }, (_, i) => {
    const lane = HOVER_LANES[i % 3];
    return {
      lane,
      x: rng.range(0, W),
      y: lane.y + rng.range(-3, 3),
      w: rng.int(7, 12),
      h: 3,
      speed: (lane.speed + rng.range(-8, 8)) * lane.dir,
      col: rng.pick([C.neonCyan, C.neonPink, C.neonOrange, hex('#e0e0e0')])
    };
  });

  // Rain particle system
  const rainDrops = Array.from({ length: 180 }, () => ({
    x: rng.range(0, W),
    y: rng.range(0, H),
    speed: rng.range(320, 440),
    len: rng.range(6, 12),
    wind: -45
  }));

  // Puddles on street (Y: 190 to 216)
  const streetY = 188;

  // Cables suspended between skyscrapers
  const cables = [
    { x0: 20, y0: 80, x1: 150, y1: 105, sag: 14 },
    { x0: 140, y0: 100, x1: 290, y1: 85, sag: 18 },
    { x0: 260, y0: 90, x1: 390, y1: 115, sag: 16 }
  ];

  const FRAME = new Uint32Array(W * H);

  function render(timeSec, timeOfDayVal = 0.78) {
    const t = timeSec;
    const fr = Math.round(t * 30);

    // 1. Cyber Dark Night Sky with neon horizon glow
    for (let y = 0; y < H; y++) {
      const ny = y / H;
      let r = lerp(8, 28, ny);
      let g = lerp(6, 16, ny);
      let b = lerp(20, 50, ny);
      // Neon sky haze in lower half
      if (ny > 0.4) {
        const haze = (ny - 0.4) / 0.6;
        r += Math.sin(t * 0.5) * 4 + haze * 22;
        b += Math.cos(t * 0.4) * 5 + haze * 32;
      }
      const col = packRgb(r, g, b);
      for (let x = 0; x < W; x++) {
        FRAME[y * W + x] = col;
      }
    }

    // 2. Distant Skyline & Mega Structures
    cityLayers.forEach((layer, lIdx) => {
      for (const b of layer.buildings) {
        const topY = b.baseY - b.h;
        // Antenna
        if (b.hasAntenna) {
          const antX = b.x + (b.w >> 1);
          for (let y = topY - b.antennaH; y < topY; y++) {
            if (antX >= 0 && antX < W && y >= 0 && y < H) {
              FRAME[y * W + antX] = hex('#454e6b');
            }
          }
          // Blinking red beacon on tip
          if (blink(t, 1.2, 0, 0.2)) {
            const tipY = topY - b.antennaH;
            if (antX >= 0 && antX < W && tipY >= 0 && tipY < H) {
              FRAME[tipY * W + antX] = hex('#ff2233');
            }
          }
        }

        // Building solid rectangle
        for (let y = topY; y < b.baseY; y++) {
          if (y < 0 || y >= H) continue;
          for (let x = b.x; x < b.x + b.w; x++) {
            if (x < 0 || x >= W) continue;
            // Border highlight on left/top edge
            let pix = layer.col;
            if (x === b.x || y === topY) {
              pix = tone(layer.col, 1);
            }
            FRAME[y * W + x] = pix;
          }
        }

        // Windows
        if (lIdx >= 1) {
          const startX = b.x + 3;
          const startY = topY + 6;
          for (let wy = 0; wy < b.winRows; wy++) {
            const py = startY + wy * 5;
            if (py >= b.baseY - 4 || py >= H) break;
            for (let wx = 0; wx < b.winCols; wx++) {
              const px = startX + wx * 4;
              if (px >= b.x + b.w - 3 || px >= W) break;
              const wCol = b.winStates[wy * b.winCols + wx];
              if (wCol !== C.windowOff) {
                // gentle window flicker
                const flk = (Math.sin(t * 3 + wx * 7 + wy * 13) > 0.95);
                const drawCol = flk ? C.windowOff : wCol;
                if (px >= 0 && py >= 0) FRAME[py * W + px] = drawCol;
                if (px + 1 < W && py >= 0) FRAME[py * W + px + 1] = drawCol;
              }
            }
          }
        }

        // Holographic & Neon Signs
        if (b.hasNeon && lIdx >= 1) {
          const neonX = b.x + 4;
          const neonY = topY + 12;
          const neonPulse = (Math.sin(t * 5 + b.x) + 1) * 0.35 + 0.65;
          const activeNeon = scalePacked(b.neonCol, neonPulse);

          if (b.neonType === 0) {
            // Vertical kanji glyphs column
            for (let i = 0; i < 4; i++) {
              const gy = neonY + i * 8;
              if (gy + 6 < b.baseY && gy + 6 < H && neonX + 5 < b.x + b.w && neonX + 5 < W) {
                for (let dy = 0; dy < 5; dy++) {
                  FRAME[(gy + dy) * W + neonX + 1] = activeNeon;
                  if (dy === 0 || dy === 2 || dy === 4) {
                    FRAME[(gy + dy) * W + neonX] = activeNeon;
                    FRAME[(gy + dy) * W + neonX + 2] = activeNeon;
                  }
                }
              }
            }
          } else if (b.neonType === 1) {
            // Cyber Diamond / Shield Logo
            for (let dy = -4; dy <= 4; dy++) {
              const span = 4 - Math.abs(dy);
              const cy = neonY + 8 + dy;
              if (cy >= 0 && cy < H) {
                for (let dx = -span; dx <= span; dx++) {
                  const cx = neonX + 6 + dx;
                  if (cx >= 0 && cx < W) {
                    if (Math.abs(dx) === span || dy === 0) {
                      FRAME[cy * W + cx] = activeNeon;
                    }
                  }
                }
              }
            }
          } else {
            // Horizontal scrolling neon ticker / bar
            const barW = Math.min(b.w - 6, 24);
            const scroll = Math.floor(t * 12) % barW;
            for (let dx = 0; dx < barW; dx++) {
              const px = neonX + dx;
              const py = neonY + 4;
              if (px >= 0 && px < W && py >= 0 && py < H) {
                const isBright = Math.abs(dx - scroll) < 3;
                FRAME[py * W + px] = isBright ? hex('#ffffff') : activeNeon;
                FRAME[(py + 1) * W + px] = activeNeon;
              }
            }
          }
        }
      }
    });

    // 3. Hanging Power Cables with Catches
    for (const cb of cables) {
      const steps = 40;
      for (let s = 0; s < steps; s++) {
        const u = s / steps;
        const cx = Math.round(lerp(cb.x0, cb.x1, u));
        // Catenary / Parabola curve
        const sagY = 4 * cb.sag * u * (1 - u);
        const cy = Math.round(lerp(cb.y0, cb.y1, u) + sagY);
        if (cx >= 0 && cx < W && cy >= 0 && cy < H) {
          FRAME[cy * W + cx] = hex('#121622');
          // occasional hanging lamp on the cable
          if (s === 20) {
            FRAME[(cy + 1) * W + cx] = hex('#ffaa00');
            FRAME[(cy + 2) * W + cx] = hex('#ff4400');
          }
        }
      }
    }

    // 4. Flying Hover Cars / Traffic
    for (const car of hoverCars) {
      const curX = ((car.x + t * car.speed) % (W + 60)) - 30;
      const rx = Math.round(curX);
      const ry = Math.round(car.y + Math.sin(t * 3 + car.x) * 1.5);
      const isRight = car.speed > 0;

      for (let dy = 0; dy < car.h; dy++) {
        for (let dx = 0; dx < car.w; dx++) {
          const px = rx + dx;
          const py = ry + dy;
          if (px >= 0 && px < W && py >= 0 && py < H) {
            FRAME[py * W + px] = car.col;
          }
        }
      }
      // Headlights beam forward & taillights beam backward
      const frontX = isRight ? rx + car.w : rx - 1;
      const backX = isRight ? rx - 1 : rx + car.w;
      if (frontX >= 0 && frontX < W && ry >= 0 && ry < H) {
        FRAME[(ry + 1) * W + frontX] = C.carHeadlight;
        // Light cone
        const coneDir = isRight ? 1 : -1;
        for (let cd = 1; cd <= 10; cd++) {
          const beamX = frontX + cd * coneDir;
          if (beamX >= 0 && beamX < W) {
            const alpha = 0.35 * (1 - cd / 10);
            FRAME[(ry + 1) * W + beamX] = mixPacked(FRAME[(ry + 1) * W + beamX], C.carHeadlight, alpha);
          }
        }
      }
      if (backX >= 0 && backX < W && ry >= 0 && ry < H) {
        FRAME[(ry + 1) * W + backX] = C.carTaillight;
      }
    }

    // 5. Wet Street Floor with Neon Puddle Reflections
    for (let y = streetY; y < H; y++) {
      const ny = (y - streetY) / (H - streetY);
      for (let x = 0; x < W; x++) {
        let baseStreet = C.streetAsphalt;
        // Sidewalk / Road divider stripes
        if (y === streetY + 4 || y === streetY + 5) {
          baseStreet = hex('#25293d');
        }
        if (y === streetY + 14 && (Math.floor(x / 14) % 2 === 0)) {
          baseStreet = C.streetLine;
        }

        // Puddles reflecting building neon above!
        // Mirror vertical coordinate
        const mirrorY = Math.max(0, streetY - (y - streetY) * 2 - 4);
        const mirrorPixel = FRAME[mirrorY * W + x];
        const puddleNoise = fractalNoise(x / 16, y / 8, SEED + 80, 2);

        if (puddleNoise > 0.45) {
          // Reflect neon with ripple shimmer
          const rip = Math.sin(x * 0.4 + t * 8 + y) * 2;
          const ripX = clamp(Math.round(x + rip), 0, W - 1);
          const refCol = FRAME[mirrorY * W + ripX];
          FRAME[y * W + x] = mixPacked(baseStreet, refCol, 0.45 + ny * 0.25);
        } else {
          FRAME[y * W + x] = baseStreet;
        }
      }
    }

    // 6. Rain Streaks (Heavy Cyber Rain)
    const dt = 1 / 30;
    for (const r of rainDrops) {
      const rx = Math.round((r.x + t * r.wind) % W);
      const ry = Math.round((r.y + t * r.speed) % H);
      const px = rx < 0 ? rx + W : rx;
      const py = ry;

      lineCells(px, py, px - 1, py + Math.round(r.len), (lx, ly) => {
        if (lx >= 0 && lx < W && ly >= 0 && ly < H) {
          // Mix translucent rain blue
          FRAME[ly * W + lx] = mixPacked(FRAME[ly * W + lx], C.rainCol, 0.4);
        }
      });
      // Splash ripple when hitting ground
      if (py >= streetY && py <= H - 3 && ((Math.floor(t * 20) + px) % 9 === 0)) {
        if (px - 1 >= 0 && px + 1 < W) {
          FRAME[py * W + px - 1] = C.rainCol;
          FRAME[py * W + px] = hex('#ffffff');
          FRAME[py * W + px + 1] = C.rainCol;
        }
      }
    }

    // 7. Occasional Cyber Lightning / Glitch Flash
    const flashPhase = (t * 0.15) % 1;
    if (flashPhase > 0.94 && flashPhase < 0.97) {
      lightningFlash(FRAME, 0.25);
    }

    return FRAME;
  }

  return {
    name: 'Cyberpunk Tokyo Rain',
    description: 'A rain-slicked mega metropolis with towering skyscrapers, glowing neon holograms, flying spinner traffic, and reflective puddles.',
    W, H,
    render
  };
}
