# Test plan

## 1. Current status

**Executed and passing: 86 checks.** **Written but not executed: the Kotlin test suites**,
because Gradle cannot run in this environment (`docs/ARCHITECTURE.md` section 6).

| Suite | Checks | Status | Runs where |
| --- | --- | --- | --- |
| `test_sources_registry.py` | 12 | **Passed** | Host |
| `test_normalize.py` | 23 | **Passed** | Host |
| `test_schemas.py` | 5 | **Passed** | Host |
| `verify_palette_contrast.py` | 36 | **Passed** | Host |
| `verify_source_policy.py` | 5 | **Passed** | Host |
| `verify_android_sources.py` | 105 assertions | **Passed** | Host |
| `ColorContrastTest.kt` | 5 | **Not run** | Gradle unit |
| `SourcePolicyTest.kt` | 5 | **Not run** | Gradle unit |
| `LocalizationTest.kt` | 6 | **Not run** | Instrumented device |

```sh
sh scripts/run_tests.sh
```

The three `verify_*` scripts exist because the Kotlin equivalents could not be run here.
They apply the **same rules with the same expressions**, so the policy is enforced now
rather than merely asserted. They are a bridge, not a substitute: the Kotlin tests remain
authoritative once Gradle works, and should not be deleted.

## 2. Layer 1 — host tests (no device, no network)

These guard the project's core promises and must stay fast enough to run on every save.

**Registry honesty.** The most important tests in the project. They prevent the registry
from drifting into claiming more than was demonstrated: no `verified` without a read
licence, dates, or stated limitations; no `unavailable` with a success date; no
`not_integrated` with a verification date; fallbacks must name real sources.

**Normalization.** Timestamp parsing rejects naive times, because freshness cannot be
compared reliably without a zone. Freshness states progress correctly through fresh, aging,
stale, expired. `kind` must be consistent with `basis`. Unknown freshness is advisory, not
fatal, but must still be surfaced.

**Measurement rules.** `may_say_measured()` is false for modelled values and true only for
lab results. `is_suitable_for()` rejects a district estimate for a field question.
`describe_value_for_user()` blocks a statistical soil interpolation at field scale.

**Schemas.** The examples embedded in the JSON Schemas are executed against the normalizer,
so documentation cannot drift from behaviour.

## 3. Layer 2 — accessibility (verified without a compiler)

36 WCAG checks computed from the real token values in `Color.kt`:

- Every foreground/background pair meets AA (4.5:1).
- The primary numeric readout meets **AAA (7:1)** in both themes.
- Semantic colours (rain, heat, drought, risk) separate on **luminance**, not hue, so status
  remains readable for colour-vision deficiency and on a dimmed screen.
- Chart series separate in both themes.
- No pure black or pure white.

This found four genuine defects in the first draft palette, which were fixed rather than
relaxed: rain/drought in light mode, rain/heat and rain/drought in dark mode were too close
in luminance to be distinguishable without colour.

## 4. Layer 3 — source policy (architectural rules)

- No hardcoded user-facing strings in Kotlin.
- No Material You dynamic colour anywhere.
- No analytics, ad, crash-reporting or commercial map SDKs in the build.
- No committed API keys or secrets.
- English-only resource directories, so the APK cannot silently grow.

## 5. Layer 4 — Android build and device tests (not yet run)

**Blocker:** Gradle cannot start on this aarch64 bionic host.

```sh
cd android
gradle wrapper --gradle-version 8.7   # once, on a working machine
./gradlew :app:assembleDebug          # first build
./gradlew :app:testDebugUnitTest      # ColorContrastTest, SourcePolicyTest
./gradlew :app:connectedDebugAndroidTest  # LocalizationTest, needs a device
```

**First build must record** the APK size, then be compared against the budget in section 6.

**Device tests needed in Stage 02:**
- Layout on a small screen (~5", 320dp width) and a large one, light and dark.
- Accessibility scan confirming no clipped or overlapping text.
- Font scale 200% without truncation.
- TalkBack pass: every value exposes source, freshness and qualifier.
- Offline cold start with no network.
- Process-death and configuration-change survival.
- Low-memory behaviour on an 8 GB device.

## 6. Performance and size budget

Not yet baselined, because no APK was built. Proposed budgets:

| Metric | Budget | Rationale |
| --- | --- | --- |
| Debug APK | < 15 MB | Debug builds are never the user-facing size |
| **Release APK** | **< 8 MB** | Must install cheaply over a 2G/3G connection |
| Installed size | < 30 MB | Leaves room on an 8 GB device |
| Cold start | < 800 ms | Asset load only; no network |
| Steady-state RSS | < 150 MB | Budget device headroom |
| APK per added language | < 400 KB | Keeps language packs a deliberate choice |
| Network per refresh | < 150 KB | Must be viable on a metered connection |

Stage 02 must measure and record actuals. If the release APK exceeds 8 MB, revisit
dependency count before features.

## 7. Test data rules

- **No fabricated fixtures presented as real.** A fixture for a test is fine; a fixture
  shown in a screenshot or demo as a live reading is not.
- Test values must carry `kind` and `basis` like any real value, so the measurement rules
  are exercised by tests rather than bypassed by them.
- Any test that stubs a network response must assert the **failure** path too, so a stub
  cannot quietly become a permanent mock in production.
- Tests must never require network access or credentials to run. If a test can only run
  with a key, it belongs in an explicitly manual suite.

## 8. CI plan (Stage 02)

1. Host tests: `sh scripts/run_tests.sh`.
2. On `ubuntu-latest`: `./gradlew :app:assembleDebug :app:testDebugUnitTest lint`.
3. Fail on any Android Lint error.
4. Upload test reports and the release APK size.
5. Nightly: re-run `probe_sources.py` and open an issue when a `verified` source stops
   answering, so registry status cannot silently become false.

## 9. Definition of done for a stage

- [ ] `scripts/run_tests.sh` passes.
- [ ] Gradle build and unit tests pass (blocked in Stage 01).
- [ ] No unverified claim is presented as a tested result.
- [ ] Registry updated with real probe results and dates.
- [ ] Licence obligations recorded for any newly integrated source.
- [ ] Documented blockers reported rather than worked around silently.
