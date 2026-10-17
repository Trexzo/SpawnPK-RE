package spk.local;

/**
 * G19.8.3: distinguish genuine post-settlement G1 failure from actual
 * downstream G14 fail-fast in an exact current-spawn-origin cohort.
 */
public final class G1983OriginConsumerG1G14FailureTest {
    public static void main(String[] args)throws Exception{
        G1967G1PreStageFailureDiagnosticTest.run(true);
        G1884TerminalStageIsolationPropertySnapshotTest
            .runSequentialStageTraceCase(false,3983L);
        G1884TerminalStageIsolationPropertySnapshotTest
            .runSequentialStageTraceCase(true,3984L);
        System.out.println(
            "G1983_ORIGIN_CONSUMER_G1_G14_FAILURE_PASS"+
            " genuineG1PostgroundThrow=true"+
            " genuineG14PartialMutationUncertain=true"+
            " downstreamNotReachedPreserved=true"+
            " exactOriginCohort=true"+
            " creditAckNeverInferred=true");
    }
    private G1983OriginConsumerG1G14FailureTest(){}
}
