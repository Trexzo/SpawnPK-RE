package spk.local;
public final class G21374StrictOneShotTokenAbsentProviderTest{
    private G21374StrictOneShotTokenAbsentProviderTest(){}
    public static void main(String[] args)throws Exception{
        if(args.length!=0)throw new AssertionError("unexpected arguments");
        G21374StrictOneShotTokenAdmissionFixture.run(
            G21374StrictOneShotTokenAdmissionFixture.Scenario.ABSENT);
    }
}
