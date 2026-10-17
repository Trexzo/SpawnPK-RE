package spk.local;

/**
 * Named G2.12 acceptance node for the composed bank-funded playable PK loop.
 *
 * The canonical heavy fixture remains PlayerPvpDeathSettlementIntegrationTest;
 * this wrapper deliberately reuses that exact harness so there is no second
 * divergent combat/death implementation.
 */
public final class PlayableBankFundedPkLoopIntegrationTest {
    public static void main(String[] args)
        throws Exception{
        PlayerPvpDeathSettlementIntegrationTest.main(
            args==null
                ?new String[0]
                :args
        );
    }

    private PlayableBankFundedPkLoopIntegrationTest(){}
}
