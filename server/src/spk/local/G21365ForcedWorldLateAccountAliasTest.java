package spk.local;
public final class G21365ForcedWorldLateAccountAliasTest {
    private G21365ForcedWorldLateAccountAliasTest(){}
    public static void main(String[] args)throws Exception{
        if(args.length!=0)throw new AssertionError("unexpected args");
        G21364ForcedWorldAccountHardlinkFixture.run(
            G21364ForcedWorldAccountHardlinkFixture.Scenario.LATE);
    }
}
