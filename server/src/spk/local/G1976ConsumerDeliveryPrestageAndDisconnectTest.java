package spk.local;

/**
 * G19.7.6: post-ground G1 exception and disconnected-recipient stage
 * skips remain distinguishable from a successfully returned callback.
 * The diagnostic model explicitly refuses automatic reward replay.
 */
public final class G1976ConsumerDeliveryPrestageAndDisconnectTest {
    public static void main(String[] args)throws Exception{
        G1967G1PreStageFailureDiagnosticTest.run(true);
        G1959DisconnectedRecipientStageTraceTest.run(false);
        System.out.println("G1976_CONSUMER_DELIVERY_PRESTAGE_DISCONNECT_PASS"+
            " actualG1ReturnedThenThrew=true"+
            " fiveDerivedStagesNotReached=true"+
            " disconnectedConsumerStagesSkipped=true"+
            " noDurableGrantAck=true"+
            " neverAutoReplay=true");
    }
    private G1976ConsumerDeliveryPrestageAndDisconnectTest(){}
}
