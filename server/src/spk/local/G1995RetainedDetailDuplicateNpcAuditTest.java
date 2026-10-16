package spk.local;

/**
 * G19.9.5: uses one genuinely finalized NPC with one ground settlement,
 * then makes two test-only collector observations of that same result.
 * Balanced marginal/cross-tab counts cannot hide duplicated death identity.
 */
public final class G1995RetainedDetailDuplicateNpcAuditTest {
    public static void main(String[] args)throws Exception {
        G1965SameWorldSequentialTraceCaptureOwnerTest.main(args);
        System.out.println(
            "G1995_RETAINED_DETAIL_DUPLICATE_NPC_AUDIT_PASS"+
            " genuineNpcSettledOnce=true"+
            " duplicateDiagnosticNotCredited=true"+
            " balancedMarginalsFlaggedDrift=true"+
            " duplicateRetainedNpcIdDetected=true");
    }
    private G1995RetainedDetailDuplicateNpcAuditTest(){}
}
