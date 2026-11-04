// 사용(저장소 루트에서): tools/loop/node_modules/.bin/tsx tools/loop/src/fit.ts <투명 입력.png> <출력.png> <w>[x<h>] [--margin 0.1]
import sharp from 'sharp';
import { alphaBox, fitLayout, parseSize } from './fit-lib.js';

const args = process.argv.slice(2);
const mi = args.indexOf('--margin');
const margin = mi >= 0 ? Number(args.splice(mi, 2)[1]) : 0.1;
const [input, output, sizeArg] = args;
if (!input || !output || !sizeArg) {
  console.error('사용: fit.ts <입력> <출력> <w>[x<h>] [--margin 0.1]');
  process.exit(2);
}
const size = parseSize(sizeArg);

const { data, info } = await sharp(input).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
const box = alphaBox(
  new Uint8Array(data.buffer, data.byteOffset, data.length),
  info.width,
  info.height,
);
if (!box) {
  console.error('입력이 전부 투명하다');
  process.exit(1);
}
const lay = fitLayout(box.w, box.h, size.w, size.h, margin);
const subject = await sharp(input)
  .ensureAlpha()
  .extract({ left: box.x, top: box.y, width: box.w, height: box.h })
  .resize(lay.w, lay.h, { fit: 'fill', kernel: 'lanczos3' })
  .png()
  .toBuffer();
await sharp({
  create: {
    width: size.w,
    height: size.h,
    channels: 4,
    background: { r: 0, g: 0, b: 0, alpha: 0 },
  },
})
  .composite([{ input: subject, left: lay.left, top: lay.top }])
  .png()
  .toFile(output);
console.log(
  `${output}: ${size.w}x${size.h}, 피사체 ${box.w}x${box.h} → ${lay.w}x${lay.h} @(${lay.left},${lay.top}), 여백 ${margin}`,
);
