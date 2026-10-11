# Aranya

Farmer-first agricultural intelligence for Android.

Aranya helps farmers make better-informed decisions about weather, soil, markets, satellite
observations, agricultural policy and livelihood risk. It is built to be evidence-first,
offline-capable, small, and free of mandatory subscriptions or proprietary AI providers.

**Current status: Stage 01 — foundation only.**

The design system, source registry, data schemas and normalization rules are in place. **No
agricultural feature works yet**, and the app says so on its only screen rather than showing
sample figures that could be mistaken for real readings.

## Repository layout

| Path | Contents |
| --- | --- |
| `android/` | Android app (Kotlin, Jetpack Compose) |
| `data/sources.yml` | **Authoritative** source registry with verified status per source |
| `data/schema/` | JSON Schemas: provenance, freshness, uncertainty, adapter health |
| `data-pipeline/sources/` | Registry loading, validation, snapshot generation |
| `data-pipeline/normalizers/` | Value normalization and the measurement rules |
| `data-pipeline/tests/` | Tests and verification scripts |
| `docs/` | Architecture, data sources, licences, AI safety, privacy, roadmap, test plan |

`data/crop_knowledge/` and `data/crop_rules/` are deliberately **not created yet**. Stage 01
does not create empty directories to look complete.

## Build and test

**Host tests (work anywhere):**

```sh
sh scripts/run_tests.sh
```

**Android build (requires a JDK 17+ and Android SDK 34 on an x86-64 or glibc host):**

```sh
cd android
gradle wrapper --gradle-version 8.7    # once; see android/gradle/wrapper/README.md
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

**The Android build has not been verified.** Gradle cannot start in the Stage 01
environment because it is an aarch64 bionic host and Gradle's native library requires
glibc. Evidence and remedy are in `docs/ARCHITECTURE.md` section 6. The Kotlin unit and
instrumented tests are therefore written but unrun.

## After changing the registry

`data/sources.yml` is the source of truth. The app reads a generated snapshot:

```sh
python3 data-pipeline/sources/build_snapshot.py
```

Never hand-edit `android/app/src/main/assets/sources_snapshot.json`.

## Non-negotiable rules

1. A modelled or statistically interpolated value is **never** presented as a laboratory
   measurement. Enforced in `data-pipeline/normalizers/normalize.py`.
2. A source is `verified` only when it was actually reached **and** its licence read.
   Enforced in `data-pipeline/sources/registry.py`.
3. No user-facing string is hardcoded. Enforced in `SourcePolicyTest`.
4. No Material You dynamic colour. The palette is fixed and deliberately authored.
5. No analytics, advertising, or commercial map SDKs.
6. No API keys or secrets in source.
7. No feature is presented as working when only its interface exists.

## Documentation

| Document | Read it for |
| --- | --- |
| `docs/ARCHITECTURE.md` | Structure, decisions, build blocker evidence |
| `docs/DATA_SOURCES.md` | Every source, what was probed, what was found |
| `docs/LICENCES.md` | Licence obligations per source |
| `docs/AI_SAFETY.md` | Limits on automated reasoning |
| `docs/PRIVACY.md` | Data handling and permissions |
| `docs/ROADMAP.md` | Stage plan and carried-forward blockers |
| `docs/TEST_PLAN.md` | What is tested, what is not |

## Licence

Code: MIT (`LICENSE`). Data displayed by Aranya carries its own licences; see
`docs/LICENCES.md`.
