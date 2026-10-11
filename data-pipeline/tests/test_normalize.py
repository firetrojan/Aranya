"""Tests for normalization, freshness and uncertainty rules.

These are the rules that stop Aranya from presenting an estimate as a measurement, so
they are tested hard and in isolation: no network, no credentials, no Android.

Run: python3 data-pipeline/tests/test_normalize.py
"""

from __future__ import annotations

import os
import sys
from datetime import datetime, timedelta, timezone

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "data-pipeline", "normalizers"))

from normalize import (  # noqa: E402
    Freshness,
    FreshValue,
    NormalizationError,
    Uncertainty,
    compute_freshness_state,
    describe_value_for_user,
    hard_failures,
    is_suitable_for,
    may_say_measured,
    parse_iso_duration,
    parse_timestamp,
    validate_record,
)

NOW = datetime(2026, 10, 10, 12, 0, 0, tzinfo=timezone.utc)


def _soil_estimate():
    """A SoilGrids-style modelled soil value: exactly the case Aranya must not fake."""
    return FreshValue(
        source_id="isric_soilgrids",
        category="soil",
        value=7.2,
        unit="pH",
        freshness=Freshness(
            as_of="2026-10-10T00:00:00Z",
            retrieved_at="2026-10-10T08:00:00Z",
            provider="isric_soilgrids",
            expected_refresh_interval="P365D",
            state="fresh",
        ),
        uncertainty=Uncertainty(
            kind="modelled",
            basis="statistical_interpolation",
            spatial_resolution_m=250,
            suitable_for=["national", "state", "district"],
            prohibited_claims=["your soil", "measured value", "lab result"],
        ),
    )


def _lab_result():
    """A farmer's own Soil Health Card result: legitimately a measurement."""
    return FreshValue(
        source_id="user_entry",
        category="soil",
        value=6.8,
        unit="pH",
        freshness=Freshness(
            as_of=None,
            retrieved_at=None,
            manual_entry_at="2026-10-09T17:40:00+05:30",
            provider=None,
            expected_refresh_interval="P180D",
            state="fresh",
        ),
        uncertainty=Uncertainty(
            kind="lab_reported",
            basis="laboratory_analysis",
            spatial_resolution_m=None,
            suitable_for=["field", "plot"],
            prohibited_claims=[],
            confidence_label="high",
        ),
    )


def test_timestamp_without_timezone_is_rejected():
    """Freshness cannot be compared reliably without a zone, so naive times are refused."""
    try:
        parse_timestamp("2026-10-10T08:00:00")
    except NormalizationError as exc:
        assert "no timezone" in str(exc)
        return
    raise AssertionError("naive timestamp was accepted; freshness comparisons would be unsafe")


def test_iso_duration_parsing():
    assert parse_iso_duration("P1D") == timedelta(days=1)
    assert parse_iso_duration("PT6H") == timedelta(hours=6)
    assert parse_iso_duration("P180D") == timedelta(days=180)
    for bad in ("1D", "", "P", "forever"):
        try:
            parse_iso_duration(bad)
        except NormalizationError:
            continue
        raise AssertionError("bad duration %r was accepted" % bad)


def test_freshness_states_progress_from_fresh_to_expired():
    now = datetime(2026, 10, 10, 12, 0, 0, tzinfo=timezone.utc)
    cases = [
        (timedelta(hours=1), "P1D", "fresh"),      # retrieved 1h ago, 24h interval
        (timedelta(hours=18), "P1D", "aging"),     # past half, still inside interval
        (timedelta(hours=30), "P1D", "stale"),     # just over one interval
        (timedelta(days=5), "P1D", "expired"),     # far beyond the interval
    ]
    for age, interval, expected in cases:
        retrieved = (now - age).isoformat()
        got = compute_freshness_state(None, retrieved, interval, now=now)
        assert got == expected, "age=%s interval=%s expected %s got %s" % (age, interval, expected, got)

    # A valid time still in the future (a forecast) is 'live', not stale.
    future = (now + timedelta(minutes=30)).isoformat()
    assert compute_freshness_state(future, None, "PT6H", now=now) == "live"


def test_freshness_unknown_when_no_timestamp():
    assert compute_freshness_state(None, None, "P1D", now=NOW) == "unknown"


def test_modelled_soil_value_may_not_be_called_measured():
    value = _soil_estimate()
    assert may_say_measured(value.uncertainty) is False


def test_lab_result_may_be_called_measured():
    value = _lab_result()
    assert may_say_measured(value.uncertainty) is True


def test_missing_uncertainty_means_not_measured():
    """Unknown provenance is never treated as authoritative."""
    assert may_say_measured(None) is False


def test_modelled_soil_not_suitable_for_field_scale():
    """ISRIC explicitly advises against farm-level use of gridded soil models."""
    value = _soil_estimate()
    assert is_suitable_for(value.uncertainty, "district") is True
    assert is_suitable_for(value.uncertainty, "field") is False
    assert is_suitable_for(value.uncertainty, "plot") is False


def test_soil_estimate_display_is_blocked_at_field_scale():
    contract = describe_value_for_user(_soil_estimate(), "field")
    assert contract["may_say_measured"] is False
    assert contract["suitable_for_requested_scale"] is False
    assert contract["required_qualifier"] == "regional_estimate_not_a_field_test"
    assert "lab result" in contract["blocked_claims"]


def test_lab_result_display_allows_measurement_qualifier():
    contract = describe_value_for_user(_lab_result(), "field")
    assert contract["may_say_measured"] is True
    assert contract["suitable_for_requested_scale"] is True
    assert contract["required_qualifier"] == "measurement"


