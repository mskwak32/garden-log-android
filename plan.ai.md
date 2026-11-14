# 일기 목록 UI 개선 구현 계획

노션 카드: 텃밭일기 작업관리 › "일기 목록 ui 개선" (진행 중)

일기 탭 목록(`feature/plant/.../diary_list/`)에 일기·할일 완료·물주기 기록을 날짜별로 묶어서 보여준다.
목록 상단(기간 이동, 월별/연도별 토글, 정렬, 식물 필터)은 지금 그대로 둔다.

## 확정된 디자인

### 목록 구성
- 날짜별로 묶는다. 날짜 헤더 형식은 `toDateWithDayOfWeek()`(`MM. dd E`, 예: `11. 14 토`)를 쓰고, 스타일은 `bodyMedium` + `onSurface` + 볼드 없음.
- 같은 날 안에서는 **일기 → 할일 → 물주기** 순서. 정렬 옵션(최신순/오래된순)은 날짜 순서에만 적용하고, 같은 날 안의 순서는 바꾸지 않는다.
- 각 날짜 그룹의 마지막 카드 뒤에 `Spacer` 아이템을 넣어 다음 날짜(또는 다음 달)와 간격을 둔다. 날짜 헤더와 월 헤더에는 위 패딩을 따로 주지 않는다.
  - 카드 사이 간격 8dp, 그룹 끝 Spacer와 날짜 헤더 패딩은 목업 기준(이전 날짜 마지막 카드 → 다음 날짜 헤더 약 30dp, 날짜 헤더 → 첫 카드 약 7dp)으로 맞춘다.
- **월별 보기:** 날짜 헤더만 둔다.
- **연도별 보기:** `LazyColumn.stickyHeader`로 월 헤더를 두고, 그 아래에 날짜별로 묶는다.
  - 월 헤더: 14sp 볼드, `onSurface`, 앞에 지름 6dp의 `primary` 색 점(간격 7dp). 배경은 `background` 단색으로 둬서 붙어 있을 때 카드가 비치지 않게 한다. 붙어 있을 때도 크기는 그대로다.
  - 지금 쓰는 `MonthHeader`(월 이름 `TextStyle.FULL`)를 이 디자인으로 바꾼다.
- 아무 기록도 없으면 빈 화면을 보여주고, 문구는 새 문자열 "기록이 없어요"로 바꾼다. `diary_list_empty`는 다른 곳에서도 쓸 수 있으니 수정하지 않고 새 키를 추가한다(ko/en/ja).

### 카드 공통
- 배경 `surfaceContainerLow`, 테두리 1dp `outlineVariant`, 모서리 12dp. 지금 `DiaryListItem` 스타일을 유지한다.
- 날짜는 카드 안에 넣지 않는다(헤더로 이동).
- **클릭 리플 없음.** `Card(onClick)` 대신 `Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null)`을 쓴다.
- 식물 이름 앞에 `R.drawable.ic_leaf`(지금 카드와 같은 아이콘)를 붙인다. 수확한 식물이면 이름 옆에 `TextBadge(text = stringResource(R.string.harvested))`를 붙인다(식물 상세 헤더와 동일).

### 일기 카드
- 사진은 카드 폭에 맞춰 **16:9 고정 비율**, `ContentScale.Crop`, 모서리 10dp.
  - 1장: 그대로 크게 보여준다.
  - 여러 장: `HorizontalPager`로 스와이프하고 사진 아래에 dots를 둔다(현재 페이지는 `primary`, 나머지는 `outlineVariant`).
  - 사진 없음: 사진 영역 없이 텍스트만.
- 사진 아래 한 줄: 일기 아이콘(18dp) + 식물 이름 + 수확 뱃지.
- 내용: `bodyMedium` / `onSurface`, 최대 2줄, `TextOverflow.Ellipsis`.
- 클릭하면 지금처럼 일기 상세로 이동한다.

