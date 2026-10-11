"""Tests for source-registry loading and honesty rules.

Run: python3 data-pipeline/tests/test_sources_registry.py
Exit code 0 means every assertion passed.
"""

from __future__ import annotations

import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "data-pipeline", "sources"))
sys.path.insert(0, os.path.join(ROOT, "data-pipeline", "normalizers"))

import registry  # noqa: E402

REGISTRY_PATH = os.path.join(ROOT, "data", "sources.yml")


def test_real_registry_is_valid():
    doc = registry.load_yaml(REGISTRY_PATH)
    problems = registry.validate_registry(doc)
    assert not problems, "real registry has problems:\n  " + "\n  ".join(problems)


def test_real_registry_loads():
    entries = registry.load_registry(REGISTRY_PATH)
    assert len(entries) >= 12, "expected a meaningful India-wide registry, got %d" % len(entries)

    # Every source named in the Stage 01 brief must be accounted for.
    expected = {
        "imd_api", "open_meteo_forecast", "nasa_power", "isric_soilgrids",
        "soil_health_card", "agmarknet", "des_statistics_india",
        "copernicus_data_space", "isro_bhoonidhi", "dgft", "apeda",
        "commerce_mib", "osm_nominatim",
    }
    missing = expected - set(entries)
    assert not missing, "registry is missing required sources: %s" % sorted(missing)


def test_verified_sources_are_actually_usable():
    entries = registry.load_registry(REGISTRY_PATH)
    verified = [e for e in entries.values() if e.status == "verified"]
    assert verified, "expected at least one verified source"
    for e in verified:
        assert e.claim().startswith("Reachable and licence checked"), \
            "%s claims verified but cannot state a checked date" % e.id


def test_unavailable_sources_have_no_success_date():
    """A dead source must not also claim a successful retrieval."""
    entries = registry.load_registry(REGISTRY_PATH)
    for e in entries.values():
        if e.status == "unavailable":
            assert e.last_success is None, \
                "%s is unavailable yet reports last_success=%s" % (e.id, e.last_success)


def test_verified_requires_licence_text():
    """The core honesty rule: no 'verified' without a read licence."""
    doc = registry.load_yaml(REGISTRY_PATH)
    raw = list(doc["sources"])
    raw.append({
        "id": "fake_source", "name": "Fake", "official_url": "https://example.org",
        "access_method": "rest", "category": "weather", "geography": "india",
        "licence": "unconfirmed", "attribution_required": True,
        "redistribution": "unknown", "authentication": "none",
        "rate_limits": "unknown", "update_frequency": "daily",
        "last_success": "2026-10-10", "verified_on": "2026-10-10",
        "status": "verified", "limitations": "none known", "fallback": "none",
    })
    problems = registry.validate_registry({"sources": raw})
    assert any("licence is unconfirmed" in p for p in problems), \
        "validator let a source claim 'verified' with an unconfirmed licence"


def test_verified_requires_success_date():
    doc = {"sources": [{
        "id": "no_date", "name": "N", "official_url": "https://example.org",
        "access_method": "rest", "category": "weather", "geography": "india",
        "licence": "CC-BY 4.0", "attribution_required": True,
        "redistribution": "ok", "authentication": "none",
        "rate_limits": "unknown", "update_frequency": "daily",
        "last_success": None, "verified_on": None,
        "status": "verified", "limitations": "x", "fallback": "none",
    }]}
    problems = registry.validate_registry(doc)
    assert any("no last_success" in p for p in problems)


def test_restricted_licence_is_not_verified():
    """Open-Meteo's free tier is non-commercial; the registry must not hide that."""
    entries = registry.load_registry(REGISTRY_PATH)
    entry = entries["open_meteo_forecast"]
    assert "NON-COMMERCIAL" in entry.licence.upper(), \
        "Open-Meteo non-commercial restriction must be recorded on the registry entry"
    assert "COMMERCIAL RISK" in entry.entry["limitations"].upper()


def test_soilgrids_records_both_hard_constraints():
    entries = registry.load_registry(REGISTRY_PATH)
    sg = entries["isric_soilgrids"]
    assert sg.status == "restricted", "SoilGrids numeric API is broken, must not be verified"
    lim = sg.entry["limitations"]
    assert "farm level" in lim.lower() or "farm-level" in lim.lower(), \
        "SoilGrids farm-level prohibition must be recorded"
    assert "null" in lim.lower(), "the observed null values must be recorded"


def test_osm_offline_bulk_download_prohibition_recorded():
    entries = registry.load_registry(REGISTRY_PATH)
    osm = entries["osm_tiles"]
    text = (osm.entry["redistribution"] or "").lower()
    assert "prohibited" in text, "OSM bulk-download prohibition must be recorded"
    assert osm.status == "restricted"


def test_duplicate_id_is_rejected():
    doc = {"sources": [
        {"id": "dup", "name": "a", "official_url": "u", "access_method": "rest",
         "category": "weather", "geography": "india", "licence": "CC-BY 4.0",
         "attribution_required": True, "redistribution": "r", "authentication": "none",
         "rate_limits": "l", "update_frequency": "d", "last_success": "2026-10-10",
         "verified_on": "2026-10-10", "status": "verified", "limitations": "l",
         "fallback": "none"},
        {"id": "dup", "name": "b", "official_url": "u", "access_method": "rest",
         "category": "weather", "geography": "india", "licence": "CC-BY 4.0",
         "attribution_required": True, "redistribution": "r", "authentication": "none",
         "rate_limits": "l", "update_frequency": "d", "last_success": "2026-10-10",
         "verified_on": "2026-10-10", "status": "verified", "limitations": "l",
         "fallback": "none"},
    ]}
    problems = registry.validate_registry(doc)
    assert any("duplicate source id" in p for p in problems)


def test_fallback_must_reference_known_source():
    doc = {"sources": [{
        "id": "solo", "name": "s", "official_url": "u", "access_method": "rest",
        "category": "weather", "geography": "india", "licence": "CC-BY 4.0",
        "attribution_required": True, "redistribution": "r", "authentication": "none",
        "rate_limits": "l", "update_frequency": "d", "last_success": "2026-10-10",
        "verified_on": "2026-10-10", "status": "verified", "limitations": "l",
        "fallback": "does_not_exist",
    }]}
    problems = registry.validate_registry(doc)
    assert any("unknown fallback" in p for p in problems)


def test_yaml_reader_handles_block_scalars_and_lists():
    doc = registry.load_yaml(REGISTRY_PATH)
    assert isinstance(doc, dict)
    assert isinstance(doc["sources"], list)
    first = doc["sources"][0]
    assert first["id"] == "open_meteo_forecast"
    assert "CC-BY" in first["licence"]
    assert isinstance(first["limitations"], str) and len(first["limitations"]) > 20


TESTS = [v for k, v in sorted(globals().items()) if k.startswith("test_")]


def main():
    failures = []
    for fn in TESTS:
        try:
            fn()
            print("PASS %s" % fn.__name__)
        except AssertionError as exc:
            failures.append(fn.__name__)
            print("FAIL %s: %s" % (fn.__name__, exc))
        except Exception as exc:  # noqa: BLE001
            failures.append(fn.__name__)
            print("ERROR %s: %s: %s" % (fn.__name__, type(exc).__name__, exc))
    print("\n%d/%d passed" % (len(TESTS) - len(failures), len(TESTS)))
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
