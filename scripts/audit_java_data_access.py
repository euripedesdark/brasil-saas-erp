#!/usr/bin/env python3
"""Audit first-party Java data access; writes a JSON inventory, never modifies Java."""
import argparse
import json
import pathlib
import re

RULES = {
    "native_sql": r"nativeQuery\s*=\s*true|createNativeQuery\s*\(",
    "jdbc": r"\b(?:JdbcTemplate|NamedParameterJdbcTemplate|prepareStatement|createStatement)\b",
    "jpql": r"@Query\s*\(|createQuery\s*\(",
    "vendor_function": r"(?i)function\s*\(|pg_advisory_|unaccent",
    "sql_text": r"(?i)\b(?:SELECT|INSERT INTO|DELETE FROM|UPDATE)\b",
}
def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default="src/main/java")
    parser.add_argument("--output", default="build/reports/java-data-access.json")
    args = parser.parse_args()
    root = pathlib.Path(args.root)
    findings = []
    files = sorted(root.rglob("*.java"))
    for file in files:
        for line_number, line in enumerate(file.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
            for kind, pattern in RULES.items():
                if re.search(pattern, line):
                    findings.append({"file": str(file.relative_to(root)), "line": line_number,
                                     "kind": kind, "excerpt": line.strip()[:160]})
    result = {"files_scanned": len(files), "findings": findings,
              "counts": {kind: sum(f["kind"] == kind for f in findings) for kind in RULES}}
    out = pathlib.Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Scanned {len(files)} Java files; {len(findings)} pattern matches. Report: {out}")
if __name__ == "__main__":
    main()
