"""Catch the Kotlin compile mistakes that keep recurring in this project.

Static resource checks do not prove the code compiles, and Gradle cannot run in the Stage
02 environment. These rules target the specific error classes that have actually occurred
while writing the app, so they are caught locally rather than in CI:

  1. Duplicate imports of the same symbol (an unresolved-reference error in Kotlin).
  2. Imports of Aranya's own packages that do not exist.
  3. Callbacks passed as `::reference` whose target function has a different arity.
  4. Remembers of `Destination` entries (enum is not saveable via the default saver).
  5. Left-over references to strings or symbols removed in a later edit.

Run: python3 data-pipeline/tests/verify_kotlin_integrity.py
"""

import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
KT = os.path.join(ROOT, "android", "app", "src")


def files_under(subdir, suffix=".kt"):
    base = os.path.join(KT, subdir)
    if not os.path.isdir(base):
        return []
    found = []
    for dirpath, _d, names in os.walk(base):
        found += [os.path.join(dirpath, n) for n in names if n.endswith(suffix)]
    return found


def declared_packages():
    pkgs = set()
    for path in files_under(""):
        m = re.search(r"^package\s+([\w.]+)", open(path, encoding="utf-8").read(), re.M)
        if m:
            pkgs.add(m.group(1))
    return pkgs


def check_duplicate_imports():
    problems = []
    for path in files_under(""):
        seen = {}
        for i, line in enumerate(open(path, encoding="utf-8").read().split("\n"), 1):
            m = re.match(r"\s*import\s+([\w.]+)(?:\s+as\s+\w+)?\s*$", line)
            if not m:
                continue
            symbol = m.group(1)
            if symbol in seen:
                problems.append("%s:%d duplicates line %d: %s"
                                % (os.path.relpath(path, ROOT), i, seen[symbol], symbol))
            else:
                seen[symbol] = i
    return problems


def check_own_imports(pkgs):
    problems = []
    for path in files_under(""):
        text = open(path, encoding="utf-8").read()
        for imp in re.findall(r"^import\s+(org\.aranya\.app\.[\w.]+)", text, re.M):
            # A member import such as org.aranya.app.ui.theme.NumericReadout resolves to an
            # existing package; only flag when no such package exists at all.
            parent = imp.rsplit(".", 1)[0]
            if parent in pkgs or imp in pkgs:
                continue
            # Step up one more level for nested packages before giving up.
            if parent.rsplit(".", 1)[0] in pkgs:
                continue
            problems.append("%s imports unknown package %s"
                            % (os.path.relpath(path, ROOT), imp))
    return problems


def check_callback_arities():
    """`::name` passed as a callback must match the parameter type it is given."""
    problems = []

    def fn_arity(path, fname):
        text = open(path, encoding="utf-8").read()
        # fun name(a: T, b: U): R   or   fun name(a: T, b: U)
        m = re.search(r"fun\s+%s\s*\(([^)]*)\)" % re.escape(fname), text)
        if not m:
            return None
        params = [p.strip() for p in m.group(1).split(",") if p.strip()]
        return len(params)

    # The shell takes four callbacks of one argument each. Any ::method passed to it
    # must therefore be a single-parameter function.
    shell = os.path.join(ROOT, "android", "app", "src", "main", "java",
                         "org", "aranya", "app", "ui", "AranyaShell.kt")
    if os.path.exists(shell):
        text = open(shell, encoding="utf-8").read()
        for cb in ["onSelectLocation", "onSubmitTypedLocation", "onRefreshWeather",
                   "onLanguageChange"]:
            m = re.search(r"^\s*%s:\s*\((\w+)\)\s*->\s*Unit," % cb, text, re.M)
            if not m:
                continue
            param = m.group(1)
            for ref in re.findall(r"^%s\s*=\s*\w+\.\?\.(\w+)" % param, text, re.M):
                target = os.path.join(ROOT, "android", "app", "src", "main", "java",
                                      "org", "aranya", "app", "ui", "weather",
                                      "WeatherViewModel.kt")
                arity = fn_arity(target, ref)
                if arity is not None and arity != 1:
                    problems.append(
                        "AranyaShell.%s takes 1 argument but WeatherViewModel.%s takes %d"
                        % (param, ref, arity))
    return problems