### 할일 완료 카드
- 한 줄: `IconPack.Gardening`(18dp, `tint = Color.Unspecified`) + 제목(`titleSmall` 정도, Medium) + 식물 이름 + 수확 뱃지.
- 내용이 있으면 아래 줄에 들여쓰지 않고 카드 왼쪽 끝에서 시작한다. 스타일은 일기 내용과 같다(`bodyMedium` / `onSurface`, 최대 2줄).
- 내용이 있으면 클릭 시 다이얼로그를 띄운다. **내용이 없으면 클릭해도 반응이 없다.**

### 물주기 카드
- 한 줄: `IconPack.WaterDropBlue`(18dp, `tint = Color.Unspecified`) + "물주기" + 식물 이름 + 수확 뱃지.
- 클릭해도 반응이 없다.

### 할일 다이얼로그 (`DiaryListTaskDialog.kt`)
- M3 `AlertDialog`. 앱에 있는 `ExactAlarmPermissionDialog`처럼 기본 스타일을 따른다.
- title: `Row { Icon(IconPack.Gardening, 24dp, Color.Unspecified); Spacer(8dp); Text(제목) }`
- text: 할일 내용
- confirmButton: `TextButton` "닫기"(`R.string.close`)

### 일기 아이콘 (새로 추가)
- `core/common_ui/.../icon/DiaryBook.kt`에 `IconPack.DiaryBook`을 추가한다. 24×24 viewport, 여러 색, 기존 `Gardening` / `WaterDropBlue`와 같은 스타일이다.
- 경로(목업 SVG 기준):
  - 뒷장(종이): rect x=5 y=2.5 w=14.5 h=19 r=1.8, `#EDE3C8`
  - 표지: rect x=4 y=2 w=14.5 h=19.5 r=1.8, `#6AA524`
  - 책등: rect x=4 y=2 w=3 h=19.5 r=1, `#557A1E`
  - 새싹 줄기: `M12.8 17.6 V12.6`, stroke `#F7FBF1` 1f, round cap
  - 왼쪽 잎: `M12.8 14.2 Q10 14.3 9.6 11.4 Q12.5 11.2 12.8 14.2 Z`, `#DDF5C9`
  - 오른쪽 잎: `M12.8 13.3 Q13.1 10.1 16.2 9.9 Q16 13 12.8 13.3 Z`, `#F7FBF1`
  - 책갈피 끈: `M14.8 21.3 V23.6 L15.9 22.7 L17 23.6 V21.3 Z`, `#D85A30`
- rect는 `ImageVector.Builder`에서 path로 그린다(둥근 모서리는 arcTo 또는 quadTo로).

### 일기 상세 날짜에 요일 추가
- `DiaryDetailScreen`의 날짜는 지금 `toDateString()`(`FormatStyle.MEDIUM`)이라 요일이 없다.
- `DateTimeTextUtil.kt`에 `toDateWithDayOfWeekLong()`(가칭)을 추가한다. `ofLocalizedDate(MEDIUM)` 결과 뒤에 ` (E)`를 붙여 `2026. 11. 14. (토)` 형태로 만들고, 일기 상세에서 이 함수를 쓴다.

## 데이터 변경

### 할일 완료 기록에 내용 저장 (DB v9 → v10)
- `PlantTaskLogEntity`에 `content: String?`를 추가한다.
- `Migration_9_10.kt`:
  - `ALTER TABLE plant_task_log ADD COLUMN content TEXT`
  - 기존 기록 채우기: `UPDATE plant_task_log SET content = (SELECT content FROM plant_task WHERE plant_task.id = plant_task_log.taskId)`. 이미 삭제된 할일의 기록은 null로 남는다.
- `GardenDatabase` version을 10으로 올리고, `DatabaseModule`에 `MIGRATION_9_10`을 등록한다. 스키마 `10.json`이 export됐는지 확인한다.
- `MigrationTest`에 9 → 10 케이스를 추가한다.
- 도메인 `PlantTaskLog`에 `content: String? = null`을 추가하고, `DatabaseMapper.toPlantTaskLog()`도 고친다.
- `PlantTaskRepositoryImpl.setTaskLog`(71행 부근)에서 기록을 저장할 때 `content = task.content`도 함께 저장한다. 제목처럼 완료 시점의 값을 보관한다.
- AGENTS.md의 DB 버전 표기(v8)를 v10으로 고치고, 엔티티 목록에 `PlantTaskEntity`, `PlantTaskLogEntity`를 추가한다.

