# tools/loop — 이미지 검수 도구

GPT 이미지 위임 파이프라인(`docs/03-gpt-image-pipeline.md`)의 검수 쪽 도구다. 크로마키 제거, 크기 맞춤, 검수 시트를 만든다.
RealCash 루트에는 `package.json`이 없다. 이 폴더는 혼자 서는 Node 패키지다 (Node 20.9 이상, `sharp` 사용).

## 설치와 테스트

```sh
cd tools/loop
npm install          # sharp는 npm의 @img/sharp-<플랫폼> 패키지로 미리 빌드된 바이너리를 받는다
npm test             # vitest: 크로마키·크기 맞춤·시트 로직 + scripts/worker-codex.sh 동작 테스트(가짜 codex)
npm run typecheck    # tsc --noEmit
```

`worker-codex.test.ts`는 `scripts/worker-codex.sh`를 임시 폴더에 복사해 실제 bash로 돌린다. 진짜 codex는 부르지 않는다.
`assets/jobs/queue/`의 첫 의뢰서(다 처리된 뒤라면 `done/`의 첫 의뢰서)를 가짜 codex로 끝까지 처리해 보는 시험도 들어 있다.

## 도구 (저장소 루트에서 실행)

```sh
T=tools/loop/node_modules/.bin/tsx

# 크로마키 제거. 녹색 피사체(만 원권 등)는 마젠타 배경으로 의뢰했으니 --key magenta
$T tools/loop/src/chroma-key.ts assets/inbox/<id>/<id>-1.png /tmp/<id>-keyed.png --key magenta

# 투명 영역을 잘라 목표 캔버스 가운데에 맞춤 (기본 여백 0.1)
$T tools/loop/src/fit.ts /tmp/<id>-keyed.png assets/img/<id>.png 512x256 --margin 0.02

# 검수 시트. 기본 출력 tools/loop/out/review/ (gitignore). --out <폴더>, --name <이름>
$T tools/loop/src/review-sheet.ts --bg dark assets/img/<id>.png      # 어두운 배경: 배경색 테두리·구멍 확인
$T tools/loop/src/review-sheet.ts --bg checker assets/img/<id>.png   # 체크무늬 배경
$T tools/loop/src/review-sheet.ts --small 40,64 assets/img/<id>.png  # 작게 줄여도 읽히나
$T tools/loop/src/review-sheet.ts --compare a.png b.png c.png        # 시안 나란히 (A, B, C 라벨)
$T tools/loop/src/review-sheet.ts --overlay base.png variant.png     # 겹치기 + 달라진 곳 빨갛게
```

`npm run chroma-key -- …`처럼 npm 스크립트로도 부를 수 있지만, 이때는 현재 폴더가 `tools/loop`가 되므로 파일 경로를 절대 경로로 준다.

## 파일

| 파일 | 내용 |
|---|---|
| `src/chroma.ts` | 크로마키·despill·번짐 점검 로직 (green / magenta), sharp 없이 RGBA 버퍼만 |
| `src/chroma-key.ts` | 크로마키 CLI |
| `src/fit-lib.ts`, `src/fit.ts` | 알파 경계 상자, contain 배치, 크기 맞춤 CLI |
| `src/sheet.ts`, `src/review-sheet.ts` | 라벨·차이 마스크, 검수 시트 CLI |
| `src/*.test.ts` | vitest |
