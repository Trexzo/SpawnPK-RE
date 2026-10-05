package spk.local;

/**
 * Focused manifest entry for the composed playable PK loop.
 *
 * The heavy live fixture remains owned by
 * PlayerPvpDeathSettlementIntegrationTest so there is one canonical integration
 * harness. This wrapper intentionally delegates to that exact regression and
 * gives the current train a permanent named acceptance node.
 */
public final class PlayablePkLoopIntegrationTest {
    public static void main(String[] args)
        throws Exception {
        PlayerPvpDeathSettlementIntegrationTest.main(
            args==null
                ?new String[0]
                :args
        );
    }

    private PlayablePkLoopIntegrationTest(){}
}
