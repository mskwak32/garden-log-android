# 작업 계획: PDF 내보내기 — 일기 없는 날 포함 + 디자인 변경 + 일기 단위 페이지 넘김

Notion 티켓 2개를 한 번에 처리한다.
- [pdf에 일기가 없어도 물주기와 할일 기록](https://app.notion.com/p/41a6e29b97d580bdbdaaf0d95c5ff9ca) (기능 추가)
- [pdf 디자인 변경](https://app.notion.com/p/4216e29b97d580e7a9c2eafac0a6191b) (보완 작업, 본문 없음 → 이 문서와 목업으로 범위 확정)

## 개요
- 목표
  - 기간 내 일기가 없어도 물주기·할일 완료 기록이 있는 날짜를 PDF에 넣는다 (일기 탭 목록과 같은 기준).
  - 일기 하나가 남은 공간에 다 들어가지 않으면 날짜만 남기지 말고 그 일기를 다음 페이지에서 시작한다. 한 페이지보다 긴 일기는 지금처럼 이어서 넘어간다.
  - PDF 표지·일기 페이지 디자인을 다듬는다.
- 범위
  - 포함: 내보내기 데이터 구성(날짜 묶기), PDF 모델, PDF 렌더링(페이지 넘김·디자인), "기록 없음" 안내 문구, 기본 기간
  - 제외: 내보내기 화면(DiaryExportScreen) 레이아웃 변경, DB 스키마 변경, 내보낸 파일 목록 화면
- 영향 범위
  - `domain/model/ExportRequest.kt`, `domain/usecase/export/`(새 UseCase), `app/di/UseCaseModule.kt`
  - `feature/plant/diary_export/DiaryExportViewModel.kt`, `DiaryExportState.kt`, `DiaryExportScreen.kt`, `res/values*/strings.xml`
  - `data/mapper/ExportMapper.kt`
  - `core/pdf/model/PdfRequest.kt`, `core/pdf/source_impl/PdfDataSourceImpl.kt`, `core/pdf/res/values*/strings.xml`

## 결정 사항
- [확정] 페이지 넘김 규칙: 날짜 하루치 블록(날짜 줄+할일+사진+내용)의 높이를 그리기 전에 미리 계산한다.
  - 남은 공간에 다 들어가면 그대로 그린다.
  - 안 들어가지만 빈 페이지에는 들어가면 새 페이지에서 시작한다.
  - 빈 페이지보다도 길면 날짜 줄+첫 사진 행(사진 없으면 본문 첫 3줄)이 들어갈 때만 현재 페이지에서 시작하고, 아니면 새 페이지에서 시작한 뒤 이어서 넘어간다.
  - 기존 `PAGE_BOTTOM_THRESHOLD` 판단은 이 규칙으로 대체한다.
- [확정] 날짜 묶기는 domain UseCase로 분리한다 (`GetDiaryExportDaysUseCase`): 일기 날짜 ∪ 물주기 날짜 ∪ 할일 기록 날짜, 날짜 오름차순. `GetDiaryListDaysUseCase`와 같은 방식이지만 Flow가 아닌 suspend 1회 조회.
- [확정] "기록 없음" 판단: 세 기록이 모두 비었을 때만 `ShowNoDiaryInRange`(이름은 `ShowNoRecordInRange`로 변경).
- [확정] 파일명 기간: 실제 들어간 날짜들(일기·물주기·할일 포함)의 최소~최대.
- [확정] 같은 날 일기가 여러 개: 날짜 줄·물주기·할일은 한 번, 그 아래 일기들(사진+내용)을 차례로. 일기 탭 목록과 같은 묶음.
- [확정] 내보내기 화면 기본 기간: 일기·물주기·할일 전체 기록의 가장 이른 날~늦은 날. 기록이 하나도 없으면 지금처럼 식물 생성일.
- [확정] 안내 문구: ko "선택한 날짜 범위에 기록이 없습니다." / en "No records in the selected date range." / ja "選択した日付範囲に記録がありません。" (`diary_export_no_diary` 값만 변경, 키 이름 유지)
- [확정] UI: 목업 컨펌됨 (날짜 줄에 요일·구분선, 물방울 아이콘 → 오른쪽 "물주기" 칩, 할일 메모 회색으로 표시, 푸터 "식물명 · 현재/전체 페이지", 표지 라벨/값 두 열 + 기록 기간 행)
- [확정] 푸터 전체 페이지 수: PdfDocument는 끝난 페이지를 다시 그릴 수 없으므로, 같은 그리기 코드를 사진 디코딩 없이(dry run) 한 번 돌려 전체 페이지 수를 센 뒤 실제로 그린다. 레이아웃 코드를 둘로 나누지 않기 위함.
- [확정] 요일은 기기 언어 기준 짧은 표기(`E`), "물주기" 칩·"기록 기간" 라벨은 core/pdf 새 문자열(ko/en/ja)로 추가.

## 작업 분할
실제 작업은 `token-saver` 스킬 방식으로 진행한다 (메인 세션은 판단·검증, 구현은 Worker 서브 에이전트).

| # | 작업 | 대상 파일/모듈 | 선행 작업 | 병렬 가능 |
|---|------|----------------|-----------|-----------|
| 1 | 내보내기 모델을 날짜 단위로 변경 | domain `ExportRequest.kt`, core/pdf `PdfRequest.kt`, data `ExportMapper.kt` | - | X (모든 작업의 기반) |
| 2 | 날짜 묶기 UseCase + 단위 테스트 | domain `usecase/export/GetDiaryExportDaysUseCase.kt`, test, app `UseCaseModule.kt` | 1 | O (3과 병렬) |
| 3 | PDF 렌더링: 높이 계산·페이지 넘김·디자인·푸터 | core/pdf `PdfDataSourceImpl.kt`, 새 `PageBreak.kt` + test, core/pdf strings | 1 | O (2·4와 병렬) |
| 4 | 전체 기록 기준 기간 조회 | domain `GetExportDateRangeUseCase`, 물주기·할일 repository/DAO에 MIN/MAX 날짜 조회 추가, `UseCaseModule.kt` | 1 | X (2와 `UseCaseModule.kt` 겹침 → 2 이후) |
| 5 | ViewModel·안내 문구 연결 | feature/plant `DiaryExportViewModel/State/Screen.kt`, strings(ko/en/ja) | 2, 4 | X |

### 구현 방식
- **#1** `DiaryExport`/`DiaryPdf`를 하루 단위로 바꾼다: `date`, `entries: List<(content, imagePaths)>`, `hasWateringRecord`, `tasks: List<(title, memo)>`. 이름은 그대로 두고 필드만 바꿔 변경 범위를 줄인다. 매퍼도 같이 맞춘다.
- **#2** 기존 repository 메서드(`getDiariesForExport` 쪽 DiaryRepository 조회, `getWateringDatesByRange`, `getPlantTaskLogsByRange`)를 그대로 사용해 날짜별로 합친다. 새 repository/DAO 메서드는 만들지 않는다.
- **#3** `PageWriter`에 `measureDay(day): Float`를 추가해 그리기와 같은 상수·`StaticLayout`으로 높이를 계산한다. 넘김 판단은 순수 함수(`PageBreak.decide(blockHeight, minHeadHeight, remaining, pageContentHeight)`)로 분리해 테스트한다. 디자인 변경은 컨펌된 항목만 Canvas로 그린다.
- **#4** 기존 `getDiaryDateRange`처럼 물주기·할일 기록에도 식물별 MIN/MAX 날짜 쿼리를 추가하고(스키마 변경 없음), UseCase에서 세 범위를 합친다. `GetDiaryDateRangeUseCase`가 내보내기 외에 쓰이지 않으면 대체·삭제.
- **#5** ViewModel은 #2 결과를 `ExportRequest`로 바꾸기만 한다. 빈 결과면 `ShowNoRecordInRange`. 초기 기간은 #4 사용.

### 서브 에이전트 공통 지시
- AGENTS.md 컨벤션 준수(문자열 하드코딩 금지, ko/en/ja 3개 언어 모두 추가, 주석은 code-comment-style).
- DB 스키마·마이그레이션 수정 금지. `publish*`/`promote*` 태스크 실행 금지.
- 자기 작업 대상 파일만 수정. 끝나면 `./gradlew :<module>:compileDebugKotlin`(또는 해당 모듈 test) 결과와 변경 파일 목록을 보고.

## 단위 테스트
- `GetDiaryExportDaysUseCaseTest` (domain): 일기 없이 물주기만/할일만 있는 날이 포함되는지, 세 기록이 같은 날 합쳐지는지, 같은 날 일기 여러 개, 모두 비었을 때 빈 목록. — 티켓의 핵심 동작.
- `PageBreakTest` (core/pdf, JVM): 남은 공간에 들어감 → 그대로 / 안 들어가지만 한 페이지 이하 → 새 페이지 / 한 페이지 초과 + 머리 들어감 → 이어서 / 머리도 안 들어감 → 새 페이지. — 이번 버그(날짜만 남음) 재발 방지.
- Canvas 그리기 자체는 테스트하지 않는다(프레임워크 동작, 결과는 PDF 수동 확인).

## 완료 기준
- [x] 전체 빌드 성공 — 확인: `./gradlew assembleDebug`
- [x] 단위 테스트 통과 — 확인: `./gradlew :domain:test :core:pdf:testDebugUnitTest`
- [ ] 일기 없이 물주기만/할일만 있는 날짜가 PDF에 나온다 — 확인: 단위 테스트 + 수동 PDF 확인 (단위 테스트 통과, PDF 수동 확인은 미실시)
- [x] 세 기록이 모두 없는 기간은 안내 후 종료 — 확인: 단위 테스트(빈 목록) + 코드 리뷰
- [ ] 일기가 남은 공간에 다 안 들어가면 날짜만 남지 않고 다음 페이지에서 시작 — 확인: `PageBreakTest` + 수동 PDF 확인(첨부 PDF와 같은 데이터로 2·3페이지) (PageBreakTest 통과, PDF 수동 확인은 미실시)
- [ ] 컨펌된 디자인이 반영됨 — 확인: 수동 PDF 확인 (미확인: PDF 수동 확인 필요)
- [ ] 수동 확인(사용자 지시 시에만): 앱에서 부추 2026.04.12~10.01 내보내기 → Downloads/GardenLog PDF 열어 표지, 2·3페이지, 일기 없는 날 확인
- [x] 작업 완료 후 `pdf-mockup.html` 삭제
