package spk.local;
public final class G21336FinalWitnessLateActiveDriftTest {
    private G21336FinalWitnessLateActiveDriftTest(){}
    public static void main(String[] args)throws Exception{
        if(args.length!=0)throw new AssertionError("unexpected args");
        G21335LateSelectedAuditFixture.run(
            G21335LateSelectedAuditFixture.Scenario.ACTIVE);
    }
}
