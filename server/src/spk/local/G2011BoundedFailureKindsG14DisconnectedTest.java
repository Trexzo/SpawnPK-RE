package spk.local;

/** G20.1.1: genuine invalid G14 and disconnected recipient are distinct. */
public final class G2011BoundedFailureKindsG14DisconnectedTest {
    public static void main(String[] args)throws Exception{
        G1884TerminalStageIsolationPropertySnapshotTest
            .runSequentialStageTraceCase(true,4011L);
        G1884TerminalStageIsolationPropertySnapshotTest
            .runSequentialStageTraceCase(false,4012L);
        G1959DisconnectedRecipientStageTraceTest.run(false);
        System.out.println(
            "G2011_BOUNDED_FAILURE_KINDS_G14_DISCONNECTED_PASS"+
            " realG14IllegalState=1"+
            " healthyNoException=1"+
            " disconnectedNoThrow=1"+
            " noGrantOrRecoveryAuthority=true");
    }
    private G2011BoundedFailureKindsG14DisconnectedTest(){}
}
