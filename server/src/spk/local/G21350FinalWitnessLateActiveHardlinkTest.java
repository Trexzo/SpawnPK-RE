package spk.local;
public final class G21350FinalWitnessLateActiveHardlinkTest {
    private G21350FinalWitnessLateActiveHardlinkTest(){}
    public static void main(String[] args)throws Exception{
        if(args.length!=0)throw new AssertionError("unexpected args");
        G21349LateFinalHardlinkFixture.run(
            G21349LateFinalHardlinkFixture.Scenario.ACTIVE);
    }
}
