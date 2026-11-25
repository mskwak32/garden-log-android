# 작업 계획: 알림에서 바로 완료

## 개요
- 목표: 물주기·할일 알림에 완료 액션 버튼을 넣어, 앱을 열지 않고 물주기 기록/할일 완료를 남긴다. (노션 "텃밭일기 작업관리 > 알림에서 바로 완료")
- 범위
  - 포함: 물주기 알림 "물주기 완료" 버튼, 할일 알림 "할일 완료" 버튼, 처리 후 알림 정리, 실패 시 알림 갱신, Analytics 기록, ko/en/ja 문자열
  - 제외: "나중에(미루기)" 액션 (별도 티켓), 앱에서 완료했을 때 떠 있는 알림 자동 제거
- 영향 범위
  - `app/alarm/` (`WateringAlarmReceiver`, `PlantTaskAlarmReceiver`, 신규 `NotificationActionReceiver`)
  - `app/src/main/AndroidManifest.xml`, `app/build.gradle.kts`
  - `app/src/main/res/values{,-en,-ja}/strings.xml`
  - `core/analytics/GardenEvent.kt`
  - DB 스키마 변경 없음, domain UseCase 변경 없음

## 결정 사항
- [확정] 알림 단위: 알림이 이미 식물별(`notify(plantId)`)·할일별(`notify("plantTask", taskId)`)로 따로 뜨고 그룹으로만 묶여 있으므로, 알림마다 버튼 하나. "모두 완료"는 만들지 않는다.
- [확정] 미루기 액션: 이번 범위에서 제외.
- [확정] 버튼 문구: 물주기 "물주기 완료"(en: Watered / ja: 水やり完了), 할일 "할일 완료"(en: Mark done / ja: タスク完了).
- [확정] 완료 처리 경로: 앱과 같은 UseCase 사용. 물주기 `WateringNowUseCase(plantId)`, 할일 `UpdatePlantTaskLogUseCase(taskId, LocalDate.now(), true)`. 두 UseCase가 기록 후 활성 알람을 재예약하므로 "앱에서 완료한 것과 같은 기록·다음 알람"이 자동으로 보장된다.
- [확정] 중복 방지: UseCase가 이미 같은 상태면 `UNCHANGED`를 반환(DB unique 제약 + 변경 여부 판단). `UNCHANGED`도 성공으로 보고 알림을 닫는다.
- [확정] 결과별 처리
  - `CHANGED`: 알림 닫기 + Analytics 기록
  - `UNCHANGED`: 알림 닫기 (Analytics 기록 안 함, 앱과 동일)
  - `ALARM_FAILED`: 기록은 저장됐으므로 알림 닫기 + Analytics 기록 + `Timber.e`
  - 예외(저장 실패): 같은 알림 ID로 갱신. 문구 "기록하지 못했어요. 눌러서 앱에서 다시 시도해 주세요", 액션 버튼 없음, 탭하면 기존과 같이 식물 상세로 이동. (액션 버튼은 알림을 자동으로 닫지 않으므로, 성공 시에만 코드에서 닫으면 실패 시 알림이 남아 사용자가 볼 수 있다.)
- [확정] 대상이 없어진 경우: 식물 삭제/수확됨, 할일 삭제됨이면 기록하지 않고 알림만 닫는다. (앱도 수확된 식물은 기록을 막음)
- [확정] 그룹 요약 알림: 자식 알림을 닫은 뒤 같은 그룹에 남은 알림이 없으면 요약 알림(물주기 1000 / 할일 2000)도 취소한다.
- [확정] 수신 구조: 별도 `NotificationActionReceiver`(exported=false) 신설. 알람 수신용 Receiver에 분기를 섞지 않는다.
- [확정] 비동기 처리: `goAsync()` + 코루틴, `finally`에서 `finish()`. WorkManager는 의존성이 없고 작업이 짧아 쓰지 않는다.
- [확정] 기록 날짜: `LocalDate.now()` (앱의 "물 주기"/오늘 할일 완료와 동일).
- [확정] Analytics: 기록함. `WateringSource.NOTIFICATION` 추가 → `WateringClick(NOTIFICATION)`, 할일은 기존 `CompleteTask(type.name.lowercase())`. app 모듈에 `core:analytics` 의존성 추가.
- [확정] UI: 목업 컨펌됨 (물주기/할일 알림에 액션 버튼 1개 추가, 성공 시 알림 사라짐, 실패 시 같은 알림이 버튼 없는 실패 문구로 바뀜)

## 작업 분할
실제 작업은 `token-saver` 스킬 방식으로 진행한다. 메인 세션은 판단·검증, 구현은 Worker 서브 에이전트가 맡는다.

