// 검수 시트 보조 로직 (sharp 없이 테스트 가능한 부분)

/** 라벨 칸 SVG. 큰 글자 하나를 가운데에 */
export function labelSvg(text: string, width: number, height: number): Buffer {
  const esc = text.replace(/&/g, '&amp;').replace(/</g, '&lt;');
  return Buffer.from(
    `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}">` +
      `<text x="50%" y="50%" dominant-baseline="central" text-anchor="middle" ` +
      `font-family="Helvetica, Arial, sans-serif" font-size="${Math.round(height * 0.7)}" font-weight="bold" fill="#333">${esc}</text></svg>`,
  );
}

/** 두 RGBA 버퍼의 차이 영역을 반투명 빨강으로 칠한 레이어와 차이 비율 */
export function diffMask(
  a: Uint8Array,
  b: Uint8Array,
  threshold = 48,
): { data: Uint8Array; ratio: number } {
  const out = new Uint8Array(a.length);
  let diff = 0;
  const pixels = a.length / 4;
  for (let i = 0; i < a.length; i += 4) {
    const d =
      Math.abs(a[i]! - b[i]!) +
      Math.abs(a[i + 1]! - b[i + 1]!) +
      Math.abs(a[i + 2]! - b[i + 2]!) +
      Math.abs(a[i + 3]! - b[i + 3]!);
    if (d > threshold) {
      diff++;
      out[i] = 255;
      out[i + 3] = 160;
    }
  }
  return { data: out, ratio: pixels ? diff / pixels : 0 };
}
