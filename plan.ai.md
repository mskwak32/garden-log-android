# 알림 클릭 → 식물 상세 화면 이동 구현 계획

## 목표

물주기 알림 / 할일 알림을 누르면 앱 첫 화면(PlantList)이 아니라 **해당 식물의 상세 화면(PlantDetail)** 으로 바로 이동한다.

## 현재 구조 (조사 결과)

| 위치 | 현재 동작 |
|---|---|
| `app/.../alarm/WateringAlarmReceiver.kt` | `plantId`로 알림 생성. `getPendingIntent()`는 extra 없이 `MainActivity`만 실행, requestCode `0` 고정, `NEW_TASK \| CLEAR_TASK` |
| `app/.../alarm/PlantTaskAlarmReceiver.kt` | `taskId`로 할일 조회 후 `task.plantId`를 이미 알고 있음. PendingIntent는 위와 동일 |
| `app/.../MainActivity.kt` | `onCreate`에서 `setContent { MainScreen(...) }`만 함. `onNewIntent` 처리 없음 |
| `app/.../ui/MainScreen.kt` | `rememberNavBackStack(PlantListNavKey)` 단일 백스택. 외부에서 백스택을 조작할 경로 없음 |
| `AndroidManifest.xml` | `MainActivity` launchMode 미지정(standard) |
| `feature/plant/.../PlantDetailNavKey.kt` | `data class PlantDetailNavKey(val plantId: Int)` — 그대로 재사용 가능 |
| `PlantDetailViewModel` | `getPlantUseCase(plantId).filterNotNull()` — 식물이 삭제된 경우 로딩 상태에서 멈출 수 있음 |

## Navigation 3에서의 딥링크 처리 방식

- Nav3에는 Nav2의 `navDeepLink { uriPattern = ... }` 같은 **내장 딥링크 매칭이 없다.** 백스택이 그냥 `NavKey` 리스트이므로 앱이 직접 처리한다.
- 공식 레시피(nav3-recipes의 deeplink 예제)의 흐름:
  1. Activity가 Intent(URI 또는 extra)를 받는다.
  2. Intent → `NavKey`로 직접 파싱한다. (예: `plantId` → `PlantDetailNavKey(plantId)`)
  3. 뒤로가기가 자연스럽도록 **합성 백스택**(부모 화면 포함)을 만들어 `rememberNavBackStack(...)` 초기값으로 넘기거나, 이미 떠 있는 백스택을 교체한다.
  4. 앱이 떠 있는 상태에서 받는 경우(`singleTop` + `onNewIntent`)에는 새 Intent를 Compose 쪽에 전달(State/Flow)해 백스택을 `clear()` 후 다시 채운다.
- 외부 URI 딥링크(웹 링크, `gardenlog://...`)가 필요하면 Manifest에 intent-filter를 추가하고 2단계에서 URI를 파싱하면 된다. **이번 작업은 앱 내부 알림이므로 명시적 Intent + extra로 충분**하고 intent-filter는 추가하지 않는다.
- 이 계획은 `CLEAR_TASK`로 항상 `onCreate`에서 진입하므로 3단계(초기 백스택 주입)만 사용한다 (D2, D3).

## 결정사항

### D1. 합성 백스택 → `[PlantList, PlantDetail(plantId)]` 고정
- `PlantList`를 반드시 맨 아래에 둔다. 이유:
  - `NavDisplay(onBack = { if (backStack.size > 1) ... })` — 백스택이 `[PlantDetail]` 하나면 뒤로가기가 화면 이동 없이 바로 앱 종료가 됨.
  - `BottomNavigationBar`의 탭 전환 로직이 "홈탭(PlantList)은 항상 백스택 맨 아래에 있다"는 전제로 작성되어 있음.
  - `BackPressFinish`(두 번 눌러 종료)가 `PlantListNavKey`일 때만 동작.
- `PlantDetail`의 부모는 화면 계층상 PlantList 하나뿐이라 그 이상 쌓지 않는다.

### D2. 기존 화면 기록 처리 → **버린다 (A안, 확정)**

