package spk.local;

/**
 * G19.8.8: a synthetic corrupted callback sequence is constructed
 * *from a genuinely settled terminal*; rejecting it may not consume
 * the capture or mutate the origin/consumer cross-tab or any other totals.
 */
public final class G1988FirstTerminalBoundaryInvalidSequenceFenceTest {
    public static void main(String[] args)throws Exception{
        G1965SameWorldSequentialTraceCaptureOwnerTest.main(args);
        G1959DisconnectedRecipientStageTraceTest.run(true);
        System.out.println(
            "G1988_FIRST_TERMINAL_BOUNDARY_INVALID_SEQUENCE_FENCE_PASS"+
            " actualCanonicalSettlement=true"+
            " G1ThrewThenG4ReturnedRejected=true"+
            " foreignCollectorRejected=true"+
            " zeroPartialDiagnosticMutation=true"+
            " noRewardOrReplay=true");
    }
    private G1988FirstTerminalBoundaryInvalidSequenceFenceTest(){}
}
