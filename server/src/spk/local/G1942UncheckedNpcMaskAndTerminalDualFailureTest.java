package spk.local;

/**
 * G19.4.2: preserve both independent runtime exceptions when failed
 * packet publication and terminal settlement callback occur together.
 */
public final class G1942UncheckedNpcMaskAndTerminalDualFailureTest {
    public static void main(String[] args)throws Exception{
        G1941UncheckedNpcMaskFailureTerminalizationTest.run(true,true);
        System.out.println(
            "G1942_UNCHECKED_NPC_MASK_AND_TERMINAL_DUAL_FAILURE_PASS"+
            " terminalFailurePrimary=true"+
            " originalUncheckedPresentationErrorSuppressed=true"+
            " exactCommittedLethalHP=true"+
            " noSecondHitOrFabricatedGrant=true");
    }

    private G1942UncheckedNpcMaskAndTerminalDualFailureTest(){}
}
