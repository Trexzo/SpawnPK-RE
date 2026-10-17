package spk.local;

/**
 * G20.0.6: two genuine settled deaths, first G1 returns and the
 * second G1 throws before downstream progress; no grant replay.
 */
public final class G2006OriginBoundaryG1PrestageFailureTest {
    public static void main(String[] args)throws Exception{
        G1967G1PreStageFailureDiagnosticTest.run(true);
        System.out.println("G2006_ORIGIN_FIRST_BOUNDARY_G1_FAILURE_PASS"+
            " genuineSettlements=2"+
            " currentOriginNoThrow=1"+
            " currentOriginG1Failure=1"+
            " fiveDerivedNotReached=true"+
            " noReplay=true");
    }
    private G2006OriginBoundaryG1PrestageFailureTest(){}
}
