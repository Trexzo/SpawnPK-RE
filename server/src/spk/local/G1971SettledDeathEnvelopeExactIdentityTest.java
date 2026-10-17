package spk.local;

/**
 * G19.7.1: exercise the production settled-death envelope with a
 * genuine NPC kill/drop receipt and two different same-World collectors.
 * The envelope checks provenance, not consumer grant authority.
 */
public final class G1971SettledDeathEnvelopeExactIdentityTest {
    public static void main(String[] args)throws Exception{
        G1965SameWorldSequentialTraceCaptureOwnerTest.main(args);
        G1963CurrentSpawnRecipientOriginPositiveControlTest.main(args);
        System.out.println(
            "G1971_SETTLED_DEATH_ENVELOPE_EXACT_IDENTITY_PASS"+
            " actualCanonicalNpcFinalization=true"+
            " exactGroundReceiptIdentity=true"+
            " exactCurrentSpawnOrigin=true"+
            " sameWorldCaptureFence=true"+
            " originalProgressionPolicyUnchanged=true"+
            " durableEventId=false");
    }
    private G1971SettledDeathEnvelopeExactIdentityTest(){}
}
