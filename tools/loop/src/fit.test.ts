import { describe, expect, it } from 'vitest';
import { alphaBox, fitLayout, parseSize } from './fit-lib.js';

describe('alphaBox', () => {
  it('보이는 픽셀만 감싼다', () => {
    const w = 4,
      h = 3;
    const buf = new Uint8Array(w * h * 4);
    buf[(1 * w + 1) * 4 + 3] = 255;
    buf[(2 * w + 2) * 4 + 3] = 200;
    buf[(0 * w + 3) * 4 + 3] = 5; // 거의 투명: 무시
    expect(alphaBox(buf, w, h)).toEqual({ x: 1, y: 1, w: 2, h: 2 });
  });
  it('전부 투명하면 null', () => {
    expect(alphaBox(new Uint8Array(16), 2, 2)).toBeNull();
  });
});

describe('fitLayout', () => {
  it('정사각 목표에 여백 10%로 contain', () => {
    expect(fitLayout(800, 600, 128, 128)).toEqual({ w: 102, h: 77, left: 13, top: 25 });
  });
  it('세로로 긴 피사체는 높이에 맞춘다', () => {
    const l = fitLayout(100, 400, 200, 200, 0);
    expect(l).toEqual({ w: 50, h: 200, left: 75, top: 0 });
  });
  it('여백 범위를 벗어나면 오류', () => {
    expect(() => fitLayout(1, 1, 1, 1, 0.5)).toThrow();
  });
});

describe('parseSize', () => {
  it('한 숫자는 정사각', () => {
    expect(parseSize('128')).toEqual({ w: 128, h: 128 });
    expect(parseSize('1024x512')).toEqual({ w: 1024, h: 512 });
    expect(() => parseSize('abc')).toThrow();
  });
});
