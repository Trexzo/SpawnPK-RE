package spk.local;
public final class G21358ForcedWorldLateActiveAliasTest {
    private G21358ForcedWorldLateActiveAliasTest() {}
    public static void main(String[] args) throws Exception {
        if (args.length != 0) throw new AssertionError("unexpected args");
        G21357ForcedWorldActiveAliasFixture.run(
            G21357ForcedWorldActiveAliasFixture.Scenario.LATE);
    }
}
