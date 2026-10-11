"""Search for a dark chart-series palette where every pair clears the contrast floor.

Tweaking one value at a time just moves the collision to another pair. This searches the
lightness axis within the four existing hue families and reports a set that is valid in
BOTH themes, so adjacent series stay separable without relying on hue.

Run: python3 data-pipeline/tests/pick_chart_series.py
"""

import itertools
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
MIN_PAIR_CONTRAST = 1.15

# Hue families for the four series, matching the existing palette: green, amber, blue, bark.
FAMILIES = [
    ("green", (0.35, 0.42, 0.34)),
    ("amber", (0.83, 0.55, 0.28)),
    ("blue", (0.22, 0.48, 0.66)),
    ("bark", (0.48, 0.40, 0.30)),
]


def luminance(rgb):
    def ch(v):
        return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4
    r, g, b = rgb
    return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b)


def contrast(a, b):
    la, lb = luminance(a), luminance(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


def scale(rgb, k):
    return tuple(min(1.0, max(0.0, c * k)) for c in rgb)


def hexcolor(rgb):
    return "0xFF%02X%02X%02X" % tuple(round(c * 255) for c in rgb)


def all_pairs_ok(colors):
    for i in range(len(colors)):
        for j in range(i + 1, len(colors)):
            if contrast(colors[i], colors[j]) < MIN_PAIR_CONTRAST:
                return False
    return True


def search(label, candidates):
    """Greedy spread: pick the combination with the largest minimum pairwise contrast."""
    best = None
    best_score = -1.0
    for combo in itertools.product(*candidates):
        if not all_pairs_ok(combo):
            continue
        score = min(contrast(combo[i], combo[j])
                    for i in range(len(combo)) for j in range(i + 1, len(combo)))
        if score > best_score:
            best_score = score
            best = combo
    if best is None:
        print("%s: no valid combination found" % label)
        return None
    print("%s (worst pair %.3f):" % (label, best_score))
    for name, (_fam, base), col in zip(FAMILIES, (f for f in FAMILIES), best):
        print("    %-6s %-8s #%s" % (label, name, hexcolor(col)))
    return best


def main():
    # Light theme: mid-to-deep tones on an ivory surface.
    light_steps = [0.62, 0.70, 0.78, 0.86, 0.94, 1.02, 1.10, 1.18]
    light = [[scale(base, k) for k in light_steps] for _f, base in FAMILIES]
    print("=== light candidates ===")
    best_light = search("light", light)

    # Dark theme: on a near-black surface the series must be light enough to read, so
    # the usable range sits above mid-grey and the spread comes from lightness ordering.
    dark_steps = [0.55, 0.65, 0.75, 0.85, 0.95, 1.05, 1.20, 1.35, 1.50]
    dark = [[scale(base, k) for k in dark_steps] for _f, base in FAMILIES]
    print()
    print("=== dark candidates ===")
    best_dark = search("dark", dark)

    print()
    print("Paste into Color.kt as:")
    if best_light:
        for i, col in enumerate(best_light, 1):
            print("internal val ChartSeries%dLight = Color(%s)" % (i, hexcolor(col)))
    if best_dark:
        for i, col in enumerate(best_dark, 1):
            print("internal val ChartSeries%dDark = Color(%s)" % (i, hexcolor(col)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
