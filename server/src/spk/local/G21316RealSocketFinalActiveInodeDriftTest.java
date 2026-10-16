package spk.local;

/** G21.316: real final World save observes byte-identical ACTIVE replacement. */
public final class G21316RealSocketFinalActiveInodeDriftTest {
    private G21316RealSocketFinalActiveInodeDriftTest(){}
    public static void main(String[] args)throws Exception{
        if(args.length!=0)throw new AssertionError("G21.316 args");
        G21315RealSocketFinalWitnessSourceDriftFixture.run(true);
    }
}
