// 검수 시트. 출력은 tools/loop/out/review/ (gitignore, 또는 --out 디렉터리)
// 사용(저장소 루트에서): tools/loop/node_modules/.bin/tsx tools/loop/src/review-sheet.ts --bg dark a.png
//   --compare a.png b.png ...     나란히 놓고 A, B, … 라벨
//   --overlay base.png variant.png  50% 겹침 + 차이 영역 빨간 표시
//   --bg checker|dark a.png ...   투명 PNG를 체크무늬/어두운 배경 위에
//   --small 40,64 a.png ...       축소 미리보기
//   --name <이름>                  출력 파일 이름 앞부분 (기본: review)
import { mkdirSync } from 'node:fs';
import { basename, dirname, extname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp, { type OverlayOptions, type Sharp } from 'sharp';
import { diffMask, labelSvg } from './sheet.js';

const DEFAULT_OUT = join(dirname(fileURLToPath(import.meta.url)), '..', 'out', 'review');
const CELL = 512;
const LABEL_H = 72;
const GAP = 16;

interface Args {
  mode: 'compare' | 'overlay' | 'bg' | 'small';
  files: string[];
  bg: 'checker' | 'dark';
  sizes: number[];
  out: string;
  name: string;
}

function parseArgs(argv: string[]): Args {
  const a: Args = {
    mode: 'compare',
    files: [],
    bg: 'checker',
    sizes: [40, 64],
    out: DEFAULT_OUT,
    name: 'review',
  };
  for (let i = 0; i < argv.length; i++) {
    const v = argv[i]!;
    if (v === '--compare') a.mode = 'compare';
    else if (v === '--overlay') a.mode = 'overlay';
    else if (v === '--bg') {
      a.mode = 'bg';
      a.bg = argv[++i] === 'dark' ? 'dark' : 'checker';
    } else if (v === '--small') {
      a.mode = 'small';
      a.sizes = (argv[++i] ?? '40,64').split(',').map(Number);
    } else if (v === '--out') a.out = argv[++i]!;
    else if (v === '--name') a.name = argv[++i]!;
    else a.files.push(v);
  }
  return a;
}

function checkerTile(size: number): Promise<Buffer> {
  const s = 16;
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}">
<defs><pattern id="p" width="${s * 2}" height="${s * 2}" patternUnits="userSpaceOnUse">
<rect width="${s * 2}" height="${s * 2}" fill="#ffffff"/><rect width="${s}" height="${s}" fill="#cccccc"/>
<rect x="${s}" y="${s}" width="${s}" height="${s}" fill="#cccccc"/></pattern></defs>
<rect width="100%" height="100%" fill="url(#p)"/></svg>`;
  return sharp(Buffer.from(svg)).png().toBuffer();
}

async function background(size: number, kind: 'checker' | 'dark' | 'light'): Promise<Sharp> {
  if (kind === 'checker') return sharp(await checkerTile(size));
  const color = kind === 'dark' ? '#1e1e24' : '#f4efe6';
  return sharp({ create: { width: size, height: size, channels: 4, background: color } });
}

async function cell(file: string, kind: 'checker' | 'dark' | 'light'): Promise<Buffer> {
  const img = await sharp(file)
    .resize(CELL, CELL, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } })
    .png()
    .toBuffer();
  return (await background(CELL, kind))
    .composite([{ input: img }])
    .png()
    .toBuffer();
}

async function compare(a: Args): Promise<string> {
  const n = a.files.length;
  const width = n * CELL + (n + 1) * GAP;
  const height = CELL + LABEL_H + 2 * GAP;
  const layers: OverlayOptions[] = [];
  for (let i = 0; i < n; i++) {
    const left = GAP + i * (CELL + GAP);
    layers.push({ input: await cell(a.files[i]!, 'checker'), left, top: GAP });
    layers.push({
      input: labelSvg(String.fromCharCode(65 + i), CELL, LABEL_H),
      left,
      top: GAP + CELL,
    });
  }
  const out = join(a.out, `${a.name}.png`);
  await sharp({ create: { width, height, channels: 4, background: '#ffffff' } })
    .composite(layers)
    .png()
    .toFile(out);
  return out;
}

async function overlay(a: Args): Promise<string[]> {
  const [baseFile, varFile] = a.files;
  if (!baseFile || !varFile) throw new Error('--overlay base.png variant.png');
  const meta = await sharp(baseFile).metadata();
  const w = meta.width!;
  const h = meta.height!;
  const raw = (f: string) => sharp(f).resize(w, h, { fit: 'fill' }).ensureAlpha().raw().toBuffer();
  const [baseRaw, varRaw] = await Promise.all([raw(baseFile), raw(varFile)]);

  const half = Buffer.from(varRaw);
  for (let i = 3; i < half.length; i += 4) half[i] = Math.round(half[i]! / 2);
  const blendOut = join(a.out, `${a.name}-overlay.png`);
  await sharp(baseRaw, { raw: { width: w, height: h, channels: 4 } })
    .composite([{ input: half, raw: { width: w, height: h, channels: 4 } }])
    .png()
    .toFile(blendOut);

  const mask = diffMask(new Uint8Array(baseRaw), new Uint8Array(varRaw));
  const diffOut = join(a.out, `${a.name}-diff.png`);
  await sharp(baseRaw, { raw: { width: w, height: h, channels: 4 } })
    .composite([{ input: Buffer.from(mask.data), raw: { width: w, height: h, channels: 4 } }])
    .png()
    .toFile(diffOut);
  console.log(`차이 픽셀 ${(mask.ratio * 100).toFixed(2)}%`);
  return [blendOut, diffOut];
}

async function bg(a: Args): Promise<string[]> {
  const outs: string[] = [];
  for (const f of a.files) {
    const out = join(a.out, `${a.name}-${basename(f, extname(f))}-${a.bg}.png`);
    await sharp(await cell(f, a.bg)).toFile(out);
    outs.push(out);
  }
  return outs;
}

async function small(a: Args): Promise<string> {
  const rows: OverlayOptions[] = [];
  const maxSize = Math.max(...a.sizes);
  const rowH = maxSize + GAP;
  let width = GAP;
  for (const s of a.sizes) width += s + GAP;
  for (let r = 0; r < a.files.length; r++) {
    let left = GAP;
    for (const s of a.sizes) {
      const img = await sharp(a.files[r]!)
        .resize(s, s, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } })
        .png()
        .toBuffer();
      rows.push({ input: img, left, top: GAP + r * rowH + (maxSize - s) });
      left += s + GAP;
    }
  }
  const out = join(a.out, `${a.name}-small.png`);
  await sharp({
    create: { width, height: GAP + a.files.length * rowH, channels: 4, background: '#f4efe6' },
  })
    .composite(rows)
    .png()
    .toFile(out);
  return out;
}

const args = parseArgs(process.argv.slice(2));
if (args.files.length === 0) {
  console.error(
    '사용: review-sheet.ts --compare|--overlay|--bg checker|dark|--small 40,64 <파일...> [--name 이름] [--out 디렉터리]',
  );
  process.exit(2);
}
mkdirSync(args.out, { recursive: true });
const run = { compare, overlay, bg, small }[args.mode];
console.log(await run(args));
