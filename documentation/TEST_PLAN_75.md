# GoSTalk - Test Coverage Plan (75% Branch Goal)

**Timestamp:** 2026-03-06T14:28:00+01:00  
**Git Commit Hash:** 9e99afda1cd69b2cc94779f712b39d412a68e8bc

## Objective
Increase branch test coverage across all business logic packages (`core`, `domain`, `data`, `util`) to at least 75%.

---

## 1. Domain: Generative AI (`domain.genai`)
The AI layer is currently the most complex part with the least coverage.

### [ ] `GeminiUseCaseTest`
- **Error Handling**: Test `generateResponse` behavior when `oauthTokenProvider` returns null.
- **Rate Limiting**: Verify `lockoutUntilTime` logic and the exception message for HTTP 429.
- **Model Selection**: Mock `listModels` to return various scenarios (empty, multiple valid, only vision) and verify model selection priority.
- **Function Calling**:
    - Test successful function call routing to `handleFunctionCall`.
    - Test multi-turn interaction (max 5 turns) logic.
    - Test fallback to another model on 404/429.
- **Wait Time Parsing**: Test `parseWaitTime` with different `Retry-After` headers and error body regex patterns.

---

## 2. Domain: Execution Layer (`domain.executors`)
Currently at 60% method coverage - needs deep branch testing.

### [ ] `ActionExecutorTest` (Branches)
- **Long Press**: Verify distinction between short and long press triggers.
- **Blocking**: Verify that `isExecuting` correctly guards against overlapping actions.
- **Failures**: Mock failures in handlers and verify the error logging/handling.

---

## 3. UI: ViewModels (`ui.templates`)
Focus on the business logic within ViewModels, as Compose code is often excluded from unit test branch coverage.

### [ ] `TemplateViewModelTest`
- **Filtering/Sorting**: Verify that the `templates` flow correctly filters by search query and respects different `SortOrder` values.
- **Initialization**: Verify that `ensureBuiltInTemplates` is called in `init`.
- **Navigation**: Verify delegation of `delete`, `reorder`, and `create` to use cases.

---

## 4. Core: Utilities & Infrastructure
### [ ] `LoggerTest`
- Test log levels and formatting logic.
### [ ] `SettingsRepositoryTest`
- Test the interaction between `Preferences` flows and the resulting `StateFlow` values (e.g., multiplier calculations).

---

## 5. Data: Repositories
Expanding on Phase 3 to cover all remaining data bridges.
### [ ] `ButtonUsageRepositoryTest`
- Test usage count increment logic and the `TopUsedButtons` flow.
### [ ] `TemplateRepositoryTest`
- Test the logic for `ensureBuiltInTemplates` (checking for existing IDs before insertion).

---

## Technical Instructions for AI Agents
- Use **MockK** for all mocking.
- For `GeminiUseCase`, mock the `oauthTokenProvider` lambda and `driveProvider` lambda using `coEvery`.
- Use `runTest` with `UnconfinedTestDispatcher` for all coroutine-based tests.
- When testing `GeminiUseCase`, mock the `HttpsURLConnection` or inject a wrapper to avoid real network calls.
