package spk.local;
public final class G21357ForcedWorldPreexistingActiveAliasTest {
    private G21357ForcedWorldPreexistingActiveAliasTest() {}
    public static void main(String[] args) throws Exception {
        if (args.length != 0) throw new AssertionError("unexpected args");
        G21357ForcedWorldActiveAliasFixture.run(
            G21357ForcedWorldActiveAliasFixture.Scenario.PREEXISTING);
    }
}
