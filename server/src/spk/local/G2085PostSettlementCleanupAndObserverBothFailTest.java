package spk.local;

/** Real canonical NPC death and settled ground-drop optional cleanup boundary. */
public final class G2085PostSettlementCleanupAndObserverBothFailTest {
    public static void main(String[] args)throws Exception {
        LocalCanonicalNpcAttackPvmRuntimeBridgeTest
            .runPostSettlementCleanupBoundary(3);
        System.out.println("G2085_POST_SETTLEMENT_DUAL_FAILURE_PASS separateTypeLogs=true noDuplicateDrop=true observerRingThrew=true noGrantReplay=true");
    }
    private G2085PostSettlementCleanupAndObserverBothFailTest(){}
}
