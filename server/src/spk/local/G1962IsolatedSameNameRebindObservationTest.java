package spk.local;

/**
 * G19.6.2: opt-in isolation already exposes false spawnOriginMatched;
 * new default stage report must characterize the same rebind without
 * changing the existing name-keyed World progression policy.
 */
public final class G1962IsolatedSameNameRebindObservationTest {
    public static void main(String[] args)throws Exception{
        G1961SameNameRecipientRebindObservationTest.run(true);
        System.out.println(
            "G1962_ISOLATED_SAME_NAME_REBIND_OBSERVATION_PASS"+
            " realSpawnOriginMismatch=true"+
            " existingFiveStageDeliveryPreserved=true"+
            " isolatedOriginMismatchVisible=true"+
            " noDuplicateGroundSettlement=true"+
            " noNewServerKillCreditPolicyAsserted=true");
    }
    private G1962IsolatedSameNameRebindObservationTest(){}
}
