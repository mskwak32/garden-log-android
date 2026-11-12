# 일기 탭 월별/연도별 보기 + 정렬 순서 유지 계획

> 상태: 계획 단계. **구현은 사용자가 "구현해줘"라고 할 때 시작한다.**
> `[결정]`은 사용자 확인 완료, `[제안]`은 기본안으로 진행할 예정이지만 확인이 필요한 항목.
> 이전 계획(식물별 할일 일정)은 git 이력(`d105cef`)에 있다.

## 1. 목표

- 일기 탭(`DiaryListScreen`)에서 **월별 / 연도별** 보기를 선택한다. [결정]
- 아래 값은 앱을 재실행해도 유지한다. [결정]
  - 일기 탭 보기 단위 (월별/연도별)
  - 일기 탭 정렬 순서 (`DiaryListSortOrder`)
  - 홈(식물 목록) 정렬 순서 (`PlantListSortOrder`)
- 일기 탭 TopBar의 화살표·날짜 텍스트·"이번달" 버튼의 시각 크기를 줄인다. 클릭 영역은 유지한다.

## 2. 현황

- DataStore, SharedPreferences 모두 없다. 위 세 값은 모두 메모리 상태라 재실행 시 초기화된다.
  - 일기 탭: `DiaryListState.sortOder` (기본 `CREATED_LATEST`)
  - 홈: `PlantListViewModel._sortOrder: MutableStateFlow` (기본 `CREATED_LATEST`)
- 일기 조회 DAO는 이미 `startDate ~ endDate`로 조회한다. **DB 스키마·마이그레이션 변경 없음.**
- 외부 저장소 접근 패턴 참고: `core/remote`의 `AppConfigRemoteSource`(인터페이스) → `source_impl/` → `data`의 `AppConfigRepositoryImpl`이 주입받아 사용. DataStore도 같은 패턴으로 만든다.

## 3. 화면

### 3.1 토글 위치 [제안]

정렬 필터 줄 start에 세그먼트 토글 `[월별│연도별]`, end에 기존 정렬 드롭다운.

- 지금 비어 있는 공간을 쓴다.
- 단위·정렬 같은 "보기 방식"을 한 줄에 모은다. TopBar는 기간 이동만 담당한다.
- 선택지가 2개라 드롭다운보다 한 번 덜 누른다.
- TopBar start 배치로 바꿔도 상태/이벤트는 같고 Composable 위치만 다르다.

### 3.2 TopBar 크기 조정 [제안]

| 요소 | 현재 | 변경 |
|---|---|---|
| ◀ ▶ | 아이콘 30dp = 클릭 영역 30dp | **클릭 영역 30dp 유지**, 안의 아이콘만 20dp (`Box(Modifier.size(30.dp).clip().clickable(), contentAlignment = Center) { Icon(Modifier.size(20.dp)) }`) |
| 기간 텍스트 | `titleLarge` 22sp, 클릭 영역 = 텍스트 크기 | `titleMedium` 16sp + `SemiBold`, `padding(horizontal = 8.dp, vertical = 6.dp)`로 클릭 영역 확보 |
| 이번달/올해 | `titleMedium` 16sp + `padding(10.dp)` | `labelLarge` 14sp, 패딩 유지 |
| 화살표 ↔ 텍스트 간격 | `Spacer(16.dp)` | `Spacer(8.dp)` (텍스트에 패딩이 생긴 만큼 축소) |

> Material 권장 최소 터치 영역은 48dp이지만, 현재 30dp로 사용 중이고 문제가 보고된 적 없으므로 현재 크기를 유지한다.

### 3.3 연도별 모드

- 타이틀 `2026-11` → `2026년` (`year_format` 재사용)
- ◀ ▶: 이전/다음 연도
- 우측 버튼: "이번달" → "올해"
- 타이틀 탭: 연도 선택 다이얼로그
- 목록: 월 구분 헤더(`11월`) [제안]

