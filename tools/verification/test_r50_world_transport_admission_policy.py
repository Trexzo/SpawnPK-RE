#!/usr/bin/env python3
"""R50 source-bound guard placement policy.

Tests that the transport admission fence is applied both before World
submission and *inside* the World callback, then for each typed request.
"""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[2]/"server"/"src"/"spk"/"local"


class R50WorldActionAdmissionPolicy(unittest.TestCase):
    def test_world_submission_has_prequeue_and_inworld_checks(self):
        text=(ROOT/"LocalSession.java").read_text(encoding="utf-8")
        start=text.index("private void processPendingOnWorld(")
        end=text.index("private void drainOutbound(",start)
        section=text[start:end]
        assert section.count("serverPackets.requireGameActionAdmission();")==2
        assert section.index("serverPackets.requireGameActionAdmission();")<section.index("world.submitAndWait(")
        assert section.rindex("serverPackets.requireGameActionAdmission();")<section.index("pendingRequests.drain(")

    def test_all_typed_handlers_are_fenced_before_dispatch(self):
        text=(ROOT/"LocalPendingRequestDispatcher.java").read_text(encoding="utf-8")
        start=text.index("private void acceptTypedRequests(")
        end=text.index("if(request instanceof",start)
        section=text[start:end]
        assert "takeTypedRequest()" in section
        assert "serverPackets.requireGameActionAdmission();" in section
        assert section.index("takeTypedRequest()")<section.index("serverPackets.requireGameActionAdmission();")

    def test_packet_writer_refuses_failed_session_queue(self):
        text=(ROOT/"ServerPacketWriter.java").read_text(encoding="utf-8")
        assert "void requireGameActionAdmission()throws IOException" in text
        assert "if(queue!=null)queue.requireGameActionAdmission();" in text

    def test_outbound_terminal_and_overflow_block_before_next_mutation(self):
        text=(ROOT/"OutboundPacketQueue.java").read_text(encoding="utf-8")
        start=text.index("synchronized void requireGameActionAdmission()")
        # Scope R50's invariant to its own method rather than scanning
        # later R54/R55 helpers. R55 intentionally clears *failed trade*
        # bytes in a separate explicit fail-closed path.
        end=text.index("synchronized void requireCommitHeadroom(",start)
        section=text[start:end]
        assert "requireTransportHealthy()" in section
        assert "if(overflowed)" in section
        assert "throw new IOException(" in section
        assert "q.remove" not in section
        assert "q.clear" not in section

    def test_r55_aborted_trade_explicitly_drops_stale_outbound_bytes(self):
        text=(ROOT/"OutboundPacketQueue.java").read_text(encoding="utf-8")
        start=text.index("synchronized void failClosedAfterTradePublication(")
        end=text.index("synchronized boolean transportFailed()",start)
        section=text[start:end]
        assert "terminalTransportFailure" in section
        assert "q.clear();" in section
        assert "bytes=0;" in section
        # R50's normal session guard must still be non-mutating.
        r50=text[text.index("synchronized void requireGameActionAdmission()"):
                 text.index("synchronized void requireCommitHeadroom(")]
        assert "q.clear" not in r50


if __name__=="__main__":
    unittest.main()
