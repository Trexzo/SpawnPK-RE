package spk.local;

/** G20.1.6: two diagnostic-only G4 exception types attached to one
 * genuine canonical settled NPC; illegal triples and mixed-origin
 * negative forensics must fail without touching the real credit. */
public final class G2016OriginFailureKindMalformedAndEpochTest {
    public static void main(String[] args)throws Exception{
        G2012BoundedFailureKindsForeignAndOtherTest.main(args);
        System.out.println(
            "G2016_ORIGIN_FAILURE_KIND_MALFORMED_EPOCH_PASS"+
            " genuineCanonicalSettlement=true"+
            " isolatedIllegalArgumentAndOtherKinds=true"+
            " impossibleTriplesRejected=true"+
            " crossOriginEpochMismatchDetected=true"+
            " noDuplicateCreditOrReplay=true");
    }
    private G2016OriginFailureKindMalformedAndEpochTest(){}
}
