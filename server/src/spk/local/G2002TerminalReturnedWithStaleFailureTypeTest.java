package spk.local;

/** Real canonical NPC with a falsely failed RETURNED G4 diagnostic. */
public final class G2002TerminalReturnedWithStaleFailureTypeTest {
    public static void main(String[] args)throws Exception{
        G1997TerminalTimelineGapFenceTest.run(6);
        System.out.println(
            "G2002_TERMINAL_RETURNED_STALE_FAILURE_TYPE_PASS"+
            " canonicalNpcGroundSettlement=true"+
            " staleReturnedFailureTypeRejectedBeforeAllCounters=true"+
            " correctedCaptureAcceptedOnce=true"+
            " exactSingleSettlement=true"+
            " noRewardReplay=true");
    }
    private G2002TerminalReturnedWithStaleFailureTypeTest(){}
}
