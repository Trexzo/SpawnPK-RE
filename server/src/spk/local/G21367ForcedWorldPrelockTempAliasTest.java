package spk.local;
public final class G21367ForcedWorldPrelockTempAliasTest {
    private G21367ForcedWorldPrelockTempAliasTest() {}
    public static void main(String[] args)throws Exception {
        if(args.length!=0)throw new AssertionError("unexpected arguments");
        G21367ForcedWorldTempHardlinkFixture.run(
            G21367ForcedWorldTempHardlinkFixture.Scenario.PRELOCK);
    }
}
