#!/usr/bin/env python3
"""R102 offline lexical supplement to the R101 event-callsite audit.

Recover WorldEventQueue *typed receivers* (including arbitrary names such as
PluginTaskTracker.queue) and conservative local aliases, then flag scheduler
calls that may receive player-affecting callbacks. This is deliberately not
type-resolved, flow-sensitive Java dataflow or a proof of a callback's safety.
No source writes, network, server launch or automatic migration.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re

from audit_world_event_provenance import mask_java_noncode, find_call_sites

IDENT = r"[A-Za-z_$][A-Za-z0-9_$]*"
METHOD = r"scheduleOwnedForTest|scheduleExplicitNonPlayerForTest|schedule"
TYPED = re.compile(r"\bWorldEventQueue\s+(?P<name>" + IDENT + r")\b")
DECL_ALIAS = re.compile(
    r"\b(?P<type>WorldEventQueue|var)\s+(?P<name>" + IDENT
    + r")\s*=\s*(?P<rhs>(?:" + IDENT + r"\s*\.\s*)?"
    + r"events\s*\(\s*\)|" + IDENT + r")\s*[;,)]")
ASSIGN_ALIAS = re.compile(
    r"(?<![.\w$])(?P<name>" + IDENT + r")\s*=\s*(?P<rhs>"
    + IDENT + r")\s*;", re.MULTILINE)
CALL = re.compile(
    r"\b(?:(?P<qual>this|super)\s*\.\s*)?"
    + r"(?P<receiver>" + IDENT + r")\s*(?P<op>\.|::)\s*"
    + r"(?P<method>" + METHOD + r")\s*(?P<paren>\()?",
    re.MULTILINE)
# Record queue object forwarding across constructors, where static typing is
# unavailable without Java symbol resolution.
FORWARD = re.compile(r"\bnew\s+(?P<target>" + IDENT
    + r")\s*\(", re.MULTILINE)
PLAYER_HINT = re.compile(
    r"\b(?:WorldPlayer|player|actor|attacker|victim|targetPlayer|"
    r"recipient|inventory|bank|playerState)\b", re.I)
CALLBACK_HINT = re.compile(r"\b(?:task|action|callback|runnable|handler)\b", re.I)


def balanced_end(masked: str, start: int) -> int:
    depth = 0
    for pos in range(start, len(masked)):
        if masked[pos] == "(":
            depth += 1
        elif masked[pos] == ")":
            depth -= 1
            if depth == 0:
                return pos + 1
    return min(start + 1000, len(masked))


def alias_sources(masked: str) -> dict[str, tuple[str, int]]:
    """Monotonic conservative aliases. Does not resolve Java block scoping."""
    result: dict[str, tuple[str, int]] = {}
    for m in TYPED.finditer(masked):
        result[m["name"]] = ("DECLARED_WORLD_EVENT_QUEUE", m.start())
    # A non-WorldEventQueue variable named 'events' is not proof of a queue.
    # Only infer 'var' from known World.events() access or typed receiver.
    for _ in range(5):
        changed = False
        for m in DECL_ALIAS.finditer(masked):
            rhs = re.sub(r"\s+", "", m["rhs"])
            known = (m["type"] == "WorldEventQueue"
                     or rhs.endswith("events()")
                     or rhs in result)
            if known and m["name"] not in result:
                result[m["name"]] = ("INFERRED_INITIALIZER", m.start())
                changed = True
        for m in ASSIGN_ALIAS.finditer(masked):
            if m["rhs"] in result and m["name"] not in result:
                result[m["name"]] = ("INFERRED_ASSIGNMENT", m.start())
                changed = True
        if not changed:
            break
    return result


def inspect_source(source: str, relative_path: str) -> tuple[list[dict], list[dict]]:
    masked = mask_java_noncode(source)
    aliases = alias_sources(masked)
    records: list[dict] = []
    transfers: list[dict] = []
    # The older R101 direct-accessor candidate list is kept separately.
    # This file specifically reports aliases R101's named-receiver filter
    # could not recognize; duplicates are omitted by call-site offset.
    r101_sites = find_call_sites(source, relative_path)
    r101_lines = {(x["line"], x["method"]) for x in r101_sites}
    for m in CALL.finditer(masked):
        receiver = m["receiver"]
        if receiver not in aliases:
            continue
        line = source.count("\n", 0, m.start()) + 1
        method = m["method"]
        if (line, method) in r101_lines:
            continue
        if m["op"] == "." and not m["paren"]:
            continue
        # Skip the WorldEventQueue's own method declaration, which has
        # no receiver and cannot match CALL in the first place.
        args = ""
        if m["paren"]:
            args = masked[m.end() - 1:balanced_end(masked, m.end() - 1)]
        sensitivity = ("POSSIBLE_PLAYER_CAPTURE" if PLAYER_HINT.search(args)
                       else "OPAQUE_CALLBACK_REVIEW" if CALLBACK_HINT.search(args)
                       else "UNKNOWN_CALLBACK_REVIEW")
        records.append({
            "path": relative_path.replace("\\", "/"),
            "line": line,
            "receiver": receiver,
            "receiver_evidence": aliases[receiver][0],
            "operator": m["op"],
            "method": method,
            "provenance": {
                "schedule": "LEGACY_UNTAGGED",
                "scheduleOwnedForTest": "OWNER_GENERATION_EXPLICIT",
                "scheduleExplicitNonPlayerForTest": "NONPLAYER_CLAIM_EXPLICIT",
            }[method],
            "callback_review": sensitivity,
            "test_fixture": relative_path.endswith("Test.java"),
            "source_text": source.splitlines()[line-1].strip()[:180],
            "lexical_not_type_or_scope_resolved": True,
        })
    # Propagation paths help a human tie a typed WorldEventQueue field to
    # a scheduler wrapper; they do not establish whether the recipient
    # mutates player state or whether its lifecycle policy is adequate.
    for m in FORWARD.finditer(masked):
        end = balanced_end(masked, m.end() - 1)
        arguments = masked[m.end():max(m.end(), end - 1)]
        passed = sorted(a for a in aliases if re.search(
            r"(?<![\w$])" + re.escape(a) + r"(?![\w$])", arguments))
        if passed:
            transfers.append({
                "path": relative_path.replace("\\", "/"),
                "line": source.count("\n", 0, m.start()) + 1,
                "constructor": m["target"],
                "event_queue_receivers": passed,
                "manual_callback_and_owner_review_required": True,
            })
    return records, transfers


def self_test() -> None:
    example = """
        class Sample {
          WorldEventQueue queue;
          WorldEventQueue first = world.events();
          var alias = first;
          void run(WorldPlayer player) {
            queue.schedule(1L, () -> player.bank().clear());
            alias.schedule(2L, task);
            first.scheduleOwnedForTest(3L, id, 1L, task);
            queue::schedule;
            new PluginTaskTracker(clock, queue, scope);
            other.schedule(1L, () -> player.bank().clear());
            world.events().schedule(4L, task);
            // queue.schedule(999L, task);
            String x = "queue.schedule(20L, task)";
          }
        }
    """
    records, transfers = inspect_source(example, "Sample.java")
    assert len(records) == 4, records
    assert [(x["receiver"], x["method"]) for x in records] == [
        ("queue", "schedule"),
        ("alias", "schedule"),
        ("first", "scheduleOwnedForTest"),
        ("queue", "schedule"),
    ], records
    assert records[0]["callback_review"] == "POSSIBLE_PLAYER_CAPTURE"
    assert records[1]["callback_review"] == "OPAQUE_CALLBACK_REVIEW"
    assert records[3]["operator"] == "::"
    assert transfers == [{
        "path": "Sample.java",
        "line": 10,
        "constructor": "PluginTaskTracker",
        "event_queue_receivers": ["queue"],
        "manual_callback_and_owner_review_required": True,
    }], transfers
    # R101 itself missed typed receiver 'queue', while it can see direct
    # World.events() calls. The two scanners serve complementary roles.
    assert len(find_call_sites(example, "Sample.java")) == 1
    assert not inspect_source(
        "class N { void test(){ other.schedule(1, x); } }",
        "N.java")[0]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path,
                        default=Path(__file__).resolve().parents[1] / "src")
    parser.add_argument("--max-lines", type=int, default=300)
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--require-production-plugin-sink",
                        action="store_true")
    args = parser.parse_args()
    self_test()
    if args.self_test:
        print("R102_ALIAS_AUDIT_SELFTEST_PASS "
              "typedAndVarAliases=true "
              "playerCaptureHintConservative=true "
              "commentsAndStringsExcluded=true "
              "otherSchedulerExcluded=true "
              "directAccessorNotDoubleCounted=true "
              "methodReferenceAndConstructorForwarding=true")
        return 0
    if not args.root.is_dir():
        raise SystemExit("R102 missing Java source root " + str(args.root))
    sources = sorted(args.root.rglob("*.java"))
    found: list[dict] = []
    transfers: list[dict] = []
    for path in sources:
        rel = path.relative_to(args.root).as_posix()
        matches, forwarding = inspect_source(
            path.read_text(encoding="utf-8", errors="replace"), rel)
        found.extend(matches)
        transfers.extend(forwarding)
    if args.require_production_plugin_sink and not any(
        x["path"].endswith("/PluginTaskTracker.java")
        and x["receiver"] == "queue"
        and x["method"] == "schedule"
        and not x["test_fixture"] for x in found
    ):
        raise SystemExit(
            "R102_EXPECTED_PLUGIN_SCHEDULER_UNTAGGED_SINK_MISSING")
    for row in found[:args.max_lines]:
        print("R102_ALIAS_CALLSITE " + json.dumps(row, sort_keys=True))
    for row in transfers[:args.max_lines]:
        print("R102_QUEUE_TRANSFER " + json.dumps(row, sort_keys=True))
    print("R102_ALIAS_EVENT_AUDIT_PASS " + json.dumps({
        "java_files_scanned": len(sources),
        "typed_or_inferred_alias_calls": len(found),
        "non_test_alias_calls": sum(not x["test_fixture"] for x in found),
        "non_test_untagged_alias_candidates": sum(
            not x["test_fixture"] and x["provenance"] == "LEGACY_UNTAGGED"
            for x in found),
        "possible_player_capture_hints": sum(
            x["callback_review"] == "POSSIBLE_PLAYER_CAPTURE"
            for x in found),
        "opaque_callback_hints": sum(
            x["callback_review"] == "OPAQUE_CALLBACK_REVIEW"
            for x in found),
        "queue_construction_forwarding_candidates": len(transfers),
        "r101_direct_calls_not_recounted_on_same_line": True,
        "not_complete_java_typing_or_closure_capture_analysis": True,
        "review_required_not_production_migration": True,
        "no_source_changes_or_server_launch": True,
    }, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
