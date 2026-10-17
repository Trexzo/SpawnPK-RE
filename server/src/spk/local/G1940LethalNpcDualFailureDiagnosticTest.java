package spk.local;

/**
 * G19.4.0: when both network presentation and terminal callback fail,
 * surface terminal failure and retain checked packet I/O as suppressed.
 */
public final class G1940LethalNpcDualFailureDiagnosticTest {
    public static void main(String[] args)throws Exception{
        G1939LethalNpcPacketIoFinalizationTest.run(true,true);
        System.out.println(
            "G1940_LETHAL_NPC_DUAL_FAILURE_DIAGNOSTIC_PASS"+
            " packetIOExceptionRetainedAsSuppressed=true"+
            " terminalFailureRemainsPrimary=true"+
            " deadNpcCannotBeHitTwice=true"+
            " noInventedRewardSettlement=true");
    }

    private G1940LethalNpcDualFailureDiagnosticTest(){}
}
