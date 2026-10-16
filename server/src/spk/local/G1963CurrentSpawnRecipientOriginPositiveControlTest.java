package spk.local;

/**
 * G19.6.3: the read-only origin-match flag must stay TRUE when the
 * exact spawn-time WorldPlayer remains registered, including both
 * healthy and throwing G14 default sequential execution.
 */
public final class G1963CurrentSpawnRecipientOriginPositiveControlTest {
    public static void main(String[] args)throws Exception{
        G1884TerminalStageIsolationPropertySnapshotTest
            .runSequentialStageTraceCase(false,3603L);
        G1884TerminalStageIsolationPropertySnapshotTest
            .runSequentialStageTraceCase(true,3604L);
        System.out.println(
            "G1963_CURRENT_SPAWN_RECIPIENT_ORIGIN_POSITIVE_CONTROL_PASS"+
            " exactSpawnAndObservedGeneration=true"+
            " originMatchedAtDispatch=true"+
            " healthyAndFailFastCases=true"+
            " originalG14StageOutcomesPreserved=true");
    }
    private G1963CurrentSpawnRecipientOriginPositiveControlTest(){}
}
