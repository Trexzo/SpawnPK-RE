package spk.local;

/**
 * G19.6.0: a finalized terminal from World A cannot be accepted or
 * inserted by a separately constructed World B diagnostic collector.
 */
public final class G1960ForeignWorldSequentialTraceFenceTest {
    public static void main(String[] args)throws Exception{
        G1959DisconnectedRecipientStageTraceTest.run(true);
        System.out.println(
            "G1960_FOREIGN_WORLD_SEQUENTIAL_TRACE_FENCE_PASS"+
            " twoDistinctWorldObjects=true"+
            " genuineSettledCanonicalTerminal=true"+
            " foreignBeginAndRecordFailClosed=true"+
            " noForeignHistoryMutation=true"+
            " originalWorldCaptureUnconsumed=true");
    }
    private G1960ForeignWorldSequentialTraceFenceTest(){}
}
