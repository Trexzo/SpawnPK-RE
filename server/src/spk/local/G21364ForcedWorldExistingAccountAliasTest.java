package spk.local;
public final class G21364ForcedWorldExistingAccountAliasTest {
    private G21364ForcedWorldExistingAccountAliasTest(){}
    public static void main(String[] args)throws Exception{
        if(args.length!=0)throw new AssertionError("unexpected args");
        G21364ForcedWorldAccountHardlinkFixture.run(
            G21364ForcedWorldAccountHardlinkFixture.Scenario.EXISTING);
    }
}