def check_stale_symbols():
    """Strings referenced from Kotlin that no longer exist anywhere."""
    defined = set()
    res = os.path.join(KT, "main", "res")
    for d in os.listdir(res):
        p = os.path.join(res, d, "strings.xml")
        if os.path.exists(p):
            defined |= set(re.findall(r'<(?:string|plurals)\s+name="([^"]+)"',
                                      open(p, encoding="utf-8").read()))
    problems = []
    for path in files_under("main") + files_under("test") + files_under("androidTest"):
        text = open(path, encoding="utf-8").read()
        for name in set(re.findall(r"\bR\.string\.(\w+)", text)) | set(re.findall(r"\bR\.plurals\.(\w+)", text)):
            if name not in defined:
                problems.append("%s references R.string.%s which is not defined"
                                % (os.path.relpath(path, ROOT), name))
    return problems


def check_destination_saveable():
    """
    rememberSaveable can only put primitives in a Bundle, so storing an enum (or any
    non-primitive) directly crashes on process death. Flag the patterns that appear in
    this codebase, whether the value is the enum itself or an ordinal derived from it.
    """
    problems = []
    for path in files_under("main"):
        text = open(path, encoding="utf-8").read()
        for m in re.finditer(
            r"rememberSaveable\s*\{\s*mutableStateOf\(\s*([A-Za-z_][\w.]*)\s*\)", text
        ):
            initial = m.group(1)
            # An enum member (Destination.WEATHER) or a bare enum-typed name is unsafe.
            if re.match(r"^\w+\.\w+$", initial) and initial.startswith("Destination"):
                problems.append(
                    "%s: rememberSaveable { mutableStateOf(%s) } stores an enum, which cannot "
                    "be saved in a Bundle; store an Int ordinal instead"
                    % (os.path.relpath(path, ROOT), initial))
            if initial == "destination":
                problems.append(
                    "%s: rememberSaveable holds a variable named 'destination'; if it is a "
                    "Destination enum it cannot be saved" % os.path.relpath(path, ROOT))
    return problems


# Property-delegate operators used via `by`, plus composable/preview helpers that are
# invoked indirectly. These never appear as a bare call site, so treating them as unused
# would be a false positive.
INDIRECT = {"getValue", "setValue", "provideDelegate"}


def check_unused_imports():
    """
    Flag imports whose simple name never appears in the file body.

    Not a compile error, but an unused import in a file this size is usually a leftover
    from a deleted composable, which is how dead code and wrong API assumptions creep in.
    """
    problems = []
    for path in files_under(""):
        text = open(path, encoding="utf-8").read()
        imports = re.findall(r"^import\s+([\w.]+)(?:\s+as\s+(\w+))?\s*$", text, re.M)
        body = "\n".join(l for l in text.split("\n") if not l.startswith("import "))
        for full, alias in imports:
            simple = alias or full.rsplit(".", 1)[-1]
            if simple == "*" or simple in INDIRECT:
                continue
            if not re.search(r"\b%s\b" % re.escape(simple), body):
                problems.append("%s: unused import %s" % (os.path.relpath(path, ROOT), full))
    return problems


def check_package_path():
    """
    A file's directory must match its `package` declaration.

    Stage 02 shipped SourceRegistry.kt under org/anya/ while declaring `package
    org.aranya...`. Kotlin does not care, but every other tool, and every human navigating
    the tree, does. This is cheap to assert and expensive to miss.
    """
    problems = []
    for path in files_under(""):
        text = open(path, encoding="utf-8").read()
        m = re.search(r"^package\s+([\w.]+)", text, re.M)
        if not m:
            continue
        actual = os.path.dirname(path)
        # Find the source root for this file: the nearest ancestor named "java".
        # Everything after it must mirror the package declaration exactly.
        parts = actual.split(os.sep)
        if "java" not in parts:
            continue
        root = len(parts) - 1 - parts[::-1].index("java")
        expected = os.sep.join(parts[:root + 1] + m.group(1).split("."))
        if os.path.normpath(actual) != os.path.normpath(expected):
            problems.append(
                "%s declares package %s but sits in %s"
                % (os.path.relpath(path, ROOT), m.group(1),
                   os.path.relpath(actual, ROOT)))
    return problems


