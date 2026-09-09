# Project Instructions

## Working Rules

- Before changing behavior, read the relevant local code and follow its established patterns.
- Keep changes scoped; preserve existing user changes and avoid unrelated refactors or formatting-only noise.
- Do not create Git commits unless the user explicitly asks for one.
- For behavior changes, add focused tests beside the code they cover in the matching package structure.
- Add or update dependencies through `gradle/libs.versions.toml`; module build files reference them with `libs.*` rather than hard-coding versions.
- Keep repository-level Gradle configuration and module structure unchanged unless the task requires a change.
- When editing files containing non-ASCII text, especially Android resources and Markdown, use an explicit UTF-8 read/write path and verify the result contains neither replacement characters nor mojibake.
- FFmpeg functionality comes from Local aar. For SDK usage, refer to the official Android docs: https://github.com/arthenica/ffmpeg-kit-next/tree/main/android.

## Architecture and Dependency Injection

- This is a single-module Android Compose application. Keep UI → ViewModel → Repo → Store dependencies one-way.
- Composables own short-lived interaction state and render state; ViewModels own business state and application logic. Repos encapsulate domain-facing data operations. Stores encapsulate local platform storage.
- Use constructor injection. Bind concrete implementations only in `di/`; UI, ViewModels, and Repos depend on interfaces rather than constructing implementations directly.
- Start Koin in the `Application` class. Use Koin `single` definitions for application-wide dependencies and `viewModel` definitions for ViewModels. Compose retrieves ViewModels with `koinViewModel()`; do not add custom ViewModel factories for dependencies managed by Koin.
- Name repository interfaces `XxxRepo` and implementations `XxxRepository`. Name local-storage interfaces `XxxStore` and implementations `XxxStorage`.
- Keep UI state focused and cohesive. Split state when fields have independent load, save, or error lifecycles; keep fields that change together in the same state.
- ViewModel state uses Kotlin explicit backing fields: expose a single `val uiState: StateFlow<XxxUiState>` initialized with `field = MutableStateFlow(...)`, mutating it internally through the field's mutable type, rather than declaring a separate `_uiState` backing property plus a public `uiState`.

## Compose UI and Navigation

- Navigation 3 routes and destinations are registered centrally in `nav/MainApp.kt`.
- `XxxPage` is a navigation destination and has its own file under `page/`. When a feature grows beyond one page or gains substantial regions, group it under `page/<feature>/`.
- `XxxScreen` is a substantial independent region within a Page, such as a tab, sheet, pager section, or loading state; place it in the feature's `screen/` directory.
- For new widgets, keep Page- or Screen-local widgets nearby and normally `private`; place widgets shared across Pages in `component/`.
- Promote code to a shared package only after two or more features use it. Feature packages do not depend on each other.
- Put user-facing or localizable text in string resources. Reuse the existing theme and resource definitions for colors, dimensions, and drawables rather than scattering reusable visual values through Composables.

## Verification

- Before reporting a change, run `git diff --check`.
- After changing code, run the platform-appropriate check command from the repository root and report the result:
  - Windows: `.\gradlew.bat check`
  - Linux/macOS/other Unix-like systems: `bash gradlew check`
- For documentation-only changes, run `git diff --check` and review UTF-8 encoding and formatting; a Gradle check is not required.
- For higher-risk visual UI changes, validate with a Preview or emulator when available. If any planned validation is skipped, state what was skipped and why.

## Agent skills

### Issue tracker

Issues are tracked in this repo's GitHub Issues via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Uses the default five canonical triage labels (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context layout: `CONTEXT.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.