| | A. `NEW_TASK \| CLEAR_TASK` 유지 (기록 버림) | B. `singleTop` + `onNewIntent`, 기록 유지 (push) | C. `singleTop` + `onNewIntent`, 백스택 교체 |
|---|---|---|---|
| 동작 | 항상 새 Activity `onCreate`, 백스택 `[PlantList, Detail]` | 앱이 떠 있으면 기존 백스택 위에 `Detail` push | 기존 Activity 재사용, 백스택을 `[PlantList, Detail]`로 교체 |
| 뒤로가기 | 상세 → 목록 → 종료 (항상 같음) | 상세 → 직전에 보던 화면 (설정, 일기 편집 등) | A와 같음 |
| 편집 중 내용 | 유실 | 보존 | 유실 |
| 구현량 | 가장 적음 (`onCreate`에서만 처리) | 많음: Intent를 Compose로 넘길 Flow, 같은 식물 중복 push 방지, 편집/다이얼로그 위에 쌓이는 경우 처리 | 중간: Intent 전달 Flow 필요 |
| 기타 | Activity 재생성으로 화면 전환 순간 깜빡임 가능, `MainViewModel`의 강제 업데이트 체크 재실행 | 백그라운드에 며칠 묵은 화면 위로 쌓여 뒤로가기 결과가 예측하기 어려움 | 깜빡임 없음. A 대비 장점은 이것뿐 |
- Android 알림 가이드도 "일반 화면으로 가는 알림은 앱 계층을 따라 뒤로가기가 되도록 새 태스크로 시작"하는 A안 방식을 권장.
- 알림을 누르는 시점에 다른 화면에서 편집 중일 확률이 낮아 유실 위험이 작다고 판단.
- B안이 필요해지면 `onNewIntent` → `MainViewModel`의 이벤트 Flow → `MainScreen`에서 `backStack.add()` 구조로 후속 작업.
- 참고: Nav2 시절의 `TaskStackBuilder`(부모 Activity들을 쌓는 방식)는 단일 Activity + Nav3 구조에선 의미가 없어 고려하지 않음.

### D3. 초기 백스택 주입 → `MainScreen(initialPlantId: Int?)`
- `MainActivity.onCreate`에서 extra를 읽어 전달, `MainScreen`에서 초기 키 목록을 먼저 만든 뒤 `rememberNavBackStack(*keys)`를 한 번만 호출.
  ```kotlin
  val initialKeys = buildList {
      add(PlantListNavKey)
      initialPlantId?.let { add(PlantDetailNavKey(it)) }
  }
  val backStack = rememberNavBackStack(*initialKeys.toTypedArray())
  ```
- `rememberNavBackStack`은 저장 가능한 상태라 **초기값은 처음 한 번만 쓰이고**, 회전·프로세스 재생성 시에는 저장된 백스택이 복원됨 → 중복 이동 없음.
- 예외: 앱을 뒤로가기로 완전히 종료한 뒤 **최근 앱 목록에서 다시 열면** 원래 알림 Intent가 그대로 다시 전달됨(저장 상태 없음). 이때 또 상세로 가지 않도록 `intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0`이면 extra를 무시한다.

### D4. Intent extra 키 / 생성 위치 → `MainActivity.companion`에 상수와 팩토리 함수
- `const val EXTRA_PLANT_ID = "extra_plant_id"`
- `fun createPlantDetailIntent(context, plantId, dataUri): Intent` — 두 Receiver가 공유. 중복된 `getPendingIntent()`를 이 함수 기반으로 교체.
- 별도 모듈/클래스 분리는 하지 않음 (사용처가 app 모듈 내 2곳뿐).

### D5. PendingIntent requestCode → 알림마다 고유값
- 현재 requestCode `0` + `FLAG_UPDATE_CURRENT` → extra가 달라도 **같은 PendingIntent로 덮어써져 모든 알림이 마지막 식물로 이동**하는 버그가 생김.
- 물주기: `requestCode = plantId`
- 할일: `requestCode = taskId`이면 물주기의 plantId와 충돌 가능(같은 Activity 대상) → 할일은 `TASK_REQUEST_CODE_OFFSET + taskId` 사용하거나, 더 확실하게는 Intent에 `data`/`identifier`를 다르게 설정.
  - **채택:** 두 Receiver 모두 Intent에 고유 `data` URI(`gardenlog://notification/watering/{plantId}`, `.../task/{taskId}`)를 설정해 PendingIntent 동일성 비교에서 구분되게 하고, requestCode는 각 ID 사용. (data는 intent-filter와 무관하게 명시적 Intent라 매칭 문제 없음)

