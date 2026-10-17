package spk.local;

/**
 * G19.3.8: missing packet output cannot kill an NPC or fire terminal
 * finalization and then abort. The same-tick valid hit may still kill.
 */
public final class G1938MissingNpcPacketWriterLethalFenceTest {
    public static void main(String[] args)throws Exception{
        G1937MissingNpcPacketWriterPreflightTest.run(true);
        System.out.println(
            "G1938_MISSING_NPC_PACKET_WRITER_LETHAL_FENCE_PASS"+
            " exactDecodedOpcode72=true"+
            " nullWriterLeavesNpcAlive=true"+
            " noTerminalCallbackOrReceipt=true"+
            " noCooldownOnInvalidCall=true"+
            " sameTickValidLethalHitFinalizesOnce=true");
    }

    private G1938MissingNpcPacketWriterLethalFenceTest(){}
}