### 3.4 연도 선택 다이얼로그 [결정]

기존 `core/common_ui/dialog/SelectYearMonthDialog`에 파라미터를 추가한다.

```kotlin
@Composable
fun SelectYearMonthDialog(
    initialYearMonth: YearMonth,
    showMonth: Boolean = true,          // false면 연도 드롭다운만 표시
    onDismiss: () -> Unit,
    onConfirm: (YearMonth) -> Unit      // showMonth=false면 월은 initialYearMonth의 월 유지
)
```

- 제목: `showMonth`에 따라 `select_year_month` / `select_year`(신규)
- `showMonth = false`면 월 `YearMonthDropdown`과 사이 `Spacer`를 그리지 않는다.
- 기존 호출처는 기본값 `true`라 변경 없음.

## 4. 구조

### 4.1 모듈 의존 관계

```
feature/plant ──▶ domain ◀── data ──▶ core/datastore (신규)
                    ▲                  core/database
app (UseCaseModule) ┘                  core/remote ...
```

- `core/datastore`는 domain을 모른다. 문자열로 저장하고, enum 변환은 `data`에서 한다. (`core/database`와 같은 규칙)

### 4.2 core/datastore (신규 모듈)

```
core/datastore/
  build.gradle.kts
  src/main/java/com/mskwak/datastore/
    UserPreferencesLocalSource.kt              # 인터페이스
    source_impl/UserPreferencesLocalSourceImpl.kt
    di/DataStoreModule.kt
```

```kotlin
interface UserPreferencesLocalSource {
    val plantListSortOrder: Flow<String?>
    val diaryListSortOrder: Flow<String?>
    val diaryListPeriodUnit: Flow<String?>

    suspend fun setPlantListSortOrder(value: String)
    suspend fun setDiaryListSortOrder(value: String)
    suspend fun setDiaryListPeriodUnit(value: String)
}

internal class UserPreferencesLocalSourceImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : UserPreferencesLocalSource {
    override val diaryListPeriodUnit = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[Keys.DIARY_LIST_PERIOD_UNIT] }
    // ... 나머지 동일 패턴

    override suspend fun setDiaryListPeriodUnit(value: String) {
        dataStore.edit { it[Keys.DIARY_LIST_PERIOD_UNIT] = value }
    }

    private object Keys {
        val PLANT_LIST_SORT_ORDER = stringPreferencesKey("plant_list_sort_order")
        val DIARY_LIST_SORT_ORDER = stringPreferencesKey("diary_list_sort_order")
        val DIARY_LIST_PERIOD_UNIT = stringPreferencesKey("diary_list_period_unit")
    }
}
```

```kotlin
@Module
@InstallIn(SingletonComponent::class)
internal object DataStoreModule {
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("user_preferences") }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataStoreSourceModule {
    @Binds @Singleton
    abstract fun bindUserPreferencesLocalSource(impl: UserPreferencesLocalSourceImpl): UserPreferencesLocalSource
}
```

- `libs.versions.toml`: `androidx.datastore:datastore-preferences` 추가 (구현 시점 최신 stable 확인)
- `settings.gradle.kts`: `include(":core:datastore")`
- `data/build.gradle.kts`: `implementation(project(":core:datastore"))`

### 4.3 domain

```kotlin
// model/DiaryListPeriodUnit.kt
enum class DiaryListPeriodUnit { MONTH, YEAR }

// model/DiaryListViewOption.kt — 일기 탭은 두 값을 항상 같이 쓰므로 묶어서 내보낸다
data class DiaryListViewOption(
    val periodUnit: DiaryListPeriodUnit,
    val sortOrder: DiaryListSortOrder
)

// repository/UserPreferenceRepository.kt
interface UserPreferenceRepository {
    val plantListSortOrder: Flow<PlantListSortOrder>
    val diaryListViewOption: Flow<DiaryListViewOption>

    suspend fun setPlantListSortOrder(order: PlantListSortOrder)
    suspend fun setDiaryListSortOrder(order: DiaryListSortOrder)
    suspend fun setDiaryListPeriodUnit(unit: DiaryListPeriodUnit)
}
```

