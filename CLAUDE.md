# Bandy — Claude Code Guidelines

## Проект
`Bandy`

## Сборка
Сборка и запуск выполняются вручную через IDE/локальные процессы.
Claude не должен запускать сборку/рантайм команды без явного запроса.

## Профиль проекта
- **Тип**: Android (mobile)
- **Язык**: Kotlin
- **UI**: Jetpack Compose (Material3)
- **Архитектура**: MVI (`MVIBaseViewModel<State, Action, Event>`) + Clean Architecture
- **DI**: Koin
- **Навигация**: Compose Navigation, `NavGraphBuilder`-экстеншены, единый `NavigationState`
- **Сеть**: `bandy-network` (Ktor/Retrofit-клиент, обёрнутый в `safeRequest { }`)
- **Хранение**: `bandy-data-storage` (DataStore/Room)
- **Аналитика**: `bandy-analytics` (обёртка над SDK, события — отдельные классы)
- **Медиа**: `bandy-player` (ExoPlayer, опционально)
- **Flavors**: под разные дистрибуции/маркеты (например gms / hms / rustore)
- **CI**: GitLab CI / GitHub Actions

## Архитектурные слои
```
UI (Compose) → ViewModel (MVI) → UseCase (interface) → UseCaseImpl
    → Repository (interface) → RepositoryImpl → DataSource → safeRequest { client.get(...) }
```
Каждый слой знает только о слое ниже через интерфейс, маппинг моделей — на границах слоёв.

## Модульная структура

### Core-модули
| Модуль | Ответственность |
|--------|----------------|
| `bandy-compose` | Базовый `MVIBaseViewModel`, общие Compose-компоненты, дизайн-система |
| `bandy-data` | Общие модели данных, мапперы |
| `bandy-data-storage` | Локальное хранилище |
| `bandy-domain` | Доменные модели, общие контракты |
| `bandy-network` | HTTP-клиент, `safeRequest`, DTO-инфраструктура |
| `bandy-navigation` | `NavigationState`, константы роутов |
| `bandy-player` | Видео/аудио-плеер (опционально) |
| `bandy-utils` | Android-утилиты |
| `bandy-kotlin-utils` | Kotlin-утилиты |
| `bandy-analytics` | Обёртка над SDK аналитики |

### Feature-модули
`feature:<name>` — каждый со своим Koin-модулем, экранами, ViewModel, use case'ами.
Изолированы друг от друга, общаются только через `domain`/`navigation`/`compose`.

## Паттерны кода

### MVI ViewModel
```kotlin
internal class XxxViewModel(
    private val getXxxUseCase: GetXxxUseCase,
) : MVIBaseViewModel<XxxScreenState, XxxScreenAction, XxxScreenEvent>(
    initialState = XxxScreenState()
) {
    override fun obtainEvent(viewEvent: XxxScreenEvent) { ... }
}
```

### Screen (Compose)
```kotlin
@Composable
fun XxxScreen(navigationState: NavigationState) {
    val viewModel = koinViewModel<XxxViewModel>()
    val state by viewModel.getViewState().collectAsStateWithLifecycle()
    CollectActions(viewModel) { action -> ... }
    XxxScreenContent(state = state, onEvent = viewModel::obtainEvent)
}
```

### DI (Koin)
```kotlin
val xxxModule = module {
    factoryOf(::XxxMapper)
    singleOf(::XxxUseCaseImpl) bind XxxUseCase::class
    viewModelOf(::XxxViewModel)
}
```

### Navigation
```kotlin
fun NavGraphBuilder.xxxScreen(navigationState: NavigationState) {
    composable(route = NavigationConstants.Route.XXX) { XxxScreen(navigationState) }
}
```

### Data flow
UseCase (interface) -> UseCaseImpl -> Repository (interface) -> RepositoryImpl -> DataSource -> safeRequest { client.get(...) }

## Принципы
1. Интерфейс + impl на каждом Repository / DataSource / UseCase — для тестируемости и DI
2. Явные мапперы на границах слоёв (Dto → Entity → Ui-модель)
3. ViewModel не знает о Android Framework напрямую (кроме Compose lifecycle-хелперов)
4. Feature-модули не зависят друг от друга напрямую, только через `domain`/`navigation`/`compose`
5. Один PR/коммит — одна логическая единица (таск), маленькие итерации
6. Не добавлять абстракции/фичи сверх того, что требует текущая задача
