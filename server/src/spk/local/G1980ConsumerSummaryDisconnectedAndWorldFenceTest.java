package spk.local;

/**
 * G19.8.0: original disconnected recipient and foreign-World
 * attempts must not create phantom consumer returns or reward ACKs.
 */
public final class G1980ConsumerSummaryDisconnectedAndWorldFenceTest {
    public static void main(String[] args)throws Exception{
        G1959DisconnectedRecipientStageTraceTest.run(false);
        G1959DisconnectedRecipientStageTraceTest.run(true);
        G1965SameWorldSequentialTraceCaptureOwnerTest.main(args);
        System.out.println(
            "G1980_CONSUMER_SUMMARY_DISCONNECTED_WORLD_FENCE_PASS"+
            " disconnectedDerivedConsumersSkipped=true"+
            " foreignWorldBeginRecordRejected=true"+
            " originalCollectorPreserved=true"+
            " noAutomaticGrantOrReplay=true");
    }
    private G1980ConsumerSummaryDisconnectedAndWorldFenceTest(){}
}
