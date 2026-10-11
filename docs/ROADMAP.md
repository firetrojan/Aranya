# Roadmap

Nine stages were planned to give Aranya a working agricultural product. **This is a
plan, not a contract.** The roadmap stays open-ended: the goal is a useful product for
farmers, and if evidence shows a different sequence serves that better, the sequence
changes.

## Status

| Stage | Scope | Status |
| --- | --- | --- |
| 01 | Foundation, source registry, design system | **Complete** (Android build unverified) |
| 02 | First real feature, on a verified source | Not started |
| 03 | Multilingual (Hindi, Marathi) | Not started |
| 04 | Offline cache and refresh | Not started |
| 05 | Maps and location | Not started |
| 06 | Crop knowledge base | Not started |
| 07 | Decision rules | Not started |
| 08 | Market and policy | Not started |
| 09 | Hardening and release | Not started |

## Stage 01 — foundation (complete)

Delivered: Gradle configuration, fixed light/dark design system, WCAG-verified palette,
source registry of 24 sources with verified statuses, schemas for provenance and
freshness, normalization rules, 86 passing host checks, documentation.

**Not delivered, deliberately:** no agricultural feature, no Android build verification, no
network code, no permissions.

## Stage 02 — first real feature

**Recommended first feature: weather.** `open_meteo_forecast` and `nasa_power` are
`verified` and need no credentials. Weather is comprehensible without domain knowledge, so
it validates the whole pipeline end to end at low risk.

Scope:
- `INTERNET` + `ACCESS_NETWORK_STATE` permissions, added **with this feature**.
- One source adapter behind a narrow interface, registered with its registry status.
- Room for cached observations; DataStore for the selected district.
- Manual district selection, coarse administrative area, **no GPS**.
- Offline-first: render cached values, then refresh.
- Designed empty, loading, stale, offline and error states.
- Open-Meteo attribution displayed, with its non-commercial constraint recorded.

Open question to resolve early: register for an IMD API key. IMD is authoritative for
India and must eventually be preferred over model output.

## Stage 03 — multilingual

`values-hi`, `values-mr` as the pilot pair. Adds locale-aware fallback chains, a language
preference, and Devanagari typography review at small sizes. No screen changes: the
architecture is already language-agnostic.

## Stage 04 — offline and refresh

WorkManager for scheduled refresh with network constraints. Bounded cache with explicit
eviction. Pagination for anything large. `data/crop_knowledge/` begins here.

## Stage 05 — maps

**Blocked on a decision.** OSM tile policy prohibits bulk and offline downloading, so
offline maps need a source that grants offline rights. Evaluate before committing.

Geocoding is capped at 1 req/s by Nominatim policy, so autocomplete needs local or
administered data.

## Stage 06 — crop knowledge

`data/crop_knowledge/`: evidence-backed, each entry citing a source. Prefer farmer-entered
data over modelled estimates where field-scale accuracy matters.

## Stage 07 — decision rules

`data/crop_rules/`: transparent, inspectable rules. A farmer must be able to see **why** a
recommendation was made. Rules consume normalized records, so measurement and freshness
constraints are inherited automatically.

## Stage 08 — market and policy

**Blocked on licensing.** Mandi prices need a `data.gov.in` API key and confirmation that
GODL covers the specific dataset, or written AGMARKNET permission. Policy features link to
official notices and never paraphrase legal instruments loosely.

## Stage 09 — hardening and release

Accessibility audit, performance on a real budget device, APK size against budget,
localisation review, licence audit. Release with a baseline profile generated **on a
device** (never fabricated).

## Principles for every stage

1. Practical usefulness over feature count.
2. Evidence and transparent uncertainty over confident guesses.
3. No feature is complete until its data source is `verified`.
4. Offline-first, and poor connectivity is a designed state.
5. Small APK, modest memory, low battery.
6. Free and open-source where practical; no mandatory paid API.
7. Multilingual from the start, not retrofitted.
8. Release useful functionality early.

## Known blockers to carry forward

| Blocker | Affects | Resolution needed |
| --- | --- | --- |
| Gradle cannot run in this environment | All Android verification | Build on x86-64/glibc host or CI |
| `api.data.gov.in` refused connections | Mandi prices | Retry, obtain API key |
| `des.gov.in` DNS failure | Crop statistics | Identify MoSPI route |
| `agricoop.nic.in` DNS failure | Policy notices | Find successor domain |
| SoilGrids numeric API paused | Modelled soil | Recheck; prefer farmer-entered |
| OSM offline-download prohibition | Offline maps | Source with explicit offline rights |
| Open-Meteo non-commercial limit | Weather, air quality | Revisit before monetisation |
