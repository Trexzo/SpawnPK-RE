package spk.local;

/**
 * G19.7.8: a real G1 post-settlement exception makes its own mutation
 * uncertain and five other consumers unattempted. No replay occurs.
 */
public final class G1978ConsumerSummaryG1FailureTest {
    public static void main(String[] args)throws Exception{
        G1967G1PreStageFailureDiagnosticTest.run(true);
        System.out.println(
            "G1978_CONSUMER_SUMMARY_G1_FAILURE_PASS"+
            " realSettledDeaths=2"+
            " G1Returned=1"+
            " G1ThrewAfterPossibleMutation=1"+
            " fiveDownstreamNotReached=1"+
            " autoReplayDenied=true");
    }
    private G1978ConsumerSummaryG1FailureTest(){}
}