### 기간 조회 (전체 식물 / 특정 식물)
지금 DAO는 식물 하나 기준의 조회만 있다. 일기 목록용으로 기간 조회 Flow를 추가한다(`plantId`가 null이면 전체).
- `WateringLogDao`: `observeWateringLogsByRange(startDate, endDate, plantId: Int?)`. `plantId`와 `date`를 반환한다.
  - 쿼리: `WHERE date BETWEEN :start AND :end AND (:plantId IS NULL OR plantId = :plantId)`
- `PlantTaskDao`: `observeTaskLogsByRange(startDate, endDate, plantId: Int?)`. `PlantTaskLogEntity`를 반환한다.
- 도메인:
  - `WateringLog(plantId, date)` 모델을 추가한다. 위치는 `domain/model/WateringDays.kt` 옆 또는 새 파일.
  - `WateringLogRepository.observeWateringLogsByRange(...)`, `PlantTaskRepository.observeTaskLogsByRange(...)`를 추가한다.
- UseCase:
  - `GetDiaryListRecordsUseCase`(가칭)를 새로 만든다. `periodUnit`, `yearMonth`, `sortOrder`, `plantId`를 받는다.
  - 기간 계산은 `GetDiariesUseCase.periodRange`와 같은 방식이다. 중복을 피하려면 private 함수를 internal 헬퍼로 빼서 같이 쓴다.
  - 일기·할일 기록·물주기 기록 Flow를 `combine`해서 날짜별 그룹 리스트를 반환한다.
  - 반환 모델 예: `DiaryListDayGroup(date, diaries, taskLogs, wateringLogs)`. 날짜 정렬은 sortOrder를 따르고, 그룹 안 순서는 일기 → 할일 → 물주기로 고정한다.
  - `app/di/UseCaseModule.kt`에 `@Provides`로 등록한다.

## UI 레이어

### 기존 공용 코드 분리 (일기 더보기·식물 상세는 디자인과 형식 그대로)

지금 일기 목록, 일기 더보기, 식물 상세가 다음 코드를 같이 쓴다.
- `model/DiaryListItemUiModel.kt`(+ `Diary.toDiaryListItemUiModel()`): 일기 목록, 일기 더보기(`DiaryMoreListItem`, `DiaryMoreViewModel`, `DiaryMoreScreen` Preview), 식물 상세(`PlantDetailState`, `PlantDetailViewModel`, `PlantDetailDiaryItem`, `PlantDetailScreen`)
- `diary_list/DiaryListItem.kt`의 `DiaryListItem` 컴포저블: 일기 더보기(`DiaryMoreScreen` 195행). `DiaryListEvent.OnDiaryClicked`를 받아 `DiaryMoreEvent`로 바꿔 전달하고 있다.

이렇게 나눈다(**동작과 디자인은 바꾸지 않고 이름과 위치만 옮긴다**).
- 기존 모델은 이름을 **`DiarySummaryUiModel`**로 바꾼다(`model/DiarySummaryUiModel.kt`, `Diary.toDiarySummaryUiModel()`). 날짜, 첫 장 사진, 내용, 식물 이름을 담은 요약 모델이고, 일기 더보기와 식물 상세가 계속 같이 쓴다.
- 기존 sealed interface `DiaryMoreListItem`(MonthHeader / DiaryItem)은 `DiaryMoreListItemUiModel`로 이름을 바꾼다. 파일은 `diary_more/DiaryMoreListItem.kt` 그대로 둔다. 일기 목록의 `DiaryListItemUiModel` + `DiaryListItem`과 같은 짝을 맞추기 위해서다.
- 기존 `DiaryListItem` 컴포저블은 `diary_more/DiaryMoreListItem.kt`의 `DiaryMoreListItem(diary: DiarySummaryUiModel, onClick: (Int) -> Unit)`로 옮긴다. 썸네일 왼쪽 배치, 날짜, 식물 이름, 내용 2줄은 그대로 둔다. `DiaryListEvent` 의존을 없애고 `DiaryMoreScreen`에서 `onEvent(DiaryMoreEvent.OnDiaryClicked(id))`를 바로 호출한다.
- `diary_more/DiaryMoreListItem.kt`의 `buildDiaryListItems`는 `buildDiaryMoreListItems`로 이름을 바꾼다(일기 목록과 헷갈리지 않게).
- 분리가 끝나면 `diary_list` 패키지는 다른 화면에서 참조하지 않는다(`PlantNavGraph` 제외).

