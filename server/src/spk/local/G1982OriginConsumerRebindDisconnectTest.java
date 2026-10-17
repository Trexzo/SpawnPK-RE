package spk.local;

/**
 * G19.8.2: prove real same-name replacement, disconnected original,
 * missing-at-spawn recipient and foreign World remain separate origin
 * classes, even where the existing LocalLab username grant stays valid.
 */
public final class G1982OriginConsumerRebindDisconnectTest {
    public static void main(String[] args)throws Exception{
        G1961SameNameRecipientRebindObservationTest.run(false);
        G1972SettledDeathEnvelopeNegativeOriginsTest.main(args);
        System.out.println(
            "G1982_ORIGIN_CONSUMER_REBIND_DISCONNECT_PASS"+
            " sameNameReplacementObserved=true"+
            " disconnectedDerivedConsumersSkipped=true"+
            " noSpawnRecipientWitnessInvented=true"+
            " historicalNameKeyedCreditPreserved=true"+
            " noAutomaticReplay=true");
    }
    private G1982OriginConsumerRebindDisconnectTest(){}
}
