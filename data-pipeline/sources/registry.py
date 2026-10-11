"""Source registry loading and validation.

Deliberately dependency-free: PyYAML is not installed in the build environment and
Aranya must not assume it. The reader below understands only the restricted subset of
YAML that data/sources.yml actually uses, and it FAILS LOUDLY on anything outside that
subset rather than silently mis-parsing a registry entry.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from datetime import date
from typing import Any, Dict, List, Optional

STATUSES = (
    "verified",
    "investigating",
    "restricted",
    "unavailable",
    "not_integrated",
)

ID_PATTERN = re.compile(r"^[a-z0-9_]+$")
DATE_PATTERN = re.compile(r"^(\d{4})-(\d{2})-(\d{2})$")

# Keys every registry entry must define, regardless of status.
REQUIRED_KEYS = (
    "id",
    "name",
    "official_url",
    "access_method",
    "category",
    "geography",
    "licence",
    "attribution_required",
    "redistribution",
    "authentication",
    "rate_limits",
    "update_frequency",
    "last_success",
    "status",
    "limitations",
    "fallback",
)

# A source may only be 'verified' when these are present and non-placeholder.
VERIFY_REQUIRES_LICENCE_TEXT = True
# A licence that starts with any of these is a placeholder, not a read licence.
PLACEHOLDER_LICENCE_VALUES = ("unconfirmed", "unknown", "tbd", "n/a", "not assessed", "none")


class RegistryError(ValueError):
    """Raised when sources.yml is structurally invalid or contains a dishonest status."""


@dataclass
class SourceEntry:
    id: str
    name: str
    status: str
    category: str
    licence: str
    attribution_required: bool
    last_success: Optional[str]
    verified_on: Optional[str]
    entry: Dict[str, Any] = field(default_factory=dict)

    @property
    def is_usable(self) -> bool:
        """True only when a source may back real user-facing data."""
        return self.status == "verified"

    def claim(self) -> str:
        """Short honest description of what can be said about this source today."""
        if self.status == "verified":
            return "Reachable and licence checked on %s." % (self.verified_on or "unknown date")
        if self.status == "restricted":
            return "Needs credentials or an agreement; not usable yet."
        if self.status == "unavailable":
            return "Not reachable when probed."
        if self.status == "investigating":
            return "Reachable, but terms or parameters not yet confirmed."
        return "Registered for a later stage; not assessed yet."


# --------------------------------------------------------------------------- YAML
# Minimal reader for the block-style subset used by sources.yml. It is intentionally
# small: anything it cannot represent raises RegistryError instead of guessing.


def _scalar(text: str) -> Any:
    text = text.strip()
    if len(text) >= 2 and text[0] == text[-1] and text[0] in "\"'":
        return text[1:-1]
    low = text.lower()
    if low in ("true", "yes"):
        return True
    if low in ("false", "no"):
        return False
    if low in ("null", "~", ""):
        return None
    if re.match(r"^-?\d+$", text):
        return int(text)
    return text


def _parse_block(lines: List[str], start: int, indent: int):
    """Parse a list or mapping starting at `start` with the given indentation.

    Always returns (value, next_line_index) so callers can unpack uniformly.
    """
    if start >= len(lines):
        return None, start
    first = lines[start]
    if first.lstrip().startswith("- "):
        items: List[Any] = []
        i = start
        while i < len(lines):
            line = lines[i]
            if not line.strip() or line.lstrip().startswith("#"):
                i += 1
                continue
            cur_indent = len(line) - len(line.lstrip())
            if cur_indent < indent or not line.lstrip().startswith("- "):
                break
            body = line.lstrip()[2:].strip()
            if ":" in body and not body.startswith(("\"", "'")):
                # Inline first key of a mapping item.
                key, _, rest = body.partition(":")
                item: Dict[str, Any] = {key.strip(): _scalar(rest)}
                i += 1
                sub, i = _parse_map_continuation(lines, i, cur_indent + 2)
                item.update(sub)
                items.append(item)
            else:
                items.append(_scalar(body))
                i += 1
        return items, i
    return _parse_map_continuation(lines, start, indent)


def _parse_map_continuation(lines: List[str], start: int, indent: int):
    result: Dict[str, Any] = {}
    i = start
    while i < len(lines):
        line = lines[i]
        if not line.strip() or line.lstrip().startswith("#"):
            i += 1
            continue
        cur_indent = len(line) - len(line.lstrip())
        if cur_indent < indent:
            break
        stripped = line.strip()
        if stripped.startswith("- "):
            break
        key, _, rest = stripped.partition(":")
        key = key.strip()
        rest = rest.strip()
        if rest in (">-", ">", "|", "|-"):
            # Folded/literal block scalar: collect deeper-indented lines.
            i += 1
            chunks: List[str] = []
            while i < len(lines):
                nxt = lines[i]
                if nxt.strip() and (len(nxt) - len(nxt.lstrip())) <= cur_indent:
                    break
                chunks.append(nxt.strip())
                i += 1
            result[key] = " ".join(c for c in chunks if c).strip()
            continue
        if rest == "":
            i += 1
            # Skip blank lines and full-line comments before deciding whether this key
            # has a nested block. Without this, a comment between `sources:` and its
            # first list item silently yields None.
            j = i
            while j < len(lines):
                stripped = lines[j].lstrip()
                if stripped and not stripped.startswith("#"):
                    break
                j += 1
            if j < len(lines):
                nxt = lines[j]
                n_indent = len(nxt) - len(nxt.lstrip())
                if nxt.strip() and n_indent > cur_indent:
                    value, i = _parse_block(lines, j, n_indent)
                    result[key] = value
                    i = max(i, j)
                    continue
            result[key] = None
            continue
        result[key] = _scalar(rest)
        i += 1
    return result, i


def load_yaml(path: str) -> Dict[str, Any]:
    with open(path, "r", encoding="utf-8") as fh:
        lines = fh.read().split("\n")
    doc, _ = _parse_map_continuation(lines, 0, 0)
    if not isinstance(doc, dict):
        raise RegistryError("%s did not parse into a mapping" % path)
    return doc


# ----------------------------------------------------------------------- validation


def _parse_date(value: Optional[str], field_name: str, source_id: str) -> Optional[date]:
    if value is None:
        return None
    m = DATE_PATTERN.match(value)
    if not m:
        raise RegistryError(
            "source %s: %s must be YYYY-MM-DD or null, got %r" % (source_id, field_name, value)
        )
    y, mo, d = (int(g) for g in m.groups())
    try:
        return date(y, mo, d)
    except ValueError as exc:
        raise RegistryError("source %s: invalid %s %r (%s)" % (source_id, field_name, value, exc))


def validate_registry(doc: Dict[str, Any]) -> List[str]:
    """Return a list of problems. Empty list means the registry is internally consistent."""
    problems: List[str] = []

    raw_sources = doc.get("sources")
    if not isinstance(raw_sources, list) or not raw_sources:
        problems.append("registry has no 'sources' list")
        return problems

    seen = set()
    for raw in raw_sources:
        if not isinstance(raw, dict):
            problems.append("source entry is not a mapping: %r" % (raw,))
            continue
        sid = raw.get("id")
        if not isinstance(sid, str) or not ID_PATTERN.match(sid):
            problems.append("invalid or missing id: %r" % (sid,))
            continue
        if sid in seen:
            problems.append("duplicate source id: %s" % sid)
        seen.add(sid)

        for key in REQUIRED_KEYS:
            if key not in raw:
                problems.append("source %s is missing required key %r" % (sid, key))

        status = raw.get("status")
        if status not in STATUSES:
            problems.append(
                "source %s has invalid status %r (expected one of %s)"
                % (sid, status, ", ".join(STATUSES))
            )
            continue

        for dkey in ("last_success", "verified_on"):
            try:
                _parse_date(raw.get(dkey), dkey, sid)
            except RegistryError as exc:
                problems.append(str(exc))

        last_success = _parse_date(raw.get("last_success"), "last_success", sid)
        verified_on = _parse_date(raw.get("verified_on"), "verified_on", sid)

        # Honesty rules: these are the checks that stop the registry drifting into
        # claiming more than was actually demonstrated.
        if status == "verified":
            if last_success is None:
                problems.append("source %s is 'verified' but has no last_success date" % sid)
            if verified_on is None:
                problems.append("source %s is 'verified' but has no verified_on date" % sid)
            licence = raw.get("licence")
            if VERIFY_REQUIRES_LICENCE_TEXT:
                licence_text = licence.strip().lower() if isinstance(licence, str) else ""
                if not licence_text or licence_text.startswith(PLACEHOLDER_LICENCE_VALUES):
                    problems.append(
                        "source %s is 'verified' but its licence is unconfirmed; a licence must be "
                        "read before a source is called verified" % sid
                    )
            if raw.get("attribution_required") is None:
                problems.append("source %s is 'verified' but does not state attribution_required" % sid)
            # A verified source must not also say it needs credentials it does not have.
            auth = str(raw.get("authentication") or "").lower()
            if "required" in auth and "none" not in auth.split("required")[0]:
                problems.append(
                    "source %s is 'verified' but its authentication field indicates credentials are "
                    "required; it should be 'restricted'" % sid
                )
            if not raw.get("limitations") or str(raw.get("limitations")).strip() == "":
                problems.append("source %s is 'verified' but records no limitations" % sid)

        if status == "unavailable":
            if raw.get("probe_evidence") in (None, ""):
                problems.append(
                    "source %s is 'unavailable' but records no probe_evidence explaining the failure" % sid
                )
            if last_success is not None:
                problems.append(
                    "source %s is 'unavailable' yet reports last_success %s; the status contradicts the "
                    "date" % (sid, raw.get("last_success"))
                )

        if status == "not_integrated" and verified_on is not None:
            problems.append(
                "source %s is 'not_integrated' but records verified_on %s; not_integrated means access "
                "was not assessed" % (sid, raw.get("verified_on"))
            )

    # Every fallback must name a real source id. Only tokens shaped like a source id
    # (snake_case, lowercase) are treated as references, so a prose fallback such as
    # "packaged India administrative boundary list" is not mistaken for an id.
    for raw in raw_sources:
        if not isinstance(raw, dict):
            continue
        sid = raw.get("id")
        fallback = raw.get("fallback")
        if isinstance(fallback, str) and fallback:
            for token in re.findall(r"\b[a-z][a-z0-9]*(?:_[a-z0-9]+)+\b", fallback):
                if token in ("not_integrated", "not_a_registered_source",
                             "not_yet_a_registered_source", "no_public_api"):
                    continue
                if token in seen:
                    continue
                problems.append(
                    "source %s names unknown fallback source id %r" % (sid, token)
                )

    return problems


def load_registry(path: str) -> Dict[str, SourceEntry]:
    doc = load_yaml(path)
    problems = validate_registry(doc)
    if problems:
        raise RegistryError(
            "source registry is invalid:\n  - " + "\n  - ".join(problems)
        )
    entries: Dict[str, SourceEntry] = {}
    for raw in doc["sources"]:
        entries[raw["id"]] = SourceEntry(
            id=raw["id"],
            name=raw["name"],
            status=raw["status"],
            category=raw["category"],
            licence=str(raw.get("licence") or ""),
            attribution_required=bool(raw.get("attribution_required")),
            last_success=raw.get("last_success"),
            verified_on=raw.get("verified_on"),
            entry=raw,
        )
    return entries