### 일기 목록 전용 모델

- `model/DiaryListItemUiModel.kt`: 기존 파일을 위 이름 변경으로 비운 뒤 일기 목록 전용으로 새로 쓴다.
  ```kotlin
  sealed interface DiaryListItemUiModel {
      val key: String
      data class Diary(id, imagePaths: List<String>, content, plantName, isHarvested)
      data class Task(logId, title, content: String?, plantName, isHarvested)
      data class Watering(plantId, date, plantName, isHarvested)
  }
  data class DiaryListDayUiModel(val date: LocalDate, val items: List<DiaryListItemUiModel>)
  ```
  - LazyColumn key는 타입 prefix를 붙여 겹치지 않게 한다(`diary_$id`, `task_$logId`, `water_${plantId}_$date`).
  - 일기 사진은 첫 장만이 아니라 `pictureList` 전체 경로를 쓴다.
- `DiaryListState.diaries` → `days: List<DiaryListDayUiModel>`로 바꾼다. 빈 화면 판정도 `days.isEmpty()`로 한다.
- `DiaryListEvent.OnTaskClicked(title, content)`를 추가하고, `DiaryListEffect.ShowTaskDialog(title, content)`로 다이얼로그를 띄운다. 다이얼로그 상태는 Screen에서 `remember`로 관리한다. 지금 `SelectYearMonthDialog`와 같은 패턴이다.
- `DiaryListViewModel.observeDiaries`는 새 UseCase로 바꾼다. 식물 이름과 수확 여부는 지금처럼 `plantFilterList`와 `combine`해서 매핑한다.
- `DiaryListScreen`:
  - 월별: `days.forEach { item(header) ; items(items) ; item(spacer) }`
  - 연도별: `days.groupBy { it.date.month }`(정렬 순서 유지) → `stickyHeader(월)` + 위와 같은 날짜 블록
- 카드 컴포저블은 `diary_list/DiaryListItem.kt`에 새로 쓴다(기존 내용은 분리 단계에서 일기 더보기로 옮겨진 상태). `DiaryListDiaryCard`, `DiaryListTaskCard`, `DiaryListWateringCard`, 그리고 공통 `PlantNameRow(plantName, isHarvested)`를 private로 만든다.
- Preview: 각 카드와 화면(월별, 연도별, 빈 화면)을 `GardenLogTheme`으로 만든다.
- 새 문자열: 빈 화면 문구, "물주기" 라벨(기존 키가 있으면 재사용). ko/en/ja 모두 추가한다.

## 작업 분할 (token-saver)

메인 세션은 계획, 리뷰, 빌드 확인을 맡는다. 구현은 아래 단위로 서브 에이전트에 맡긴다.
- 0, 1, 3, 4번은 서로 다른 파일만 건드리므로 병렬로 돌려도 된다.
- 2번은 1번 뒤에, 5번은 0·2번 뒤에, 6번은 3·4·5번 뒤에 진행한다.

