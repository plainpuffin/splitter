# splitter work state

Last updated: 2026-05-04 19:19 UTC

## Current status

In progress. The offline Android MVP is in place locally with the first polish pass completed, and the next step is GitHub build/release verification.

## What is done

- created a dedicated Android project home for Splitter
- chose Kotlin + Jetpack Compose + SharedPreferences as the local-first stack
- implemented editable people/group management
- implemented local expense entry with payer selection
- implemented equal split mode
- implemented custom exact-amount split mode
- implemented expense editing for common mistake recovery
- implemented live custom-split remainder feedback
- implemented quick participant select-all / clear actions
- implemented running balances and settle-up suggestions
- implemented settle-up summary copy-to-clipboard
- implemented local persistence for group, members, and expenses
- prepared GitHub Actions debug APK release workflow with a suitable APK asset name
- refreshed the launcher and in-app icon treatment
- documented assumptions, setup, and continuation state

## What is not done

- first GitHub Actions compile verification for this new repo
- first prerelease APK publication for Splitter
- real-device visual QA
- richer features like categories, recurring items, receipt attachments, weights/percent splits, or file export

## Current blocker

This container still lacks Java/Android SDK tooling, so local APK builds are blocked. GitHub Actions is the primary verification path.

## What changed this pass

- added common fix-up and sharing affordances, especially expense editing and copyable settle-up output
- tightened the custom split workflow with live feedback and participant quick actions
- refreshed the branding with a new Splitter icon for launcher and in-app use

## Verification

- project files created locally
- no local Java/Gradle verification available in this container
- next verification step is GitHub Actions after push

## Next wake instruction

Open this file first. Then complete `T04-push-build-verify-release.md`. If CI fails, fix the build and update this file with the exact failure and fix. If CI passes, record the run ID and release URL, then continue with polish tickets only if Carl asks.

## Recommended next ticket

`T04-push-build-verify-release.md`
