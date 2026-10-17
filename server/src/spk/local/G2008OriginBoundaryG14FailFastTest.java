package spk.local;

/**
 * G20.0.8: genuine healthy and malformed-G14 post-settlement NPC
 * terminals show the first failing G14 separately from no throw.
 */
public final class G2008OriginBoundaryG14FailFastTest {
    public static void main(String[] args)throws Exception{
        G1884TerminalStageIsolationPropertySnapshotTest
            .runSequentialStageTraceCase(false,4008L);
        G1884TerminalStageIsolationPropertySnapshotTest
            .runSequentialStageTraceCase(true,4009L);
        System.out.println("G2008_ORIGIN_FIRST_BOUNDARY_G14_FAIL_FAST_PASS"+
            " genuinePostSettlementG14Failure=true"+
            " currentOriginHealthyNoThrow=true"+
            " currentOriginG14Failure=true"+
            " G15G16G18NotReachedOnThrow=true"+
            " replayNeverAuthorized=true");
    }
    private G2008OriginBoundaryG14FailFastTest(){}
}
