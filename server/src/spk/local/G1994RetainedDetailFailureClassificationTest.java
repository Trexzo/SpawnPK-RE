package spk.local;

/**
 * G19.9.4: post-ground real G1 exception and malformed G14 cases.
 * The retained reports reconcile their first blocker, failure type and
 * six consumer evidence without asserting any durable grant ACK.
 */
public final class G1994RetainedDetailFailureClassificationTest {
    public static void main(String[] args)throws Exception {
        G1967G1PreStageFailureDiagnosticTest.run(true);
        G1884TerminalStageIsolationPropertySnapshotTest
            .runSequentialStageTraceCase(false,3994L);
        G1884TerminalStageIsolationPropertySnapshotTest
            .runSequentialStageTraceCase(true,3995L);
        System.out.println(
            "G1994_RETAINED_DETAIL_FAILURE_CLASSIFICATION_PASS"+
            " actualG1PostgroundThrow=true"+
            " actualG14FailFast=true"+
            " healthyPositiveControl=true"+
            " callbackEvidenceNotGrantAck=true");
    }
    private G1994RetainedDetailFailureClassificationTest(){}
}
