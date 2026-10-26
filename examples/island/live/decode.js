// Reading the art in a browser: the browser's own PNG decoder, through an image and a canvas, so the page carries no
// decoder of its own. (In Node, png.mjs reads the file instead.) The picture comes back as the almanac's packed pixels,
// 0xAABBGGRR, which is how an ImageData's bytes read as 32-bit words on a little-endian machine.

/**
 * A PNG's pixels, decoded by the browser.
 * @param {ArrayBuffer} bytes the file
 * @returns {Promise<{W: number, H: number, px: Uint32Array}>} the picture, packed
 * @see imagedata-mdn
 */
export async function decodePngBytes(bytes) {
  const blob = new Blob([bytes], { type: 'image/png' });
  let W, H, draw;
  if (typeof createImageBitmap === 'function') {
    const bitmap = await createImageBitmap(blob);
    W = bitmap.width;
    H = bitmap.height;
    draw = (g) => g.drawImage(bitmap, 0, 0);
  } else {
    const url = URL.createObjectURL(blob),
      image = new Image();
    image.src = url;
    await image.decode();
    W = image.naturalWidth;
    H = image.naturalHeight;
    draw = (g) => g.drawImage(image, 0, 0);
    setTimeout(() => URL.revokeObjectURL(url), 4000);
  }
  const canvas = document.createElement('canvas');
  canvas.width = W;
  canvas.height = H;
  const g = canvas.getContext('2d', { willReadFrequently: true });
  draw(g);
  const data = g.getImageData(0, 0, W, H).data;
  return { W, H, px: new Uint32Array(data.buffer, data.byteOffset, W * H) };
}