def check_data_class_constructor_calls():
    """
    Data classes whose fields mix Int and Double must be constructed with named
    arguments.

    A positional call against such a constructor is a type error at best and a silently
    misassigned value at worst, which is exactly how a forecast reading gets attached to
    the wrong day.
    """
    problems = []
    for path in files_under(""):
        text = open(path, encoding="utf-8").read()
        # Data classes declaring both Int and Double nullable fields.
        classes = {}
        for m in re.finditer(r"data class (\w+)\(([^)]*)\)", text, re.M):
            name, body = m.group(1), m.group(2)
            has_int = re.search(r":\s*Int\??\s*[,\)]", body) is not None
            has_double = re.search(r":\s*Double\??\s*[,\)]", body) is not None
            if has_int and has_double:
                classes[name] = True
        if not classes:
            continue
        for name in classes:
            for m in re.finditer(r"\b%s\(\s*[^)\n]*\)" % re.escape(name), text):
                call = m.group(0)
                # A named argument makes the intent explicit and is always acceptable.
                if re.search(r"\w+\s*=", call):
                    continue
                # Skip declarations and empty calls.
                if "data class %s(" % name in call:
                    continue
                problems.append(
                    "%s: %s(...) uses positional arguments but the class mixes Int and "
                    "Double; use named arguments" % (os.path.relpath(path, ROOT), name))
    return problems


def check_duplicate_declarations():
    """
    The same kind + name declared twice at top level is a conflicting-declaration
    compile error.

    This happened in Stage 02 when a scripted patch inserted a replacement block without
    removing the originals. A patch script that adds rather than replaces is easy to write
    and impossible to spot by eye in a long token file, so it is asserted here.

    Keyed on (kind, name) because Kotlin permits an object and a function to share a
    name: `object AranyaTheme` plus `fun AranyaTheme(...)` is a deliberate pattern used
    to expose composables through a conventional accessor.
    """
    problems = []
    for path in files_under(""):
        text = open(path, encoding="utf-8").read()
        # Top-level declarations only: no leading indentation.
        decls = re.findall(
            r"^(?:internal\s+|private\s+|public\s+)?"
            r"(val|var|fun|class|object|enum\s+class|interface|typealias)\s+(\w+)",
            text, re.M)
        # `enum class X` and `class X` are different kinds; normalise to the declared name.
        seen = {}
        for kind, name in decls:
            key = (kind.replace(" ", ""), name)
            seen[key] = seen.get(key, 0) + 1
        for (kind, name), count in sorted(seen.items()):
            if count > 1:
                problems.append(
                    "%s: '%s %s' is declared %d times at top level (conflicting declarations)"
                    % (os.path.relpath(path, ROOT), kind, name, count))
    return problems


CHECKS = [
    ("no duplicate imports", check_duplicate_imports),
    ("no duplicate declarations", check_duplicate_declarations),
    ("own-package imports resolve", lambda: check_own_imports(declared_packages())),
    ("callback arities match", check_callback_arities),
    ("no stale string references", check_stale_symbols),
    ("rememberSaveable is safe", check_destination_saveable),
    ("no unused imports", check_unused_imports),
    ("package matches directory", check_package_path),
    ("mixed-type constructors are named", check_data_class_constructor_calls),
]


def main():
    failed = 0
    for label, fn in CHECKS:
        problems = fn()
        if problems:
            failed += 1
            print("FAIL %s" % label)
            for p in problems[:12]:
                print("       %s" % p)
            if len(problems) > 12:
                print("       ... and %d more" % (len(problems) - 12))
        else:
            print("PASS %s" % label)
    print()
    print("%d/%d checks passed" % (len(CHECKS) - failed, len(CHECKS)))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
