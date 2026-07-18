/**
 * Geometry helpers for Telegram-style circular avatar cropping.
 *
 * At userScale = 1 the shorter side exactly fills the crop circle diameter;
 * the user can then pinch/zoom and pan. Export is a square PNG of the circle contents.
 */

export const OUTPUT_SIZE = 512;
const MIN_USER_SCALE = 1;
const MAX_USER_SCALE = 4;

export function baseScale(
  bitmapWidth: number,
  bitmapHeight: number,
  cropDiameter: number
): number {
  const shorter = Math.max(1, Math.min(bitmapWidth, bitmapHeight));
  return cropDiameter / shorter;
}

export function clampUserScale(scale: number): number {
  return Math.min(MAX_USER_SCALE, Math.max(MIN_USER_SCALE, scale));
}

/** Keeps the crop circle fully covered by the transformed image. */
export function clampOffset(
  offsetX: number,
  offsetY: number,
  bitmapWidth: number,
  bitmapHeight: number,
  cropDiameter: number,
  userScale: number
): { x: number; y: number } {
  const displayScale =
    baseScale(bitmapWidth, bitmapHeight, cropDiameter) * clampUserScale(userScale);
  const displayedW = bitmapWidth * displayScale;
  const displayedH = bitmapHeight * displayScale;
  const maxX = Math.max(0, (displayedW - cropDiameter) / 2);
  const maxY = Math.max(0, (displayedH - cropDiameter) / 2);
  return {
    x: Math.min(maxX, Math.max(-maxX, offsetX)),
    y: Math.min(maxY, Math.max(-maxY, offsetY)),
  };
}

export type CropImageSource = HTMLImageElement | HTMLCanvasElement | ImageBitmap;

export function imagePixelSize(source: CropImageSource): { width: number; height: number } {
  if (source instanceof HTMLImageElement) {
    return { width: source.naturalWidth, height: source.naturalHeight };
  }
  return { width: source.width, height: source.height };
}

/** Renders the square region inside the crop circle to an offscreen canvas. */
export function renderSquareCrop(
  source: CropImageSource,
  cropDiameter: number,
  userScale: number,
  offsetX: number,
  offsetY: number,
  outputSize: number = OUTPUT_SIZE
): HTMLCanvasElement {
  const { width, height } = imagePixelSize(source);
  const scale = clampUserScale(userScale);
  const { x: ox, y: oy } = clampOffset(offsetX, offsetY, width, height, cropDiameter, scale);
  const displayScale = baseScale(width, height, cropDiameter) * scale;
  const srcSize = cropDiameter / displayScale;
  const srcLeft = width / 2 - srcSize / 2 - ox / displayScale;
  const srcTop = height / 2 - srcSize / 2 - oy / displayScale;

  const canvas = document.createElement("canvas");
  canvas.width = outputSize;
  canvas.height = outputSize;
  const ctx = canvas.getContext("2d");
  if (!ctx) {
    throw new Error("Could not create 2d canvas context");
  }
  ctx.imageSmoothingEnabled = true;
  ctx.imageSmoothingQuality = "high";
  ctx.drawImage(source, srcLeft, srcTop, srcSize, srcSize, 0, 0, outputSize, outputSize);
  return canvas;
}

export function canvasToPngBlob(canvas: HTMLCanvasElement): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => {
        if (blob) resolve(blob);
        else reject(new Error("Failed to encode cropped avatar"));
      },
      "image/png",
      1
    );
  });
}

const MAX_DECODE_SIDE = 2048;

/** Load a File into an HTMLImageElement, downscaling if the longest side exceeds 2048. */
export async function loadImageForCrop(file: File): Promise<HTMLImageElement> {
  const objectUrl = URL.createObjectURL(file);
  try {
    const img = await decodeImage(objectUrl);
    const longest = Math.max(img.naturalWidth, img.naturalHeight);
    if (longest <= MAX_DECODE_SIDE) {
      return img;
    }
    const scale = MAX_DECODE_SIDE / longest;
    const w = Math.max(1, Math.round(img.naturalWidth * scale));
    const h = Math.max(1, Math.round(img.naturalHeight * scale));
    const canvas = document.createElement("canvas");
    canvas.width = w;
    canvas.height = h;
    const ctx = canvas.getContext("2d");
    if (!ctx) throw new Error("Could not create 2d canvas context");
    ctx.drawImage(img, 0, 0, w, h);
    const scaledUrl = canvas.toDataURL("image/png");
    return decodeImage(scaledUrl);
  } finally {
    URL.revokeObjectURL(objectUrl);
  }
}

function decodeImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error("Failed to load image"));
    img.src = src;
  });
}
