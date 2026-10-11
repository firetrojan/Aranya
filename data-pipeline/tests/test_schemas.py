"""Validate the JSON Schema documents and the examples embedded in them.

Examples inside a schema are the easiest thing to let rot, so they are parsed and
checked here rather than trusted.
"""

from __future__ import annotations

import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
SCHEMA_DIR = os.path.join(ROOT, "data", "schema")
sys.path.insert(0, os.path.join(ROOT, "data-pipeline", "normalizers"))

import normalize  # noqa: E402

SCHEMA_FILES = (
    "source-metadata.schema.json",
    "freshness.schema.json",
    "uncertainty.schema.json",
    "adapter-report.schema.json",
)


def _load(name):
    with open(os.path.join(SCHEMA_DIR, name), "r", encoding="utf-8") as fh:
        return json.load(fh)


def test_all_schemas_are_valid_json():
    for name in SCHEMA_FILES:
        doc = _load(name)
        assert isinstance(doc, dict), "%s did not parse to an object" % name
        assert doc.get("$schema"), "%s is missing $schema" % name
        assert doc.get("title"), "%s is missing a title" % name


def test_record_examples_pass_the_normalizer():
    """The schema examples must agree with the executable rules, not just the prose."""
    doc = _load("source-metadata.schema.json")
    checked = 0
    for example in doc.get("examples", []):
        # Strip the convenience fields that only exist for documentation.
        record = {k: v for k, v in example.items() if k in
                  ("schema_version", "record_id", "source_id", "retrieval", "freshness",
                   "uncertainty", "licence")}
        failures = normalize.hard_failures(record)
        assert not failures, "example failed validation: %s" % failures
        checked += 1
    assert checked >= 2, "expected at least a success and a failure example"


def test_freshness_examples_are_accepted():
    doc = _load("freshness.schema.json")
    for example in doc.get("examples", []):
        interval = example.get("expected_refresh_interval")
        normalize.parse_iso_duration(interval)
        for key in ("as_of", "retrieved_at", "manual_entry_at"):
            if example.get(key):
                normalize.parse_timestamp(example[key])


def test_uncertainty_examples_respect_measurement_rules():
    doc = _load("uncertainty.schema.json")
    for example in doc.get("examples", []):
        unc = normalize.Uncertainty(
            kind=example["kind"],
            basis=example["basis"],
            spatial_resolution_m=example.get("spatial_resolution_m"),
            suitable_for=example.get("suitable_for"),
            prohibited_claims=example.get("prohibited_claims"),
        )
        assert normalize.may_say_measured(unc) == (unc.kind in ("measured", "lab_reported"))
        # A modelled value must never be offered at field scale.
        if unc.kind == "modelled":
            assert normalize.is_suitable_for(unc, "field") is False


def test_adapter_report_can_retrieve_is_consistent():
    """A non-verified adapter must never advertise that it can retrieve."""
    doc = _load("adapter-report.schema.json")
    for report in doc.get("examples", []):
        for adapter in report.get("adapters", []):
            if adapter["integration_status"] != "verified":
                assert adapter["can_retrieve"] is False, \
                    "adapter %s is %s but claims can_retrieve" % (
                        adapter["source_id"], adapter["integration_status"])


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
