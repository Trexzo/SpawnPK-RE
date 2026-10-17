package spk.local;
public final class G21342FinalWitnessLateActivePublicationTest {
    private G21342FinalWitnessLateActivePublicationTest(){}
    public static void main(String[] args)throws Exception{
        if(args.length!=0)throw new AssertionError("unexpected args");
        G21341LatePublicationFixture.run(
            G21341LatePublicationFixture.Scenario.ACTIVE);
    }
}
