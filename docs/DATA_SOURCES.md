# Aranya data sources

Companion to `data/sources.yml`, which is the authoritative machine-readable registry.
This document explains what was checked, what was found, and what that means for Stage 02.

**All probe results below were produced on 2026-10-10 by scripts in
`data-pipeline/tests/`:**

| Script | Question |
| --- | --- |
| `probe_sources.py` | Do the candidate hosts answer at all? |
| `probe_dns.py` | Is a failure DNS-level or application-level? |
| `probe_api_shapes.py` | What do the endpoints require and return? |
| `probe_api_shapes2.py` | Second round: soil and policy sources |
| `probe_soilgrids.py` | Is a `null` value a parameter error or genuine no-data? |

## 1. Verified (6)

Reached with a real response **and** licence terms read.

| Source | Licence | Attribution | Auth |
| --- | --- | --- | --- |
| `open_meteo_forecast` | CC-BY 4.0, **free tier non-commercial** | Required | None |
| `open_meteo_air_quality` | CC-BY 4.0, **free tier non-commercial** | Required | None |
| `open_meteo_archive` | CC-BY 4.0, **free tier non-commercial** | Required | None |
| `nasa_power` | NASA open use, citation requested | Required | None |
| `osm_nominatim` | ODbL 1.0 | Required | None |
| `osm_overpass` | ODbL 1.0 | Required | None |

"Verified" means reachable and legally checked. It does **not** mean suitable for a
field-level claim; see section 5.

## 2. Restricted (4)

Authoritative but not usable without something Aranya does not have.

**`imd_api` — requires registration.**
`api.imd.gov.in` resolves and answers HTTP 200 at the root, but unauthenticated
`/weather` endpoints return HTTP 404. IMD runs an API management portal requiring
registration, a Development or Production key, and a JWT with server/IP binding. No data
was retrieved. This is the *authoritative* Indian weather source and must be preferred
once a key exists.

**`datagov_in_agmarknet_resource` — the cleanest licensing route for mandi prices, currently blocked.**
`api.data.gov.in` **refused the TCP connection** while `data.gov.in` (HTML portal)
answered HTTP 200 showing "The portal is undergoing maintenance". A per-user API key is
also required. Datasets on the portal are under the Government Open Data Licence – India
(GODL), which permits reuse with attribution.

**`isric_soilgrids` — licence is clear, data is not usable.**
CC-BY 4.0 confirmed, with a documented fair-use limit of **5 API calls/minute,
500/day, 10,000/month**. See section 5.

**`osm_tiles` — conflicts with offline-first by policy.**
See section 4.

## 3. Investigating (9)

Reachable, but terms or required parameters are unconfirmed. **No adapter may be built on
these until the specific gap is closed.**

`soil_health_card`, `agmarknet`, `enam`, `copernicus_data_space`, `isro_bhoonidhi`,
`bhuvan`, `dgft`, `apeda`, `commerce_mib`.

## 4. Hard constraints that change the design

### OSM tiles prohibit bulk and offline downloading

Both the raster and vector tile policies state:

> Bulk downloading ("scraping") is the downloading of tiles in advance instead of
> downloading when a user views those tiles. Common examples include creating a tile
> archive or downloading for offline usage. Bulk downloading is prohibited.

Also required: visible attribution, a valid identifying HTTP `User-Agent`, no `no-cache`
headers, and honouring HTTP caching (minimum 7 days).

**Consequence for Aranya.** This directly conflicts with the offline-first goal, so the
resolution is deliberate and recorded: Aranya may display OSM tiles online with
attribution and may cache per HTTP headers, but it will **not** prefetch or ship an
offline tile pack from OSMF servers. Offline maps in later stages need a source that grants
offline rights explicitly.

`osm_nominatim` is additionally limited to **1 request per second**, absolutely, with bulk
geocoding prohibited. Usable for occasional place lookup, never for autocomplete on every
keystroke.

### Copernicus data is open but enormous

STAC catalogue access worked without a key; the OData path `/odata/v1/Products` returned
HTTP 404 while `/stac/collections` returned a full collection list. Licence is free, full
and open under EU law with no warranty of quality or suitability.

**Consequence:** Sentinel scenes are hundreds of MB each. Bundling imagery is excluded by
the size budget. Aranya must carry the credit "Contains modified Copernicus Sentinel data
(year), processed by Aranya."

## 5. Soil: the most important negative finding

This is the single most consequential result of Stage 01, because field-level soil data is
what a farmer most needs and least often has.

`classification/query` returns a real soil class (e.g. `Luvisols` at 28.6N 77.2E), but
`properties/query` returned **no numeric value at any coordinate tested**:

