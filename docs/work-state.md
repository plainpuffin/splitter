# splitter work state

Last updated: 2026-04-27 20:14 UTC

## Current status

In progress. Initial offline Android MVP is scaffolded and implemented locally, and the next step is GitHub build/release verification.

## What is done

- created a dedicated Android project home for Splitter
- chose Kotlin + Jetpack Compose + SharedPreferences as the local-first stack
- implemented editable people/group management
- implemented local expense entry with payer selection
- implemented equal split mode
- implemented custom exact-amount split mode
- implemented running balances and settle-up suggestions
- implemented local persistence for group, members, and expenses
- prepared GitHub Actions debug APK release workflow with a suitable APK asset name
- documented assumptions, setup, and continuation state

## What is not done

- first GitHub Actions compile verification for this new repo
- first prerelease APK publication for Splitter
- real-device visual QA
- richer features like categories, recurring items, receipt attachments, weights/percent splits, or export

## Current blocker

This container still lacks Java/Android SDK tooling, so local APK builds are blocked. GitHub Actions is the primary verification path.

## What changed this pass

- created the new `splitter` project from a clean Android baseline
- replaced the old calculator domain with a local expense-sharing model
- added durable project docs and tickets
- prepared the repo for GitHub push/build/release

## Verification

- project files created locally
- no local Java/Gradle verification available in this container
- next verification step is GitHub Actions after first push

## Next wake instruction

Open this file first. Then complete `T04-push-build-verify-release.md`. If CI fails, fix the build and update this file with the exact failure and fix. If CI passes, record the run ID and release URL, then continue with polish tickets only if Carl asks.

## Recommended next ticket

`T04-push-build-verify-release.md`