| # | 작업 | 범위(파일) | 권장 모델 | 완료 조건 |
|---|---|---|---|---|
| 0 | 공용 코드 분리 (이름 변경·이동만) | `model/DiaryListItemUiModel.kt` → `DiarySummaryUiModel.kt`, `diary_list/DiaryListItem.kt` → `diary_more/DiaryMoreListItem.kt`, `DiaryMoreListItem` → `DiaryMoreListItemUiModel`, `diary_more/*`, `plant_detail/*`, `diary_list/*`의 참조 수정 | haiku | `./gradlew :feature:plant:compileDebugKotlin` 통과. 일기 더보기·식물 상세의 화면 코드에 디자인 변경 없음 |
| 1 | DB v10 마이그레이션 + 할일 기록 content 저장 | `core/database` entity·migration·DatabaseModule·MigrationTest, `domain` PlantTaskLog, `data` mapper·PlantTaskRepositoryImpl, AGENTS.md | sonnet | `./gradlew :core:database:test` 통과, `schemas/.../10.json` 생성 |
| 2 | 기간 조회 DAO/Repository/UseCase | `WateringLogDao`, `PlantTaskDao`, 두 Repository와 Impl, `WateringLog` 모델, `GetDiaryListRecordsUseCase`, `UseCaseModule` | sonnet | `./gradlew :domain:test` 통과. UseCase 단위 테스트는 그룹 순서·정렬만 최소로 작성(test-scope-guard) |
| 3 | 일기 아이콘 + 날짜 유틸 + 일기 상세 요일 | `core/common_ui/icon/DiaryBook.kt`, `DateTimeTextUtil.kt`, `DiaryDetailScreen.kt` | haiku | 빌드 통과, Preview로 아이콘 확인 |
| 4 | 문자열 리소스 | `feature/plant/res/values{,-en,-ja}/strings.xml` | haiku | 새 키 3개 언어 모두 추가 |
| 5 | UI 모델 + State/Event/Effect + ViewModel | `model/DiaryListItemUiModel.kt`(새 내용), `DiaryListState.kt`, `DiaryListViewModel.kt` | sonnet | 컴파일 통과 |
| 6 | 카드·목록·다이얼로그 UI | `DiaryListItem.kt`, `DiaryListScreen.kt`, `DiaryListTaskDialog.kt` | sonnet | `./gradlew assembleDebug` 통과, Preview 갖춤 |
| 7 | 최종 검증 (메인 세션) | 전체 | — | `./gradlew assembleDebug test` 통과, diff 리뷰. 기기 실행·스크린샷은 꼭 필요할 때만 |

### 서브 에이전트에 줄 공통 지시
- AGENTS.md 규칙(문자열 하드코딩 금지, TopBar·Dialog 파일 분리, 비자명한 로직에만 주석)을 따른다.
- 주석은 `code-comment-style` 스킬 스타일을 따른다.
- 맡은 범위 밖의 파일은 수정하지 않는다. 커밋하지 않는다.
- 완료하면 바뀐 파일 목록과 빌드·테스트 결과만 짧게 보고한다.

## 결정 사항 기록
- 사진 비율: 16:9 고정
- 내용 없는 할일 카드: 클릭 반응 없음
- 기록 종류 필터(일기/할일/물주기 표시 여부): 이번 범위에서 제외. 써보고 필요하면 후속 작업으로 진행
- 빈 화면 문구: "기록이 없어요"(새 키)
- 물주기·할일 기록에는 시간 정보가 없어서 같은 날 안의 순서는 타입 순서로 고정
- 할일 기록 content는 완료 시점의 값을 보관한다(이후 할일을 수정해도 기존 기록은 바뀌지 않음, 제목과 같은 정책)
- `DiaryListItemUiModel`은 일기 목록 전용 이름으로 쓴다. 기존 요약 모델은 `DiarySummaryUiModel`로 바꿔 일기 더보기·식물 상세가 같이 쓴다.
- 일기 더보기는 기존 디자인과 형식을 그대로 유지한다(물주기·할일 카드 없음). 기존 카드 컴포저블은 `diary_more/DiaryMoreListItem.kt`의 `DiaryMoreListItem`으로 옮기고, 기존 sealed interface는 `DiaryMoreListItemUiModel`로 이름을 바꾼다.
