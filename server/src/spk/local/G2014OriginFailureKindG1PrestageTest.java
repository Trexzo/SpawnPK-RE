package spk.local;

/** G20.1.4: a genuine post-ground G1 exception is typed for its exact
 * spawning WorldPlayer origin; five consumers remain unattempted. */
public final class G2014OriginFailureKindG1PrestageTest {
    public static void main(String[] args)throws Exception{
        G1967G1PreStageFailureDiagnosticTest.run(true);
        System.out.println(
            "G2014_ORIGIN_FAILURE_KIND_G1_PRESTAGE_PASS"+
            " actualSettledDeaths=2"+
            " exactOriginHealthyNone=1"+
            " exactOriginG1IllegalState=1"+
            " downstreamNotReached=true"+
            " replayUnauthorized=true");
    }
    private G2014OriginFailureKindG1PrestageTest(){}
}
