package spk.local;

/**
 * G19.8.7: a genuine G14 failed callback is a distinct first failure
 * from a disconnected recipient, which is NOT a callback exception.
 */
public final class G1987FirstTerminalBoundaryG14AndDisconnectTest {
    public static void main(String[] args)throws Exception{
        G1884TerminalStageIsolationPropertySnapshotTest
            .runSequentialStageTraceCase(false,3987L);
        G1884TerminalStageIsolationPropertySnapshotTest
            .runSequentialStageTraceCase(true,3988L);
        G1959DisconnectedRecipientStageTraceTest.run(false);
        System.out.println(
            "G1987_FIRST_TERMINAL_BOUNDARY_G14_DISCONNECT_PASS"+
            " G14FirstFailureWhenMalformed=true"+
            " noThrowWhenHealthy=true"+
            " disconnectClassifiedNoCurrentRecipient=true"+
            " noGrantAckOrAutoReplay=true");
    }
    private G1987FirstTerminalBoundaryG14AndDisconnectTest(){}
}
