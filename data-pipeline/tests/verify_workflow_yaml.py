"""Check the CI workflow for duplicate mapping keys.

A duplicated key inside one mapping is a silent override in many YAML parsers, and in
GitHub Actions it produces a workflow that looks right and behaves unexpectedly. This
happened in Stage 02 when a patch added a second `disable-animations` line to the
emulator job.

PyYAML is not installed in every environment, so this tracks block structure directly.
A `- name:` list item opens a new scope, which is what makes distinct steps at the same
indentation distinct mappings. A key is a duplicate only when it sits at the same
indentation, with the same key, inside the same parent scope.

Run: python3 data-pipeline/tests/verify_workflow_yaml.py
"""

import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
WORKFLOW = os.path.join(ROOT, ".github", "workflows", "ci.yml")

MAPPING_KEY = re.compile(r"^([A-Za-z0-9_.\-]+):(?:\s|$)")


def main():
    if not os.path.exists(WORKFLOW):
        print("FAIL: %s not found" % WORKFLOW)
        return 1

    lines = open(WORKFLOW, encoding="utf-8").read().split("\n")

    problems = []
    checked = 0

    # Scope stack. Each element: (indent, {key: line_no}) for one open mapping.
    stack = []
    # Keys recorded per scope id, so a duplicate is judged within one mapping only.
    seen = {}

    for n, raw in enumerate(lines, 1):
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue

        indent = len(raw) - len(raw.lstrip())
        stripped = raw.strip()

        is_item = stripped.startswith("- ")
        if is_item:
            body = stripped[2:].strip()
            # A list item closes every scope it is not inside of.
            while stack and stack[-1][0] >= indent:
                stack.pop()
            m = MAPPING_KEY.match(body)
            if m:
                # "- name: X" opens a mapping for this step.
                stack.append((indent, {}))
                seen[id(stack[-1])] = {}
                key = m.group(1)
                seen[id(stack[-1])][key] = n
                checked += 1
                # Its own keys are indented further than the dash.
                inner_indent = indent + 2
                while stack and stack[-1][0] >= inner_indent:
                    stack.pop()
                if stack and stack[-1][0] < inner_indent:
                    stack.append((inner_indent, {}))
                    seen[id(stack[-1])] = {}
            continue

        m = MAPPING_KEY.match(stripped)
        if not m:
            continue
        key = m.group(1)
        checked += 1

        # Find or create the scope at this indentation, under the enclosing scopes.
        target = None
        for i, scope in enumerate(stack):
            if scope[0] == indent:
                target = i
                break
        if target is None:
            stack.append((indent, {}))
            seen[id(stack[-1])] = {}
            target = len(stack) - 1
        else:
            # Drop deeper scopes: we have left them.
            del stack[target + 1:]

        bucket = seen[id(stack[target])]
        if key in bucket:
            problems.append(
                "duplicate key '%s' at line %d (first at line %d, indent %d)"
                % (key, n, bucket[key], indent))
        else:
            bucket[key] = n

    if problems:
        print("FAIL: duplicate keys in ci.yml")
        for p in problems:
            print("  - " + p)
        return 1

    print("checked %d mapping keys across ci.yml" % checked)
    print("PASS: no duplicate keys in the CI workflow")
    return 0


if __name__ == "__main__":
    sys.exit(main())
