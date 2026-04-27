# T04 push, build, verify, release

Status: open
Type: implementation + QA

## Goal

Push Splitter to GitHub, run the Android debug workflow, and publish the first device APK release.

## Why this exists

This environment cannot build Android locally, so GitHub Actions is required to verify the app and produce the APK.

## Tasks

- initialize git if needed
- create the GitHub repo
- push the initial code
- watch the first Actions run
- fix any build or packaging failures
- confirm the `debug-latest` prerelease and APK asset
- update docs/work-state with exact verification results

## Deliverables

- GitHub repo
- passing GitHub Actions run or documented blocker
- prerelease with Splitter APK asset

## Verification

- `gh run view` or `gh run list` shows the workflow result
- `gh release view debug-latest` shows the APK asset
