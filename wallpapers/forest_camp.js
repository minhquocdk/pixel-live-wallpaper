// Scene 3: Enchanted Forest & Campfire (Rừng Phép Thuật & Lửa Trại)
// Ancient mossy giant trees, animated warm crackling campfire with rising sparks, glowing fireflies drifting, wandering deer herd, starry canopy through foliage.
import * as almanac from '../src/index.js';

export function createForestCampScene(seedPhrase = 'ancient-glade-42') {
  const {
    clamp, lerp, smoothstep, SeededRandom, fractalNoise,
    packRgb, hexToRgb, gradeColour, hourLight, hourTables, gradeFrame, twinkle,
    branchingTree, fillEllipse, capsuleSteps, lineCells, ditherEllipse,
    shadeEllipse, blitSprite, blitSway, outlinePass, leafCrown, barkTrunk,
    seedOf, scalePacked, mixPacked, hueShiftTone, gaitTiming, footStep, flockStep, fleeVector
  } = almanac;

  const W = 384, H = 216;
  const SEED = seedOf(seedPhrase);
  const rng = new SeededRandom(SEED);

  const LOOK = { sat: 1.18, warm: 0.12, contrast: 1.08 };
  const hex = (s) => packRgb(...hexToRgb(gradeColour(s, LOOK)));
  const tone = (c, k) => hueShiftTone(c, k);

  const C = {
    nightSky1: hex('#0a1128'),
    nightSky2: hex('#1c1f3b'),
    groundGrass1: hex('#1b3820'),
    groundGrass2: hex('#274e2d'),
    groundMoss: hex('#3f6e38'),
    dirtEarth: hex('#3e2d1d'),
    stoneGrey: hex('#5a6068'),
    fireCore: hex('#ffffff'),
    fireYellow: hex('#ffe066'),
    fireOrange: hex('#ff8c1a'),
    fireRed: hex('#d9381e'),
    fireEmber: hex('#ff4d1a'),
    smoke: hex('#78788c'),
    firefly: hex('#c8ff4d'),
    tentCloth: hex('#406080'),
    tentWarm: hex('#d48c46'),
    deerCoat: hex('#965d38'),
    deerBelly: hex('#d9b18f'),
    starCol: hex('#eaf4ff'),
    leavesDark: ['#122918', '#1c3d24', '#2b5c37', '#428050'].map(hex),
    leavesGlow: ['#143a38', '#1f5956', '#318580', '#67d1ca'].map(hex),
    trunkCol: [hex('#301e14'), hex('#543625')]
  };

  const buffer = (w, h) => ({ w, h, px: new Uint32Array(w * h) });

  // Ground contour
  const groundY = new Int16Array(W);
  for (let x = 0; x < W; x++) {
    const hill = Math.sin(x / 60) * 8 + fractalNoise(x / 40, 0.5, SEED, 3) * 12;
    groundY[x] = Math.round(155 + hill);
  }

  // Pre-draw ground layer
  const groundLayer = buffer(W, H);
  for (let x = 0; x < W; x++) {
    const gy = groundY[x];
    for (let y = gy; y < H; y++) {
      const depth = y - gy;
      let col;
      if (depth <= 2) {
        col = (x + y) % 3 === 0 ? C.groundGrass1 : C.groundGrass2;
        if (fractalNoise(x / 10, y / 10, SEED + 3, 2) > 0.6) col = C.groundMoss;
      } else if (depth <= 6) {
        col = C.dirtEarth;
      } else {
        col = hex('#221a14');
      }
      groundLayer.px[y * W + x] = col;
    }
  }

  // Great Ancient Trees (Silhouettes & Detailed Foreground)
  const trees = [
    { x: 30, y: groundY[30], scale: 1.25, seed: SEED + 10 },
    { x: 100, y: groundY[100], scale: 0.85, seed: SEED + 20 },
    { x: 285, y: groundY[285], scale: 0.9, seed: SEED + 30 },
    { x: 355, y: groundY[355], scale: 1.35, seed: SEED + 40 }
  ].map((tr) => {
    const r = buffer(80, 110);
    const ax = 40, ay = 105;
    const rnd = new SeededRandom(tr.seed);
    const tTree = branchingTree(ax, ay, { levels: 4, forks: 2, spread: 0.62, decay: 0.75, length: 18 * tr.scale, width: 6 * tr.scale }, 1, rnd);
    for (const s of tTree.segments) {
      capsuleSteps(s.x0, s.y0, s.width / 2, s.x1, s.y1, (s.width * 0.75) / 2, (x, y, rr) =>
        fillEllipse(r, x, y, rr, rr, s.level > 2 ? C.trunkCol[0] : C.trunkCol[1])
      );
    }
    tTree.tips.forEach(([tx, ty], k) => {
      leafCrown(r, rnd, tx, ty, 8 * tr.scale, 6 * tr.scale, k % 2 === 0 ? C.leavesDark : C.leavesGlow, { clumps: 5 });
    });
    outlinePass(r, hex('#080d0a'));
    return { ...tr, sp: { ...r, ax, ay } };
  });

  // Camper Tent Sprite near fire
  const tentSprite = (() => {
    const r = buffer(32, 24);
    const ax = 16, ay = 22;
    // A-frame tent
    for (let y = 4; y <= 22; y++) {
      const span = Math.round(((y - 4) / 18) * 14);
      for (let x = ax - span; x <= ax + span; x++) {
        // Shading: left lit by campfire, right in shadow
        r.px[y * 32 + x] = x < ax ? C.tentWarm : C.tentCloth;
      }
    }
    // Tent entrance slit
    for (let y = 10; y <= 22; y++) {
      r.px[y * 32 + ax] = hex('#151820');
      r.px[y * 32 + ax + 1] = hex('#151820');
    }
    outlinePass(r, hex('#0f1418'));
    return { ...r, ax, ay };
  })();

  // Animated Deer Herd
  const deerHerd = [
    { x: 310, y: groundY[310] - 2, dir: -1, phase: 0 },
    { x: 335, y: groundY[335] - 2, dir: -1, phase: 1.4 },
    { x: 260, y: groundY[260] - 2, dir: 1, phase: 2.8 }
  ];

  function drawDeer(layer, d, t) {
    const r = buffer(20, 16);
    const ax = 10, ay = 14;
    const walkCycle = gaitTiming('walk');
    const stepA = footStep(d.phase, 1.2, 1.2);
    const stepB = footStep(d.phase + walkCycle[0].far, 1.2, 1.2);

    // Legs
    for (let y = 8; y <= 14; y++) {
      r.px[y * 20 + 6] = C.deerCoat;
      r.px[y * 20 + 13] = C.deerCoat;
    }
    // Body ellipse
    fillEllipse(r, 10, 7, 5.5, 3.2, C.deerCoat);
    fillEllipse(r, 10, 8.5, 4.2, 1.4, C.deerBelly);
    // Neck & Head
    fillEllipse(r, d.dir > 0 ? 15 : 5, 4, 2.5, 2.2, C.deerCoat);
    // Antlers
    const hx = d.dir > 0 ? 15 : 5;
    r.px[1 * 20 + hx] = hex('#e0d5c1');
    r.px[2 * 20 + hx] = hex('#e0d5c1');
    r.px[1 * 20 + (d.dir > 0 ? hx - 1 : hx + 1)] = hex('#e0d5c1');

    outlinePass(r, hex('#181008'));
    blitSprite(layer, { ...r, ax, ay }, Math.round(d.x), Math.round(d.y), d.dir < 0);
  }

  // Campfire Location: Center clearing
  const fireX = 180;
  const fireY = groundY[fireX] - 2;

  // Campfire Embers & Smoke
  const embers = Array.from({ length: 30 }, (_, k) => ({
    x: fireX,
    y: fireY,
    vx: rng.range(-0.8, 0.8),
    vy: rng.range(-15, -35),
    life: rng.range(0.5, 2.2),
    age: rng.range(0, 2)
  }));

  // Fireflies (Đom Đóm) drifting around trees
  const fireflies = Array.from({ length: 28 }, (_, k) => ({
    x: rng.range(20, W - 20),
    y: rng.range(80, H - 20),
    speed: rng.range(0.8, 2.2),
    phase: rng.range(0, Math.PI * 2),
    radius: rng.range(6, 18)
  }));

  // Stars
  const stars = Array.from({ length: 90 }, () => ({
    x: rng.int(0, W - 1),
    y: rng.int(0, 110),
    bright: rng.range(0.4, 1.0),
    phase: rng.range(0, 6.28)
  }));

  const FRAME = new Uint32Array(W * H);

  function render(timeSec, timeOfDayVal = 0.85) {
    const t = timeSec;
    const fr = Math.round(t * 30);
    const hl = hourLight(timeOfDayVal, 1, 0);

    // 1. Deep Midnight Blue Sky
    for (let y = 0; y < H; y++) {
      const ny = y / H;
      const r = lerp(6, 18, ny);
      const g = lerp(10, 28, ny);
      const b = lerp(26, 55, ny);
      const skyCol = packRgb(r, g, b);
      for (let x = 0; x < W; x++) {
        FRAME[y * W + x] = skyCol;
      }
    }

    // 2. Stars twinkling through foliage
    for (const s of stars) {
      const tw = (Math.sin(t * 2.5 + s.phase) + 1) * 0.5 * s.bright;
      if (tw > 0.45) {
        FRAME[s.y * W + s.x] = mixPacked(FRAME[s.y * W + s.x], C.starCol, tw);
      }
    }

    // 3. Distant Mountain Silhouette
    for (let x = 0; x < W; x++) {
      const mY = Math.round(115 + Math.sin(x / 45) * 14 + Math.cos(x / 90) * 20);
      for (let y = mY; y < 160; y++) {
        FRAME[y * W + x] = hex('#12172a');
      }
    }

    // 4. Ground Layer
    for (let y = 0; y < H; y++) {
      for (let x = 0; x < W; x++) {
        const pix = groundLayer.px[y * W + x];
        if (pix !== 0) FRAME[y * W + x] = pix;
      }
    }

    // 5. Trees (Gently swaying in night breeze)
    for (const tr of trees) {
      blitSway({ w: W, h: H, px: FRAME }, tr.sp, tr.x, tr.y, (hh) => {
        return hh > 25 ? Math.sin(t * 1.5 + tr.x) * (hh - 25) * 0.03 : 0;
      });
    }

    // 6. Tent Sprite
    blitSprite({ w: W, h: H, px: FRAME }, tentSprite, fireX - 45, groundY[fireX - 45]);

    // 7. Deer Herd (Slow wandering & breathing animation)
    for (const d of deerHerd) {
      d.phase += 0.03;
      drawDeer({ w: W, h: H, px: FRAME }, d, t);
    }

    // 8. Animated Campfire Stones & Logs
    for (let dx = -8; dx <= 8; dx++) {
      for (let dy = -2; dy <= 2; dy++) {
        if (Math.abs(dx) >= 5 || Math.abs(dy) === 2) {
          const px = fireX + dx, py = fireY + dy + 1;
          if (px >= 0 && px < W && py >= 0 && py < H) {
            FRAME[py * W + px] = C.stoneGrey;
          }
        }
      }
    }

    // Fire Flames (Multistage procedural particle & noise flicker)
    const flameH = 14 + Math.sin(t * 18) * 3 + Math.cos(t * 26) * 2;
    for (let dy = 0; dy < flameH; dy++) {
      const u = dy / flameH; // 0 at base, 1 at tip
      const span = Math.round((1 - u) * 6);
      const wobble = Math.round(Math.sin(dy * 0.8 - t * 15) * (u * 3));
      for (let dx = -span; dx <= span; dx++) {
        const px = fireX + dx + wobble;
        const py = Math.round(fireY - dy);
        if (px >= 0 && px < W && py >= 0 && py < H) {
          let col;
          if (u < 0.25) col = C.fireCore;
          else if (u < 0.55) col = C.fireYellow;
          else if (u < 0.85) col = C.fireOrange;
          else col = C.fireRed;
          FRAME[py * W + px] = col;
        }
      }
    }

    // Rising Embers (Tàn Lửa Bay)
    for (const eb of embers) {
      eb.age = (eb.age + 0.03) % eb.life;
      const u = eb.age / eb.life;
      const ex = Math.round(fireX + eb.vx * eb.age * 20 + Math.sin(eb.age * 8 + eb.vx) * 4);
      const ey = Math.round(fireY + eb.vy * eb.age);
      if (ex >= 0 && ex < W && ey >= 0 && ey < H) {
        const col = u < 0.4 ? C.fireYellow : (u < 0.8 ? C.fireEmber : C.fireRed);
        FRAME[ey * W + ex] = col;
      }
    }

    // 9. Fireflies (Đom đóm phát sáng nhấp nháy bay vòng)
    for (const ff of fireflies) {
      const fx = Math.round(ff.x + Math.cos(t * ff.speed + ff.phase) * ff.radius);
      const fy = Math.round(ff.y + Math.sin(t * ff.speed * 1.3 + ff.phase) * (ff.radius * 0.6));
      const glow = (Math.sin(t * 4 + ff.phase) + 1) * 0.5;
      if (glow > 0.4 && fx >= 1 && fx < W - 1 && fy >= 1 && fy < H - 1) {
        FRAME[fy * W + fx] = C.firefly;
        if (glow > 0.75) {
          FRAME[(fy - 1) * W + fx] = mixPacked(FRAME[(fy - 1) * W + fx], C.firefly, 0.4);
          FRAME[(fy + 1) * W + fx] = mixPacked(FRAME[(fy + 1) * W + fx], C.firefly, 0.4);
          FRAME[fy * W + fx - 1] = mixPacked(FRAME[fy * W + fx - 1], C.firefly, 0.4);
          FRAME[fy * W + fx + 1] = mixPacked(FRAME[fy * W + fx + 1], C.firefly, 0.4);
        }
      }
    }

    // 10. Campfire Warm Radial Lighting on the surroundings
    const fireRadius = 65;
    for (let dy = -fireRadius; dy <= fireRadius; dy++) {
      for (let dx = -fireRadius; dx <= fireRadius; dx++) {
        const d = Math.hypot(dx, dy);
        if (d <= fireRadius) {
          const px = fireX + dx, py = fireY + dy;
          if (px >= 0 && px < W && py >= 0 && py < H) {
            const flicker = (Math.sin(t * 14) + Math.cos(t * 22)) * 0.06;
            const factor = Math.max(0, (1 - d / fireRadius) + flicker) * 0.6;
            if (factor > 0.05) {
              FRAME[py * W + px] = mixPacked(FRAME[py * W + px], C.fireOrange, factor * 0.6);
            }
          }
        }
      }
    }

    return FRAME;
  }

  return {
    name: 'Enchanted Forest & Campfire',
    description: 'A serene ancient twilight woodland with a roaring crackling campfire, dancing embers, resting deer, and wandering luminous fireflies.',
    W, H,
    render
  };
}
