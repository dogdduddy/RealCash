import { describe, expect, it } from 'vitest';
import { diffMask, labelSvg } from './sheet.js';

describe('diffMask', () => {
  it('다른 픽셀만 빨갛게 표시하고 비율을 센다', () => {
    const a = new Uint8Array([0, 0, 0, 255, 10, 10, 10, 255]);
    const b = new Uint8Array([0, 0, 0, 255, 200, 10, 10, 255]);
    const m = diffMask(a, b);
    expect(m.ratio).toBe(0.5);
    expect(Array.from(m.data.slice(0, 4))).toEqual([0, 0, 0, 0]);
    expect(m.data[4]).toBe(255);
    expect(m.data[7]).toBeGreaterThan(0);
  });
});

describe('labelSvg', () => {
  it('특수 문자를 이스케이프한다', () => {
    expect(labelSvg('<A&B>', 100, 40).toString()).toContain('&lt;A&amp;B>');
  });
});