| Query | Result |
| --- | --- |
| `phh2o`, 0-5cm, Pune | `{"layers":[{"depths":[{"values":{"mean":null}}]}]}` |
| `phh2o`, 0-5cm, Delhi | `{"mean": null}` |
| `clay`, 0-20cm, Pune / Delhi | `{"layers":[]}` |
| `silt`, 0-20cm, Pune | `{"layers":[]}` |
| `bdod`, 0-5cm, Pune | `{"mean": null}` |

ISRIC's own documentation states the SoilGrids REST API is **currently PAUSED with
issues**, and separately warns:

> SoilGrids is a global model intended for global applications, the results are best suited
> for continental or macro region analysis... **we do not advise to use SoilGrids at the
> local or farm level.**

**Two rules therefore apply, and both are enforced in code:**

1. A SoilGrids value may never be presented as a soil test result, or as a reading for a
   farmer's field. `describe_value_for_user()` returns
   `required_qualifier = "regional_estimate_not_a_field_test"` and
   `suitable_for_requested_scale = false` for a statistical interpolation at field scale.
   Covered by `test_soil_estimate_display_is_blocked_at_field_scale`.
2. The registry marks SoilGrids `restricted`, not `verified`, and records the observed null
   values. Covered by `test_soilgrids_records_both_hard_constraints`.

**The path forward.** `soil_health_card` is the authoritative Indian source but has no
public API and unconfirmed terms, and its records are farm-level personal data. The
pragmatic Stage 02 direction is **farmer-entered values from their own Soil Health Card**,
which requires no redistribution of government records, carries no licence risk, and gives
a genuinely field-scale measurement the moment it exists.

## 6. Unavailable (2)

### `des_statistics_india` — DNS failure

`des.gov.in` and `www.des.gov.in` both fail to resolve:

```
socket.gaierror: No address associated with hostname
```

`example.com` resolved normally in the same run, so this is not a local network problem.
Whether DES is retired, renamed, or blocked was **not determined**. Crop production
statistics therefore have no working source.

**Stage 02 must test:** `mospi.gov.in` (registered as `mospi_india`, `not_integrated`),
PIB press releases, and the MoSPI Farmgate Value series.

### `agricoop_dac` — DNS failure

`agricoop.nic.in` does not resolve. The Department was restructured into the Department of
Agriculture and Farmers Welfare; the successor domain must be identified.

## 7. Not integrated (3)

`icar_soil_atlas_india`, `mospi_india`, `kisan_suvidha`. Registered as candidates, access
deliberately not assessed. `verified_on` is `null` by rule, enforced by the validator.

## 8. Market prices: the hardest constraint

Mandi prices are the highest-value feature for a farmer and the most legally constrained.

- **`agmarknet`** answered HTTP 200 but is an ASP.NET form interface with **no public
  API**. Submitting its search form would be scraping unless permission is granted.
- **`data.gov.in`** is the clean route (GODL, clear terms) but the API gateway refused
  connections on the probe date.
- **`enam`** reachable, terms unconfirmed.

**Stage 02 must not scrape AGMARKNET.** Priority: obtain a `data.gov.in` API key and
confirm GODL applicability to the specific AGMARKNET dataset. Written permission from AGMARKNET
is the fallback.

## 9. Snapshot generation

`data/sources.yml` is the single source of truth. The app cannot parse YAML without a
dependency, and a YAML parser for a small read-only list is not worth its size.

```sh
python3 data-pipeline/sources/build_snapshot.py
```

writes `android/app/src/main/assets/sources_snapshot.json` (19 KB), ordered verified
first, and refuses to emit any source that is `verified` without a `verified_on` date.
Never hand-edit the JSON.

## 10. Honesty rules enforced by code

| Rule | Test |
| --- | --- |
| `verified` requires a read licence, not a placeholder | `test_verified_requires_licence_text` |
| `verified` requires `last_success` and `verified_on` dates | `test_verified_requires_success_date` |
| `verified` requires stated limitations | validator, `validate_registry` |
| `verified` is refused when authentication indicates credentials | validator |
| `unavailable` requires recorded probe evidence | validator |
| `unavailable` may not also report a success date | `test_unavailable_sources_have_no_success_date` |
| `not_integrated` may not claim a `verified_on` date | validator |
| Fallbacks must name a real registered source | `test_fallback_must_reference_known_source` |
| Duplicate ids rejected | `test_duplicate_id_is_rejected` |
| Non-verified adapters may not claim `can_retrieve` | `test_adapter_report_can_retrieve_is_consistent` |
| Prose free text in `limitations` is not validated by machine | **manual review only** |

That last row is a real gap. `limitations` is free prose and is only as accurate as the
person who wrote it. Stage 02 should consider promoting key limitations to structured
fields once there is a UI that consumes them.
