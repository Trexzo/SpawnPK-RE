package spk.local;
public final class G21341FinalWitnessLateAccountPublicationTest {
    private G21341FinalWitnessLateAccountPublicationTest(){}
    public static void main(String[] args)throws Exception{
        if(args.length!=0)throw new AssertionError("unexpected args");
        G21341LatePublicationFixture.run(
            G21341LatePublicationFixture.Scenario.ACCOUNT);
    }
}
