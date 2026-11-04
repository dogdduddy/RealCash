# 에셋 출처 기록

검수를 통과한 에셋마다 한 블록씩 추가한다. 형식과 절차: `docs/03-gpt-image-pipeline.md`.
각 블록: 에셋 id, 의뢰서 경로, 원본(assets/raw/) 경로와 실제 크기, 처리(크로마키·크기 맞춤 명령), 검수 근거, 상태(draft/final), aiGenerated.

## bill.w10000.a — 만원권 앞면 (화풍 기준)
- 의뢰서: assets/jobs/done/00-bill.w10000.a.prompt.md (1회 생성, 69초)
- 원본: assets/raw/bill.w10000.a-1.png, 1254x1254 (요청 1024, 재생성하지 않음)
- 처리: chroma-key --key magenta (despill 0.10%, 잔여 배경 0%) → fit 1024x448 --margin 0.0 → assets/img/money/bill.w10000.png
- 검수: 인물·문자(숫자 10000 외)·일련번호·보안요소 없음, 녹색·청록 + 크림 종이, 기요셰·테두리·추상 문양. 어두운 배경 시트 가장자리 깨끗, 40/64px 축소 가독. 2026-11-04 PASS draft
- 상태: draft · aiGenerated: true · source: gpt
