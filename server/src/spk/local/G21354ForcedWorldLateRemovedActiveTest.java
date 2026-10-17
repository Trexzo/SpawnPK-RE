package spk.local;
public final class G21354ForcedWorldLateRemovedActiveTest {
    private G21354ForcedWorldLateRemovedActiveTest(){}
    public static void main(String[] args)throws Exception{
        if(args.length!=0)throw new AssertionError("unexpected args");
        G21353ForcedWorldActiveFixture.run(
            G21353ForcedWorldActiveFixture.Scenario.LATE_DELETED);
    }
}