UseCase (`usecase/preference/`)

| UseCase | 반환 |
|---|---|
| `GetPlantListSortOrderUseCase` | `Flow<PlantListSortOrder>` |
| `SetPlantListSortOrderUseCase` | `suspend` |
| `GetDiaryListViewOptionUseCase` | `Flow<DiaryListViewOption>` |
| `SetDiaryListSortOrderUseCase` | `suspend` |
| `SetDiaryListPeriodUnitUseCase` | `suspend` |

### 4.4 data

```kotlin
internal class UserPreferenceRepositoryImpl @Inject constructor(
    private val localSource: UserPreferencesLocalSource
) : UserPreferenceRepository {

    override val plantListSortOrder = localSource.plantListSortOrder
        .map { it.toEnumOrDefault(PlantListSortOrder.CREATED_LATEST) }
        .distinctUntilChanged()

    // 두 키를 combine — 다른 키(홈 정렬)가 바뀌어도 재방출되지 않도록 distinctUntilChanged
    override val diaryListViewOption = combine(
        localSource.diaryListPeriodUnit.map { it.toEnumOrDefault(DiaryListPeriodUnit.MONTH) },
        localSource.diaryListSortOrder.map { it.toEnumOrDefault(DiaryListSortOrder.CREATED_LATEST) },
        ::DiaryListViewOption
    ).distinctUntilChanged()

    override suspend fun setDiaryListPeriodUnit(unit: DiaryListPeriodUnit) =
        localSource.setDiaryListPeriodUnit(unit.name)
    // ...
}

// 저장값이 없거나 enum 이름이 바뀌어 매칭되지 않으면 기본값
private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T =
    this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default
```

- `data/di/RepositoryModule.kt`에 `bindUserPreferenceRepository` 추가
- `app/di/UseCaseModule.kt`에 UseCase 5개 `@Provides`

### 4.5 월 → 기간 계산 이동

**변경 전**

```kotlin
// ViewModel
getDiariesUseCase(year = ym.year, month = ym.monthValue, plantId = ..., sortOder = ...)

// GetDiariesUseCase
operator fun invoke(year: Int, month: Int, sortOder: DiaryListSortOrder, plantId: Int? = null) =
    diaryRepository.getDiaries(year, month, plantId).map { it.applySort(sortOder) }

// DiaryRepository
fun getDiaries(year: Int, month: Int, plantId: Int?): Flow<List<Diary>>

// DiaryRepositoryImpl — 기간 계산이 여기 있음
override fun getDiaries(year: Int, month: Int, plantId: Int?): Flow<List<Diary>> {
    val startDate = LocalDate.of(year, month, 1)
    val endDate = startDate.withDayOfMonth(startDate.lengthOfMonth())
    ...
}
```

**변경 후**

```kotlin
// ViewModel — 단위와 기준 연월만 넘긴다
getDiariesUseCase(
    periodUnit = option.periodUnit,
    yearMonth = state.currentYearMonth,
    sortOrder = option.sortOrder,
    plantId = state.currentPlant?.plantId
)

// GetDiariesUseCase — 기간 계산 담당
operator fun invoke(
    periodUnit: DiaryListPeriodUnit,
    yearMonth: YearMonth,
    sortOrder: DiaryListSortOrder,
    plantId: Int? = null
): Flow<List<Diary>> {
    val (startDate, endDate) = periodRange(periodUnit, yearMonth)
    return diaryRepository.getDiaries(startDate, endDate, plantId)
        .map { it.applySort(sortOrder) }
        .flowOn(Dispatchers.IO)
}

private fun periodRange(unit: DiaryListPeriodUnit, ym: YearMonth): Pair<LocalDate, LocalDate> =
    when (unit) {
        DiaryListPeriodUnit.MONTH -> ym.atDay(1) to ym.atEndOfMonth()
        DiaryListPeriodUnit.YEAR -> LocalDate.of(ym.year, 1, 1) to LocalDate.of(ym.year, 12, 31)
    }

// DiaryRepository
fun getDiaries(startDate: LocalDate, endDate: LocalDate, plantId: Int?): Flow<List<Diary>>

// DiaryRepositoryImpl — 받은 기간을 DAO에 그대로 전달
override fun getDiaries(startDate: LocalDate, endDate: LocalDate, plantId: Int?): Flow<List<Diary>> {
    val diariesFlow = if (plantId == null) {
        diaryDao.getDiaries(startDate, endDate)
    } else {
        diaryDao.getDiariesByPlantId(plantId, startDate, endDate)
    }
    ... // 이하 기존과 동일
}
```

