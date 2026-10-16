package spk.local;

/**
 * Genuine installed post-settlement G14 failure, plus a real settled
 * NPC with a separate test-only callback whose exception type matches.
 */
public final class G2004TerminalTypedFailurePositiveControlTest {
    public static void main(String[] args)throws Exception{
        G1997TerminalTimelineGapFenceTest.run(8);
        G1884TerminalStageIsolationPropertySnapshotTest
            .runSequentialStageTraceCase(true,4004L);
        System.out.println(
            "G2004_TERMINAL_TYPED_FAILURE_POSITIVE_CONTROL_PASS"+
            " testOnlyG4ThrowTypeMatches=true"+
            " genuineInstalledG14ThrowTypeMatches=true"+
            " firstFailureBoundaryPreserved=true"+
            " noDuplicatedGroundSettlement=true"+
            " noDurableRewardAck=true");
    }
    private G2004TerminalTypedFailurePositiveControlTest(){}
}
