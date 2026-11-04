/** 알파가 이 값 이하인 픽셀은 trim에서 빈 칸으로 본다 */
export const ALPHA_TRIM = 8;

export interface Box {
  x: number;
  y: number;
  w: number;
  h: number;
}

/** RGBA 버퍼에서 보이는 픽셀의 경계 상자. 전부 투명하면 null */
export function alphaBox(buf: Uint8Array, width: number, height: number): Box | null {
  let minX = width,
    minY = height,
    maxX = -1,
    maxY = -1;
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      if (buf[(y * width + x) * 4 + 3]! > ALPHA_TRIM) {
        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
        if (y < minY) minY = y;
        if (y > maxY) maxY = y;
      }
    }
  }
  if (maxX < 0) return null;
  return { x: minX, y: minY, w: maxX - minX + 1, h: maxY - minY + 1 };
}

export interface FitLayout {
  /** 축소한 피사체 크기 */
  w: number;
  h: number;
  /** 목표 캔버스 안에서의 위치(가운데 정렬) */
  left: number;
  top: number;
}

/** 피사체(srcW×srcH)를 목표 캔버스에 사방 margin 비율만큼 여백을 두고 contain으로 넣는다 */
export function fitLayout(
  srcW: number,
  srcH: number,
  dstW: number,
  dstH: number,
  margin = 0.1,
): FitLayout {
  if (margin < 0 || margin >= 0.5) throw new Error(`margin은 0 이상 0.5 미만: ${margin}`);
  const scale = Math.min((dstW * (1 - 2 * margin)) / srcW, (dstH * (1 - 2 * margin)) / srcH);
  const w = Math.max(1, Math.round(srcW * scale));
  const h = Math.max(1, Math.round(srcH * scale));
  return { w, h, left: Math.floor((dstW - w) / 2), top: Math.floor((dstH - h) / 2) };
}

/** "128" 또는 "1024x512" */
export function parseSize(s: string): { w: number; h: number } {
  const m = /^(\d+)(?:x(\d+))?$/.exec(s);
  if (!m) throw new Error(`크기 형식: 128 또는 1024x512 (받은 값 ${s})`);
  const w = Number(m[1]);
  return { w, h: m[2] ? Number(m[2]) : w };
}
