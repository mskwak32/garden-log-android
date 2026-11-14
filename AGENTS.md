# AGENTS.md

## Project

Plant management & growth diary Android app.
Compose + Navigation 3 + MVI + Multi-module (Clean Architecture)

## Build

```bash
./gradlew assembleDebug          # build
./gradlew installDebug           # install
./gradlew test                   # unit tests
./gradlew :domain:test           # per-module test
./gradlew connectedAndroidTest   # instrumented tests
```

## Modules

```
app/              # Entry point, DI (UseCaseModule, RepositoryModule), alarm
domain/           # UseCase, Repository interfaces, Model (pure Kotlin)
data/             # RepositoryImpl, Entity↔Domain Mapper, DI: RepositoryModule
feature/plant/    # All screens, ViewModels, States (UI layer)
core/database/    # Room DB (Entity, DAO, Migration), DI: DatabaseModule
core/datastore/   # Preferences DataStore (sort orders, diary period unit), DI: DataStoreModule
core/remote/      # Retrofit/OkHttp
core/file/        # File management
core/pdf/         # PDF generation (diary export)
core/analytics/   # Firebase Analytics
core/common_ui/   # BaseViewModel, ViewState/Event/Effect, Theme, Icons, UI Components
```

**Data flow:** `Screen → ViewModel → UseCase → Repository(interface) → RepositoryImpl → DAO/API`

## BaseViewModel

Extend `BaseViewModel<UiState, Event, Effect>` from `core/common_ui`.
- `viewState: StateFlow<UiState>`, `setEvent(event)`, `effect: Flow<Effect>`

## DI

Hilt. `@HiltViewModel(assistedFactory = VM.Factory::class)` + `@AssistedInject constructor`.
- UseCase: `app/di/UseCaseModule.kt` (`@Provides`)
- Repository: `data/di/RepositoryModule.kt` (`@Binds`); alarm repository: `app/di/RepositoryModule.kt`
- DB: `core/database/di/DatabaseModule.kt`

## Navigation 3

**NavKey:** `...NavKey.kt`, `data object FooNavKey : NavKey` or `data class FooNavKey(val id: Int) : NavKey`

**ViewModel with NavKey:** Use `@AssistedInject` + `@Assisted navKey`. Do NOT use `SavedStateHandle`.
```kotlin
@AssistedFactory interface Factory { fun create(navKey: FooNavKey): FooViewModel }
```

**Screen:** No NavKey param, only ViewModel + navigate callback.

**NavGraph:**
```kotlin
entry<FooNavKey> {
    val viewModel = hiltViewModel<FooViewModel, FooViewModel.Factory>(
        creationCallback = { factory -> factory.create(it) }
    )
    FooScreen(viewModel = viewModel, navigate = { ... })
}
```
**hiltViewModel import:** `androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel`

## Screens

- **Plant tab:** PlantList → PlantDetail → PlantEdit / DiaryEdit / DiaryMore / DiaryExport
- **Diary tab:** DiaryList → DiaryDetail → DiaryEdit
- **Setting tab:** Setting → ExportedDiaryList
- **Shared:** DiaryDetail (from both Plant and Diary tabs)

## Tech Stack

Versions: `gradle/libs.versions.toml` (don't trust version numbers written elsewhere).

- Kotlin, JVM 17
- Jetpack Compose, Material 3
- Hilt, KSP
- Room (migrations required for schema changes)
- Navigation 3
- Retrofit, OkHttp, Kotlin Serialization
- Coil, Lottie
- Firebase (Analytics, Crashlytics — disabled in Debug)

## Release (Google Play)

Gradle Play Publisher. `publishReleaseBundle` creates a **completed production release**: Play sends it for review automatically and rolls it out to all users once approved. There is no draft step to stop it.

```bash
./gradlew bundleRelease          # signed .aab only (signing keys in local.properties)
./gradlew publishReleaseBundle   # build + release to Play production (sent for review)
```

- Release notes: edit `docs/release_note.xml` only. `syncPlayReleaseNotes` regenerates `app/src/main/play/release-notes/` (gitignored) before upload.
- Credentials: `keystore/play-service-account.json` (gitignored, never commit).
- **Never run `publish*` / `promote*` tasks on your own.** Run them only when the user explicitly asks to release in their latest message — not as part of other work, a verification step, or a previous approval.

## Conventions

- UI models: `feature/.../model/*UiModel.kt`
- Domain↔Entity mapping: `data/mapper/`
- Preview: `GardenLogTheme` from `core/common_ui`
- Versions: `gradle/libs.versions.toml`
- TopBar: extract as `private fun TopBar(...)`, not inline in Scaffold
- Dialogs: separate file `*Dialog.kt` per screen
- Strings: search `strings.xml` first; add new entry if missing — no hardcoding
- Comments: add for non-obvious logic (alarm branches, complex Flow combinations)

## Database

`garden.db` (Room v10) — `PlantEntity`, `DiaryEntity`, `PictureEntity`, `DiaryPictureCrossRef`, `WateringLogEntity`, `PlantTaskEntity`, `PlantTaskLogEntity`
Migrations: `core/database/migration/`, schema export enabled
