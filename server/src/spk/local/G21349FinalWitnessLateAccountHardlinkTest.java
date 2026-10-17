package spk.local;
public final class G21349FinalWitnessLateAccountHardlinkTest {
    private G21349FinalWitnessLateAccountHardlinkTest(){}
    public static void main(String[] args)throws Exception{
        if(args.length!=0)throw new AssertionError("unexpected args");
        G21349LateFinalHardlinkFixture.run(
            G21349LateFinalHardlinkFixture.Scenario.ACCOUNT);
    }
}
