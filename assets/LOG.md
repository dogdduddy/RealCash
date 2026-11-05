# 에셋 출처 기록

검수를 통과한 에셋마다 한 블록씩 추가한다. 형식과 절차: `docs/03-gpt-image-pipeline.md`.
각 블록: 에셋 id, 의뢰서 경로, 원본(assets/raw/) 경로와 실제 크기, 처리(크로마키·크기 맞춤 명령), 검수 근거, 상태(draft/final), aiGenerated.

## bill.w10000.a — 만원권 앞면 (화풍 기준)
- 의뢰서: assets/jobs/done/00-bill.w10000.a.prompt.md (1회 생성, 69초)
- 원본: assets/raw/bill.w10000.a-1.png, 1254x1254 (요청 1024, 재생성하지 않음)
- 처리: chroma-key --key magenta (despill 0.10%, 잔여 배경 0%) → fit 1024x448 --margin 0.0 → assets/img/money/bill.w10000.png
- 검수: 인물·문자(숫자 10000 외)·일련번호·보안요소 없음, 녹색·청록 + 크림 종이, 기요셰·테두리·추상 문양. 어두운 배경 시트 가장자리 깨끗, 40/64px 축소 가독. 2026-11-04 PASS draft
- 상태: draft · aiGenerated: true · source: gpt

## bill.w10000.a — 상태 변경 2026-11-04
- 오리지널 스타일 방향을 폐기하고 실물풍(bill.w10000.b)으로 대체. 파일은 assets/img/drawable/bill_w10000_a.png 로 이동해 기록용 보관. REVIEWED "PASS 미채택".

## bill.w10000.b / bill.w50000.b / bill.w5000.b / bill.w1000.b — 실물풍 지폐 앞면 4종 (2026-11-04)
- 근거: 「한국은행권 및 주화의 도안 이용기준」(2024-09-01 개정, docs/reference/). 초상은 그리지 않고 뒷면 모티프로 대체(혼천의·월매도/풍죽도·초충도·계상정거도).
- 의뢰서: assets/jobs/done/01~04-*.prompt.md. 각 1회 생성(224s/118s/117s/108s). 원본 전부 1254x1254 → assets/raw/<id>-1.png
- 처리: chroma-key(만원 magenta, 나머지 green; despill 0.2~0.4%, 잔여 배경 0%) → fit 1024 x 실물 비율 높이(470/452/490/512) --margin 0.0 → assets/img/drawable/bill_w{10000,50000,5000,1000}.png
- 검수: 인물·실루엣 없음, 실물 색·레이아웃·홀로그램·점자·견본 일련번호 재현, 어두운 배경 시트 가장자리 깨끗, 40/64px 축소 시 색+숫자로 권종 구분. 전부 PASS draft
- 상태: draft · aiGenerated: true · source: gpt

## coin.c500.a / coin.c100.a — 실물풍 주화 (2026-11-04)
- 의뢰서: assets/jobs/done/05-coin.c500.a, 06-coin.c100.a. 각 1회 생성(81s/76s). 원본 1254x1254 → assets/raw/<id>-1.png
- 100원은 앞면 이순신 초상을 피하기 위해 숫자 면(뒷면)만 의뢰. 500원은 앞면(학).
- 처리: chroma-key --key magenta(despill 0.3%, 잔여 0%) → fit 512x512 --margin 0.0 → assets/img/drawable/coin_c500.png, coin_c100.png
- 검수: 인물 없음, 백동 재질·톱니 테두리·글자 실물 충실, 정원. PASS draft · aiGenerated: true · source: gpt

## coin.c50.a / coin.c10.a — 실물풍 주화 (2026-11-04)
- 의뢰서: assets/jobs/done/07-coin.c50.a, 08-coin.c10.a. 각 1회 생성(c50 은 1236s — 한도 대기 포함, c10 97s). 원본 1254x1254 → assets/raw/<id>-1.png
- 처리: chroma-key --key magenta(despill 0.26%, 잔여 0%) → fit 512x512 → assets/img/drawable/coin_c50.png, coin_c10.png
- 검수: 인물 없음, 벼 이삭/다보탑 도안·재질(양백/구리) 충실, 정원. PASS draft · aiGenerated: true · source: gpt

## wallet.closed.a / wallet.open.a / tray.a — 홈·열린 지갑 소품 (2026-11-04)
- 의뢰서: assets/jobs/done/09~11. 각 1회 생성(62s/60s/74s). 원본 1254x1254 → assets/raw/<id>-1.png
- 처리: chroma-key --key green(잔여 0%) → fit 1024x540 / 640x1024 / 1024x427 --margin 0.0 → assets/img/drawable/wallet_closed.png, wallet_open.png, tray.png
- 검수: 글자·로고·손·돈 없음, 가죽·고무 질감 사실적, 포켓 비움. 포켓·트레이 안쪽 좌표(비율)는 manifest.json 에 기록. PASS draft · aiGenerated: true · source: gpt

## wallet.closed.b / wallet.open.b — 검은 크로스그레인 반지갑 (닫힘·열림) (2026-11-05)
- 의뢰서: assets/jobs/done/12, 13. 각 1회 생성(69s/127s). 원본 1254x1254 → assets/raw/<id>-1.png
- 처리: chroma-key --key green → fit 1024x945 / 640x1518 --margin 0.0 → assets/img/drawable/wallet_closed.png, wallet_open.png (리소스 이름 유지, 갈색 장지갑 .a 정리본을 덮어씀)
- 검수: 글자·로고·손·돈·카드 없음. 닫힘은 참고 제품 사진과 같은 3/4 구도·십자 결 질감, 장지갑 아님. 열림은 세로 아몬드 입구가 넓게 벌어진 빈 칸, 왼쪽 벽에 카드 슬롯 선만. 입구 좌표(비율)는 manifest.json wallet.open.b.opening. PASS draft · aiGenerated: true · source: gpt

## wallet.open.c — 눕혀 펼친 검은 반지갑 (열린 지갑 v4 본체) (2026-11-05)
- 의뢰서: assets/jobs/done/14. 1회 생성(124s). 원본 1254x1254 → assets/raw/wallet.open.c-1.png
- 처리: chroma-key --key green → fit 640x1230 --margin 0.0 → assets/img/drawable/wallet_open.png (리소스 이름 유지, wallet.open.b 정리본을 덮어씀)
- 검수: 글자·로고·손·돈·카드 없음. 세로 평면 구도, 접힘선 가로, 위 절반 매끈, 아래 절반 슬롯 절개선 2줄. 오른쪽 가장자리 입구 틈(열 밝기 프로필로 x 비율 0.971~0.981 확인) 좌표는 manifest.json wallet.open.c.opening. PASS draft · aiGenerated: true · source: gpt
