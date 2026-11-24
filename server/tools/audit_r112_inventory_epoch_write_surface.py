#!/usr/bin/env python3
"""R112 read-only lexical coverage gate for opt-in BankState inventory epochs.

This is not an AST/dataflow proof and does not block arbitrary code with
BankState.Stack references from editing qty. The scanner verifies the
20 known BankState inventory-mutating method entrypoints stay guarded,
reports candidate direct writes, and denies newly introduced inventory[]
assignment sites outside guarded methods.
"""
import json
import re
from pathlib import Path

PATH = Path(__file__).resolve().parents[1] / "src/spk/local/BankState.java"
GUARDED = {
    "depositInventory", "consumeInventoryAmountSemantic",
    "consumeInventoryOne", "addInventoryOne", "addInventoryOnePreferred",
    "consumeInventoryAll", "addInventoryAmount",
    "equipCosmeticFromInventory", "unequipCosmeticToInventory",
    "transformInventoryOne", "restoreTradeInventory",
    "restoreAccountState", "spawnItem", "equipFromInventory",
    "unequipToInventory", "splitInventoryOne",
    "combineInventoryOne", "applyDrag", "withdrawAmount",
    "depositAmount",
}
HELPERS = {"swap", "insertMove"}


def mask_noncode(source):
    pattern = re.compile(
        r'//[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"|'
        r"'(?:\\.|[^'\\])*'")
    return pattern.sub(
        lambda m: "".join("\n" if c == "\n" else " "
                          for c in m.group()), source)


def brace_end(code, opening):
    depth = 0
    for index in range(opening, len(code)):
        if code[index] == "{":
            depth += 1
        elif code[index] == "}":
            depth -= 1
            if depth == 0:
                return index + 1
    raise AssertionError("R112 unterminated method body")


def method_spans(clean):
    spans = {}
    pattern = re.compile(
        r"(?m)^    (?:(?:private|static|synchronized|final)\s+)*"
        r"[A-Za-z_][\w\[\]<>?, ]*\s+(\w+)\s*\(")
    for m in pattern.finditer(clean):
        name = m.group(1)
        # Permit multiline signatures; methods may include 'throws'.
        start = clean.find("{", m.end())
        if start < 0 or start-m.end() > 330:
            continue
        if ";" in clean[m.end():start]:
            continue
        spans[name] = (m.start(),start,brace_end(clean,start))
    return spans


def audit():
    source = PATH.read_text(encoding="utf-8")
    clean = mask_noncode(source)
    spans = method_spans(clean)
    missing = sorted(GUARDED - spans.keys())
    assert not missing, "R112 missing guarded methods: " + repr(missing)
    for name in sorted(GUARDED):
        _, opening, end = spans[name]
        prefix = clean[opening+1:min(opening+180,end)]
        assert "invalidateOfflineInventoryEpochIfEnabled();" in prefix, (
            "R112 unguarded mutator: " + name)

    def owner(position):
        # Find innermost candidate method containing lexical position.
        matches = [(name,end-start) for name,(start,_,end)
                   in spans.items() if start<=position<end]
        return min(matches,key=lambda x:x[1])[0] if matches else None

    # Direct canonical array assignments, incl. inventory[i].qty,
    # and compound assignments to the quantity of the indexed stack.
    direct = re.compile(
        r"\binventory\s*\[[^\]\n]+\]\s*(?:"
        r"=(?!=)|\+=|-=|\+\+|--|\.qty\s*"
        r"(?:=(?!=)|\+=|-=|\+\+|--))")
    sites = []
    for hit in direct.finditer(clean):
        name = owner(hit.start())
        line = source.count("\n",0,hit.start())+1
        if name is None and line<110:
            # Field initializer of the canonical inventory array.
            continue
        assert name in GUARDED, (
            "R112 new direct canonical inventory write outside covered "
            "method: line=%d method=%r" % (line,name))
        sites.append({"line":line,"method":name})

    # Source-dataflow escape hatches cannot be proven guarded by this
    # lexical inventory; keep them explicitly visible in the output.
    assert "Stack inventoryAt(int slot)" in source, (
        "R112 raw mutable Stack getter changed: review required")
    assert "System.arraycopy(" in source and "nextInventory" in source, (
        "R112 persistence account restore changed: review required")
    assert "Stack[] xs;" in source and re.search(r"\\bxs\\s*=\\s*inventory\\s*;", source), (
        "R112 drag inventory alias changed: review required")
    assert "insertMove(xs," in source and "swap(xs," in source, (
        "R112 drag inventory writes changed: review required")
    assert "final long offlineMutationEpoch;" in source, (
        "R112 immutable snapshot revision missing")
    assert "if(!offlineInventoryEpochEnabled)return;" in source, (
        "R112 test opt-in-only branch missing")

    return {
        "known_mutator_entrypoints": len(GUARDED),
        "guarded_methods": sorted(GUARDED),
        "direct_inventory_write_site_count": len(sites),
        "direct_inventory_write_sites": sites,
        "account_restore_system_arraycopy_checked": True,
        "drag_mutable_array_alias_and_helpers_checked": True,
        "raw_mutable_inventoryAt_Stack_alias_exposed": True,
        "untracked_external_raw_alias_mutation_possible": True,
        "all_Java_alias_dataflows_proven": False,
        "inventory_revision_opt_in_only": True,
        "whole_inventory_invalidated_per_covered_mutator": True,
        "fine_grained_per_slot_mutation_epoch_proven": False,
        "production_item_on_player_enabled": False,
    }


if __name__ == "__main__":
    out = audit()
    print("R112_INVENTORY_EPOCH_WRITE_SURFACE_PASS "
          + json.dumps(out,sort_keys=True,separators=(",",":")))
