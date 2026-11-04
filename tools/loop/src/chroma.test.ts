import { describe, expect, it } from 'vitest';
import { DEFAULT_CHROMA, chromaKey, greenness, magentaness, spillCheck } from './chroma.js';

const px = (...rgba: number[][]) => new Uint8Array(rgba.flat());

describe('chromaKey', () => {
  it('순수 녹색은 투명, 주황·크림색은 불투명', () => {
    const buf = px([0, 255, 0, 255], [255, 170, 60, 255], [250, 235, 210, 255]);
    const r = chromaKey(buf);
    expect(buf[3]).toBe(0);
    expect(buf[7]).toBe(255);
    expect(buf[11]).toBe(255);
    expect(r.visible).toBe(2);
    expect(r.despilled).toBe(0);
  });

  it('경계 픽셀은 반투명이 되고 녹색이 빠진다', () => {
    const buf = px([128, 200, 40, 255]);
    chromaKey(buf);
    expect(buf[3]).toBeGreaterThan(0);
    expect(buf[3]).toBeLessThan(255);
    expect(buf[1]).toBe(128);
  });

  it('처리 결과에는 녹색 우세 픽셀이 남지 않는다', () => {
    const buf = px([0, 255, 0, 255], [100, 180, 90, 255], [255, 170, 60, 255]);
    chromaKey(buf);
    const s = spillCheck(buf);
    expect(s.edgeGreenRatio).toBe(0);
    expect(s.bodyGreenRatio).toBe(0);
  });
});

describe('greenness', () => {
  it('녹색이 아니면 0', () => {
    expect(greenness(255, 100, 100)).toBe(0);
    expect(greenness(0, 255, 0)).toBe(1);
  });
});

describe('chromaKey 마젠타', () => {
  const opts = { ...DEFAULT_CHROMA, key: 'magenta' as const };

  it('순수 마젠타는 투명, 잎 초록·흙색·나무색은 불투명', () => {
    const buf = px(
      [255, 0, 255, 255],
      [140, 200, 90, 255],
      [240, 200, 150, 255],
      [200, 146, 92, 255],
    );
    const r = chromaKey(buf, opts);
    expect(buf[3]).toBe(0);
    expect([buf[7], buf[11], buf[15]]).toEqual([255, 255, 255]);
    expect(r.visible).toBe(3);
    expect(magentaness(140, 200, 90)).toBe(0);
  });

  it('경계 픽셀은 반투명이 되고 마젠타 몫이 빠진다', () => {
    const buf = px([230, 120, 200, 255]);
    chromaKey(buf, opts);
    expect(buf[3]).toBeGreaterThan(0);
    expect(buf[3]).toBeLessThan(255);
    expect([buf[0], buf[1], buf[2]]).toEqual([150, 120, 120]);
    const s = spillCheck(buf, 'magenta');
    expect(s.edgeGreenRatio).toBe(0);
  });
});
