package spk.local;
public final class G21371StrictPreActiveAbsentAccountProviderTest {
    private G21371StrictPreActiveAbsentAccountProviderTest(){}
    public static void main(String[] args)throws Exception{
        if(args.length!=0)throw new AssertionError("unexpected args");
        G21371StrictPreActiveLinkProviderFixture.run(
            G21371StrictPreActiveLinkProviderFixture.Scenario.ABSENT);
    }
}
