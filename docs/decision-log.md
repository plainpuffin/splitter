# decision log

## 2026-04-27

### Product direction
- Build Splitter as a fully local-first Android app inspired by the common offline value of Splitwise.
- Reason: Carl explicitly asked for a local offline app and a device APK, not a cloud clone.

### Baseline choice
- Reused the Bills Android project structure as a bootstrap baseline.
- Reason: it already has a working Kotlin/Compose/GitHub Actions shape, which is the fastest path to a new app and APK.

### Scope for first delivery
- Prioritized editable members, expense entry, equal/custom splits, balances, and settle-up suggestions.
- Reason: these are the core common flows that make the app useful immediately.

### Persistence choice
- Use SharedPreferences with JSON serialization for the first MVP.
- Reason: no backend is needed, and the data model is still small enough that a heavier local database would slow initial delivery.

### Custom split model
- Use exact-amount custom splits in MVP instead of percentages/weights.
- Reason: it is easier to reason about, avoids hidden rounding surprises, and still covers common uneven-split cases.

### Verification path
- Use GitHub Actions as the primary build/release verifier.
- Reason: the current container lacks Java and Android SDK tooling.
