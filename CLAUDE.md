# RealCash

RealCash는 카드·간편결제는 그대로 쓰면서 돈을 보고·꺼내고·거슬러 받는 감각만 현금처럼 되살려 지출을 줄이는 Android·iOS 앱이다 (Kotlin Multiplatform). 제품·아키텍처는 `docs/01-realcash-analysis-and-design.md`, 작업 분해와 작업 규칙은 `docs/02-engineering-plan.md`를 먼저 읽는다. `docs/`와 `README.md`는 리드 전용이다. 이미지 파이프라인 운영 설명은 `docs/03-gpt-image-pipeline.md`.

## 이미지 작업 (GPT 위임)
- 이미지는 직접 만들지 않는다. assets/jobs/queue/NN-<id>.prompt.md 의뢰서를 .tmp로 쓴 뒤
  이름을 바꿔 넣고 커밋·push한다. 의뢰서 하나 = 이미지 1장, 첫 줄 $imagegen,
  그 자체로 완결된 프롬프트(틀은 docs/03-gpt-image-pipeline.md), 저장 경로 assets/inbox/<id>/<id>-1.png.
- 돈 그림은 오리지널 스타일라이즈드 디자인만 의뢰한다. 실제 한국은행 도안 복제 금지:
  실존 인물 초상, "한국은행" 등 읽히는 글자, 일련번호, 관인, 은선·홀로그램 같은 보안 요소를 [절대 금지]에 이유와 함께 적는다.
  권종 식별은 색 + 숫자 + 크기로 한다 (docs/01 §5.5).
- codex를 직접 실행하지 않는다. 맥의 동기화 워커가 5분마다 동기화 브랜치(SYNC_BRANCH, 기본 main)를 받아
  Codex 워커가 처리하고 결과를 올린다. 의뢰서도 그 브랜치에 push해야 맥에 닿는다. scripts/는 수정하지 않는다.
- 검수 대상: assets/inbox/<id>/STATUS가 있고 REVIEWED가 없는 폴더. 이미지를 직접 보고,
  필요하면 tools/loop의 chroma-key·fit·review-sheet로 확인한다 (처음 한 번 cd tools/loop && npm install,
  실행은 저장소 루트에서 tools/loop/node_modules/.bin/tsx tools/loop/src/<도구>.ts …).
  - 통과: assets/raw/에 원본, assets/img/에 정리본, assets/manifest.json에 draft(source gpt, aiGenerated),
    assets/LOG.md에 출처, REVIEWED에 "PASS draft".
  - 불합격: REVIEWED에 "FAIL 이유", 결과를 attempt-N/으로 옮기고 의뢰서를 고쳐
    새 NN으로 다시 넣는다. 같은 id 3회 실패면 사람에게 묻는다.
- 미검수가 4개 쌓이면 워커가 멈추므로 검수를 먼저 한다.
- 시안 여러 장은 사용자에게 보여 주고 고르게 한다. final은 사람 승인 뒤에만.
- push 충돌 시 assets/inbox·assets/jobs는 원격(맥) 쪽 변경을 살린다.
