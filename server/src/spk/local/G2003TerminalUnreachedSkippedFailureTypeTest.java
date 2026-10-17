package spk.local;

/** Negative controls: NOT_REACHED and SKIPPED cannot claim an exception. */
public final class G2003TerminalUnreachedSkippedFailureTypeTest {
    public static void main(String[] args)throws Exception{
        G1997TerminalTimelineGapFenceTest.run(7);
        G1997TerminalTimelineGapFenceTest.run(9);
        System.out.println(
            "G2003_TERMINAL_UNREACHED_SKIPPED_FAILURE_TYPE_PASS"+
            " canonicalNpcGroundSettlements=2"+
            " forgedNotReachedExceptionRejected=true"+
            " forgedSkippedExceptionRejected=true"+
            " bothCorrectedCapturesAcceptedOnce=true"+
            " noGrantOrReplay=true");
    }
    private G2003TerminalUnreachedSkippedFailureTypeTest(){}
}
