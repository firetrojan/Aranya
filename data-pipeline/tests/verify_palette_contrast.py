"""Independently verify the Aranya palette contrast claims.

The Kotlin test (ColorContrastTest) cannot run in this environment because Gradle's
native library will not load on aarch64 bionic (see docs/ARCHITECTURE.md). This script
recomputes the same WCAG checks in Python so the palette values are verified against
real arithmetic rather than assumed to be correct.

The Kotlin test remains authoritative once it can run. Keep the two in sync.

Run: python3 data-pipeline/tests/verify_palette_contrast.py
"""

import os
import re
import sys

# Parsed from android/app/src/main/java/org/aranya/app/ui/theme/Color.kt
ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
COLOR_FILE = os.path.join(ROOT, "android", "app", "src", "main", "java", "org", "aranya",
                          "app", "ui", "theme", "Color.kt")


def parse_colors(path):
    """Parse `val Name = Color(0xAARRGGBB)` declarations.

    Kotlin's Color takes 8 hex digits (alpha, red, green, blue). Alpha is ignored here
    because every token in the Aranya palette is fully opaque.
    """
    text = open(path, "r", encoding="utf-8").read()
    out = {}
    pat = r"val\s+(\w+)\s*=\s*Color\(0x([0-9A-Fa-f]{2})([0-9A-Fa-f]{2})([0-9A-Fa-f]{2})([0-9A-Fa-f]{2})\)"
    for m in re.finditer(pat, text):
        # m.group(2) is alpha; 3/4/5 are R, G, B.
        out[m.group(1)] = tuple(int(m.group(i), 16) / 255.0 for i in (3, 4, 5))
    return out


WHITE = (1.0, 1.0, 1.0)


def luminance(rgb):
    def ch(v):
        return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4
    r, g, b = rgb
    return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b)


def contrast(a, b):
    la, lb = luminance(a), luminance(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


def main():
    colors = parse_colors(COLOR_FILE)
    if len(colors) < 20:
        print("FAIL: only parsed %d colours from %s" % (len(colors), COLOR_FILE))
        return 1

    def c(name):
        if name not in colors:
            raise KeyError("token %s not found in %s" % (name, COLOR_FILE))
        return colors[name]

    # (fg, bg, min_ratio, description)
    light = [
        ("Charcoal", "Ivory", 4.5, "body text on background (light)"),
        ("Charcoal", "Parchment", 4.5, "body text on content surface (light)"),
        ("Slate", "Ivory", 4.5, "secondary text on background (light)"),
        ("Slate", "IvorySunk", 4.5, "secondary text on sunken surface (light)"),
        ("Leaf900", "Leaf050", 4.5, "primary text on provenance fill (light)"),
        ("Leaf900", "Leaf100", 4.5, "text on primary container (light)"),
        ("Bark600", "Bark100", 4.5, "secondary on secondary container (light)"),
        ("Ivory", "Leaf900", 4.5, "inverse text (light)"),
        ("RainLight", "Ivory", 4.5, "rain semantics on background (light)"),
        ("HeatLight", "Ivory", 4.5, "heat semantics on background (light)"),
        ("RiskHighLight", "Ivory", 4.5, "risk semantics on background (light)"),
    ]
    dark = [
        ("Mist", "Night", 4.5, "body text on background (dark)"),
        ("Mist", "Slate900", 4.5, "body text on content surface (dark)"),
        ("MistDim", "Night", 4.5, "secondary text on background (dark)"),
        ("MistDim", "Slate900", 4.5, "secondary text on content surface (dark)"),
        ("Leaf300", "Night", 4.5, "primary semantics on background (dark)"),
        ("Leaf900Dark", "Leaf300", 4.5, "text on primary button (dark)"),
        ("Night", "Mist", 4.5, "inverse text (dark)"),
        ("RainDark", "Night", 4.5, "rain semantics on background (dark)"),
        ("HeatDark", "Night", 4.5, "heat semantics on background (dark)"),
        ("RiskHighDark", "Night", 4.5, "risk semantics on background (dark)"),
    ]
    # button pair using white text on the light primary
    light.append(("__white__", "Leaf600", 4.5, "white text on primary button (light)"))

    failures = []
    checked = 0
    for name, pairs in (("light", light), ("dark", dark)):
        for fg, bg, need, desc in pairs:
            fg_rgb = WHITE if fg == "__white__" else c(fg)
            ratio = contrast(fg_rgb, c(bg))
            checked += 1
            status = "PASS" if ratio >= need else "FAIL"
            if ratio < need:
                failures.append("%s: %s %.2f < %.2f" % (name, desc, ratio, need))
            print("%-4s %-6s %-42s %.2f (need %.1f)" % (status, name, desc, ratio, need))

    # AAA for the primary numeric readout.
    for label, fg, bg in (("light", "Charcoal", "Ivory"), ("dark", "Mist", "Night")):
        ratio = contrast(c(fg), c(bg))
        ok = ratio >= 7.0
        checked += 1
        if not ok:
            failures.append("%s numeric readout AAA: %.2f < 7.0" % (label, ratio))
        print("%-4s %-6s %-42s %.2f (need 7.0)" % ("PASS" if ok else "FAIL", label,
                                                   "numeric readout AAA", ratio))

    # Semantic colours must separate on lightness, not hue alone.
    for label, names in (
        ("light", ["RainLight", "HeatLight", "DroughtLight", "RiskHighLight"]),
        ("dark", ["RainDark", "HeatDark", "DroughtDark", "RiskHighDark"]),
    ):
        for i, a in enumerate(names):
            for b in names[i + 1:]:
                delta = abs(luminance(c(a)) - luminance(c(b)))
                checked += 1
                ok = delta > 0.02
                if not ok:
                    failures.append("%s: %s vs %s luminance delta %.4f too small" % (label, a, b, delta))
                print("%-4s %-6s %-42s %.4f (need >0.02)" % ("PASS" if ok else "FAIL", label,
                                                             "%s/%s separation" % (a, b), delta))

    print("\n%d/%d checks passed" % (checked - len(failures), checked))
    if failures:
        print("\nFailures:")
        for f in failures:
            print("  - " + f)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
