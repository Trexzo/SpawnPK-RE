package spk.local;
public final class G21369ForcedWorldPostmoveAliasTest {
    private G21369ForcedWorldPostmoveAliasTest() {}
    public static void main(String[] args)throws Exception {
        if(args.length!=0)throw new AssertionError("unexpected arguments");
        G21367ForcedWorldTempHardlinkFixture.run(
            G21367ForcedWorldTempHardlinkFixture.Scenario.POSTMOVE);
    }
}
