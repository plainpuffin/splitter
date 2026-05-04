# Splitter

Local-first Android expense sharing app inspired by the common offline parts of Splitwise.

## What it is

Splitter tracks people, expenses, who paid, how each expense is split, current balances, and simple settle-up suggestions, all on-device with no account system and no backend.

App name:
- **Splitter**

Project / repo name:
- **splitter**

## Current state

This repo now contains a first native Android MVP with:
- editable local group members
- expense entry with payer selection
- equal split mode
- custom exact-amount split mode
- live custom-split remainder feedback while editing
- quick participant select-all / clear controls
- existing expense editing and deletion
- running balances per person
- settle-up suggestions
- one-tap settle-up summary copy
- local persistence using SharedPreferences
- GitHub Actions debug APK build and prerelease publishing

## Technical direction

Chosen stack:
- Kotlin
- Jetpack Compose
- SharedPreferences for local persistence
- GitHub Actions for debug APK builds/releases

Why this direction:
- fastest route to a clean native Android app
- no backend required
- easy to keep fully offline
- Compose is flexible for continued iteration

## Scope assumptions

Assumptions made for this first pass:
- the app should be fully offline and local-only
- no login, sync, server, or cloud backup is required
- the first delivery should cover the common core flow: people, expenses, splits, balances, settle-up
- exact-amount custom splits are more useful in MVP than percentage-based multi-user rules

## Requirements

For local development:
- Android Studio Hedgehog or newer, or equivalent modern tooling
- JDK 17
- Android SDK 34
- Gradle 8.7-compatible environment

No API keys or external services are required.

## How to run locally

### Android Studio
1. Open this folder as a project.
2. Let Android Studio install any missing SDK pieces.
3. Run the `app` configuration on a device or emulator.

### CLI
If you have Java and Android tooling locally:
1. install JDK 17
2. install Android SDK 34
3. run `gradle :app:assembleDebug`

## How to use

1. Edit the group name and member names.
2. Add or remove people as needed.
3. Enter an expense title and amount.
4. Choose who paid.
5. Choose equal split or custom split.
6. Add the expense, or edit an existing one if you need to fix it.
7. Review balances and settle-up suggestions below.
8. Copy the settle-up summary when you want to send the result elsewhere.
9. Delete mistakes or clear all expenses when the group is settled.

## GitHub debug APK workflow

Workflow file:
- `.github/workflows/android-debug-release.yml`

Behavior:
- builds on push to `main`
- uploads the debug APK as a workflow artifact
- publishes a prerelease tagged `debug-latest`
- renames the release asset to `splitter-debug.apk`

Expected release URL shape:
- `https://github.com/plainpuffin/splitter/releases/tag/debug-latest`

## Verification

Main verification path in this environment:
- files created locally
- repo can be pushed to GitHub
- GitHub Actions builds the debug APK remotely
- prerelease publishes the APK asset

Because this container does not currently have Java/Android SDK installed, local APK compilation is not available here.

## Known limitations

- current build is a debug APK, not a signed release build
- no cloud sync or multi-device sharing
- no recurring expenses, categories, receipts, or history filters yet
- custom split mode currently uses exact amounts, not weights or percentages
- no dedicated export file format yet beyond copy-to-clipboard settle-up text
- visual QA still needs an Android device pass

## Fresh-machine restart notes

On a fresh machine, clone the repo, open it in Android Studio, install SDK 34 if prompted, and run the app. If you only need a debug APK, push to `main` or manually dispatch the GitHub Actions workflow.
