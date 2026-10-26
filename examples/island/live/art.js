// The art the live island paints on, and the few things in it that colour alone cannot find. Everything else (the
// water, the falls, the foam, the grass, the crystals) analyse.js finds for itself; these are the braziers on the two
// shrines' gold stands, whose flames are the same gold as the stands under them.
//
// The picture is examples/island/live/art/island.png: an island in halves, a red shrine at the top and a blue one at the
// bottom, a river and a bridge between them, and lamps along the roads. Its pixels are 841 x 1870, drawn as a phone's
// wallpaper: 9:20.

/** Where the picture of the island is, from this folder. */
export const ART = './art/island.png';

/**
 * The lamps colour alone cannot find: the braziers on the shrines' gold stands, one either side of each shrine's
 * crystal.
 * @type {{x: number, y: number, r: number, kind: string}[]}
 */
export const torches = [
  { x: 318, y: 170, r: 30, kind: 'torch' },
  { x: 522, y: 170, r: 30, kind: 'torch' },
  { x: 319, y: 1597, r: 30, kind: 'torch' },
  { x: 522, y: 1598, r: 30, kind: 'torch' },
];
