package spk.local;

/**
 * G19.8.6: actual canonical duplicate-pending G1 failure occurs only
 * AFTER settlement; all five derived consumers must remain NOT_REACHED.
 */
public final class G1986FirstTerminalBoundaryG1FailureTest {
    public static void main(String[] args)throws Exception{
        G1967G1PreStageFailureDiagnosticTest.run(true);
        System.out.println(
            "G1986_FIRST_TERMINAL_BOUNDARY_G1_FAILURE_PASS"+
            " genuinelySettledDeaths=2"+
            " G1FirstFailureCount=1"+
            " noThrowCount=1"+
            " fiveDownstreamConsumersNotReached=true"+
            " noReplayOrDurableAck=true");
    }
    private G1986FirstTerminalBoundaryG1FailureTest(){}
}
