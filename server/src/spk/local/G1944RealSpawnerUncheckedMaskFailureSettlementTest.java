package spk.local;

/** G19.4.4: unchecked packet I/O failure still settles exactly one spawner death. */
public final class G1944RealSpawnerUncheckedMaskFailureSettlementTest {
    public static void main(String[] args)throws Exception{
        LocalCanonicalNpcAttackPvmRuntimeBridgeTest
            .runSpawnerLethalAfterMaskFailure(true);
        System.out.println(
            "G1944_REAL_SPAWNER_UNCHECKED_MASK_FAILURE_SETTLEMENT_PASS"+
            " realDecodedAttack=true"+
            " uncheckedMaskFailurePreserved=true"+
            " exactCanonicalTeardown=true"+
            " oneGroundLootAndPresentation=true"+
            " noDuplicateTerminalSettlement=true");
    }
    private G1944RealSpawnerUncheckedMaskFailureSettlementTest(){}
}
