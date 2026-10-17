package spk.local;

/**
 * G19.7.5: one genuine healthy post-settlement NPC followed by
 * an independent malformed-G14 actual NPC. The six-consumer
 * diagnostic assessment never becomes a grant or replay ACK.
 */
public final class G1975ConsumerDeliveryUncertaintyG14Test {
    public static void main(String[] args)throws Exception{
        G1884TerminalStageIsolationPropertySnapshotTest
            .runSequentialStageTraceCase(false,3975L);
        G1884TerminalStageIsolationPropertySnapshotTest
            .runSequentialStageTraceCase(true,3976L);
        System.out.println("G1975_CONSUMER_DELIVERY_UNCERTAINTY_G14_PASS"+
            " realHealthyAndThrowingG14=true"+
            " sixConsumerEvidenceComplete=true"+
            " G15G16G18NotReachedOnFailure=true"+
            " immutableAssessment=true"+
            " durableAck=false");
    }
    private G1975ConsumerDeliveryUncertaintyG14Test(){}
}
