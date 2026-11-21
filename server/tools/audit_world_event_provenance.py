#!/usr/bin/env python3
"""R101: read-only, lexical WorldEventQueue schedule call-site inventory.

This finds syntactically recognizable Java World-event scheduling calls.
It does NOT resolve symbols/types, lambdas' captured player state, or
third-party generated source. Ambiguous calls require manual review.
No source is modified; no game/server process is launched.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re


def mask_java_noncode(source: str) -> str:
    """Preserve offsets/newlines while removing comments and literals."""
    output = list(source)
    i = 0
    state = "code"
    while i < len(source):
        c = source[i]
        nxt = source[i:i+2]
        triple = source[i:i+3]
        if state == "code":
            if nxt == "//":
                state = "line"
                size = 2
            elif nxt == "/*":
                state = "block"
                size = 2
            elif triple == '"""':
                state = "textblock"
                size = 3
            elif c == '"':
                state = "string"
                size = 1
            elif c == "'":
                state = "char"
                size = 1
            else:
                i += 1
                continue
            for k in range(i, min(i + size, len(source))):
                if source[k] != "\n":
                    output[k] = " "
            i += size
            continue
        if state == "line" and c == "\n":
            state = "code"
            i += 1
            continue
        if state == "block" and nxt == "*/":
            output[i:i+2] = [" ", " "]
            state = "code"
            i += 2
            continue
        if state == "textblock" and triple == '"""':
            output[i:i+3] = [" ", " ", " "]
            state = "code"
            i += 3
            continue
        if state in ("string", "char"):
            quote = '"' if state == "string" else "'"
            if c == "\\" and i + 1 < len(source):
                for k in (i, i+1):
                    if source[k] != "\n":
                        output[k] = " "
                i += 2
                continue
            if c == quote:
                output[i] = " "
                state = "code"
                i += 1
                continue
        if c != "\n":
            output[i] = " "
        i += 1
    return "".join(output)


# Recognize the actual accessor from World.java, or clearly named local
# WorldEventQueue receiver. The receiver's static type is not resolved.
ACCESSOR = re.compile(
    r"\bevents\s*\(\s*\)\s*\.\s*"
    r"(?P<method>scheduleOwnedForTest|scheduleExplicitNonPlayerForTest|schedule)\s*\("
)
MEMBER = re.compile(
    r"\b(?P<receiver>events|eventQueue|worldEvents|worldEventQueue)"
    r"\s*\.\s*"
    r"(?P<method>scheduleOwnedForTest|scheduleExplicitNonPlayerForTest|schedule)\s*\("
)


def find_call_sites(source: str, relative_path: str) -> list[dict]:
    masked = mask_java_noncode(source)
    results = {}
    for receiver, pattern in (("events_accessor", ACCESSOR),
                              ("named_queue_candidate", MEMBER)):
        for match in pattern.finditer(masked):
            method = match.group("method")
            line = source.count("\n", 0, match.start()) + 1
            kind = {
                "schedule": "LEGACY_UNTAGGED",
                "scheduleOwnedForTest": "OWNER_GENERATION_EXPLICIT",
                "scheduleExplicitNonPlayerForTest": "NONPLAYER_CLAIM_EXPLICIT",
            }[method]
            results.setdefault(
                match.start(),
                {
                    "path": relative_path.replace("\\", "/"),
                    "line": line,
                    "receiver": receiver,
                    "method": method,
                    "provenance": kind,
                    "test_fixture": relative_path.endswith("Test.java"),
                    "source_text": source.splitlines()[line-1].strip()[:180],
                },
            )
    return [results[k] for k in sorted(results)]


def self_test() -> None:
    sample = r'''class Example {
      // world.events().schedule(0, () -> {});
      String literal = "world.events().schedule(0, foo)";
      void run() {
        world.events().schedule(1, () -> {});
        events.scheduleOwnedForTest(2, id, generation, task);
        world.events().scheduleExplicitNonPlayerForTest(3, "purpose", task);
        world.realtime().schedule(4, actor, task);
        /* events.schedule(5, task); */
      }
    }'''
    observed = find_call_sites(sample, "Example.java")
    assert len(observed) == 3, observed
    assert [x["provenance"] for x in observed] == [
        "LEGACY_UNTAGGED",
        "OWNER_GENERATION_EXPLICIT",
        "NONPLAYER_CLAIM_EXPLICIT",
    ], observed
    assert all(not x["test_fixture"] for x in observed)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path,
                        default=Path(__file__).resolve().parents[1] / "src")
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--max-lines", type=int, default=300)
    args = parser.parse_args()
    self_test()
    if args.self_test:
        print("R101_EVENT_INVENTORY_SELFTEST_PASS "
              "commentsAndStringsIgnored=true "
              "sourceLocationsPreserved=true "
              "ownerAndNonplayerTagsDistinguished=true")
        return 0
    if not args.root.is_dir():
        raise SystemExit("R101 source directory not found: " + str(args.root))
    sources = sorted(args.root.rglob("*.java"))
    records = []
    for path in sources:
        rel = path.relative_to(args.root).as_posix()
        records.extend(find_call_sites(
            path.read_text(encoding="utf-8", errors="replace"), rel))
    untagged = sum(x["provenance"] == "LEGACY_UNTAGGED" for x in records)
    tagged = sum(x["provenance"] == "OWNER_GENERATION_EXPLICIT" for x in records)
    nonplayer = sum(x["provenance"] == "NONPLAYER_CLAIM_EXPLICIT" for x in records)
    prod_untagged = sum(
        x["provenance"] == "LEGACY_UNTAGGED" and not x["test_fixture"]
        for x in records)
    for record in records[:args.max_lines]:
        print("R101_EVENT_CALLSITE " + json.dumps(
            record, sort_keys=True, separators=(",", ":")))
    print("R101_EVENT_INVENTORY_PASS " + json.dumps({
        "java_files_scanned": len(sources),
        "syntactically_recognized_calls": len(records),
        "owner_tagged": tagged,
        "explicit_nonplayer_claims": nonplayer,
        "legacy_untagged": untagged,
        "non_test_legacy_candidates": prod_untagged,
        "printed": min(len(records), args.max_lines),
        "truncated": len(records) > args.max_lines,
        "not_a_type_resolved_or_exhaustive_player_mutation_audit": True,
        "no_source_mutation_or_server_launch": True,
    }, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
