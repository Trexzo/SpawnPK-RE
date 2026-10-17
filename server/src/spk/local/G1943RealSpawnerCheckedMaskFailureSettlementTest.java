package spk.local;

/** G19.4.3: checked packet I/O failure must not strand a real spawner death. */
public final class G1943RealSpawnerCheckedMaskFailureSettlementTest {
    public static void main(String[] args)throws Exception{
        LocalCanonicalNpcAttackPvmRuntimeBridgeTest
            .runSpawnerLethalAfterMaskFailure(false);
        System.out.println(
            "G1943_REAL_SPAWNER_CHECKED_MASK_FAILURE_SETTLEMENT_PASS"+
            " realDecodedAttack=true"+
            " checkedMaskIOExceptionPreserved=true"+
            " exactCanonicalTeardown=true"+
            " oneGroundLootAndPresentation=true"+
            " noDuplicateTerminalSettlement=true");
    }
    private G1943RealSpawnerCheckedMaskFailureSettlementTest(){}
}
