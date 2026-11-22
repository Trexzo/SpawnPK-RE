#!/usr/bin/env python3
"""R108 read-only, lexical inventory of plugin/content player mutation surfaces.

This is a *source audit*, not a call graph, proof of bytecode sandboxing or
claim that external plugins can directly access package-private WorldPlayer.
Exit nonzero when required known surfaces disappear or public raw types leak.
No files are written and no game/server/plugin process is launched.
"""
import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WORLD = ROOT / "src/spk/local/WorldPlayer.java"
PLUGIN = ROOT / "src/spk/plugin/api"
CONTENT = ROOT / "src/spk/content/api"
KNOWN_RAW_GETTERS = {
    "movement", "bank", "equipment", "petState", "playerState",
    "prayers", "magic", "combatStyles", "combatState", "lifecycle",
    "statusState", "timedEffects", "petEffects", "miniPets",
    "petAccessoryState", "snapshotExtensions",
}
KNOWN_CONTENT_MUTATORS = {
    "restoreCombatSkillsAndSpecial", "clearTimedStatuses", "setRunEnergy",
    "syncMaintainedPetEffects", "grantItem", "switchPrayerBook",
    "switchSpellBook", "deactivatePrayers",
}

def mask_noncode(source):
    # Preserve newlines and overall offsets while discarding Java comments
    # and literals. Deliberately conservative and not an AST parser.
    pattern = re.compile(r'//[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'')
    return pattern.sub(lambda m: "".join("\n" if c == "\n" else " " for c in m.group()), source)

def get_raw_getters(source):
    clean = mask_noncode(source)
    pattern = re.compile(
        r"(?m)^\s{4}([\w<>]+)\s+(\w+)\(\)\s*\{\s*return\s+\w+\s*;\s*\}")
    return {m.group(2): m.group(1) for m in pattern.finditer(clean)}

def interface_methods(source):
    clean = mask_noncode(source)
    # One return value + name + call start, possibly across newlines.
    return sorted(set(re.findall(
        r"(?m)^\s*(?:default\s+)?[\w<>?, ]+?\s+(\w+)\s*\(",
        clean)))

def assert_sources():
    raw = WORLD.read_text(encoding="utf-8")
    assert re.search(r"(?m)^final class WorldPlayer\s*\{", mask_noncode(raw)), (
        "WorldPlayer is no longer the expected package-private type")
    getters = get_raw_getters(raw)
    assert KNOWN_RAW_GETTERS.issubset(getters.keys()), (
        "missing known raw WorldPlayer mutable getter(s): "
        + str(sorted(KNOWN_RAW_GETTERS - getters.keys())))

    public_plugin_files = sorted(PLUGIN.rglob("*.java"))
    public_content_files = sorted(CONTENT.rglob("*.java"))
    assert public_plugin_files and public_content_files
    raw_exposures = []
    for file in public_plugin_files:
        source = mask_noncode(file.read_text(encoding="utf-8"))
        # Source names in real API signatures, not documentation comments.
        for typename in ("WorldPlayer", "BankState"):
            if re.search(r"\b" + typename + r"\b", source):
                raw_exposures.append(str(file.relative_to(ROOT)) + ":" + typename)
    assert not raw_exposures, "raw internal player type exposed in plugin api: " + repr(raw_exposures)

    context = (PLUGIN / "PluginContext.java").read_text(encoding="utf-8")
    expected_context = {"content", "events", "scheduler"}
    context_methods = set(interface_methods(context))
    assert expected_context.issubset(context_methods), "PluginContext changed"
    player = (CONTENT / "ContentPlayer.java").read_text(encoding="utf-8")
    methods = set(interface_methods(player))
    assert KNOWN_CONTENT_MUTATORS.issubset(methods), (
        "missing known ContentPlayer mutator(s): "
        + repr(sorted(KNOWN_CONTENT_MUTATORS - methods)))
    player_contexts = []
    for file in public_content_files:
        source = mask_noncode(file.read_text(encoding="utf-8"))
        for m in re.finditer(r"\bContentPlayer\s+(player|target)\s*\(", source):
            player_contexts.append({
                "file": file.name,
                "method": m.group(1),
            })
    assert any(x["file"] == "ContentItemOnPlayerContext.java"
               and x["method"] == "target" for x in player_contexts), (
                   "item-on-player target ContentPlayer no longer located")
    return {
        "raw_worldplayer_class_package_private": True,
        "worldplayer_mutable_getters_count": len(KNOWN_RAW_GETTERS),
        "worldplayer_known_mutable_getters": [
            {"name": name, "return_type": getters[name]}
            for name in sorted(KNOWN_RAW_GETTERS)],
        "plugin_api_files_scanned": len(public_plugin_files),
        "content_api_files_scanned": len(public_content_files),
        "plugin_context_capabilities": sorted(context_methods),
        "public_plugin_api_raw_internal_type_references": raw_exposures,
        "content_player_methods": sorted(methods),
        "content_player_known_mutator_candidates": sorted(KNOWN_CONTENT_MUTATORS),
        "content_player_context_returns": player_contexts,
        "classification": (
            "ContentPlayer semantic mutation methods and item-on-player target "
            "are exposed via content callback contexts. Package-private "
            "WorldPlayer raw fields are not directly typed by PluginContext. "
            "No dynamic reachability, reflection, native or Java bytecode proof."),
        "runtime_plugin_isolation_proven": False,
    }

def self_test():
    sample = (
        "final class WorldPlayer {\n"
        "    BankState bank(){return bank;}\n"
        "    // BankState bogus(){return bogus;}\n"
        "    String comment=\"WorldPlayer\x20BankState\";\n"
        "    /* Foo fake(){return fake;} */\n"
        "}\n")
    assert get_raw_getters(sample) == {"bank": "BankState"}
    assert interface_methods(
        "public interface X { int skillLevel(); default String grantItem(\n"
        " int id); // boolean fake()\n }") == ["grantItem", "skillLevel"]
    assert not re.search(r"\bWorldPlayer\b",
                         mask_noncode('/* WorldPlayer */ String x="WorldPlayer";'))
    print("R108_PLUGIN_SURFACE_SELFTEST_PASS "
          "commentsAndLiteralsMasked=true getterInventory=true "
          "semanticMethodInventory=true noASTOrSandboxClaim=true")

if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--self-test", action="store_true")
    ns = ap.parse_args()
    if ns.self_test:
        self_test()
    result = assert_sources()
    print("R108_PLUGIN_MUTABILITY_SURFACE_PASS "
          + json.dumps(result, sort_keys=True, separators=(",", ":")))
