package spk.local;

/** G21.315: real final World save observes byte-identical ACCOUNT replacement. */
public final class G21315RealSocketFinalAccountInodeDriftTest {
    private G21315RealSocketFinalAccountInodeDriftTest(){}
    public static void main(String[] args)throws Exception{
        if(args.length!=0)throw new AssertionError("G21.315 args");
        G21315RealSocketFinalWitnessSourceDriftFixture.run(false);
    }
}