def test_stale_value_is_labelled_outdated():
    value = _soil_estimate()
    value.freshness.state = "expired"
    contract = describe_value_for_user(value, "district")
    assert contract["required_qualifier"].startswith("outdated_"), \
        "an expired value must never be presented as current"


def test_weather_estimate_uses_estimate_qualifier():
    value = FreshValue(
        source_id="open_meteo_forecast",
        category="weather",
        value=29.5,
        unit="degC",
        freshness=Freshness(
            as_of="2026-10-11T00:00:00+05:30",
            retrieved_at="2026-10-10T08:12:44Z",
            provider="open_meteo_forecast",
            expected_refresh_interval="PT6H",
            state="fresh",
        ),
        uncertainty=Uncertainty(
            kind="modelled",
            basis="numerical_model",
            spatial_resolution_m=9000,
            suitable_for=["national", "state", "district", "block"],
            prohibited_claims=["measured rainfall at your field"],
        ),
    )
    contract = describe_value_for_user(value, "district")
    assert contract["required_qualifier"] == "estimate"
    assert contract["may_say_measured"] is False
    assert contract["requires_attribution"] is True


def _valid_record():
    return {
        "schema_version": 1,
        "source_id": "open_meteo_forecast",
        "retrieval": {
            "attempted_at": "2026-10-10T08:12:00Z",
            "completed_at": "2026-10-10T08:12:44Z",
            "outcome": "ok",
            "http_status": 200,
            "error": None,
            "bytes": 4821,
            "cache": "network",
        },
        "freshness": {
            "as_of": "2026-10-11T00:00:00+05:30",
            "retrieved_at": "2026-10-10T08:12:44Z",
            "provider": "open_meteo_forecast",
            "expected_refresh_interval": "PT6H",
            "state": "fresh",
            "partial": False,
        },
        "uncertainty": {
            "kind": "modelled",
            "basis": "numerical_model",
            "spatial_resolution_m": 9000,
            "suitable_for": ["district"],
            "prohibited_claims": [],
        },
    }


def test_valid_record_has_no_problems():
    assert validate_record(_valid_record()) == []


def test_failed_retrieval_requires_localized_error_key():
    record = _valid_record()
    record["retrieval"] = {
        "attempted_at": "2026-10-10T08:12:00Z",
        "completed_at": None,
        "outcome": "failed",
        "http_status": None,
        "error": {
            "kind": "timeout",
            "message_key": "error_network_timeout",
            "detail": "SocketTimeoutException",
            "retryable": True,
            "retry_after_seconds": None,
        },
        "bytes": None,
        "cache": "network",
    }
    assert validate_record(record) == []


def test_failed_retrieval_without_error_is_rejected():
    record = _valid_record()
    record["retrieval"]["outcome"] = "failed"
    record["retrieval"]["error"] = None
    problems = validate_record(record)
    assert any("must carry an error object" in p for p in problems)


def test_error_without_message_key_is_rejected():
    """Raw exception text must never become the farmer-facing message."""
    record = _valid_record()
    record["retrieval"]["outcome"] = "failed"
    record["retrieval"]["error"] = {
        "kind": "network",
        "detail": "java.net.UnknownHostException",
        "retryable": True,
    }
    problems = validate_record(record)
    assert any("message_key is required" in p for p in problems)


def test_ok_outcome_must_not_carry_an_error():
    record = _valid_record()
    record["retrieval"]["error"] = {
        "kind": "timeout",
        "message_key": "error_network_timeout",
        "retryable": True,
    }
    problems = validate_record(record)
    assert any("must not carry an error" in p for p in problems)


def test_kind_and_basis_must_be_consistent():
    """A record cannot claim a lab analysis while declaring a numerical model."""
    record = _valid_record()
    record["uncertainty"]["kind"] = "lab_reported"
    problems = validate_record(record)
    assert any("inconsistent with basis" in p for p in problems)


def test_unknown_freshness_is_advisory_not_fatal():
    """Unknown age is allowed but must be surfaced, not silently dropped."""
    record = _valid_record()
    record["freshness"] = {
        "as_of": None,
        "retrieved_at": None,
        "expected_refresh_interval": "PT6H",
        "state": "unknown",
        "partial": False,
    }
    all_problems = validate_record(record)
    assert any(p.startswith("NOTE:") for p in all_problems), "expected an advisory about unknown freshness"
    assert hard_failures(record) == [], "unknown freshness must not be a hard failure"


def test_wrong_schema_version_is_rejected():
    record = _valid_record()
    record["schema_version"] = 99
    problems = validate_record(record)
    assert any("schema_version" in p for p in problems)


def test_bad_freshness_interval_is_rejected():
    record = _valid_record()
    record["freshness"]["expected_refresh_interval"] = "six hours"
    problems = validate_record(record)
    assert any("ISO-8601 duration" in p or "refresh interval" in p for p in problems)


def test_bad_error_kind_is_rejected():
    record = _valid_record()
    record["retrieval"]["outcome"] = "failed"
    record["retrieval"]["error"] = {
        "kind": "gremlins",
        "message_key": "error_unknown",
        "retryable": False,
    }
    problems = validate_record(record)
    assert any("error.kind" in p for p in problems)


def test_unknown_spatial_scale_is_rejected():
    record = _valid_record()
    record["uncertainty"]["suitable_for"] = ["farmyard"]
    problems = validate_record(record)
    assert any("unknown scale" in p for p in problems)


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
