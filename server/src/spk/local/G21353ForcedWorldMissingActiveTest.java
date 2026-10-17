package spk.local;
public final class G21353ForcedWorldMissingActiveTest {
    private G21353ForcedWorldMissingActiveTest(){}
    public static void main(String[] args)throws Exception{
        if(args.length!=0)throw new AssertionError("unexpected args");
        G21353ForcedWorldActiveFixture.run(
            G21353ForcedWorldActiveFixture.Scenario.MISSING);
    }
}
