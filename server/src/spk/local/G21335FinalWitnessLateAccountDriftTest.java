package spk.local;
public final class G21335FinalWitnessLateAccountDriftTest {
    private G21335FinalWitnessLateAccountDriftTest(){}
    public static void main(String[] args)throws Exception{
        if(args.length!=0)throw new AssertionError("unexpected args");
        G21335LateSelectedAuditFixture.run(
            G21335LateSelectedAuditFixture.Scenario.ACCOUNT);
    }
}