- 이유: "한 달/한 해가 언제부터 언제까지인가"는 비즈니스 규칙이라 domain에 둔다. Repository는 기간만 받아 조회한다.
- `DiaryRepository.getDiaries` 호출처는 `GetDiariesUseCase`뿐 (구현 시 grep 재확인).

### 4.6 데이터 흐름

**읽기 (앱 시작 / 값 변경 시)**

```
DataStore<Preferences>.data
  → UserPreferencesLocalSourceImpl   (String?)
  → UserPreferenceRepositoryImpl     (enum, 기본값 처리, combine)
  → GetDiaryListViewOptionUseCase    (Flow<DiaryListViewOption>)
  → DiaryListViewModel               setState { copy(viewOption = it) }
  → DiaryListState.viewOption        → 토글·정렬·TopBar 표시
                                     → observeDiaries()가 GetDiariesUseCase 재조회
```

**쓰기 (사용자가 토글/정렬 변경)**

```
Screen → setEvent(PeriodUnitChanged(YEAR))
  → ViewModel: viewModelScope.launch { setDiaryListPeriodUnitUseCase(YEAR) }
  → Repository → LocalSource → dataStore.edit { }
  → DataStore가 새 값을 방출 → 위 "읽기" 흐름으로 State 갱신
```

- **단방향:** 이벤트에서 `setState`로 단위/정렬을 직접 바꾸지 않는다. DataStore가 유일한 출처다.
- 홈 정렬도 같은 흐름: `PlantListViewModel._sortOrder: MutableStateFlow` → `getPlantListSortOrderUseCase()` Flow로 교체, `OnSortChanged`는 `setPlantListSortOrderUseCase` 호출.

### 4.7 feature/plant

**DiaryListState**

```kotlin
data class DiaryListState(
    val viewOption: DiaryListViewOption? = null, // null = DataStore 첫 값 받기 전
    val currentYearMonth: YearMonth = YearMonth.now(),
    val currentPlant: DiaryListPlantFilterUiModel? = null,
    val plantFilterList: List<DiaryListPlantFilterUiModel> = emptyList(),
    val diaries: List<DiaryListItemUiModel> = emptyList()
)
```

- 기존 `sortOder` 필드는 `viewOption.sortOrder`로 대체
- `viewOption == null`이면 조회를 시작하지 않는다 → 기본값(월별)으로 한 번 그렸다가 연도별로 바뀌는 깜빡임 방지
- 연도 모드도 `currentYearMonth`를 기준점으로 쓴다. 연도 모드의 ◀ ▶는 `±1년`, 월 모드로 돌아오면 기준점의 월을 보여준다.
  - 예: 2026-11(월) → 연도 전환 → ◀ 2025년 → 월 전환 → 2025-11

**Event / Effect**

