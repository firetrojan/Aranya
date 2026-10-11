# Aranya

Farmer-first agricultural intelligence for Android.

Aranya helps farmers make better-informed decisions about weather, soil, markets, satellite
observations, agricultural policy and livelihood risk. It is built to be evidence-first,
offline-capable, small, and free of mandatory subscriptions or proprietary AI providers.

**Current status: Stage 02 — the first working feature.**

Weather is real and functional: it fetches a live forecast from a verified source,
validates the response, persists it, and shows it again offline with its age and
provenance. Soil, markets and policy are registered but not built, and the Source Status
screen says so plainly rather than implying they work.

## What works

| Screen | What it does |
| --- | --- |
| **Home** | Summarises the last reading with its age, navigates to each section, refreshes weather |
| **Weather** | Live 5-day forecast from Open-Meteo, current conditions, provider attribution, retrieval time |
| **Sources** | Every registered source with its real licence, verification date and limitations |
| **Settings** | Language override (in-app, no restart, no system-setting change) and units |

Weather handles loading, refreshing, no-data, error, rate-limited, offline and stale-data
states as designed product states. A cached reading is shown when the network fails, and
is always labelled with how old it is.

## Repository layout

| Path | Contents |
| --- | --- |
| `android/` | Android app (Kotlin, Jetpack Compose, minSdk 26) |
| `data/sources.yml` | **Authoritative** source registry with verified status per source |
| `data/schema/` | JSON Schemas: provenance, freshness, uncertainty, adapter health |
| `data-pipeline/sources/` | Registry loading, validation, snapshot generation |
| `data-pipeline/normalizers/` | Value normalization and the measurement rules |
| `data-pipeline/tests/` | Tests and verification scripts |
| `docs/` | Architecture, data sources, licences, AI safety, privacy, roadmap, test plan |

## Build and test

```sh
# Host tests: no Android SDK needed
bash scripts/run_tests.sh

# Android build (JDK 17+, Android SDK 34)
cd android
gradle wrapper --gradle-version 8.7    # once; see android/gradle/wrapper/README.md
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

CI (`.github/workflows/ci.yml`) builds both APK variants, runs the Kotlin unit tests and
Lint, and boots an emulator for the instrumented tests.

**Measured, 2026-10-11 (run 38113764948):**

| Artifact | Size |
| --- | --- |
| `app-debug.apk` | 12.1 MB |
| `app-release.apk` | **1.8 MB** (budget: under 8 MB) |

**Local Android builds do not work on an aarch64 Android host**: Gradle needs a
glibc-linked native library that bionic cannot load. `scripts/diagnose_gradle.sh`
reproduces it. CI, or any normal development machine, is unaffected. See
`docs/ARCHITECTURE.md` section 6.

## After changing the registry

`data/sources.yml` is the source of truth. The app reads a generated snapshot:

```sh
python3 data-pipeline/sources/build_snapshot.py
```

Never hand-edit `android/app/src/main/assets/sources_snapshot.json`.

## Non-negotiable rules

1. A modelled or interpolated value is **never** presented as a laboratory measurement.
   Enforced in `data-pipeline/normalizers/normalize.py`.
2. A source is `verified` only when it was actually reached **and** its licence read.
3. No user-facing string is hardcoded. Enforced by `SourcePolicyTest`.
4. No Material You dynamic colour. The palette is fixed and deliberately authored.
5. No analytics, advertising, or commercial map SDKs.
6. No API keys or secrets in source.
7. No feature is presented as working when only its interface exists.
8. A missing value is `null` and shown as "Not reported" — never coerced to zero.

## Documentation

| Document | Read it for |
| --- | --- |
| `docs/ARCHITECTURE.md` | Structure, decisions, build environment |
| `docs/DATA_SOURCES.md` | Every source, what was probed, what was found |
| `docs/LICENCES.md` | Licence obligations per source |
| `docs/AI_SAFETY.md` | Limits on automated reasoning |
| `docs/PRIVACY.md` | Data handling and permissions |
| `docs/ROADMAP.md` | Stage plan and carried-forward blockers |
| `docs/TEST_PLAN.md` | What is tested, what is not, size budget |

## Licence

Code: MIT (`LICENSE`). Data displayed by Aranya carries its own licences; see
`docs/LICENCES.md`.
