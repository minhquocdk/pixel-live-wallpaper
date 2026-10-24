// Scene 1: Floating Island (Đảo Bay Kỳ Ảo)
// An island suspended in the clouds with floating waterfalls, ancient ruins, shrines, floating lanterns, clouds drifting below and above.
import * as almanac from '../src/index.js';

export function createFloatingIslandScene(seedPhrase = 'celestial-haven-7') {
  const {
    clamp, lerp, smoothstep, SeededRandom, latticeHash, latticeRandom, fractalNoise, noiseLine,
    packRgb, hexToRgb, gradeColour, hourLight, hourTables, gradeFrame, twinkle, blink,
    chamferDistance, windowLit, particleField, fall, rise, hover, wrapAround, pulseRise,
    branchingTree, swayingChain, stampCache, fillEllipse, capsuleSteps, lineCells, ditherEllipse,
    shadeEllipse, blitSprite, blitSway, outlinePass, cutSprite, drawnBounds, spriteFromRows,
    leafCrown, barkTrunk, cloudShadows, fogBanks, lightningFlash, roofPlan, slopeShade, roofTexel,
    terraceLevel, wallShade, faceShade, seedOf, scalePacked, mixPacked, hueShiftTone, ditheredBorder
  } = almanac;

  const W = 384, H = 216;
  const SEED = seedOf(seedPhrase);
  const rng = new SeededRandom(SEED);

  const LOOK = { sat: 1.15, warm: 0.08, contrast: 1.05 };
  const hex = (s) => packRgb(...hexToRgb(gradeColour(s, LOOK)));
  const RAMPS = new Map();
  const tone = (c, k) => {
    let r = RAMPS.get(c);
    if (!r) RAMPS.set(c, (r = [-1.7, -0.85, 0, 0.75, 1.5].map((s) => (s ? hueShiftTone(c, s) : c))));
    return r[k + 2];
  };

  const C = {
    skyTop: hex('#181b34'),
    skyMid: hex('#3b4170'),
    skyBottom: hex('#8e728e'),
    cloudLight: hex('#f3e8eb'),
    cloudDark: hex('#a792a5'),
    islandGrass1: hex('#529e58'),
    islandGrass2: hex('#78c067'),
    cliff1: hex('#4a4349'),
    cliff2: hex('#695c63'),
    cliff3: hex('#8b7b80'),
    rockHighlight: hex('#b8a8ad'),
    crystalCyan: hex('#56f0e3'),
    crystalCore: hex('#ffffff'),
    crystalPurple: hex('#b362ff'),
    water1: hex('#48b6d6'),
    water2: hex('#83e6fa'),
    waterFoam: hex('#ffffff'),
    shrineWood: hex('#8f3b2e'),
    shrineGold: hex('#f5cc5b'),
    shrineRoof: hex('#353942'),
    lantern: hex('#ffb84d'),
    star: hex('#fff8e7'),
    mist: hex('#d5cfe3'),
    treeTrunk: hex('#4d3326'),
    treeTrunkLight: hex('#734e3a'),
    leavesPink: ['#80224b', '#b33b68', '#f06e9c', '#ffaec7'].map(hex),
    leavesCyan: ['#1d5c63', '#2b8891', '#42b8bf', '#85f2f2'].map(hex)
  };

  const buffer = (w, h) => ({ w, h, px: new Uint32Array(w * h) });

  // Generate the central floating landmass profile
  // The island floats in the center: y from ~70 to ~165, x from ~70 to ~314
  const islandW = 240, islandH = 110;
  const islandX = (W - islandW) >> 1; // 72
  const islandY = 65;
  const topGrassY = 82;

  // Generate top grass height across columns
  const surfaceY = new Int16Array(W).fill(-1);
  const bottomCliffY = new Int16Array(W).fill(-1);
  const islandMask = new Uint8Array(W * H);

  for (let x = islandX + 10; x < islandX + islandW - 10; x++) {
    const nx = (x - islandX) / islandW; // 0..1
    const distFromCenter = Math.abs(nx - 0.5) * 2; // 0..1
    if (distFromCenter < 0.94) {
      // Grass line
      const bump = fractalNoise(x / 32, 0.5, SEED, 3) * 16 - 8;
      const sy = Math.round(topGrassY + bump);
      surfaceY[x] = sy;

      // Bottom cliff tapers down to rocky stalactite spires
      const cliffDepth = Math.sin(nx * Math.PI) * 75 * (0.8 + fractalNoise(x / 18, 2.5, SEED + 1, 3) * 0.45);
      const by = Math.round(sy + cliffDepth);
      bottomCliffY[x] = Math.min(H - 8, by);

      for (let y = sy; y <= bottomCliffY[x]; y++) {
        if (y >= 0 && y < H) {
          islandMask[y * W + x] = 1;
        }
      }
    }
  }

  // Pre-generate Island Texture Layer
  const islandLayer = buffer(W, H);
  for (let x = 0; x < W; x++) {
    const sy = surfaceY[x];
    const by = bottomCliffY[x];
    if (sy < 0) continue;

    for (let y = sy; y <= by; y++) {
      const depth = y - sy;
      let col;
      if (depth <= 2) {
        // lush grass top with floral speckles
        col = (x + y) % 3 === 0 ? C.islandGrass1 : C.islandGrass2;
        if (fractalNoise(x / 8, y / 8, SEED + 4, 2) > 0.65) col = C.leavesPink[2];
      } else if (depth <= 5) {
        // dirt under grass
        col = depth === 3 ? tone(C.islandGrass1, -2) : C.cliff3;
      } else {
        // jagged rock face with strata and crystal veins
        const rockNoise = fractalNoise(x / 12, y / 16, SEED + 7, 3);
        const crystalNoise = fractalNoise(x / 6, y / 6, SEED + 13, 2);
        if (crystalNoise > 0.82 && depth > 15) {
          col = crystalNoise > 0.88 ? C.crystalCore : C.crystalCyan;
        } else if (rockNoise > 0.62) {
          col = C.cliff3;
        } else if (rockNoise > 0.38) {
          col = C.cliff2;
        } else {
          col = C.cliff1;
        }
        // side shading: left lit, right darker
        if (x > islandX + islandW * 0.6) col = tone(col, -1);
        if (x < islandX + islandW * 0.3) col = tone(col, 1);
      }
      islandLayer.px[y * W + x] = col;
    }
  }

  // Floating Waterfalls pouring off the cliff edges into the clouds!
  const waterfallX = [islandX + 45, islandX + 160];
  const waterfalls = waterfallX.map(wx => {
    const startY = surfaceY[wx] + 2;
    return { x: wx, startY, width: 6 };
  });

  // Shrine / Ancient Temple on the grass
  const shrineSprite = (() => {
    const r = buffer(36, 32);
    const ax = 18, ay = 30;
    // Pillars
    for (let y = 14; y <= 28; y++) {
      for (const px of [6, 12, 22, 28]) {
        r.px[y * r.w + px] = C.shrineWood;
        r.px[y * r.w + px + 1] = tone(C.shrineWood, 1);
      }
    }
    // Floor
    for (let y = 28; y <= 30; y++) {
      for (let x = 4; x <= 30; x++) {
        r.px[y * r.w + x] = C.shrineRoof;
      }
    }
    // Tiered pagodal roof
    for (let tier = 0; tier < 2; tier++) {
      const baseY = tier === 0 ? 14 : 6;
      const wSpan = tier === 0 ? 16 : 13;
      for (let dy = 0; dy < 6; dy++) {
        const span = wSpan - dy * 2;
        const curY = baseY - dy;
        for (let x = ax - span; x <= ax + span; x++) {
          r.px[curY * r.w + x] = dy === 0 ? C.shrineGold : C.shrineRoof;
        }
        // Upturned eaves
        r.px[(curY - 1) * r.w + (ax - span - 1)] = C.shrineGold;
        r.px[(curY - 1) * r.w + (ax + span + 1)] = C.shrineGold;
      }
    }
    // Crystal orb glowing inside shrine
    for (let dy = -2; dy <= 2; dy++) {
      for (let dx = -2; dx <= 2; dx++) {
        if (dx * dx + dy * dy <= 5) {
          r.px[(21 + dy) * r.w + (17 + dx)] = (dx === 0 && dy === 0) ? C.crystalCore : C.crystalCyan;
        }
      }
    }
    outlinePass(r, hex('#15161e'));
    return { ...r, ax, ay };
  })();

  // Giant Cherry / Celestial Blossom Tree on the hill
  const blossomTree = (() => {
    const r = buffer(70, 70);
    const ax = 35, ay = 66;
    const treeSeed = SEED + 99;
    const rnd = new SeededRandom(treeSeed);
    const tree = branchingTree(ax, ay, { levels: 4, forks: 2, spread: 0.65, decay: 0.72, length: 13, width: 4.5 }, 1, rnd);
    for (const s of tree.segments) {
      capsuleSteps(s.x0, s.y0, s.width / 2, s.x1, s.y1, (s.width * 0.72) / 2, (x, y, rr) =>
        fillEllipse(r, x, y, rr, rr, s.level > 2 ? C.treeTrunk : C.treeTrunkLight)
      );
    }
    tree.tips.forEach(([x, y], k) => {
      leafCrown(r, rnd, x, y, 5.5, 4.2, C.leavesPink, { clumps: 4, bloom: hex('#ffffff'), bloomEvery: 5, seed: k });
    });
    outlinePass(r, hex('#1a1622'));
    return { ...r, ax, ay, tips: tree.tips.map(([tx, ty]) => [tx - ax, ty - ay]) };
  })();

  // Floating mini satellite islands
  const miniIslands = [
    { x: 38, y: 110, w: 32, h: 26, bob: 0, amp: 3, speed: 0.8 },
    { x: 334, y: 88, w: 38, h: 30, bob: 2.1, amp: 4, speed: 0.65 },
    { x: 310, y: 155, w: 26, h: 22, bob: 4.2, amp: 2.5, speed: 1.1 }
  ].map((isle, idx) => {
    const r = buffer(isle.w, isle.h);
    const ax = isle.w >> 1, ay = 6;
    // draw mini floating rock
    for (let x = 2; x < isle.w - 2; x++) {
      const nx = (x - 2) / (isle.w - 4);
      const cliff = Math.sin(nx * Math.PI) * (isle.h - 8);
      for (let y = 4; y < 4 + cliff; y++) {
        if (y <= 6) {
          r.px[y * isle.w + x] = (x + y) % 2 ? C.islandGrass1 : C.islandGrass2;
        } else {
          r.px[y * isle.w + x] = ((x + y * 2) % 5 === 0) ? C.crystalCyan : C.cliff2;
        }
      }
    }
    // small tree or crystal cluster on top
    if (idx === 0) {
      fillEllipse(r, ax, 3, 4, 3, C.leavesCyan[2]);
    } else {
      for (let dy = -4; dy <= 0; dy++) r.px[(4 + dy) * isle.w + ax] = C.crystalPurple;
    }
    outlinePass(r, hex('#15161e'));
    return { ...isle, sp: { ...r, ax, ay } };
  });

  // Animated Particles: Floating lanterns, glowing spores/petals, stars
  const spores = Array.from({ length: 45 }, (_, k) => ({
    x: rng.range(islandX, islandX + islandW),
    y: rng.range(topGrassY - 30, topGrassY + 30),
    vx: rng.range(-0.3, 0.4),
    vy: rng.range(-0.6, -0.1),
    seed: k,
    size: rng.range(0.8, 1.8),
    col: k % 3 === 0 ? C.crystalCyan : C.leavesPink[3]
  }));

  const lanterns = Array.from({ length: 14 }, (_, k) => ({
    x: islandX + 30 + k * 14 + rng.range(-4, 4),
    y: topGrassY + rng.range(-20, 20),
    baseY: topGrassY + rng.range(-25, 10),
    seed: k,
    speed: rng.range(0.8, 1.4),
    phase: rng.range(0, Math.PI * 2)
  }));

  const stars = Array.from({ length: 85 }, (_, k) => ({
    x: rng.int(0, W - 1),
    y: rng.int(0, 95),
    phase: rng.range(0, 6.28),
    speed: rng.range(1.5, 4.0),
    bright: rng.range(0.5, 1.0)
  }));

  // Animated Torii Gate on the left ridge
  const toriiSprite = (() => {
    const r = buffer(22, 22);
    const ax = 11, ay = 20;
    // Pillars
    for (let y = 4; y <= 20; y++) {
      r.px[y * 22 + 5] = C.shrineWood;
      r.px[y * 22 + 6] = tone(C.shrineWood, 1);
      r.px[y * 22 + 15] = C.shrineWood;
      r.px[y * 22 + 16] = tone(C.shrineWood, 1);
    }
    // Crossbars
    for (let x = 2; x <= 19; x++) {
      r.px[3 * 22 + x] = tone(C.shrineWood, 1);
      r.px[4 * 22 + x] = C.shrineWood;
    }
    for (let x = 4; x <= 17; x++) {
      r.px[7 * 22 + x] = C.shrineWood;
    }
    outlinePass(r, hex('#15161e'));
    return { ...r, ax, ay };
  })();

  const FRAME = new Uint32Array(W * H);
  let cloudScratch, fogScratch;

  function render(timeSec, timeOfDayVal = 0.82) {
    const t = timeSec;
    const fr = Math.round(t * 30);

    // 1. Sky Gradient (Day/Sunset/Night cycle dependent)
    // timeOfDayVal: 0 = dawn, 0.25 = noon, 0.5 = sunset, 0.75 = midnight
    const hl = hourLight(timeOfDayVal, 1, 0);

    for (let y = 0; y < H; y++) {
      const ny = y / H;
      // Sky interpolation
      let r0, g0, b0;
      if (ny < 0.5) {
        const u = ny / 0.5;
        r0 = lerp(20, 60, u);
        g0 = lerp(22, 65, u);
        b0 = lerp(55, 120, u);
      } else {
        const u = (ny - 0.5) / 0.5;
        r0 = lerp(60, 160, u);
        g0 = lerp(65, 110, u);
        b0 = lerp(120, 150, u);
      }
      const lineCol = packRgb(r0, g0, b0);
      for (let x = 0; x < W; x++) {
        FRAME[y * W + x] = lineCol;
      }
    }

    // 2. Stars (twinkling in upper sky)
    const starAlpha = clamp((hl.night - 0.2) * 1.5);
    if (starAlpha > 0.05) {
      for (const s of stars) {
        const tw = (Math.sin(t * s.speed + s.phase) + 1) * 0.5 * s.bright * starAlpha;
        if (tw > 0.4) {
          const idx = s.y * W + s.x;
          FRAME[idx] = mixPacked(FRAME[idx], C.star, tw);
        }
      }
    }

    // 3. Giant Celestial Moon / Sun
    const moonX = 64, moonY = 46;
    const moonR = 24;
    for (let dy = -moonR; dy <= moonR; dy++) {
      for (let dx = -moonR; dx <= moonR; dx++) {
        const d2 = dx * dx + dy * dy;
        if (d2 <= moonR * moonR) {
          const px = moonX + dx, py = moonY + dy;
          if (px >= 0 && px < W && py >= 0 && py < H) {
            const glow = (1 - Math.sqrt(d2) / moonR);
            const mCol = packRgb(255, 250, 230);
            FRAME[py * W + px] = mixPacked(FRAME[py * W + px], mCol, 0.75 + glow * 0.25);
          }
        }
      }
    }

    // 4. Distant Cloud Sea (below the islands, y > 140)
    for (let y = 135; y < H; y++) {
      for (let x = 0; x < W; x++) {
        const cNoise = fractalNoise(x / 48 + t * 0.04, y / 24, SEED + 22, 3);
        const ny = (y - 135) / (H - 135);
        if (cNoise + ny * 0.5 > 0.62) {
          const col = cNoise > 0.78 ? C.cloudLight : C.cloudDark;
          FRAME[y * W + x] = mixPacked(FRAME[y * W + x], col, Math.min(1, (ny + 0.3) * 0.9));
        }
      }
    }

    // 5. Mini satellite islands (floating & bobbing)
    for (const isle of miniIslands) {
      const curY = isle.y + Math.round(Math.sin(t * isle.speed + isle.bob) * isle.amp);
      blitSprite({ w: W, h: H, px: FRAME }, isle.sp, isle.x, curY);
    }

    // 6. Central Island Base
    const islandBobY = Math.round(Math.sin(t * 0.7) * 2);
    for (let y = 0; y < H; y++) {
      const srcY = y - islandBobY;
      if (srcY < 0 || srcY >= H) continue;
      for (let x = 0; x < W; x++) {
        const pix = islandLayer.px[srcY * W + x];
        if (pix !== 0) {
          FRAME[y * W + x] = pix;
        }
      }
    }

    // 7. Waterfalls flowing down into the void
    for (const wf of waterfalls) {
      const startY = wf.startY + islandBobY;
      for (let y = startY; y < H - 8; y++) {
        const fallDist = y - startY;
        const wave = Math.sin(y * 0.5 - t * 14) * 1.5;
        for (let dx = -1; dx <= wf.width; dx++) {
          const px = Math.round(wf.x + dx + wave);
          if (px >= 0 && px < W && y >= 0 && y < H) {
            const isFoam = ((y + Math.floor(t * 18)) % 4 === 0) || dx === -1 || dx === wf.width;
            const wCol = isFoam ? C.waterFoam : (dx % 2 === 0 ? C.water2 : C.water1);
            const fade = clamp(1 - fallDist / 85);
            FRAME[y * W + px] = mixPacked(FRAME[y * W + px], wCol, 0.85 * fade);
          }
        }
      }
    }

    // 8. Shrine & Torii Gate & Ancient Tree
    const shrineBaseY = surfaceY[islandX + 130] + islandBobY;
    blitSprite({ w: W, h: H, px: FRAME }, shrineSprite, islandX + 130, shrineBaseY);

    const toriiBaseY = surfaceY[islandX + 50] + islandBobY;
    blitSprite({ w: W, h: H, px: FRAME }, toriiSprite, islandX + 50, toriiBaseY);

    const treeBaseY = surfaceY[islandX + 185] + islandBobY;
    // Tree sways gently in wind
    blitSway({ w: W, h: H, px: FRAME }, blossomTree, islandX + 185, treeBaseY, (hh) => {
      return hh > 10 ? Math.sin(t * 1.8) * (hh - 10) * 0.04 : 0;
    });

    // 9. Floating Lanterns (drifting and pulsing)
    for (const l of lanterns) {
      const ly = Math.round(l.baseY + islandBobY + Math.sin(t * l.speed + l.phase) * 6);
      const lx = Math.round(l.x + Math.cos(t * 0.5 + l.phase) * 4);
      if (lx >= 2 && lx < W - 2 && ly >= 2 && ly < H - 2) {
        // Draw lantern 3x3 pixel glow
        FRAME[ly * W + lx] = C.lantern;
        FRAME[(ly - 1) * W + lx] = hex('#ffe599');
        FRAME[(ly + 1) * W + lx] = hex('#d97724');
        FRAME[ly * W + lx - 1] = hex('#ffe599');
        FRAME[ly * W + lx + 1] = hex('#ffe599');
      }
    }

    // 10. Spores / Petals falling and floating
    for (const sp of spores) {
      const sx = Math.round((sp.x + t * sp.vx * 20) % W);
      const sy = Math.round((sp.y + Math.sin(t * 2 + sp.seed) * 8 - t * sp.vy * 12) % H);
      const px = sx < 0 ? sx + W : sx;
      const py = sy < 0 ? sy + H : sy;
      if (px >= 0 && px < W && py >= 0 && py < H) {
        FRAME[py * W + px] = sp.col;
      }
    }

    // 11. Fog / Mist drifting across bottom
    fogScratch = fogBanks(FRAME, W, H, {
      x: t * 14,
      y: t * 2,
      density: 0.45,
      colour: C.mist,
      seed: SEED + 44,
      scratch: fogScratch
    });

    // 12. Grade Frame according to Day/Night
    if (!hl.plain) {
      const tables = hourTables(hl.night, hl.dusk, hl.morning, 1, 0);
      gradeFrame(FRAME, W, H, tables, { night: hl.night, lights: [
        { x: islandX + 130, y: shrineBaseY - 10, r: 24, k: 0.9 }, // Shrine glow
        { x: islandX + 185, y: treeBaseY - 30, r: 32, k: 0.8 }, // Tree glow
        { x: 64, y: 46, r: 40, k: 0.7 } // Moon glow
      ] });
    }

    return FRAME;
  }

  return {
    name: 'Floating Celestial Island',
    description: 'An ancient enchanted sanctuary hovering above an endless sea of clouds, with waterfalls tumbling into the ether, glowing celestial flora, and gentle shrines.',
    W, H,
    render
  };
}
