# GoSTalk - System Architecture & Behavior Documentation

**Timestamp:** 2026-03-06T14:28:00+01:00  
**Git Commit Hash:** 9e99afda1cd69b2cc94779f712b39d412a68e8bc

## Executive Summary
GoSTalk is an Android AAC (Augmented and Alternative Communication) application designed for switch-based or touch-based interaction. It revolves around a scanning engine that selects items from a grid, which then execute multi-modal actions (Speech, Navigation, Cloud AI).

---

## 1. Domain Model Hierarchy
- **Book**: The top-level container for a communication set (e.g., "School", "Home").
- **Page**: A grid-based layout within a book. Defined by `rows` x `columns`.
- **ButtonConfig**: A configuration for a single grid cell.
    - `label`: Display text/icon info.
    - `actions`: A list of `Action` objects (e.g., `SpeechAction`, `NavigationAction`).
- **PageTemplate**: A reusable page structure (e.g., "Food Grid 4x4").

---

## 2. Core Scanning Engine (`core.scanning`)
The app uses a pluggable `ScanStrategy` architecture:
- **LinearScan**: Iterates through buttons one by one (left-to-right, top-to-bottom).
- **RowByRowScan**: Focuses on high-level rows first, then drills into individual buttons within a row.
- **Engine State**: Managed by `ScannerEngine`, which tracks the `activeItemIndex`, `activeRowIndex`, and scanning speed (`scanDelayFlow`).
- **Feedback**: Provides auditory cues (Haptic/Audio) when an item is focused, handled by the `AuditoryCueListener`.

---

## 3. Action Execution Framework (`core.actions`)
Actions are executed via an `ActionExecutor` which delegates to specialized handlers:
- **SpeechActionHandler**: Uses `TextToSpeechHelper` to speak text or play recorded audio. Handles volume multipliers for different contexts (e.g., cues vs. main speech).
- **NavigationActionHandler**: Changes the active page. Can wait for a "Speech Done" signal from TTS before navigating.
- **GenAIActionHandler**: Bridges to `GeminiUseCase` for dynamic community-based or generative responses.

---

## 4. Multi-Modal TTS & Audio (`tts` & `audio`)
A critical component of GoSTalk is **Audio Routing**:
- **RoutedAudioPlayer**: Can send audio to specific internal/external devices (Bluetooth, Earpiece) using `AudioDeviceManager`.
- **TextToSpeechHelper**: Wraps Android's `TextToSpeech` API.
    - Supports **Cues vs. Main Speech**: Allows auditory prompts to be heard privately while main speech is loud.
    - **SSML Support**: Wraps text for SHOUT/WHISPER modes.

---

## 5. Persistence & Sync (`data` & `cloud`)
- **Database**: Room-based local storage (`AppDatabase`).
- **Sync Engine**: Uses `WorkManager` for background synchronization.
- **Google Drive Integration**: Uses `GoogleAuthManager` (CredentialManager) and `DriveServiceHelper` to backup/restore books as JSON files in a dedicated app folder.

---

## 6. UI Logic (`ui`)
- **Screens**: Built with Jetpack Compose.
- **Overlays**: Auditory cues and scanning highlights are rendered as overlays on top of the main grid.
- **ViewModels**: Manage the synchronization of repository flows and user interactions (e.g., `PageViewModel`, `TemplateViewModel`).

---

## 7. Rebuild Principles
To rebuild GoSTalk from scratch:
1.  **Architecture**: Use Clean Architecture (Domain/Data/UI separation).
2.  **Dependencies**: Hilt for DI, Room for DB, Coroutines for Async.
3.  **Key Challenge**: The scanning engine must remain decoupled from the UI grids via a shared `ScannerEngine` state.
4.  **Routing**: Audio routing logic must be robust against device connection/disconnection changes.
5.  **GenAI**: The AI interaction is turn-based REST, supporting tool-calling (Drive, Calendar, Weather).
