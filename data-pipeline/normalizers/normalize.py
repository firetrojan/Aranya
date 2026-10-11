"""Normalization and validation rules for agricultural values.

This module is the mechanism behind Aranya's core honesty rule: a modelled or
statistically interpolated value must never be renderable as a laboratory
measurement. The rules here are unit-testable without network access, so they can be
verified in any environment, including CI without credentials.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from enum import Enum
from typing import Dict, List, Optional

SCHEMA_VERSION = 1

VALID_STATUSES = ("verified", "investigating", "restricted", "unavailable", "not_integrated")
VALID_OUTCOMES = ("ok", "partial", "failed", "not_attempted", "skipped_unavailable")
VALID_FRESHNESS_STATES = ("live", "fresh", "aging", "stale", "expired", "unknown")
VALID_ERROR_KINDS = (
    "network", "timeout", "dns", "tls", "http", "auth",
    "rate_limited", "parse", "validation", "not_implemented",
    "unavailable", "unknown",
)

# Kinds that legitimately support the words "measured" or "lab result".
MEASURED_KINDS = ("measured", "lab_reported")

# Basis -> kinds that are consistent with it. Catches a record claiming to be a
# laboratory analysis while declaring itself a numerical model.
BASIS_TO_KINDS = {
    "field_observation": ("measured", "user_reported", "estimated"),
    "laboratory_analysis": ("lab_reported", "measured"),
    "station_observation": ("measured", "modelled"),
    "satellite_retrieval": ("remote_sensing", "modelled", "estimated"),
    "reanalysis": ("modelled", "remote_sensing"),
    "numerical_model": ("modelled", "estimated"),
    "statistical_interpolation": ("modelled", "estimated"),
    "remote_report": ("remote_sensing", "estimated"),
    "user_input": ("user_reported", "measured"),
    "unknown": ("unknown",),
}

# Spatial scales, ordered coarse -> fine, so suitability can be compared.
SCALE_ORDER = ["national", "state", "district", "block", "village", "field", "plot"]
SCALE_INDEX = {s: i for i, s in enumerate(SCALE_ORDER)}

# Categories whose values must never be presented beyond their natural scale.
FARM_SCALE_CATEGORIES = ("soil",)


class NormalizationError(ValueError):
    pass


@dataclass
class Freshness:
    as_of: Optional[str] = None
    retrieved_at: Optional[str] = None
    manual_entry_at: Optional[str] = None
    provider: Optional[str] = None
    expected_refresh_interval: str = "P1D"
    state: str = "unknown"
    partial: bool = False


@dataclass
class Uncertainty:
    kind: str = "unknown"
    basis: str = "unknown"
    spatial_resolution_m: Optional[float] = None
    suitable_for: Optional[List[str]] = None
    prohibited_claims: Optional[List[str]] = None
    confidence_label: Optional[str] = None


@dataclass
class FreshValue:
    """A single agricultural value with provenance, ready for persistence or display."""

    source_id: str
    category: str
    value: object
    unit: Optional[str]
    freshness: Freshness
    uncertainty: Optional[Uncertainty] = None
    geo: Optional[Dict[str, object]] = None

    def describe(self) -> str:
        """One-line human summary. Safe to log; not intended for direct display."""
        return "%s %s %s from %s (%s)" % (
            self.category, self.value, self.unit or "", self.source_id, self.freshness.state,
        )


def parse_timestamp(text: Optional[str]) -> Optional[datetime]:
    if text is None:
        return None
    cleaned = text.strip()
    if cleaned.endswith("Z"):
        cleaned = cleaned[:-1] + "+00:00"
    try:
        parsed = datetime.fromisoformat(cleaned)
    except ValueError as exc:
        raise NormalizationError("not an ISO-8601 timestamp: %r (%s)" % (text, exc))
    if parsed.tzinfo is None:
        raise NormalizationError(
            "timestamp %r has no timezone; Aranya never stores naive times because freshness "
            "cannot be compared reliably" % text
        )
    return parsed


def parse_iso_duration(text: str) -> timedelta:
    """Parse the subset of ISO-8601 durations used for refresh intervals."""
    import re

    m = re.match(r"^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?$", text.strip())
    if not m or not any(m.groups()):
        raise NormalizationError("not a usable ISO-8601 duration: %r" % text)
    days, hours, minutes, seconds = (int(g) if g else 0 for g in m.groups())
    return timedelta(days=days, hours=hours, minutes=minutes, seconds=seconds)


def compute_freshness_state(
    as_of: Optional[str],
    retrieved_at: Optional[str],
    expected_refresh_interval: str,
    now: Optional[datetime] = None,
) -> str:
    """Derive the freshness state. Returns 'unknown' rather than inventing a value.

    Thresholds are deliberately conservative: a value is only called 'fresh' inside
    half its refresh interval, and 'expired' at four times that interval.
    """
    now = now or datetime.now(timezone.utc)
    reference = parse_timestamp(retrieved_at) or parse_timestamp(as_of)
    if reference is None:
        return "unknown"
    interval = parse_iso_duration(expected_refresh_interval)
    if interval.total_seconds() <= 0:
        raise NormalizationError("refresh interval must be positive: %r" % expected_refresh_interval)
    age = now - reference.astimezone(timezone.utc)
    ratio = age / interval
    if ratio < 0:
        return "live"
    if ratio <= 0.5:
        return "fresh"
    if ratio <= 1.0:
        return "aging"
    if ratio <= 4.0:
        return "stale"
    return "expired"


def is_suitable_for(uncertainty: Uncertainty, scale: str) -> bool:
    """Whether a value may inform a decision at the given spatial scale.

    Suitable means the value is valid at a scale at least as coarse as the request:
    a district-level estimate is legitimate context for a village question, but a
    single plot's reading says nothing about a whole district.
    """
    if uncertainty is None or not uncertainty.suitable_for:
        return False
    if scale not in SCALE_INDEX:
        raise NormalizationError("unknown spatial scale: %r" % scale)
    requested = SCALE_INDEX[scale]
    usable = [SCALE_INDEX[s] for s in uncertainty.suitable_for if s in SCALE_INDEX]
    if not usable:
        return False
    # The value is usable only if it resolves something at least as fine as the
    # question being asked. A district-wide estimate cannot answer a field-level
    # question; it can only give background to a national or district question.
    return max(usable) >= requested


def may_say_measured(uncertainty: Optional[Uncertainty]) -> bool:
    """The single question the UI must ask before using the word 'measured'."""
    if uncertainty is None:
        return False
    return uncertainty.kind in MEASURED_KINDS


def describe_value_for_user(value: FreshValue, scale: str) -> Dict[str, object]:
    """Produce the display contract for a value.

    Returns flags rather than prose so the decision stays testable and the wording
    itself stays localizable.
    """
    unc = value.uncertainty
    measured = may_say_measured(unc)
    suitable = is_suitable_for(unc, scale) if unc else False

    if not measured:
        required_qualifier = "estimate"
    else:
        required_qualifier = "measurement"

    if value.category in FARM_SCALE_CATEGORIES and unc is not None:
        if unc.basis == "statistical_interpolation" and scale in ("field", "plot"):
            # ISRIC explicitly advises against farm-level use of gridded soil models.
            suitable = False
            required_qualifier = "regional_estimate_not_a_field_test"

    if value.freshness.state in ("stale", "expired"):
        required_qualifier = "outdated_" + required_qualifier

    return {
        "required_qualifier": required_qualifier,
        "may_say_measured": measured,
        "suitable_for_requested_scale": suitable,
        "must_show_timestamp": value.freshness.state != "unknown" or value.freshness.as_of is not None,
        "freshness_state": value.freshness.state,
        "blocked_claims": list(unc.prohibited_claims) if unc and unc.prohibited_claims else [],
        "requires_attribution": True,
        "attribution_text": None,  # Filled by the UI from the source registry.
    }


def validate_record(record: Dict[str, object]) -> List[str]:
    """Validate a record envelope against the Stage 01 conventions.

    Returns a list of problems. This mirrors data/schema/source-metadata.schema.json
    without requiring a JSON Schema library, so it runs in the bare build environment.
    """
    problems: List[str] = []

    if record.get("schema_version") != SCHEMA_VERSION:
        problems.append("schema_version must be %d, got %r" % (SCHEMA_VERSION, record.get("schema_version")))

    source_id = record.get("source_id")
    if not isinstance(source_id, str) or not source_id:
        problems.append("source_id is required")

    retrieval = record.get("retrieval")
    if not isinstance(retrieval, dict):
        problems.append("retrieval must be an object")
    else:
        outcome = retrieval.get("outcome")
        if outcome not in VALID_OUTCOMES:
            problems.append("retrieval.outcome %r is not one of %s" % (outcome, ", ".join(VALID_OUTCOMES)))
        try:
            parse_timestamp(retrieval.get("attempted_at"))
        except NormalizationError as exc:
            problems.append(str(exc))
        err = retrieval.get("error")
        if outcome in ("failed", "partial") and not isinstance(err, dict):
            problems.append("retrieval.outcome %r must carry an error object" % outcome)
        if outcome == "ok" and isinstance(err, dict):
            problems.append("retrieval.outcome 'ok' must not carry an error object")
        if isinstance(err, dict):
            kind = err.get("kind")
            if kind not in VALID_ERROR_KINDS:
                problems.append("error.kind %r is not one of %s" % (kind, ", ".join(VALID_ERROR_KINDS)))
            if not err.get("message_key"):
                problems.append("error.message_key is required so the UI can localize the failure")
            if err.get("retryable") is None:
                problems.append("error.retryable is required so retries can be scheduled sensibly")

    freshness = record.get("freshness")
    if not isinstance(freshness, dict):
        problems.append("freshness must be an object")
    else:
        state = freshness.get("state")
        if state not in VALID_FRESHNESS_STATES:
            problems.append("freshness.state %r is not one of %s" % (state, ", ".join(VALID_FRESHNESS_STATES)))
        for key in ("as_of", "retrieved_at", "manual_entry_at"):
            if freshness.get(key) is not None:
                try:
                    parse_timestamp(freshness.get(key))
                except NormalizationError as exc:
                    problems.append(str(exc))
        if not freshness.get("expected_refresh_interval"):
            problems.append("freshness.expected_refresh_interval is required")
        else:
            try:
                parse_iso_duration(freshness["expected_refresh_interval"])
            except NormalizationError as exc:
                problems.append(str(exc))
        if freshness.get("state") == "unknown" and not freshness.get("as_of"):
            # Not an error: unknown freshness is legitimate. Recorded so a caller can
            # assert it was a deliberate choice rather than an oversight.
            problems.append(
                "NOTE: freshness.state is 'unknown' with no as_of; the UI must say the age is unknown "
                "rather than hide it"
            )

    unc = record.get("uncertainty")
    if isinstance(unc, dict):
        kind = unc.get("kind")
        basis = unc.get("basis")
        if basis in BASIS_TO_KINDS and kind not in BASIS_TO_KINDS[basis]:
            problems.append(
                "uncertainty.kind %r is inconsistent with basis %r (expected one of %s)"
                % (kind, basis, ", ".join(BASIS_TO_KINDS[basis]))
            )
        suitable = unc.get("suitable_for") or []
        for s in suitable:
            if s not in SCALE_INDEX:
                problems.append("uncertainty.suitable_for contains unknown scale %r" % s)
        if unc.get("spatial_resolution_m") is not None and unc.get("spatial_resolution_m") <= 0:
            problems.append("uncertainty.spatial_resolution_m must be positive")

    licence = record.get("licence")
    if isinstance(licence, dict):
        if licence.get("attribution_required") and not licence.get("attribution_text"):
            problems.append("licence requires attribution but no attribution_text is stored")

    return problems


def hard_failures(record: Dict[str, object]) -> List[str]:
    """Only genuine blocking errors, excluding the advisory 'NOTE' entry."""
    return [p for p in validate_record(record) if not p.startswith("NOTE:")]
