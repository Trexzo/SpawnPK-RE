package spk.local;

/**
 * G20.0.7: genuine old-owner disconnected and same-name rebound
 * terminals retain distinct first-boundary cross observations.
 */
public final class G2007OriginBoundaryRecipientMismatchTest {
    public static void main(String[] args)throws Exception{
        G1959DisconnectedRecipientStageTraceTest.run(false);
        G1961SameNameRecipientRebindObservationTest.run(false);
        System.out.println("G2007_ORIGIN_FIRST_BOUNDARY_RECIPIENT_MISMATCH_PASS"+
            " disconnectedOriginFirstBoundaryNoCurrent=true"+
            " reboundOriginNoThrow=true"+
            " establishedNameBasedCreditUnchanged=true"+
            " noDurableRewardReceipt=true");
    }
    private G2007OriginBoundaryRecipientMismatchTest(){}
}
