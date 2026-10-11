# Aranya architecture

## 1. What Stage 01 actually is

Stage 01 is the foundation, not a working product. It establishes:

- a reproducible Android build configuration,
- a fixed, deliberately authored design system,
- a verified data-source registry,
- shared schemas for provenance, freshness and uncertainty,
- the normalization rules that prevent Aranya from overstating what it knows,
- tests that run without a device or network.

**No agricultural feature works yet.** The app ships one screen that states this and
shows the real source registry. That is intentional: an early build that displays
plausible-looking sample figures teaches a farmer to trust numbers the product cannot yet
stand behind, and that trust is expensive to lose.

## 2. Repository layout

```
android/                    Android application (Gradle root)
  app/                      Single application module
    src/main/java/          Kotlin sources
    src/main/res/           Resources, including all user-facing strings
    src/main/assets/        Generated registry snapshot
  gradle/libs.versions.toml Pinned dependency versions
data/
  sources.yml               The authoritative source registry
  schema/                   JSON Schemas for shared data conventions
  crop_knowledge/           (Stage 06+) evidence-backed crop knowledge
  crop_rules/               (Stage 07+) transparent decision rules
data-pipeline/
  sources/                  Registry loading, validation, snapshot generation
  normalizers/              Value normalization and the honesty rules
  tests/                    Tests and verification scripts
docs/                       Architecture, licences, roadmap, test plan
scripts/run_tests.sh        Runs everything runnable without a device
```

`data/crop_knowledge/` and `data/crop_rules/` are **not created yet**. They are listed so
the intended location is known, not as an instruction to create empty directories.

## 3. Architecture decisions

### 3.1 Single Gradle module

One app, one module. Module boundaries are introduced when a second component genuinely
needs to be isolated (for example a shared domain library consumed by both an app and a
wear client), not before. Splitting now would add build complexity with nothing to enforce.

### 3.2 Fixed palettes, never dynamic colour

Aranya ships two authored schemes and never derives colour from the user's wallpaper.
`SourcePolicyTest` and `verify_source_policy.py` fail the build if
`dynamicLightColorScheme` or `dynamicDarkColorScheme` appears.

Rationale: a farmer checks this app before leaving the house, often in bright sun on a
cheap display with poor colour reproduction. A wallpaper-sampled palette is unpredictable
and can destroy contrast. A reading must look and mean the same on every phone.

### 3.3 Provenance travels with every value

Every value is wrapped in a record carrying source id, retrieval attempt, freshness and
uncertainty (`data/schema/source-metadata.schema.json`). A value cannot enter the local
database, or reach a screen, without its provenance attached. This is what makes the
"when was this measured, by whom, at what resolution" answer always available.

### 3.4 The measurement rule

`data-pipeline/normalizers/normalize.py` is the enforcement point:

- `may_say_measured()` returns true only for `measured` or `lab_reported`.
- `is_suitable_for()` compares the value's `suitable_for` scales against the question
  being asked, and fails closed when uncertainty is absent.
- `describe_value_for_user()` returns a **display contract**, not prose: a required
  qualifier, whether the word "measured" is permitted, and any blocked claims.

Rendering consumes that contract. Strings come from resources, so the wording is
translatable while the decision stays in testable code.

### 3.5 Offline-first means cached-then-refreshed

Stage 01 loads from a bundled asset, so cold start needs no network. Stage 02 adds
retrieval with a bounded cache. Network failure is a normal designed state
(`state_offline_*`, `state_stale_*`), not an error dialog.

### 3.6 Permissions on demand

Stage 01's manifest declares **no permissions**. A permission is added only with the
feature that needs it. No location permission is planned for district selection: manual
entry or a coarse administrative area is sufficient, and tracking a farmer's exact
position is disproportionate for agricultural advice.

## 4. Multilingual architecture

Multilingual support is a structural property, not a language dropdown.

**Already true:**
- No user-facing string lives in Kotlin. Enforced mechanically.
- All formatting goes through `AranyaFormat`, which uses the active locale for digits,
  grouping, dates and plurals.
- Quantity strings are real `<plurals>`, not concatenated strings.
- The registry snapshot carries identifiers and prose, so translation is data work.

**Rules for translators:**
- Translate crop names, measures and UI text.
- Do **not** translate: units, scientific terms, official document titles, source names,
  or licence names. These stay in the original script and may carry a contextual
  explanation. A translated legal instrument title is a different document.
- Preserve original terminology alongside a translation where a common rendering would
  change the meaning, especially for crop varieties and chemical measures.

**Still to build (Stage 03):** `values-hi`, `values-mr`, and further Indian languages, plus
a language preference in DataStore. Adding a locale is `values-xx/strings.xml`; no screen
changes. Directionality is already handled (`supportsRtl`).

## 5. Source adapter architecture

Each source gets one adapter implementing a narrow interface: declare capability, retrieve,
normalize. Adapters are registered with their registry id and their status is read from
`data/sources.yml`, never hardcoded.

An adapter whose status is not `verified` **cannot report `can_retrieve = true`**
(`adapter-report.schema.json`, enforced in `test_schemas.py`). This is what prevents a
planned integration from being presented as a working one.

## 6. Build environment blocker

### The problem

**The Android build was NOT executed in Stage 01.** Gradle cannot start on this host.

Host: aarch64 Android (bionic libc). Gradle 9.8.0 and OpenJDK 17.0.20 are present.

