package spk.local;

/**
 * G19.5.7: a real installed default G14 failure must identify which
 * consumers were reached before the terminal observer failed.
 */
public final class G1957SequentialG14FailureStageTraceTest {
    public static void main(String[] args)throws Exception{
        G1884TerminalStageIsolationPropertySnapshotTest
            .runSequentialStageTraceCase(true,3307L);
        System.out.println(
            "G1957_SEQUENTIAL_G14_FAILURE_STAGE_TRACE_PASS"+
            " realG1AndGroundCommitted=true"+
            " G4Returned=true"+
            " G14Threw=true"+
            " G15G16G18NotReached=true"+
            " exactNpcDeathAndSpawnGeneration=true"+
            " noGrantReplay=true");
    }
    private G1957SequentialG14FailureStageTraceTest(){}
}
