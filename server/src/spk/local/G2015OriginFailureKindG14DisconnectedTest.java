package spk.local;

/** G20.1.5: real healthy/failing G14 and recipient disconnection
 * classify origin, first boundary and coarse type distinctly. */
public final class G2015OriginFailureKindG14DisconnectedTest {
    public static void main(String[] args)throws Exception{
        G1884TerminalStageIsolationPropertySnapshotTest
            .runSequentialStageTraceCase(false,4015L);
        G1884TerminalStageIsolationPropertySnapshotTest
            .runSequentialStageTraceCase(true,4016L);
        G1959DisconnectedRecipientStageTraceTest.run(false);
        System.out.println(
            "G2015_ORIGIN_FAILURE_KIND_G14_DISCONNECTED_PASS"+
            " realG14IllegalState=1"+
            " healthyCurrentNone=1"+
            " disconnectedNoCurrentNone=1"+
            " noGrantOrAutomaticRecovery=true");
    }
    private G2015OriginFailureKindG14DisconnectedTest(){}
}
