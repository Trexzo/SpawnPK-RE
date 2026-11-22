#!/usr/bin/env python3
"""R110: read-only fail-closed source anchors for client item-on-player opcode 14.

This lexical audit guards *current* source route only, not Java call graphs
or bytecode, and neither enables a real item-on-player behavior nor proves
that all possible reflective/internal routes are safe.
"""
from pathlib import Path
import re

LOCAL = Path(__file__).resolve().parents[1] / "src" / "spk" / "local"


def source(name):
    return (LOCAL / name).read_text(encoding="utf-8")


def method_body(text, name):
    token = re.search(r"\b(?:void|String|ContentInteractionResult|GenericInteractionEvent)\s+"
                      + re.escape(name) + r"\s*\(", text)
    if token is None:
        raise AssertionError("R110 source method missing: " + name)
    brace = text.find("{", token.end())
    if brace < 0:
        raise AssertionError("R110 missing method body: " + name)
    depth = 0
    for pos in range(brace, len(text)):
        if text[pos] == "{":
            depth += 1
        elif text[pos] == "}":
            depth -= 1
            if depth == 0:
                return text[brace:pos + 1]
    raise AssertionError("R110 unterminated method: " + name)


def main():
    decoder = source("GenericInteractionPacketDecoder.java")
    require = lambda ok, why: ok or (_ for _ in ()).throw(
        AssertionError("R110 source guard: " + why))
    require(re.search(r"case\s+14\s*:\s*return\s+8\s*;", decoder),
            "opcode14 length must be fixed8")
    decode = method_body(decoder, "decode")
    require("case 14:" in decode and
            "GenericInteractionEvent.itemOnPlayer(" in decode,
            "decoder must produce typed item-on-player event")

    event = source("GenericInteractionEvent.java")
    require("enum Family { ITEM_ON_PLAYER" in event,
            "typed family no longer recognized")
    require("final int selectedSlot;" in event and
            "final int selectedWidget;" in event and
            "final int selectedItemId;" in event and
            "final int targetId;" in event,
            "selected item provenance fields missing")
    handler = source("LocalGenericInteractionHandler.java")
    route = method_body(handler, "handle")
    require(re.search(r"case\s+ITEM_ON_PLAYER\s*:\s*return\s+"
                      r"\"V5185_ITEM_ON_PLAYER", route),
            "inbound item-on-player is not directly fail closed")
    require("result=DECODED_FAIL_CLOSED" in route.split(
            "case ITEM_ON_PLAYER:", 1)[1].split("case ", 1)[0],
            "item-on-player unknown semantics no longer denied")
    require("dispatchItemOnPlayer" not in route,
            "generic handler unexpectedly calls content registry")

    request = source("LocalPendingRequestDispatcher.java")
    dispatch = method_body(request, "routeGenericInteraction")
    require("genericInteractionHandler.handle(event)" in dispatch and
            "dispatchItemOnPlayer" not in dispatch,
            "pending client request bypasses generic fail-closed handler")
    registry = source("ContentRegistry.java")
    require("ContentInteractionResult dispatchItemOnPlayer(" in registry,
            "independent content registry path missing")

    print("R110_OPCODE14_INBOUND_SOURCE_FENCE_PASS "
          "opcode14Fixed8=true typedSelectedItemSlotWidgetViewerIndex=true "
          "pendingRequestRoutesToGenericHandler=true "
          "itemOnPlayerGenericBranchFailClosed=true "
          "registeredContentDispatcherNotCalledFromClientRoute=true "
          "internalContentRegistryDispatchRemainsSeparate=true "
          "noProductionItemOnPlayerPromotion=true "
          "lexicalSourceGuardNotACompleteCallGraph=true")


if __name__ == "__main__":
    main()
