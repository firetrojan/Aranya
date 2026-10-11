"""Check that icons referenced from Kotlin are available in the declared dependency.

Aranya depends on androidx.compose.material:material-icons-core, which is a deliberately
small subset of the full icon set. If WbSunny or List is only in the extended artifact, the
build fails with an unresolved reference. This checks before CI does.

Run: python3 data-pipeline/tests/verify_icons.py
"""

import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
KT = os.path.join(ROOT, "android", "app", "src", "main", "java")

# Icons present in material-icons-core. Subset confirmed against the Compose artifact.
CORE_ICONS = {
    "Add", "ArrowBack", "ArrowDropDown", "ArrowForward", "Check", "Clear", "Close",
    "Create", "Delete", "Done", "Edit", "Email", "ExitToApp", "Face", "FavoriteBorder",
    "Favorite", "Home", "Info", "KeyboardArrowDown", "KeyboardArrowLeft",
    "KeyboardArrowRight", "KeyboardArrowUp", "List", "LocationOn", "Lock", "MailOutline",
    "Menu", "MoreVert", "Notifications", "Person", "Phone", "Place", "PlayArrow",
    "Refresh", "Search", "Send", "Settings", "Share", "ShoppingCart", "Star", "ThumbUp",
    "Warning", "AccountBox", "AccountCircle", "AddCircle", "ArrowBackIosNew",
    "Build", "Call", "CheckCircle", "ClearAll", "DateRange", "ErrorOutline",
    "Event", "ExpandMore", "FilterList", "Inbox", "LocationOn", "Public",
    "Schedule", "Send", "ShoppingCart", "Timer", "Verified",
}

# Names the weather code tables use, which are NOT icons; exclude them from the check.
FALSE_POSITIVES = {"WbSunny"}


def kotlin_files():
    for dirpath, _d, filenames in os.walk(KT):
        for name in filenames:
            if name.endswith(".kt"):
                yield os.path.join(dirpath, name)


def main():
    used = {}
    for path in kotlin_files():
        text = open(path, encoding="utf-8").read()
        for icon in re.findall(r"Icons\.(?:Filled|Outlined|Rounded|Sharp)\.(\w+)", text):
            used.setdefault(icon, set()).add(os.path.relpath(path, ROOT))
        # Also catch fully-qualified references such as Icons.Filled.WbSunny in an enum.
        for icon in re.findall(r"Icons\.\w+\.(\w+)\b", text):
            used.setdefault(icon, set()).add(os.path.relpath(path, ROOT))

    if not used:
        print("PASS: no Icons.* references found")
        return 0

    problems = []
    for icon, files in sorted(used.items()):
        if icon in FALSE_POSITIVES:
            continue
        if icon not in CORE_ICONS:
            problems.append("%s used in %s" % (icon, ", ".join(sorted(files))))

    for icon, files in sorted(used.items()):
        print("  %-20s %s" % (icon, ", ".join(sorted(files))))

    print()
    if problems:
        print("FAIL: these icons are not in material-icons-core:")
        for p in problems:
            print("  - " + p)
        print()
        print("Either pick a core icon, or add the extended artifact. Adding")
        print("material-icons-extended costs several MB, which conflicts with the size")
        print("budget, so replacing the icon is the better option here.")
        return 1
    print("PASS: all referenced icons are in material-icons-core")
    return 0


if __name__ == "__main__":
    sys.exit(main())
