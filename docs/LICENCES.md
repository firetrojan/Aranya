# Licences

Aranya is MIT licensed (see `LICENSE`).

That covers **Aranya's own code**. It does not cover the data Aranya displays. Data
licences are independent obligations that attach to each source, and this document records
them.

**This is not legal advice.** Obligations were read from provider documentation on
2026-10-10 and should be re-checked before any public release.

## 1. Code and assets

| Item | Licence | Notes |
| --- | --- | --- |
| Kotlin sources, design system, pipeline | MIT | |
| Launcher icon (vector paths) | MIT | Original work |
| Colour palette, typography tokens | MIT | Original work; **not** derived from any third-party brand |
| Bundled font | None | Platform font only, nothing bundled |

The palette was authored from scratch. It does not copy another application's branding.

## 2. Data obligations by source

### Open-Meteo — CC-BY 4.0, free tier **non-commercial only**

Data under CC-BY 4.0; attribution required ("Weather data by Open-Meteo.com").

**The critical constraint:** the free tier states *"You may only use the free API services
for non-commercial purposes"*, with limits of 10,000 calls/day, 5,000/hour and 600/minute.
Open-Meteo gives concrete examples. Non-commercial includes *"private or non-profit
websites or apps that do not have subscriptions or advertising"*. Commercial includes
*"operating websites or apps that have subscriptions or display advertisements"*.

**This affects three of the six verified sources** (`open_meteo_forecast`,
`open_meteo_air_quality`, `open_meteo_archive`).

**Action required before any monetised release:** replace with a paid plan, or substitute
a source without the restriction. Recorded in the registry `limitations` for
`open_meteo_forecast` and asserted by `test_restricted_licence_is_not_verified`.

Aranya currently has no subscriptions and no advertising, so the free tier is correct
today. This is a constraint on future decisions, not a present violation.

### NASA POWER — free to use, citation requested

NASA states data may be used freely; the project **requests** citation and asks to be
notified of published works. Not a formal open licence, but no restriction was found.

Required attribution, from POWER's referencing guide:

> The data was obtained from National Aeronautics and Space Administration (NASA) Langley
> Research Center's Prediction Of Worldwide Energy Resources (POWER) project funded through
> the NASA Earth Science Division.

Include service name, version and date accessed.

### ISRIC SoilGrids — CC-BY 4.0

Confirmed by ISRIC: *"Since 2019, SoilGrids products are provided under the CC BY 4.0
(publicly accessible environmental data)."*

Attribution, as specified by ISRIC:

> Poggio, L., de Sousa, L. M., Batjes, N. H., Heuvelink, G. B. M., Kempen, B., Ribeiro, E.,
> and Rossiter, D.: SoilGrids 2.0: producing soil information for the globe with quantified
> spatial uncertainty, SOIL, 7, 217-240, 2021.

**Fair use: maximum 5 API calls per minute, 500 per day, 10,000 per month.** Aranya's
cache must respect this. Screen screenshots must name the source website.

### OpenStreetMap — ODbL 1.0

Data under ODbL 1.0. Required attribution: **"© OpenStreetMap contributors"**.

Share-alike: a derived database must remain under ODbL. Aranya does not produce a public
derived geodatabase; it displays and caches. If a future stage publishes a bundled
geodata product, the obligation must be reassessed.

The Nominatim API returns a `licence` field per result stating the terms. That field must
be surfaced to the user.

**Service policy (not licence) is the binding constraint here:** tile bulk downloading and
offline prefetching are **prohibited** by OSMF policy. See `docs/DATA_SOURCES.md` section 4.

### Copernicus Data Space / Sentinel — free, full and open access

Regulated under EU law, providing *"free, full and open access... without any express or
implied warranty, including as regards quality and suitability for any purpose"*.

Credit is **required**: "Contains modified Copernicus Sentinel data (year), processed by
Aranya." Defined in `strings.xml` as `attribution_modified_copernicus`.

### data.gov.in datasets — GODL-India

The portal footer states content is *"licensed under the Government Open Data License -
India"*. Reuse permitted with attribution. **Not practically confirmed**, because the API
gateway refused connections on the probe date.

## 3. Unconfirmed licences

These sources have **no licence text located**. Aranya must not build features on them
until terms are established. Treating "the website is public" as "public domain" is the
most common way a project like this creates legal exposure.

| Source | Position |
| --- | --- |
| `soil_health_card` | No licence found. Records are farm-level personal data; redistribution is likely restricted and raises privacy obligations. |
| `agmarknet` | No reuse terms published. No public API. |
| `enam` | Unknown. |
| `dgft`, `apeda`, `commerce_mib` | Government portals, no machine-readable licence. |
| `isro_bhoonidhi`, `bhuvan` | No published licence. |
| `imd_api` | Terms text not retrieved; registration required. |
| `des_statistics_india` | Unreachable, licence moot for now. |

**Policy:** a source with an unconfirmed licence is never marked `verified`, and a feature
is never built on it. Enforced by `test_verified_requires_licence_text`.

## 4. Deliberately excluded sources

| Excluded | Reason |
| --- | --- |
| Google Maps / Mapbox / Apple MapKit | Requires an account, key and per-request billing. Violates the no-paid-API constraint. |
| Proprietary AI/LLM APIs | Stage 01 forbids making one a hard dependency. See `docs/AI_SAFETY.md`. |
| Paid soil/weather APIs | Core functionality must stay free. |

## 5. Attribution implementation

Attribution is **not optional and not best-effort**:

1. Every source with `attribution_required: true` carries `attribution_text` in
   `data/sources.yml`.
2. That text is projected into `sources_snapshot.json`.
3. Records carry a `licence` object; `validate_record()` fails if attribution is required
   but no text is stored.
4. `describe_value_for_user()` always returns `requires_attribution = true`.

A cached or exported value therefore cannot lose its licence. Stage 02 must render
`attribution_text` on every data view.

## 6. Re-verification

Licence terms change. Before each release:

1. Re-run `data-pipeline/tests/probe_sources.py`.
2. Re-read the licence for each `verified` source and update `verified_on`.
3. Review the `investigating` list and promote only with terms actually confirmed.
4. Re-check the Open-Meteo non-commercial constraint against the project's funding model.

`data/sources.yml` is designed so a stale or wrong claim fails loudly rather than passing
quietly. Keep it that way.
