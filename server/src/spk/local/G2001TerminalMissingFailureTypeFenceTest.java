package spk.local;

/** A real settled NPC and a test-only G4 THREW without exception evidence. */
public final class G2001TerminalMissingFailureTypeFenceTest {
    public static void main(String[] args)throws Exception{
        G1997TerminalTimelineGapFenceTest.run(5);
        System.out.println(
            "G2001_TERMINAL_MISSING_FAILURE_TYPE_FENCE_PASS"+
            " canonicalNpcGroundSettlement=true"+
            " missingG4FailureTypeRejectedBeforeAllCounters=true"+
            " rejectedCaptureNotConsumed=true"+
            " correctedCaptureAcceptedOnce=true"+
            " noRewardReplay=true");
    }
    private G2001TerminalMissingFailureTypeFenceTest(){}
}