| 기존 | 변경 |
|---|---|
| `OnPreviousMonthClicked` / `OnNextMonthClicked` / `OnThisMonthClicked` | `OnPreviousClicked` / `OnNextClicked` / `OnCurrentClicked` — 단위에 따라 월/연 이동 |
| `SelectMonth` | `SelectPeriod` |
| `ShowSelectYearMonth(yearMonth)` | `ShowSelectYearMonth(yearMonth, showMonth)` |
| `SortOderChanged` | 유지, ViewModel에서 `SetDiaryListSortOrderUseCase` 호출 |
| - | `PeriodUnitChanged(unit)` → `SetDiaryListPeriodUnitUseCase` |

**DiaryListViewModel.observeDiaries()**

```kotlin
viewState
    .map { DiaryQuery(it.viewOption, it.currentYearMonth, it.currentPlant?.plantId) }
    .distinctUntilChanged()
    .filter { it.viewOption != null }
    .flatMapLatest { q -> getDiariesUseCase(q.viewOption!!.periodUnit, q.yearMonth, q.viewOption.sortOrder, q.plantId) }
```

**PlantListViewModel**

```kotlin
// 변경 전
private val _sortOrder = MutableStateFlow(PlantListSortOrder.CREATED_LATEST)
is OnSortChanged -> _sortOrder.value = event.sortOrder

// 변경 후
private val sortOrder = getPlantListSortOrderUseCase()
is OnSortChanged -> viewModelScope.launch { setPlantListSortOrderUseCase(event.sortOrder) }
// observePlants()의 combine에서 _sortOrder → sortOrder
```

- 홈은 combine이 DataStore 첫 값을 받기 전까지 방출하지 않으므로 별도 null 처리 불필요.

**Screen**

- `TopBar(...)`: 3.2 크기 조정, 단위별 텍스트·contentDescription 분기
- 새 파일 `DiaryListPeriodUnitToggle.kt` (Preview 포함)
- `DiaryListSortFilter` 줄을 `Row(SpaceBetween)`로 바꿔 start에 토글, end에 정렬
- 연도 모드 월 헤더: `diaries.groupBy { YearMonth.from(it.date) }` → 헤더 `item` + `items`

### 4.8 strings (ko / en / ja)

| key | 모듈 | ko | en | ja |
|---|---|---|---|---|
| `period_unit_month` | feature/plant | 월별 | Monthly | 月別 |
| `period_unit_year` | feature/plant | 연도별 | Yearly | 年別 |
| `this_year` | feature/plant | 올해 | This Year | 今年 |
| `previous_year` | feature/plant | 이전 연도 | Previous Year | 前年 |
| `next_year` | feature/plant | 다음 연도 | Next Year | 翌年 |
| `select_year` | core/common_ui | 연도 선택 | Select Year | 年を選択 |

## 5. 구현 순서

1. `core/datastore` 모듈 + 버전 카탈로그 + DI
2. domain: `DiaryListPeriodUnit`, `DiaryListViewOption`, `UserPreferenceRepository`, UseCase 5개
3. domain/data: `GetDiariesUseCase`·`DiaryRepository`·`DiaryRepositoryImpl` 기간 시그니처 변경
4. data: `UserPreferenceRepositoryImpl`, `@Binds` / app: `UseCaseModule`
5. 홈 `PlantListViewModel` 정렬 유지
6. 일기 탭 State / Event / ViewModel
7. TopBar 크기 조정 + 단위별 분기
8. 토글 Composable + 정렬 줄 배치
9. `SelectYearMonthDialog`에 `showMonth` 추가
10. 연도 모드 월 헤더
11. strings 3개 언어
12. 빌드 + 수동 확인 (재실행 후 3개 값 유지, 월↔연 전환 기준점, 빈 목록)

## 6. 테스트 [제안]

- `GetDiariesUseCase` 기간 계산: 월(30/31일, 윤년 2월), 연(1/1~12/31) — Repository를 fake로 두고 전달된 기간 검증
- `toEnumOrDefault`: 값 없음 / 알 수 없는 이름 → 기본값
- DataStore 자체, Compose UI는 테스트하지 않는다.