| # | 작업 | 대상 파일/모듈 | 선행 작업 | 병렬 가능 |
|---|------|----------------|-----------|-----------|
| 1 | 문자열 추가 (ko/en/ja) | `app/src/main/res/values{,-en,-ja}/strings.xml` | - | O (#2와) |
| 2 | Analytics 이벤트 소스 추가 + app 의존성 | `core/analytics/.../GardenEvent.kt`, `app/build.gradle.kts` | - | O (#1과) |
| 3 | 알림 액션 추가 + 액션 Receiver 구현 + Manifest 등록 | `app/.../alarm/*`, `AndroidManifest.xml` | #1, #2 | X |
| 4 | 빌드·테스트 검증 | - | #3 | X |

### 구현 방식
- **#1** 문자열 키: `noti_action_watering_done`(물주기 완료 / Watered / 水やり完了), `noti_action_task_done`(할일 완료 / Mark done / タスク完了), `noti_record_failed`(기록하지 못했어요. 눌러서 앱에서 다시 시도해 주세요 / Couldn't save it. Tap to try again in the app. / 記録できませんでした。タップしてアプリで再度お試しください). 기존 `noti_*` 항목 옆에 둔다.
- **#2** `WateringSource`에 `NOTIFICATION` 추가(로거는 `name.lowercase()`라 변경 불필요). `app/build.gradle.kts`에 `implementation(project(":core:analytics"))` 추가.
- **#3**
  - `NotificationActionReceiver`(@AndroidEntryPoint): action `ACTION_COMPLETE_WATERING`(extra plantId), `ACTION_COMPLETE_TASK`(extra taskId). 주입: `WateringNowUseCase`, `UpdatePlantTaskLogUseCase`, `PlantRepository`, `PlantTaskRepository`, `AnalyticsLogger`. 결과별 처리는 결정 사항 표대로.
  - 두 알람 Receiver의 `showNotification`에 `addAction(0, 문자열, PendingIntent.getBroadcast(...))` 추가. requestCode는 plantId/taskId, 고유 data URI(`gardenlog://action/watering/$id`, `gardenlog://action/task/$id`)로 PendingIntent 덮어쓰기 방지, `FLAG_IMMUTABLE`.
  - 알림 ID·tag·채널·그룹 키·content PendingIntent 생성 함수는 각 알람 Receiver companion에 `internal`로 열고, 실패 알림 빌더(`buildRecordFailedNotification` 등)도 각 Receiver 쪽에 둬서 액션 Receiver가 같은 알림 ID로 갱신·취소한다. 그룹 잔여 확인은 `NotificationManager.activeNotifications`를 group 키로 필터링.
  - Manifest에 `.alarm.NotificationActionReceiver` (exported=false) 등록.
  - 비자명한 분기(UNCHANGED도 닫기, ALARM_FAILED는 성공 취급, 요약 알림 정리)에 짧은 한국어 주석.

### 서브 에이전트 공통 지시
- AGENTS.md 컨벤션 준수: 문자열 하드코딩 금지, 비자명한 로직에 한국어 주석(`code-comment-style`).
- domain UseCase, DB(Entity/DAO/Migration)는 수정하지 않는다.
- 맡은 파일 외에는 수정하지 않는다. 커밋하지 않는다.
- 검증: `./gradlew assembleDebug` 통과 여부와 변경 파일 목록을 보고.

## 단위 테스트
- 추가하지 않는다. 기록·알람 재예약 로직은 기존 UseCase를 그대로 재사용하고(`SetPlantTaskAlarmUseCaseTest` 등 기존 테스트 존재), 새 코드는 BroadcastReceiver·NotificationManager 같은 프레임워크 연결부라 단위 테스트 가치가 낮다.

## 완료 기준
- [x] 빌드 성공 — 확인 방법: `./gradlew assembleDebug`
- [x] 기존 단위 테스트 통과 — 확인 방법: `./gradlew test`
- [x] 결과별 처리(CHANGED/UNCHANGED/ALARM_FAILED/예외/대상 없음)가 결정 사항대로 구현됨 — 확인 방법: 메인 세션 코드 리뷰
- [x] 액션 PendingIntent가 식물·할일별로 고유(requestCode + data URI)하고 알람 PendingIntent와 겹치지 않음 — 확인 방법: 코드 리뷰
- [x] 문자열 ko/en/ja 3종 모두 존재 — 확인 방법: 각 `strings.xml`에서 3개 키 grep
- [ ] (미확인 — 실기기 테스트는 사용자 결정으로 생략) 기기 확인 — 확인 절차:
  1. 물주기 주기 1일·알람 시각을 1~2분 뒤로 둔 식물을 만들고 알림을 기다린다 → "물주기 완료" 버튼이 보인다.
  2. 버튼을 누른다 → 알림이 닫히고, 앱의 식물 상세에 오늘 물주기 기록이 있으며 다음 알람이 앱에서 "물 주기"를 누른 경우와 같은 시각이다.
  3. 할일 알림도 같은 방식으로 "할일 완료" 확인(반복 할일은 다음 회차 알람, 1회성은 알람 없음).
  4. 앱에서 오늘 이미 기록한 뒤 알림 버튼을 누른다 → 알림만 닫히고 기록 중복 없음.
