// 크로마키(#00FF00, 녹색이 들어간 피사체는 #FF00FF) 제거 핵심 로직. sharp 없이 RGBA 버퍼만 다룬다.

export type ChromaKeyColor = 'green' | 'magenta';

export interface ChromaOptions {
  /** 녹색 우세도가 이 값 이하면 완전 불투명 */
  opaqueAt: number;
  /** 녹색 우세도가 이 값 이상이면 완전 투명. 사이는 선형 */
  clearAt: number;
  /** 뺄 배경색. 기본 green */
  key?: ChromaKeyColor;
}

export const DEFAULT_CHROMA: ChromaOptions = { opaqueAt: 0.12, clearAt: 0.4 };

/** 녹색 우세도 0..1. (g - max(r, b)) / 255, 음수는 0 */
export function greenness(r: number, g: number, b: number): number {
  return Math.max(0, (g - Math.max(r, b)) / 255);
}

/** 마젠타 우세도 0..1. (min(r, b) - g) / 255, 음수는 0 */
export function magentaness(r: number, g: number, b: number): number {
  return Math.max(0, (Math.min(r, b) - g) / 255);
}

export interface ChromaResult {
  /** 처리 후 알파 > 0 픽셀 수 */
  visible: number;
  /** 보이는 픽셀 중 녹색 기운이 있어 despill된 픽셀 수 (녹색 테두리 후보) */
  despilled: number;
}

/** RGBA 버퍼를 제자리에서 처리한다. */
export function chromaKey(data: Uint8Array, opts: ChromaOptions = DEFAULT_CHROMA): ChromaResult {
  let visible = 0;
  let despilled = 0;
  const span = opts.clearAt - opts.opaqueAt;
  for (let i = 0; i < data.length; i += 4) {
    const r = data[i]!;
    const g = data[i + 1]!;
    const b = data[i + 2]!;
    const magenta = opts.key === 'magenta';
    const d = magenta ? magentaness(r, g, b) : greenness(r, g, b);
    const alpha = d <= opts.opaqueAt ? 1 : d >= opts.clearAt ? 0 : 1 - (d - opts.opaqueAt) / span;
    if (magenta) {
      // despill: r, b에 함께 남은 마젠타 몫(min(r, b) - g)을 뺀다
      const extra = Math.min(r, b) - g;
      if (extra > 0) {
        data[i] = r - extra;
        data[i + 2] = b - extra;
      }
    } else {
      // despill: 녹색을 r, b 중 큰 값까지 끌어내린다
      const cap = Math.max(r, b);
      if (g > cap) data[i + 1] = cap;
    }
    const a = Math.round(alpha * data[i + 3]!);
    data[i + 3] = a;
    if (a > 0) {
      visible++;
      if (d > 0.06) despilled++;
    }
  }
  return { visible, despilled };
}

export interface SpillReport {
  /** 반투명 이상 픽셀 수 */
  visible: number;
  /** 가장자리(반투명) 픽셀 중 녹색 우세 비율 */
  edgeGreenRatio: number;
  /** 불투명 픽셀 중 녹색 우세 비율 */
  bodyGreenRatio: number;
}

/** 처리 결과에 배경색이 남았는지 점검한다. 녹색 우세 = g > max(r,b) + 16, 마젠타 우세 = min(r,b) > g + 16 */
export function spillCheck(data: Uint8Array, key: ChromaKeyColor = 'green'): SpillReport {
  let visible = 0;
  let edge = 0;
  let edgeGreen = 0;
  let body = 0;
  let bodyGreen = 0;
  for (let i = 0; i < data.length; i += 4) {
    const a = data[i + 3]!;
    if (a === 0) continue;
    visible++;
    const green =
      key === 'magenta'
        ? Math.min(data[i]!, data[i + 2]!) > data[i + 1]! + 16
        : data[i + 1]! > Math.max(data[i]!, data[i + 2]!) + 16;
    if (a < 250) {
      edge++;
      if (green) edgeGreen++;
    } else {
      body++;
      if (green) bodyGreen++;
    }
  }
  return {
    visible,
    edgeGreenRatio: edge ? edgeGreen / edge : 0,
    bodyGreenRatio: body ? bodyGreen / body : 0,
  };
}
