package spk.local;

/**
 * G19.4.8: authoritative HP may commit before packet output fails, but
 * a source mask that was never published must not create a remote mask.
 */
public final class G1948FailedCanonicalNpcMaskNoRemoteRelayTest {
    public static void main(String[] args)throws Exception{
        G1947CanonicalNpcHitmaskRemoteRelayTest.run(true);
        System.out.println(
            "G1948_FAILED_CANONICAL_NPC_MASK_NO_REMOTE_RELAY_PASS"+
            " realDecodedAttack=true"+
            " committedHPWithoutSourcePacket=true"+
            " noPhantomRemoteMask=true"+
            " sourceIOExceptionPreserved=true"+
            " noInventedRewardCredit=true");
    }
    private G1948FailedCanonicalNpcMaskNoRemoteRelayTest(){}
}
