package spk.local;
public final class G21373StrictPreActiveForeignHardlinkControlTest {
    private G21373StrictPreActiveForeignHardlinkControlTest(){}
    public static void main(String[] args)throws Exception{
        if(args.length!=0)throw new AssertionError("unexpected args");
        G21371StrictPreActiveLinkProviderFixture.run(
            G21371StrictPreActiveLinkProviderFixture.Scenario.FOREIGN_ALIAS);
    }
}
