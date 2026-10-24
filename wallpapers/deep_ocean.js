// Scene 4: Deep Ocean Abyss (Đáy Biển Huyền Bí)
// Bioluminescent coral reefs, giant glowing jellyfish pulsing, schools of fish flocking, ancient sunken shipwreck, ambient light rays, deep sea bubbles rising.
import * as almanac from '../src/index.js';

export function createDeepOceanScene(seedPhrase = 'abyssal-trench-100') {
  const {
    clamp, lerp, smoothstep, SeededRandom, fractalNoise,
    packRgb, hexToRgb, gradeColour, twinkle,
    fillEllipse, lineCells, ditherEllipse, blitSprite, outlinePass,
    seedOf, scalePacked, mixPacked, hueShiftTone, flockStep, smoothCurve, followJoint
  } = almanac;

  const W = 384, H = 216;
  const SEED = seedOf(seedPhrase);
  const rng = new SeededRandom(SEED);

  const LOOK = { sat: 1.25, warm: -0.15, contrast: 1.1 };
  const hex = (s) => packRgb(...hexToRgb(gradeColour(s, LOOK)));
  const tone = (c, k) => hueShiftTone(c, k);

  const C = {
    abyssDark: hex('#030814'),
    abyssMid: hex('#0a1b38'),
    abyssLight: hex('#133561'),
    lightRay: hex('#69d9eb'),
    sandFloor: hex('#1a2638'),
    sandRidge: hex('#273a54'),
    coralPink: hex('#ff4f81'),
    coralPurple: hex('#9d4edd'),
    coralCyan: hex('#00f5d4'),
    coralOrange: hex('#ff7b00'),
    jellyDome: hex('#70e0ff'),
    jellyCore: hex('#ffffff'),
    jellyTentacle: hex('#c77dff'),
    bubbleCol: hex('#bbf2f6'),
    shipWood: hex('#1f1916'),
    shipPlank: hex('#33261f'),
    fishCol: hex('#ffd166'),
    fishBelly: hex('#06d6a0')
  };

  const buffer = (w, h) => ({ w, h, px: new Uint32Array(w * h) });

  // Ocean floor seafloor terrain
  const seafloorY = new Int16Array(W);
  for (let x = 0; x < W; x++) {
    const rise = Math.sin(x / 40) * 12 + fractalNoise(x / 25, 1.2, SEED, 3) * 16;
    seafloorY[x] = Math.round(175 + rise);
  }

  // Pre-generate coral reefs along the seafloor
  const coralList = [];
  for (let x = 15; x < W - 15; x += rng.int(10, 24)) {
    const cy = seafloorY[x];
    const type = rng.int(0, 3); // 0: brain coral, 1: branch coral, 2: tube sea anemone
    const col = rng.pick([C.coralPink, C.coralPurple, C.coralCyan, C.coralOrange]);
    coralList.push({ x, y: cy, type, col, h: rng.int(12, 26) });
  }

  // Sunken Galleon Shipwreck on the left seabed (x: 25 to 110)
  const shipwreck = (() => {
    const r = buffer(85, 55);
    const ax = 42, ay = 50;
    // Hull tilting into the sand
    for (let y = 15; y < 50; y++) {
      const u = (y - 15) / 35;
      const wSpan = Math.round(lerp(28, 38, u));
      for (let x = ax - wSpan + Math.round(u * 10); x <= ax + wSpan; x++) {
        const isPlank = (y % 4 === 0) || ((x + y) % 8 === 0);
        r.px[y * 85 + x] = isPlank ? tone(C.shipWood, -1) : C.shipPlank;
      }
    }
    // Broken mast & rigging
    for (let y = 0; y < 28; y++) {
      r.px[y * 85 + 32] = C.shipPlank;
      r.px[y * 85 + 33] = C.shipWood;
      if (y === 10) {
        for (let x = 18; x <= 46; x++) r.px[y * 85 + x] = C.shipPlank;
      }
    }
    // Broken cannon port holes
    for (const px of [22, 34, 46, 58]) {
      r.px[32 * 85 + px] = hex('#050505');
      r.px[33 * 85 + px] = hex('#050505');
    }
    outlinePass(r, hex('#080d14'));
    return { ...r, ax, ay };
  })();

  // Giant Bioluminescent Jellyfish
  const jellyfish = [
    { x: 160, y: 85, r: 18, speed: 0.8, phase: 0 },
    { x: 265, y: 55, r: 14, speed: 0.95, phase: 1.8 },
    { x: 330, y: 120, r: 10, speed: 1.1, phase: 3.5 }
  ];

  // School of fish (Flocking algorithm)
  let fishSchool = Array.from({ length: 22 }, (_, k) => ({
    x: rng.range(120, 300),
    y: rng.range(60, 140),
    vx: rng.range(15, 30),
    vy: rng.range(-5, 5)
  }));

  // Deep Sea Bubbles (Bọt Khí Nổi Lên)
  const bubbles = Array.from({ length: 40 }, () => ({
    x: rng.range(10, W - 10),
    y: rng.range(0, H),
    speed: rng.range(18, 45),
    wobble: rng.range(0.8, 2.5),
    size: rng.range(1.2, 3.0)
  }));

  const FRAME = new Uint32Array(W * H);

  function render(timeSec, timeOfDayVal = 0.5) {
    const t = timeSec;
    const fr = Math.round(t * 30);

    // 1. Abyssal Ocean Gradient (top blue to deep trench midnight)
    for (let y = 0; y < H; y++) {
      const ny = y / H;
      const r = lerp(16, 2, ny);
      const g = lerp(48, 8, ny);
      const b = lerp(92, 22, ny);
      const bgCol = packRgb(r, g, b);
      for (let x = 0; x < W; x++) {
        FRAME[y * W + x] = bgCol;
      }
    }

    // 2. Volumetric Caustic / Sun Light Rays piercing down through water
    for (let i = 0; i < 5; i++) {
      const rayX0 = 50 + i * 70 + Math.sin(t * 0.4 + i) * 20;
      const rayWidth = 24 + Math.sin(t * 0.6 + i * 2) * 8;
      for (let y = 0; y < H - 25; y++) {
        const ny = y / H;
        const drift = Math.sin(y * 0.03 + t + i) * 6;
        const cx = rayX0 + (y * 0.35) + drift;
        const alpha = (1 - ny) * 0.16;
        for (let dx = -rayWidth / 2; dx <= rayWidth / 2; dx++) {
          const px = Math.round(cx + dx);
          if (px >= 0 && px < W) {
            const edgeFade = 1 - Math.abs(dx) / (rayWidth / 2);
            FRAME[y * W + px] = mixPacked(FRAME[y * W + px], C.lightRay, alpha * edgeFade);
          }
        }
      }
    }

    // 3. Sunken Shipwreck on Seabed
    blitSprite({ w: W, h: H, px: FRAME }, shipwreck, 75, seafloorY[75] - 8);

    // 4. Seafloor Terrain with Bioluminescent Sand
    for (let x = 0; x < W; x++) {
      const sy = seafloorY[x];
      for (let y = sy; y < H; y++) {
        const depth = y - sy;
        let sCol = (depth <= 2) ? C.sandRidge : C.sandFloor;
        // occasional bioluminescent plankton speck
        if (fractalNoise(x / 6, y / 6, SEED + 55, 2) > 0.78) {
          sCol = C.coralCyan;
        }
        FRAME[y * W + x] = sCol;
      }
    }

    // 5. Corals (Branching & Glowing anemones swaying in current)
    for (const c of coralList) {
      const sway = Math.sin(t * 2 + c.x * 0.1) * 3;
      if (c.type === 0) {
        // Dome / Brain coral
        fillEllipse({ w: W, h: H, px: FRAME }, c.x, c.y - 6, 8, 7, c.col);
      } else if (c.type === 1) {
        // Branching staghorn coral
        for (let dy = 0; dy < c.h; dy++) {
          const py = c.y - dy;
          const px = Math.round(c.x + (dy / c.h) * sway);
          if (px >= 0 && px < W && py >= 0 && py < H) {
            FRAME[py * W + px] = c.col;
            if (dy > 6 && dy % 5 === 0) {
              // side branches
              for (let b = 1; b <= 4; b++) {
                if (px + b < W) FRAME[py * W + px + b] = c.col;
                if (px - b >= 0) FRAME[py * W + px - b] = c.col;
              }
            }
          }
        }
      } else {
        // Sea anemone tentacles
        for (let a = -3; a <= 3; a++) {
          const tenX = c.x + a * 3;
          lineCells(tenX, c.y, Math.round(tenX + sway * 1.5 + a * 2), c.y - c.h, (lx, ly) => {
            if (lx >= 0 && lx < W && ly >= 0 && ly < H) FRAME[ly * W + lx] = c.col;
          });
        }
      }
    }

    // 6. Fish School Flocking Simulation
    fishSchool = flockStep(fishSchool, 0.03, {
      cohesion: 0.25,
      alignment: 0.45,
      separation: { within: 6, push: 10 },
      speed: [15, 35],
      wander: { t, rate: [0.6, 0.8], amount: [8, 8] }
    }).map((f) => {
      // Screen wrap
      let nx = f.x;
      let ny = f.y;
      if (nx > W + 20) nx = -10;
      if (nx < -20) nx = W + 10;
      if (ny < 30) { ny = 30; f.vy = Math.abs(f.vy); }
      if (ny > 165) { ny = 165; f.vy = -Math.abs(f.vy); }
      return { ...f, x: nx, y: ny };
    });

    for (const f of fishSchool) {
      const rx = Math.round(f.x), ry = Math.round(f.y);
      const isRight = f.vx > 0;
      if (rx >= 2 && rx < W - 2 && ry >= 2 && ry < H - 2) {
        FRAME[ry * W + rx] = C.fishCol;
        FRAME[ry * W + (isRight ? rx + 1 : rx - 1)] = hex('#ffffff');
        FRAME[ry * W + (isRight ? rx - 1 : rx + 1)] = C.coralOrange;
        FRAME[(ry + 1) * W + rx] = C.fishBelly;
      }
    }

    // 7. Giant Pulsing Bioluminescent Jellyfish
    for (const j of jellyfish) {
      // Periodic rhythmic propulsion contraction
      const cycle = (t * j.speed + j.phase) % (Math.PI * 2);
      const pulse = Math.pow(Math.sin(cycle), 4); // sharp contraction, slow expansion
      const curY = j.y - Math.sin(cycle) * 8;
      const bellRx = j.r * (1 - pulse * 0.25);
      const bellRy = j.r * 0.75 * (1 + pulse * 0.35);

      // Translucent glowing bell
      for (let dy = -bellRy; dy <= 2; dy++) {
        for (let dx = -bellRx; dx <= bellRx; dx++) {
          const normD = (dx * dx) / (bellRx * bellRx) + (dy * dy) / (bellRy * bellRy);
          if (normD <= 1.0) {
            const px = Math.round(j.x + dx);
            const py = Math.round(curY + dy);
            if (px >= 0 && px < W && py >= 0 && py < H) {
              const alpha = (1 - normD * 0.5) * 0.75;
              const col = (normD < 0.25) ? C.jellyCore : C.jellyDome;
              FRAME[py * W + px] = mixPacked(FRAME[py * W + px], col, alpha);
            }
          }
        }
      }

      // Trailing flowing tentacles with wavy sine curves
      const tentacleCount = 7;
      for (let k = 0; k < tentacleCount; k++) {
        const tox = j.x + (k - (tentacleCount >> 1)) * (j.r * 0.28);
        const toy = curY + 2;
        const len = 28 + (k % 3) * 6;
        for (let seg = 1; seg <= len; seg++) {
          const u = seg / len;
          const wav = Math.sin(seg * 0.3 - t * 5 + k) * (u * 6);
          const px = Math.round(tox + wav);
          const py = Math.round(toy + seg);
          if (px >= 0 && px < W && py >= 0 && py < H) {
            const tCol = (seg % 3 === 0) ? C.jellyCore : C.jellyTentacle;
            FRAME[py * W + px] = mixPacked(FRAME[py * W + px], tCol, 0.7 * (1 - u * 0.4));
          }
        }
      }
    }

    // 8. Rising Bubbles
    for (const b of bubbles) {
      const curY = ((b.y - t * b.speed) % H);
      const by = Math.round(curY < 0 ? curY + H : curY);
      const bx = Math.round(b.x + Math.sin(t * b.wobble + b.x) * 4);
      if (bx >= 1 && bx < W - 1 && by >= 1 && by < H - 1) {
        FRAME[by * W + bx] = C.bubbleCol;
        if (b.size > 2) {
          FRAME[(by - 1) * W + bx] = C.bubbleCol;
          FRAME[by * W + bx + 1] = hex('#ffffff'); // shine spot
        }
      }
    }

    return FRAME;
  }

  return {
    name: 'Deep Ocean Abyss',
    description: 'A mystical underwater abyss with piercing caustic light rays, pulsing bioluminescent jellyfish, sunken pirate shipwrecks, and shimmering schools of fish.',
    W, H,
    render
  };
}
