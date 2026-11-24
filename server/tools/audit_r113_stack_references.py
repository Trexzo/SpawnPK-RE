#!/usr/bin/env python3
"""R113 lexical inventory of stack getter references; not Java dataflow proof."""
import json
import re
from pathlib import Path
from audit_r112_inventory_epoch_write_surface import mask_noncode

ROOT = Path(__file__).resolve().parents[1]
REFERENCE = re.compile(r"\b(?:inventoryAt|bankAt)\s*\(|::\s*(?:inventoryAt|bankAt)\b")
WRITE = re.compile(r"(?:(?:\+\+|--)\s*[\w.()\[\]]+\.(?:qty|tab)\b|\.(?:qty|tab)\s*(?:=(?!=)|[+*/%&|^-]=|\+\+|--|<<=|>>>=|>>=))")
TYPE = re.compile(r"\bBankState\s*\.\s*Stack\b")

def scan(source):
    clean = mask_noncode(source)
    out = []
    for kind, pattern in (("getter_reference", REFERENCE), ("field_write_candidate", WRITE), ("qualified_stack_type", TYPE)):
        for match in pattern.finditer(clean):
            line = clean.count("\n", 0, match.start()) + 1
            out.append({"kind": kind, "line": line, "match": match.group().strip()})
    return sorted(out, key=lambda x: (x["line"], x["kind"]))

def self_test():
    assert len(scan("b.inventoryAt(0); b::bankAt;")) == 2
    assert len(scan("BankState.Stack s = b.inventoryAt(0); s.qty++;")) == 3
    assert len(scan("s.qty == 3; // b.inventoryAt(0)\nString x = \"s.qty++\";")) == 0
    assert len(scan("++s.qty; s.tab -= 1;")) == 2

def main():
    self_test()
    files = sorted(p for p in ROOT.rglob("*.java") if not any(part in {"build", ".gradle", ".git"} for part in p.relative_to(ROOT).parts))
    assert files, "no Java source found"
    reports = []
    for path in files:
        hits = scan(path.read_text(encoding="utf-8"))
        if hits:
            reports.append({"file": path.relative_to(ROOT).as_posix(), "sites": hits})
    assert any(r["file"].endswith("/BankState.java") for r in reports)
    counts = {kind: sum(s["kind"] == kind for r in reports for s in r["sites"]) for kind in ("getter_reference", "field_write_candidate", "qualified_stack_type")}
    print("R113_STACK_REFERENCE_AUDIT " + json.dumps({"java_files_scanned": len(files), "counts": counts, "dataflow_proven": False, "reports": reports}, sort_keys=True))

if __name__ == "__main__":
    main()