```
$ gradle --version
FAILURE: Build failed with an exception.
* What went wrong:
Gradle could not start your build.
> Could not initialize native services.
   > Failed to load native library 'libnative-platform.so' for Linux aarch64.

Caused by: java.io.IOException: No such file or directory
```

### Root cause

`libnative-platform.so` from `native-platform-linux-aarch64-0.22-milestone-29.jar` is a
**glibc** binary. Its dynamic dependencies:

```
$ readelf -d libnative-platform.so | grep NEEDED
  libstdc++.so.6
  libm.so.6
  libgcc_s.so.1
  libc.so.6
```

The toolchain ships LLVM `libc++`, not GCC's `libstdc++`, and has no glibc at all. The
"no such file or directory" is `dlopen` failing to resolve those. Confirmed directly:

```
dlopen failed: library "libstdc++.so.6" not found
```

Android's linker cannot run a glibc binary. Gradle 9.x requires native services at
startup, so there is no flag to disable them.

### What was tried

| Attempt | Result |
| --- | --- |
| `-Dorg.gradle.native=false` | No effect; native services initialize regardless |
| `--no-daemon` | Same failure |
| `pkg_install qemu-user-x86-64` | Install reported success but no binary appeared, then broke the toolchain launchers. Not viable. |
| Locate glibc/libstdc++ for aarch64 | Debian URLs reachable, but bionic cannot link a glibc process anyway |

### Remedy for a normal machine

Any x86-64 or glibc-aarch64 Linux host, macOS, or Windows with the Android SDK works
normally. Nothing about Aranya is unusual; this is purely a host-architecture limitation.

On this device, the workable options are:

1. **A Linux VM or container on the device.** Requires an emulator, which is not available.
2. **A remote/CI builder.** Recommended: GitHub Actions on `ubuntu-latest` builds and runs
   the Gradle tests, which is where the unverified Kotlin tests should first be executed.
3. **Run the Python pipeline tests only**, which is what `scripts/run_tests.sh` does now.

### Consequences for this stage

| Verified | Not verified |
| --- | --- |
| Registry validation, normalizer, schemas | `:app:assembleDebug` |
| WCAG contrast arithmetic (36 checks) | `ColorContrastTest` (Kotlin) |
| Source policy (no dynamic colour, no secrets) | `SourcePolicyTest` (Kotlin) |
| Android source consistency (105 assertions) | `LocalizationTest` (instrumented) |
| Kotlin file structure, brace balance, string refs | Lint, R8/ProGuard, resource shrinking |
| | **APK size** |

**No APK was produced, so no application size figure is claimed.** The first build on a
working machine must record baseline APK size in this document.

## 7. Dependency choices and justifications

| Dependency | Why |
| --- | --- |
| Compose + Material3 | Mandated defaults. Material3 is used as a **primitive**; all colour, type and shape come from Aranya tokens, so the product does not look like stock Material. |
| Ktor | Coroutine-native HTTP, smaller than Retrofit plus its converter stack. Mockable in tests via `ktor-client-mock`. |
| kotlinx.serialization | Kotlin multiplatform, reflection-free, smaller than Moshi/Gson at runtime. |
| Room | Structured local data, no ORM hand-written. |
| DataStore | Preferences. |
| WorkManager | Deferred, constraint-aware background refresh that survives reboots. No foreground services. |
| Platform font (Roboto) | Bundling a font costs megabytes and would *degrade* Devanagari coverage. |

Explicitly excluded: analytics and crash-reporting SDKs, ad SDKs, Google Play Services
(location/maps), Mapbox, ML model runtimes. Enforced by `SourcePolicyTest`.

`resourceConfigurations = ["en"]` keeps the initial APK to one language.

## 8. Size strategy

- Vector launcher icon only: no PNG densities at all.
- One language, no bundled fonts.
- No bundled maps, imagery or models. Satellites are fetched per tile on demand, never
  packed. See the OSM tile constraint in `docs/DATA_SOURCES.md`.
- Release build enables R8 and `shrinkResources`.
- `minSdk 26` allows `java.time` without desugaring, avoiding the desugaring library.
- Cache growth is bounded by design in Stage 02; large datasets are paginated rather than
  loaded whole.

## 9. Security and secrets

- **No API key or secret is committed.** Enforced by `SourcePolicyTest` and
  `verify_source_policy.py`.
- Keys, when a source needs one, are supplied by the user at runtime or through an
  opt-in build config. The registry records *that* authentication is required, never a
  credential.
- The release build is currently **debug-signed** so `assembleRelease` runs without
  secrets. This must be replaced before any real distribution.
- No network security config is declared in Stage 01 because no network code exists yet.
  Stage 02 adds cleartext-disabled configuration explicitly.

## 10. Verified sources of truth

| Concern | Authoritative location |
| --- | --- |
| Source status, licence, limits | `data/sources.yml` |
| On-device source list | generated `sources_snapshot.json` (never hand-edited) |
| Value honesty rules | `data-pipeline/normalizers/normalize.py` |
| Record shape | `data/schema/source-metadata.schema.json` |
| Palette and type | `android/app/src/main/java/org/aranya/app/ui/theme/` |
| Licence obligations | `docs/LICENCES.md` |

If code and registry disagree, the registry wins and the code is wrong. Run
`python3 data-pipeline/sources/build_snapshot.py` after any registry edit.
