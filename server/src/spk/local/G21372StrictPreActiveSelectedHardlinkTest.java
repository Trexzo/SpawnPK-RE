package spk.local;
public final class G21372StrictPreActiveSelectedHardlinkTest {
    private G21372StrictPreActiveSelectedHardlinkTest(){}
    public static void main(String[] args)throws Exception{
        if(args.length!=0)throw new AssertionError("unexpected args");
        G21371StrictPreActiveLinkProviderFixture.run(
            G21371StrictPreActiveLinkProviderFixture.Scenario.SELECTED_ALIAS);
    }
}
