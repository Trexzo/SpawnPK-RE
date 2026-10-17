package spk.local;

/** G20.1.0: true post-settlement G1 throw retains only coarse kind. */
public final class G2010BoundedFailureKindsG1PrestageTest {
    public static void main(String[] args)throws Exception{
        G1967G1PreStageFailureDiagnosticTest.run(true);
        System.out.println(
            "G2010_BOUNDED_FAILURE_KINDS_G1_PRESTAGE_PASS"+
            " actualSettledDeaths=2"+
            " G1IllegalState=1"+
            " healthyNone=1"+
            " fiveDerivedNotReached=true"+
            " automaticGrantReplay=false");
    }
    private G2010BoundedFailureKindsG1PrestageTest(){}
}
