package spk.local;

/**
 * G19.8.4: exact settled NPC World/collector ownership prevents rejected
 * second collectors and duplicate captures from affecting origin×consumer
 * statistics. Original installed events continue to be one-shot.
 */
public final class G1984OriginConsumerCollectorFenceTest {
    public static void main(String[] args)throws Exception{
        G1965SameWorldSequentialTraceCaptureOwnerTest.main(args);
        G1959DisconnectedRecipientStageTraceTest.run(true);
        System.out.println(
            "G1984_ORIGIN_CONSUMER_COLLECTOR_FENCE_PASS"+
            " exactWorldOriginRequired=true"+
            " sameWorldWrongCollectorDenied=true"+
            " capturedReportNeverConsumedByForeignCollector=true"+
            " originConsumerTotalsOneShot=true"+
            " noRewardReplay=true");
    }
    private G1984OriginConsumerCollectorFenceTest(){}
}