### D6. 그룹 Summary 알림 클릭 → 현행 유지(앱 실행만)
- Summary는 여러 식물을 묶으므로 특정 식물로 갈 수 없음. contentIntent 없이 두면 시스템이 그룹을 펼치므로 현재처럼 둔다. (현재 Summary에는 contentIntent 미설정 — 그대로)

### D7. 할일 알림 → 식물 상세로 이동만 (확정)
- `PlantTaskAlarmReceiver`는 이미 `task.plantId`를 갖고 있으므로 `showNotification`에 `plantId` 파라미터만 추가.
- 해당 할일 카드로 스크롤·강조는 하지 않는다. 상세 화면이 길지 않아 효과가 작음.

### D8. 알림 이후 식물이 삭제된 경우 → 토스트 + 목록으로 (확정)
- `PlantDetailViewModel`이 `getPlantUseCase(plantId).filterNotNull()`이라 지금은 무한 로딩.
- `init`에서 `getPlantUseCase(plantId).first() == null`이면 `ShowSnackbar(R.string.message_plant_not_found)` + `Navigation.Back` 발행.
  - 기존 `ShowSnackbar` effect가 실제로는 `Toast`로 표시하므로 새 effect를 만들지 않고 재사용.
  - **첫 값만** 검사 → 상세 화면에서 직접 삭제할 때 흐르는 null과는 충돌하지 않음 (그 흐름은 기존 `filterNotNull()` 유지).
- `strings.xml`(ko/en)에 `message_plant_not_found` 추가 (예: "삭제된 식물이에요").

## 구현 단계

1. **MainActivity**
   - companion에 `EXTRA_PLANT_ID`, `EXTRA_TASK_ID`, `createPlantDetailIntent(context, plantId, dataUri)` 추가.
   - `onCreate`에서 extra 읽어 `MainScreen(initialPlantId)` 전달 (기본값 `-1` → null 변환, `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`면 무시).
2. **MainScreen**
   - 파라미터 추가, 초기 백스택 `[PlantListNavKey, PlantDetailNavKey(plantId)]` 구성.
   - `app` 모듈에서 `PlantDetailNavKey` import (feature:plant 의존성 이미 있음).
3. **WateringAlarmReceiver**
   - `getPendingIntent(context, plantId)`로 변경: 공통 팩토리 사용, requestCode = plantId, 고유 data URI.
4. **PlantTaskAlarmReceiver**
   - `showNotification`에 `plantId` 추가, `getPendingIntent(context, plantId, taskId)`로 변경.
5. **PlantDetailViewModel** (D8)
   - 첫 조회가 null이면 토스트(`ShowSnackbar` 재사용) + Back effect 발행, `strings.xml`에 문구 추가.
6. **확인**
   - 빌드: `./gradlew assembleDebug`
   - 수동 확인 시나리오 (필요한 것만):
     - 앱 종료 상태에서 물주기 알림 클릭 → 해당 식물 상세, 뒤로 → PlantList
     - 서로 다른 식물 알림 2개 → 각각 올바른 식물로 이동 (D5 검증)
     - 할일 알림 클릭 → 해당 식물 상세
     - 상세 진입 후 회전 → 백스택 유지, 중복 push 없음

## 영향 범위

- 수정: `MainActivity.kt`, `MainScreen.kt`, `WateringAlarmReceiver.kt`, `PlantTaskAlarmReceiver.kt`, `PlantDetailViewModel.kt`, `strings.xml`(ko/en)
- DB/도메인 변경 없음, Manifest 변경 없음.

## 열린 질문

- 없음. (후속 후보: 기존 화면 기록 유지(B안))
