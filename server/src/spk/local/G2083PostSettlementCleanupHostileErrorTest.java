package spk.local;

/** Real canonical NPC death and settled ground-drop optional cleanup boundary. */
public final class G2083PostSettlementCleanupHostileErrorTest {
    public static void main(String[] args)throws Exception {
        LocalCanonicalNpcAttackPvmRuntimeBridgeTest
            .runPostSettlementCleanupBoundary(1);
        System.out.println("G2083_POST_SETTLEMENT_CLEANUP_HOSTILE_ERROR_PASS hostileToStringNeverInvoked=true observerDelivered=true noGrantReplay=true");
    }
    private G2083PostSettlementCleanupHostileErrorTest(){}
}
