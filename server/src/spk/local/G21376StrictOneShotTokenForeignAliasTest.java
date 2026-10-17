package spk.local;
public final class G21376StrictOneShotTokenForeignAliasTest{
    private G21376StrictOneShotTokenForeignAliasTest(){}
    public static void main(String[] args)throws Exception{
        if(args.length!=0)throw new AssertionError("unexpected arguments");
        G21374StrictOneShotTokenAdmissionFixture.run(
            G21374StrictOneShotTokenAdmissionFixture.Scenario.FOREIGN_ALIAS);
    }
}
