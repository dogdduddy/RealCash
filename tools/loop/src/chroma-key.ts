// 사용(저장소 루트에서): tools/loop/node_modules/.bin/tsx tools/loop/src/chroma-key.ts <입력.png> <출력.png> [--key magenta]
import sharp from 'sharp';
import { DEFAULT_CHROMA, chromaKey, spillCheck, type ChromaKeyColor } from './chroma.js';

/** despill된 픽셀이 보이는 픽셀의 이 비율을 넘으면 경고 */
const DESPILL_WARN = 0.03;

const args = process.argv.slice(2);
const ki = args.indexOf('--key');
const key: ChromaKeyColor = ki >= 0 && args.splice(ki, 2)[1] === 'magenta' ? 'magenta' : 'green';
const [input, output] = args;
if (!input || !output) {
  console.error('사용: chroma-key.ts <입력> <출력> [--key magenta]');
  process.exit(2);
}

const { data, info } = await sharp(input).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
const buf = new Uint8Array(data.buffer, data.byteOffset, data.length);
const result = chromaKey(buf, { ...DEFAULT_CHROMA, key });
const spill = spillCheck(buf, key);
await sharp(buf, { raw: { width: info.width, height: info.height, channels: 4 } })
  .png()
  .toFile(output);

const ratio = result.visible ? result.despilled / result.visible : 0;
console.log(
  `${output}: ${info.width}x${info.height}, 보이는 픽셀 ${result.visible}, despill ${(ratio * 100).toFixed(2)}%, 남은 배경색 가장자리 ${(spill.edgeGreenRatio * 100).toFixed(2)}%`,
);
if (result.visible === 0) console.warn('경고: 피사체가 모두 투명해졌다');
if (ratio > DESPILL_WARN || spill.edgeGreenRatio > 0.01 || spill.bodyGreenRatio > 0.001) {
  console.warn('경고: 배경색 번짐이 많다. 체크무늬·어두운 배경 시트로 확인할 것');
}
