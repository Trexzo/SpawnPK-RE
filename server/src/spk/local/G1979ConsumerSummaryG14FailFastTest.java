package spk.local;

/**
 * G19.7.9: independent genuine installed healthy and invalid G14
 * terminals have distinct six-consumer outcomes; callbacks are not ACKs.
 */
public final class G1979ConsumerSummaryG14FailFastTest {
    public static void main(String[] args)throws Exception{
        G1884TerminalStageIsolationPropertySnapshotTest
            .runSequentialStageTraceCase(false,3979L);
        G1884TerminalStageIsolationPropertySnapshotTest
            .runSequentialStageTraceCase(true,3980L);
        System.out.println(
            "G1979_CONSUMER_SUMMARY_G14_FAIL_FAST_PASS"+
            " healthyAndThrownG14Captured=true"+
            " G4ReturnPreserved=true"+
            " G15G16G18NotReachedOnFailure=true"+
            " noDurableGrantAck=true");
    }
    private G1979ConsumerSummaryG14FailFastTest(){}
}
